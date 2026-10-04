package com.intutec.viveroapp.feature.auth.backend

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.auth.data.remote.BackendAuthException
import com.intutec.viveroapp.feature.auth.data.remote.BackendAuthRemoteDataSource
import com.intutec.viveroapp.feature.auth.data.remote.BackendLogin
import com.intutec.viveroapp.feature.auth.data.repository.BackendAuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BackendAuthRepositoryTest {
    private val token = "a".repeat(43)
    private val access = BackendAccessContext(BackendUser(2, "demo@example.invalid", "Demo"), "ACTIVE", BackendRole(1, UserRole.MANAGER, "Gerencia"), BackendBranch(3, "DEMO", "Demo", true), setOf("CREATE_SALES"))
    private inner class Fake : BackendAuthRemoteDataSource {
        var contextError: Exception? = null; var logoutError: Exception? = null
        var passwordError: Exception? = null; var passwordCalls = 0
        var contextGate: CompletableDeferred<BackendAccessContext>? = null
        var logouts = 0; var logins = 0
        override suspend fun login(email: String, password: String): BackendLogin { logins++; return BackendLogin(token, 3600) }
        override suspend fun context(token: String, expectedUserId: Long?): BackendAccessContext { contextError?.let { throw it }; return contextGate?.await() ?: access }
        override suspend fun changePassword(token: String, current: String, next: String) { passwordCalls++; passwordError?.let { throw it } }
        override suspend fun logout(token: String) { logouts++; logoutError?.let { throw it } }
    }
    @Test fun passwordSuccessClearsAuthorizationButDoesNotRequestNewSession() = runTest {
        val store = SessionStore(); val fake = Fake(); val repository = BackendAuthRepository(fake, store)
        repository.signIn("demo@example.invalid", "old")
        assertTrue(repository.changePassword("old", "Synthetic new phrase").isSuccess)
        assertNull(store.backend.value.session); assertNull(store.backend.value.context)
        assertEquals(1, fake.passwordCalls); assertEquals(1, fake.logins)
    }
    @Test fun wrongCurrentPasswordKeepsSessionAndDoesNotRetry() = runTest {
        val store = SessionStore(); val fake = Fake(); val repository = BackendAuthRepository(fake, store)
        repository.signIn("demo@example.invalid", "old"); fake.passwordError = BackendAuthException(403, "CURRENT_PASSWORD_INCORRECT")
        assertTrue(repository.changePassword("old", "Synthetic new phrase").isFailure)
        assertNotNull(store.backend.value.session); assertEquals(1, fake.passwordCalls)
    }
    @Test fun uncertainPasswordChangeClearsSessionAndNeverResends() = runTest {
        val store = SessionStore(); val fake = Fake(); val repository = BackendAuthRepository(fake, store)
        repository.signIn("demo@example.invalid", "old"); fake.passwordError = BackendAuthException(0)
        val result = repository.changePassword("old", "Synthetic new phrase")
        assertTrue(result.isFailure); assertNull(store.backend.value.session)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("No se confirmó")); assertEquals(1, fake.passwordCalls)
    }
    @Test fun publishesOnlyAfterContextAndDoesNotTouchLegacySession() = runTest {
        val fake = Fake(); fake.contextGate = CompletableDeferred()
        val store = SessionStore(); val repository = BackendAuthRepository(fake, store) { 1000 }
        val login = async { repository.signIn("demo@example.invalid", "demo") }; runCurrent()
        assertNull(store.backend.value.session); assertEquals(BackendAuthStatus.AUTHENTICATING, store.backend.value.status)
        fake.contextGate!!.complete(access); assertTrue(login.await().isSuccess)
        assertEquals(3601000L, store.backend.value.session?.expiresAtMillis)
        assertNull(store.session.value); assertEquals(BackendAccessStatus.READY, store.backend.value.accessStatus)
        assertFalse(store.backend.value.session.toString().contains(token))
    }
    @Test fun monitorExpiresUsingOwningCoroutineScope() = runTest {
        val store = SessionStore(); val repository = BackendAuthRepository(Fake(), store) { testScheduler.currentTime }
        repository.signIn("demo@example.invalid", "demo")
        backgroundScope.launch { repository.monitorExpiry() }; runCurrent()
        advanceTimeBy(3600000); runCurrent()
        assertNull(store.backend.value.session); assertNull(store.backend.value.context)
    }
    @Test fun transientRefreshKeepsTokenButDropsPermissions() = runTest {
        val store = SessionStore(); val fake = Fake(); val repository = BackendAuthRepository(fake, store)
        repository.signIn("demo@example.invalid", "demo"); fake.contextError = BackendAuthException(503)
        assertTrue(repository.refresh().isFailure)
        assertNotNull(store.backend.value.session); assertNull(store.backend.value.context)
        assertEquals(BackendAccessStatus.ERROR, store.backend.value.accessStatus)
        fake.contextError = null; assertTrue(repository.refresh().isSuccess)
    }
    @Test fun revokedRefreshAndExpiryBeforeRefreshClearIdentity() = runTest {
        val store = SessionStore(); val fake = Fake(); var now = 0L
        val repository = BackendAuthRepository(fake, store) { now }
        repository.signIn("demo@example.invalid", "demo"); fake.contextError = BackendAuthException(401)
        repository.refresh(); assertNull(store.backend.value.session)
        fake.contextError = null; repository.signIn("demo@example.invalid", "demo")
        now = 3600000; assertTrue(repository.refresh().isFailure); assertNull(store.backend.value.session)
    }
    @Test fun logoutInvalidatesLateRefreshEvenIfRevocationFails() = runTest {
        val store = SessionStore(); val fake = Fake(); val repository = BackendAuthRepository(fake, store)
        repository.signIn("demo@example.invalid", "demo"); fake.contextGate = CompletableDeferred()
        val refresh = async { repository.refresh() }; runCurrent()
        assertNull(store.backend.value.context)
        fake.logoutError = BackendAuthException(503)
        assertTrue(repository.signOut().isFailure); assertNull(store.backend.value.session)
        fake.contextGate!!.complete(access); assertTrue(refresh.await().isFailure)
        assertNull(store.backend.value.context)
    }
    @Test fun globalClearInvalidatesLateLoginAndRevokesItsToken() = runTest {
        val store = SessionStore(); val fake = Fake(); fake.contextGate = CompletableDeferred()
        val repository = BackendAuthRepository(fake, store)
        val login = async { repository.signIn("demo@example.invalid", "demo") }; runCurrent()
        assertTrue(repository.signIn("demo@example.invalid", "demo").isFailure)
        store.clear(); fake.contextGate!!.complete(access)
        assertTrue(login.await().isFailure); assertNull(store.backend.value.session); assertEquals(1, fake.logouts)
    }
    @Test fun cancelledLoginCleansPartialStateAndPropagatesCancellation() = runTest {
        val store = SessionStore(); val fake = Fake(); fake.contextGate = CompletableDeferred()
        val repository = BackendAuthRepository(fake, store)
        val login = async { repository.signIn("demo@example.invalid", "demo") }; runCurrent()
        login.cancel(); runCurrent()
        assertTrue(login.isCancelled); assertNull(store.backend.value.session); assertEquals(1, fake.logouts)
        assertEquals(BackendAuthStatus.ANONYMOUS, store.backend.value.status)
    }
    @Test fun failedContextRevokesPartialLoginAndDoesNotGrantAccess() = runTest {
        val store = SessionStore(); val fake = Fake(); fake.contextError = BackendAuthException(502)
        val repository = BackendAuthRepository(fake, store)
        assertTrue(repository.signIn("demo@example.invalid", "demo").isFailure)
        assertEquals(1, fake.logouts); assertNull(store.backend.value.session); assertNull(store.backend.value.context)
    }
}
