package com.intutec.viveroapp.feature.home

import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.common.UiState
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.feature.home.domain.model.Dashboard
import com.intutec.viveroapp.feature.home.domain.model.DashboardModule
import com.intutec.viveroapp.feature.home.domain.repository.DashboardRepository
import com.intutec.viveroapp.feature.home.domain.usecase.GetDashboardUseCase
import com.intutec.viveroapp.feature.home.presentation.HomeViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun `loads dashboard from repository`() = runTest(dispatcherRule.testDispatcher) {
        val expected = demoDashboard()
        val viewModel = HomeViewModel(GetDashboardUseCase(StubRepository(Result.success(expected))))

        advanceUntilIdle()

        assertEquals(UiState.Success(expected), viewModel.uiState.value)
    }

    @Test
    fun `shows understandable error when repository fails`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = HomeViewModel(GetDashboardUseCase(StubRepository(Result.failure(IllegalStateException("Sin conexión")))))

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("Sin conexión", (state as UiState.Error).message)
    }

    private class StubRepository(private val result: Result<Dashboard>) : DashboardRepository {
        override suspend fun getDashboard(): Result<Dashboard> = result
    }

    private fun demoDashboard() = Dashboard(
        userName = "Prueba",
        role = UserRole.WORKER,
        branchName = "Centro",
        pendingTickets = 0,
        lowStockProducts = 0,
        activePromotions = 0,
        modules = listOf(DashboardModule("catalog", "Catálogo", "Productos", true)),
    )
}
