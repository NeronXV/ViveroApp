package com.intutec.viveroapp.feature.auth.data.remote

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.network.BackendApiResponse
import com.intutec.viveroapp.core.network.BackendApiTransport
import com.intutec.viveroapp.core.session.BackendAccessContext
import com.intutec.viveroapp.core.session.BackendBranch
import com.intutec.viveroapp.core.session.BackendRole
import com.intutec.viveroapp.core.session.BackendUser
import java.util.Collections
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

class BackendAuthException(val status: Int, code: String? = null) : IllegalStateException(
    when { code == "CURRENT_PASSWORD_INCORRECT" -> "La contraseña actual no es correcta."
        code == "PASSWORD_UNCHANGED" -> "Elige una contraseña diferente de la actual."
        status == 401 -> "Correo, contraseña o sesión no válidos."
        status == 429 -> "Demasiados intentos. Espera antes de volver a intentar."
        else -> "No fue posible completar la autenticación." },
)
class BackendLogin(val token: String, val expiresInSeconds: Int) {
    override fun toString(): String = "BackendLogin(token=<redacted>, expiresInSeconds=$expiresInSeconds)"
}
interface BackendAuthRemoteDataSource {
    suspend fun login(email: String, password: String): BackendLogin
    suspend fun context(token: String, expectedUserId: Long? = null): BackendAccessContext
    suspend fun logout(token: String)
    suspend fun changePassword(token: String, current: String, next: String)
}
class ApiBackendAuthRemoteDataSource @Inject constructor(private val transport: BackendApiTransport) : BackendAuthRemoteDataSource {
    override suspend fun login(email: String, password: String): BackendLogin {
        require(email.trim().length in 3..254 && email.contains('@')) { "Escribe un correo válido." }
        require(password.codePointCount(0, password.length) in 1..128 && !password.contains('\u0000')) { "Escribe una contraseña válida." }
        val r = call { transport.login(buildJsonObject { put("email", email.trim()); put("password", password) }.toString()) }
        return parse {
            exact(r, "token_type", "access_token", "expires_in")
            check(text(r, "token_type") == "Bearer" && integer(r, "expires_in") == 3600L)
            BackendLogin(token(text(r, "access_token")), 3600)
        }
    }
    override suspend fun context(token: String, expectedUserId: Long?): BackendAccessContext {
        val r = call { transport.get("/api/v1/auth/me", token(token)) }
        return parse {
            exact(r, "schema_version", "user", "access_state", "role", "branch", "capabilities")
            check(integer(r, "schema_version") == 1L)
            val u = r["user"] as JsonObject
            exact(u, "id", "email", "full_name")
            val user = BackendUser(integer(u, "id"), text(u, "email"), text(u, "full_name"))
            check(expectedUserId == null || expectedUserId == user.id)
            val role = if (r["role"] == JsonNull) null else {
                val value = r["role"] as JsonObject; exact(value, "id", "name", "display_name")
                BackendRole(integer(value, "id"), UserRole.valueOf(text(value, "name")), text(value, "display_name"))
            }
            val branch = if (r["branch"] == JsonNull) null else {
                val value = r["branch"] as JsonObject; exact(value, "id", "code", "name", "is_active")
                BackendBranch(integer(value, "id"), text(value, "code"), text(value, "name"), boolean(value, "is_active"))
            }
            val accessState = text(r, "access_state")
            check(accessState in setOf("ACTIVE", "NO_ROLE") && (accessState == "ACTIVE") == (role != null))
            val capabilities = (r["capabilities"] as JsonArray).map {
                val value = it as JsonPrimitive; check(value.isString && Regex("^[A-Z][A-Z0-9_]{2,63}$").matches(value.content)); value.content
            }
            check(capabilities.distinct().size == capabilities.size && (role != null || capabilities.isEmpty()))
            BackendAccessContext(user, accessState, role, branch, Collections.unmodifiableSet(capabilities.toSet()))
        }
    }
    override suspend fun logout(token: String) {
        val r = call { transport.post("/api/v1/auth/logout", token(token), emptyMap(), "{}") }
        parse { exact(r, "signed_out"); check(boolean(r, "signed_out")) }
    }
    override suspend fun changePassword(token: String, current: String, next: String) {
        require(current.codePointCount(0, current.length) in 1..128 && !current.contains('\u0000'))
        require(next.codePointCount(0, next.length) in 6..128 && !next.contains('\u0000') && current != next)
        val response = try { transport.post("/api/v1/auth/change-password", token(token), emptyMap(),
            buildJsonObject { put("current_password", current); put("new_password", next) }.toString()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw BackendAuthException(0) }
        if (response.status != 200) {
            val code = runCatching { Json.parseToJsonElement(response.body).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
            throw BackendAuthException(response.status, code.takeIf { it in setOf("CURRENT_PASSWORD_INCORRECT", "PASSWORD_UNCHANGED") })
        }
        parse {
            val result = Json.parseToJsonElement(response.body).jsonObject
            exact(result, "schema_version", "password_changed", "sessions_revoked")
            check(integer(result, "schema_version") == 1L && boolean(result, "password_changed") && boolean(result, "sessions_revoked"))
        }
    }
    private suspend fun call(action: suspend () -> BackendApiResponse): JsonObject {
        val response = try { action() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw BackendAuthException(0) }
        if (response.status !in 200..299) throw BackendAuthException(response.status)
        return parse { Json.parseToJsonElement(response.body) as JsonObject }
    }
    private fun <T> parse(action: () -> T): T = try { action() } catch (_: Exception) { throw BackendAuthException(502) }
    private fun exact(r: JsonObject, vararg fields: String) { check(r.keys == fields.toSet()) }
    private fun integer(r: JsonObject, name: String): Long {
        val value = r[name] as JsonPrimitive; check(!value.isString)
        return checkNotNull(value.longOrNull).also { check(it in 1..4294967295L) }
    }
    private fun boolean(r: JsonObject, name: String): Boolean {
        val value = r[name] as JsonPrimitive; check(!value.isString); return checkNotNull(value.booleanOrNull)
    }
    private fun text(r: JsonObject, name: String): String {
        val value = r[name] as JsonPrimitive
        check(value.isString && value.content.isNotBlank() && value.content.none { it.code < 32 || it.code in 127..159 })
        return value.content
    }
    private fun token(value: String): String { check(Regex("^[A-Za-z0-9_-]{43}$").matches(value)); return value }
}
