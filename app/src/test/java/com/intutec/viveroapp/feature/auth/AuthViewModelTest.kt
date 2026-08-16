package com.intutec.viveroapp.feature.auth

import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.RolePermissions
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.UserBranch
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.auth.domain.repository.AuthRepository
import com.intutec.viveroapp.feature.auth.presentation.AuthStatus
import com.intutec.viveroapp.feature.auth.presentation.AuthViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun `restored session opens authenticated state`() = runTest(dispatcherRule.testDispatcher) {
        val session = demoSession()
        val viewModel = AuthViewModel(FakeAuthRepository(restored = session))

        advanceUntilIdle()

        assertEquals(AuthStatus.AUTHENTICATED, viewModel.uiState.value.status)
        assertEquals(session, viewModel.uiState.value.session)
    }

    @Test
    fun `slow initialization stays on checking without login flash then restores owner`() =
        runTest(dispatcherRule.testDispatcher) {
            val restoration = CompletableDeferred<Result<UserSession?>>()
            val session = ownerSession()
            val viewModel = AuthViewModel(FakeAuthRepository(restoration = restoration))

            assertEquals(AuthStatus.CHECKING, viewModel.uiState.value.status)
            assertEquals(null, viewModel.uiState.value.session)

            restoration.complete(Result.success(session))
            advanceUntilIdle()

            assertEquals(AuthStatus.AUTHENTICATED, viewModel.uiState.value.status)
            assertEquals("OWNER", viewModel.uiState.value.session?.role?.name)
            assertEquals("CENTRO", viewModel.uiState.value.session?.branch?.code)
        }

    @Test
    fun `initialization without stored session shows login`() = runTest(dispatcherRule.testDispatcher) {
        val restoration = CompletableDeferred<Result<UserSession?>>()
        val viewModel = AuthViewModel(FakeAuthRepository(restoration = restoration))

        assertEquals(AuthStatus.CHECKING, viewModel.uiState.value.status)
        restoration.complete(Result.success(null))
        advanceUntilIdle()

        assertEquals(AuthStatus.SIGNED_OUT, viewModel.uiState.value.status)
    }

    @Test
    fun `refresh failure leaves checking and presents recoverable message`() =
        runTest(dispatcherRule.testDispatcher) {
            val restoration = CompletableDeferred<Result<UserSession?>>()
            val viewModel = AuthViewModel(FakeAuthRepository(restoration = restoration))

            restoration.complete(Result.failure(IllegalStateException("Problema transitorio de sesión.")))
            advanceUntilIdle()

            assertEquals(AuthStatus.SIGNED_OUT, viewModel.uiState.value.status)
            assertTrue(viewModel.uiState.value.errorMessage?.contains("transitorio") == true)
        }

    @Test
    fun `invalid session leaves checking and presents understandable message`() =
        runTest(dispatcherRule.testDispatcher) {
            val restoration = CompletableDeferred<Result<UserSession?>>()
            val viewModel = AuthViewModel(FakeAuthRepository(restoration = restoration))

            restoration.complete(Result.failure(IllegalStateException("Tu sesión ya no es válida.")))
            advanceUntilIdle()

            assertEquals(AuthStatus.SIGNED_OUT, viewModel.uiState.value.status)
            assertEquals("Tu sesión ya no es válida.", viewModel.uiState.value.errorMessage)
            assertEquals(null, viewModel.uiState.value.session)
        }

    @Test
    fun `demo access authenticates without remote configuration`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = AuthViewModel(FakeAuthRepository())
        advanceUntilIdle()

        viewModel.signInDemo()
        advanceUntilIdle()

        assertEquals(AuthStatus.AUTHENTICATED, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.session?.isDemo == true)
    }

    private class FakeAuthRepository(
        private val restored: UserSession? = null,
        private val restoration: CompletableDeferred<Result<UserSession?>>? = null,
    ) : AuthRepository {
        override val isRemoteConfigured = false
        override val isDemoAvailable = true
        override suspend fun restoreSession() = restoration?.await() ?: Result.success(restored)
        override suspend fun signIn(email: String, password: String) = Result.success(demoSession())
        override suspend fun signInDemo() = Result.success(demoSession())
        override suspend fun sendPasswordReset(email: String) = Result.success(Unit)
        override suspend fun signOut() = Result.success(Unit)
    }

    companion object {
        private fun demoSession() = UserSession(
            userId = "demo",
            email = "demo@vivero.test",
            fullName = "Demo",
            role = UserRole.SALES,
            capabilities = RolePermissions.permissionsFor(UserRole.SALES),
            branch = UserBranch("demo-branch", "CENTRO", "Centro", true),
            mode = SessionMode.DEMO,
        )

        private fun ownerSession() = UserSession(
            userId = "owner",
            email = "",
            fullName = "Owner",
            role = UserRole.OWNER,
            capabilities = RolePermissions.permissionsFor(UserRole.OWNER),
            branch = UserBranch("branch", "CENTRO", "Sucursal Centro", true),
            mode = SessionMode.REMOTE,
        )
    }
}
