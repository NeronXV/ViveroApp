package com.intutec.viveroapp.feature.auth.data.repository

import com.intutec.viveroapp.core.session.BackendAccessStatus
import com.intutec.viveroapp.core.session.BackendAuthStatus
import com.intutec.viveroapp.core.session.BackendSession
import com.intutec.viveroapp.core.session.BackendSessionState
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.auth.data.remote.BackendAuthException
import com.intutec.viveroapp.feature.auth.data.remote.BackendAuthRemoteDataSource
import com.intutec.viveroapp.feature.auth.domain.repository.BackendAuthGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class BackendAuthRepository(
    private val remote: BackendAuthRemoteDataSource,
    private val store: SessionStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : BackendAuthGateway {
    override val state = store.backend
    private val operation = Mutex()
    private fun safe(error: Exception) = if (error is BackendAuthException) error.message else "No fue posible completar la autenticación."
    private suspend fun revoke(token: String) = withContext(NonCancellable) {
        withTimeoutOrNull(8000) { try { remote.logout(token) } catch (_: Exception) { } }
    }
    override suspend fun signIn(email: String, password: String): Result<Unit> {
        if (!operation.tryLock()) return Result.failure(IllegalStateException("Hay una operación de acceso en curso."))
        if (state.value.session != null) { operation.unlock(); return Result.failure(IllegalStateException("Cierra la sesión antes de cambiar de cuenta.")) }
        val revision = store.beginBackendChange(BackendSessionState(status = BackendAuthStatus.AUTHENTICATING))
        var token: String? = null
        val started = nowMillis()
        try {
            val login = remote.login(email.trim(), password); token = login.token
            val context = remote.context(login.token)
            val expiresAt = Math.addExact(started, login.expiresInSeconds.toLong() * 1000)
            check(nowMillis() < expiresAt)
            val session = BackendSession(login.token, context.user.id, expiresAt)
            if (!store.publishBackend(revision, BackendSessionState(session, context, BackendAuthStatus.AUTHENTICATED, BackendAccessStatus.READY))) {
                revoke(login.token)
                return Result.failure(IllegalStateException("La operación de acceso fue cancelada."))
            }
            return Result.success(Unit)
        } catch (cancelled: CancellationException) {
            store.publishBackend(revision, BackendSessionState())
            token?.let { revoke(it) }; throw cancelled
        } catch (error: Exception) {
            store.publishBackend(revision, BackendSessionState(error = safe(error)))
            token?.let { revoke(it) }; return Result.failure(BackendAuthException(if (error is BackendAuthException) error.status else 0))
        } finally { operation.unlock() }
    }
    override suspend fun refresh(): Result<Unit> {
        store.expireBackend(nowMillis())
        if (!operation.tryLock()) return Result.failure(IllegalStateException("Hay una operación de acceso en curso."))
        val session = state.value.session
        if (session == null) { operation.unlock(); return Result.failure(BackendAuthException(401)) }
        val revision = store.beginBackendChange(BackendSessionState(session, status = BackendAuthStatus.AUTHENTICATED, accessStatus = BackendAccessStatus.LOADING))
        try {
            val context = remote.context(session.token, session.userId)
            if (nowMillis() >= session.expiresAtMillis) { store.expireBackend(nowMillis()); return Result.failure(BackendAuthException(401)) }
            if (!store.publishBackend(revision, BackendSessionState(session, context, BackendAuthStatus.AUTHENTICATED, BackendAccessStatus.READY))) return Result.failure(BackendAuthException(401))
            return Result.success(Unit)
        } catch (cancelled: CancellationException) {
            store.publishBackend(revision, BackendSessionState(session, status = BackendAuthStatus.AUTHENTICATED, accessStatus = BackendAccessStatus.ERROR, error = "La consulta de permisos se interrumpió."))
            throw cancelled
        } catch (error: Exception) {
            val next = if (error is BackendAuthException && error.status == 401) BackendSessionState(error = safe(error))
                else BackendSessionState(session, status = BackendAuthStatus.AUTHENTICATED, accessStatus = BackendAccessStatus.ERROR, error = safe(error))
            store.publishBackend(revision, next)
            return Result.failure(BackendAuthException(if (error is BackendAuthException) error.status else 0))
        } finally { operation.unlock() }
    }
    override suspend fun signOut(): Result<Unit> {
        val token = state.value.session?.token
        store.clearBackend() // invalidates access and late requests before remote I/O
        if (token == null) return Result.success(Unit)
        return try { remote.logout(token); Result.success(Unit) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.failure(IllegalStateException("Sesión cerrada localmente; no se confirmó la revocación en el servidor.")) }
    }
    override suspend fun changePassword(current: String, next: String): Result<Unit> {
        if (!operation.tryLock()) return Result.failure(IllegalStateException("Hay una operación de acceso en curso."))
        try {
            store.expireBackend(nowMillis())
            val identity = state.value.session ?: return Result.failure(BackendAuthException(401))
            if (current.codePointCount(0, current.length) !in 1..128 || current.contains('\u0000') ||
                next.codePointCount(0, next.length) !in 6..128 || next.contains('\u0000') || current == next)
                return Result.failure(BackendAuthException(400))
            remote.changePassword(identity.token, current, next)
            // Clear all local authorization; the server has revoked every session.
            if (state.value.session?.token == identity.token) store.clearBackend()
            return Result.success(Unit)
        } catch (cancelled: CancellationException) {
            store.clearBackend(); throw cancelled
        } catch (error: Exception) {
            if (error is BackendAuthException && error.status in setOf(400, 403, 429)) return Result.failure(error)
            store.clearBackend()
            return Result.failure(IllegalStateException("No se confirmó el cambio. Inicia sesión con la contraseña nueva; si no funciona, prueba la anterior antes de solicitar recuperación."))
        } finally { operation.unlock() }
    }
    override suspend fun monitorExpiry() {
        state.collectLatest { current ->
            val session = current.session ?: return@collectLatest
            delay((session.expiresAtMillis - nowMillis()).coerceAtLeast(0))
            store.expireBackend(nowMillis())
        }
    }
}
