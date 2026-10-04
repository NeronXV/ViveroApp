package com.intutec.viveroapp.feature.auth.backend

import com.intutec.viveroapp.core.network.BackendApiResponse
import com.intutec.viveroapp.core.network.BackendApiTransport
import com.intutec.viveroapp.feature.auth.data.remote.ApiBackendAuthRemoteDataSource
import com.intutec.viveroapp.feature.auth.data.remote.BackendAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class BackendAuthRemoteTest {
    @Test fun passwordChangePreservesWhitespaceAndRequiresExplicitRevocation() = runTest {
        val fake = Fake(BackendApiResponse(200, """{"schema_version":1,"password_changed":true,"sessions_revoked":true}"""))
        val source = ApiBackendAuthRemoteDataSource(fake)
        source.changePassword("a".repeat(43), " old phrase ", "  New synthetic phrase 2026  ")
        assertEquals("/api/v1/auth/change-password", fake.path)
        assertEquals("  New synthetic phrase 2026  ", Json.parseToJsonElement(fake.body).jsonObject["new_password"]!!.jsonPrimitive.content)
        assertEquals(1, fake.calls)
        fake.response = BackendApiResponse(200, """{"schema_version":1,"password_changed":true,"sessions_revoked":false}""")
        try { source.changePassword("a".repeat(43), "old", "Synthetic new phrase"); fail("Accepted unconfirmed revocation") }
        catch (error: BackendAuthException) { assertEquals(502, error.status) }
    }
    @Test fun passwordErrorsAreSafeAndRequestsAreNotRetried() = runTest {
        val fake = Fake(BackendApiResponse(403, """{"error":"CURRENT_PASSWORD_INCORRECT"}"""))
        try { ApiBackendAuthRemoteDataSource(fake).changePassword("a".repeat(43), "old", "Synthetic new phrase"); fail() }
        catch (error: BackendAuthException) { assertEquals("La contraseña actual no es correcta.", error.message) }
        assertEquals(1, fake.calls)
    }
    @Test fun passwordChangeAcceptsSixCharactersAndRejectsFiveBeforeTransport() = runTest {
        val fake = Fake(BackendApiResponse(200, """{"schema_version":1,"password_changed":true,"sessions_revoked":true}"""))
        val source = ApiBackendAuthRemoteDataSource(fake)
        try { source.changePassword("a".repeat(43), "old", "Ab9!x"); fail("Accepted five characters") }
        catch (_: IllegalArgumentException) { }
        assertEquals(0, fake.calls)
        source.changePassword("a".repeat(43), "old", "Ab9!xy")
        assertEquals(1, fake.calls)
    }
    private val token = "a".repeat(43)
    private val context = """{"schema_version":1,"user":{"id":2,"email":"demo@example.invalid","full_name":"Demo"},"access_state":"ACTIVE","role":{"id":1,"name":"MANAGER","display_name":"Gerencia"},"branch":{"id":3,"code":"DEMO","name":"Demo","is_active":true},"capabilities":["CREATE_SALES","VIEW_CATALOG"]}"""
    private class Fake(var response: BackendApiResponse) : BackendApiTransport {
        var body = ""; var path = ""; var calls = 0; var error: Exception? = null
        private fun result(): BackendApiResponse { calls++; error?.let { throw it }; return response }
        override suspend fun login(body: String): BackendApiResponse { this.body = body; path = "/api/v1/auth/login"; return result() }
        override suspend fun get(path: String, token: String): BackendApiResponse { this.path = path; return result() }
        override suspend fun post(path: String, token: String, headers: Map<String, String>, body: String): BackendApiResponse { this.path = path; this.body = body; return result() }
    }
    @Test fun loginPreservesPasswordAndUsesAnonymousTransport() = runTest {
        val fake = Fake(BackendApiResponse(200, """{"token_type":"Bearer","access_token":"$token","expires_in":3600}"""))
        val login = ApiBackendAuthRemoteDataSource(fake).login(" demo@example.invalid ", " password ")
        assertEquals(token, login.token); assertEquals(3600, login.expiresInSeconds)
        val body = Json.parseToJsonElement(fake.body).jsonObject
        assertEquals(" password ", body["password"]!!.jsonPrimitive.content)
        assertEquals("demo@example.invalid", body["email"]!!.jsonPrimitive.content)
        assertFalse(login.toString().contains(token))
    }
    @Test fun contextPreservesIntegerIdentityBranchAndCapabilities() = runTest {
        val fake = Fake(BackendApiResponse(200, context))
        val value = ApiBackendAuthRemoteDataSource(fake).context(token, 2)
        assertEquals(2L, value.user.id); assertEquals(3L, value.branch?.id)
        assertTrue(value.canOperate("CREATE_SALES")); assertEquals("/api/v1/auth/me", fake.path)
    }
    @Test fun rejectsUnknownRoleVersionFieldsDuplicateCapabilitiesAndChangedIdentity() = runTest {
        for (body in listOf(context.replace("\"id\":2", "\"id\":\"2\""), context.replace("\"schema_version\":1", "\"schema_version\":2"), context.replace("MANAGER", "UNKNOWN"), context.replace("\"VIEW_CATALOG\"", "\"CREATE_SALES\""), context.dropLast(1) + ",\"extra\":1}")) {
            try { ApiBackendAuthRemoteDataSource(Fake(BackendApiResponse(200, body))).context(token, 2); fail("Accepted bad context") }
            catch (error: BackendAuthException) { assertEquals(502, error.status) }
        }
        try { ApiBackendAuthRemoteDataSource(Fake(BackendApiResponse(200, context))).context(token, 9); fail("Accepted changed identity") }
        catch (error: BackendAuthException) { assertEquals(502, error.status) }
    }
    @Test fun inactiveBranchAndNoRoleGrantNoBranchOperations() = runTest {
        val inactive = ApiBackendAuthRemoteDataSource(Fake(BackendApiResponse(200, context.replace("\"is_active\":true", "\"is_active\":false")))).context(token)
        assertFalse(inactive.canOperate("CREATE_SALES"))
        val body = """{"schema_version":1,"user":{"id":2,"email":"demo@example.invalid","full_name":"Demo"},"access_state":"NO_ROLE","role":null,"branch":null,"capabilities":[]}"""
        val noRole = ApiBackendAuthRemoteDataSource(Fake(BackendApiResponse(200, body))).context(token)
        assertFalse(noRole.canOperate("CREATE_SALES")); assertNull(noRole.role)
    }
    @Test fun logoutRequiresPositiveConfirmationAndUsesOriginalToken() = runTest {
        val fake = Fake(BackendApiResponse(200, """{"signed_out":true}"""))
        val source = ApiBackendAuthRemoteDataSource(fake)
        source.logout(token); assertEquals("/api/v1/auth/logout", fake.path)
        fake.response = BackendApiResponse(200, """{"signed_out":false}""")
        try { source.logout(token); fail("Accepted unconfirmed logout") } catch (error: BackendAuthException) { assertEquals(502, error.status) }
    }
    @Test fun httpErrorsAreSafeDoNotRetryAndCancellationPropagates() = runTest {
        val fake = Fake(BackendApiResponse(429, "server-secret")); val source = ApiBackendAuthRemoteDataSource(fake)
        try { source.context(token); fail("Accepted HTTP error") } catch (error: BackendAuthException) { assertEquals(429, error.status); assertFalse(error.message!!.contains("server-secret")) }
        assertEquals(1, fake.calls)
        fake.error = CancellationException("cancelled")
        try { source.context(token); fail("Expected cancellation") } catch (_: CancellationException) { }
    }
    @Test fun validatesOpaqueTokenAndFixedLifetime() = runTest {
        for (body in listOf("""{"token_type":"Bearer","access_token":"jwt","expires_in":3600}""", """{"token_type":"Bearer","access_token":"$token","expires_in":7200}""")) {
            try { ApiBackendAuthRemoteDataSource(Fake(BackendApiResponse(200, body))).login("demo@example.invalid", "demo"); fail("Accepted incompatible login") }
            catch (error: BackendAuthException) { assertEquals(502, error.status) }
        }
    }
}
