package com.intutec.viveroapp.feature.cashier

import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserBranch
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.cashier.data.remote.CashierRemoteDataSource
import com.intutec.viveroapp.feature.cashier.data.repository.SupabaseCashierRepository
import com.intutec.viveroapp.feature.cashier.domain.usecase.GetCashierOrderDetailUseCase
import com.intutec.viveroapp.feature.cashier.domain.usecase.GetPendingCashierOrdersUseCase
import com.intutec.viveroapp.feature.cashier.presentation.CashierDetailUiState
import com.intutec.viveroapp.feature.cashier.presentation.CashierDetailViewModel
import com.intutec.viveroapp.feature.cashier.presentation.CashierQueueUiState
import com.intutec.viveroapp.feature.cashier.presentation.CashierQueueViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CashierViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun `queue exposes content and empty distinctly and preserves content on refresh failure`() = runTest(dispatcherRule.testDispatcher) {
        val contentRemote = StubRemote(listOf(CashierRepositoryTest.order()))
        val contentVm = queueViewModel(contentRemote)
        val emptyVm = queueViewModel(StubRemote(emptyList()))

        contentVm.loadOnce()
        emptyVm.loadOnce()

        assertTrue(contentVm.uiState.value is CashierQueueUiState.Content)
        assertTrue(emptyVm.uiState.value is CashierQueueUiState.Empty)

        contentRemote.failure = IllegalStateException("technical detail")
        contentVm.refresh()
        advanceUntilIdle()

        val preserved = contentVm.uiState.value as CashierQueueUiState.Content
        assertEquals(1, preserved.orders.size)
        assertTrue(preserved.refreshError?.contains("No pudimos actualizar") == true)
        assertTrue(!preserved.refreshError.orEmpty().contains("technical detail"))
    }

    @Test
    fun `queue shows friendly error`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = queueViewModel(StubRemote(failure = IllegalStateException("technical detail")))

        viewModel.loadOnce()

        val state = viewModel.uiState.value as CashierQueueUiState.Error
        assertTrue(state.message.contains("No pudimos actualizar"))
        assertTrue(!state.message.contains("technical detail"))
    }

    @Test
    fun `polling stops while queue is hidden`() = runTest(dispatcherRule.testDispatcher) {
        val remote = StubRemote(listOf(CashierRepositoryTest.order()))
        val viewModel = queueViewModel(remote)

        viewModel.onVisible()
        runCurrent()
        assertEquals(1, remote.queueCalls)
        viewModel.onHidden()
        advanceTimeBy(90_000)
        runCurrent()

        assertEquals(1, remote.queueCalls)
    }

    @Test
    fun `detail loads real order preserves it on refresh failure and maps initial failure to error`() = runTest(dispatcherRule.testDispatcher) {
        val store = activeStore()
        val successRemote = StubRemote(listOf(CashierRepositoryTest.order()))
        val successRepository = SupabaseCashierRepository(successRemote)
        val success = CashierDetailViewModel(GetCashierOrderDetailUseCase(successRepository, store))
        success.load(CashierRepositoryTest.SALE_ID)
        advanceUntilIdle()
        assertTrue(success.uiState.value is CashierDetailUiState.Content)

        successRemote.failure = IllegalStateException("technical detail")
        success.refresh()
        advanceUntilIdle()
        val preserved = success.uiState.value as CashierDetailUiState.Content
        assertEquals(CashierRepositoryTest.SALE_ID, preserved.order.summary.id)
        assertTrue(preserved.refreshError?.contains("No pudimos actualizar") == true)
        assertTrue(!preserved.refreshError.orEmpty().contains("technical detail"))

        val missingRepository = SupabaseCashierRepository(StubRemote(emptyList()))
        val missing = CashierDetailViewModel(GetCashierOrderDetailUseCase(missingRepository, store))
        missing.load(CashierRepositoryTest.SALE_ID)
        advanceUntilIdle()
        assertTrue(missing.uiState.value is CashierDetailUiState.Error)
    }

    private fun queueViewModel(remote: CashierRemoteDataSource): CashierQueueViewModel {
        val store = activeStore()
        val repository = SupabaseCashierRepository(remote)
        return CashierQueueViewModel(GetPendingCashierOrdersUseCase(repository, store), store)
    }

    private fun activeStore() = SessionStore().apply {
        update(
            UserSession(
                userId = CashierRepositoryTest.USER_ID,
                email = "owner@example.test",
                fullName = "Owner Test",
                role = UserRole.OWNER,
                capabilities = setOf(AppPermission.OPERATE_CASHIER),
                branch = UserBranch(
                    CashierRepositoryTest.BRANCH_ID,
                    "CENTRO",
                    "Sucursal Centro",
                    true,
                ),
                mode = SessionMode.REMOTE,
            ),
        )
    }

    private class StubRemote(
        private val orders: List<com.intutec.viveroapp.feature.cashier.data.remote.RemoteCashierOrderDto> = emptyList(),
        var failure: Throwable? = null,
    ) : CashierRemoteDataSource {
        var queueCalls = 0
        override suspend fun loadPendingOrders(branchId: String): List<com.intutec.viveroapp.feature.cashier.data.remote.RemoteCashierOrderDto> {
            queueCalls += 1
            failure?.let { throw it }
            return orders
        }

        override suspend fun loadPendingOrder(branchId: String, orderId: String): List<com.intutec.viveroapp.feature.cashier.data.remote.RemoteCashierOrderDto> {
            failure?.let { throw it }
            return orders.filter { it.id == orderId }
        }
    }
}

internal fun sampleDetail() = SupabaseCashierRepository(
    object : CashierRemoteDataSource {
        override suspend fun loadPendingOrders(branchId: String) = listOf(CashierRepositoryTest.order())
        override suspend fun loadPendingOrder(branchId: String, orderId: String) = listOf(CashierRepositoryTest.order())
    },
).let { repository ->
    kotlinx.coroutines.runBlocking {
        repository.getPendingOrder(CashierRepositoryTest.BRANCH_ID, CashierRepositoryTest.SALE_ID).getOrThrow()
    }
}
