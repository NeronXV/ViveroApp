package com.intutec.viveroapp.feature.auth

import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.auth.domain.repository.AuthRepository
import com.intutec.viveroapp.feature.auth.presentation.AuthStatus
import com.intutec.viveroapp.feature.auth.presentation.AuthViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun `demo access authenticates without remote configuration`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = AuthViewModel(FakeAuthRepository())
        advanceUntilIdle()

        viewModel.signInDemo()
        advanceUntilIdle()

        assertEquals(AuthStatus.AUTHENTICATED, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.session?.isDemo == true)
    }

    private class FakeAuthRepository(private val restored: UserSession? = null) : AuthRepository {
        override val isRemoteConfigured = false
        override suspend fun restoreSession() = Result.success(restored)
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
            branchName = "Centro",
            isDemo = true,
        )
    }
}
