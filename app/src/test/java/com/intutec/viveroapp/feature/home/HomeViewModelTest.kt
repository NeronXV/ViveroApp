package com.intutec.viveroapp.feature.home

import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.common.UiState
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.cart.domain.model.Cart
import com.intutec.viveroapp.feature.cart.domain.model.CartCustomer
import com.intutec.viveroapp.feature.cart.domain.model.CartItem
import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import com.intutec.viveroapp.feature.cart.domain.repository.CartRepository
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.home.domain.model.Dashboard
import com.intutec.viveroapp.feature.home.domain.model.DashboardModule
import com.intutec.viveroapp.feature.home.domain.repository.DashboardRepository
import com.intutec.viveroapp.feature.home.domain.usecase.GetDashboardUseCase
import com.intutec.viveroapp.feature.home.presentation.HomeViewModel
import com.intutec.viveroapp.feature.home.presentation.HomeContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
        val cart = Cart()
        val viewModel = HomeViewModel(GetDashboardUseCase(StubRepository(Result.success(expected))), StubCartRepository(cart))

        advanceUntilIdle()

        assertEquals(UiState.Success(HomeContent(expected, cart)), viewModel.uiState.value)
    }

    @Test
    fun `shows understandable error when repository fails`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = HomeViewModel(
            GetDashboardUseCase(StubRepository(Result.failure(IllegalStateException("Sin conexión")))),
            StubCartRepository(Cart()),
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("Sin conexión", (state as UiState.Error).message)
    }

    @Test
    fun `exposes quantity and total from observed Room cart`() = runTest(dispatcherRule.testDispatcher) {
        val cart = Cart(
            items = listOf(
                CartItem(
                    productId = "00000000-0000-4000-8000-000000000001",
                    internalCode = "TEST-001",
                    name = "Producto",
                    imageKey = "",
                    unit = "pieza",
                    listPriceCents = 12_500,
                    unitPriceCents = 10_000,
                    quantity = 3,
                    stockAvailable = 0,
                    stockKnown = false,
                ),
            ),
        )
        val viewModel = HomeViewModel(
            GetDashboardUseCase(StubRepository(Result.success(demoDashboard()))),
            StubCartRepository(cart),
        )

        advanceUntilIdle()

        val content = (viewModel.uiState.value as UiState.Success).data
        assertEquals(3, content.cart.itemCount)
        assertEquals(30_000, content.cart.totalCents)
    }

    private class StubRepository(private val result: Result<Dashboard>) : DashboardRepository {
        override suspend fun getDashboard(): Result<Dashboard> = result
    }

    private class StubCartRepository(private val cart: Cart) : CartRepository {
        override fun observeCart(): Flow<Cart> = flowOf(cart)
        override suspend fun addProduct(product: Product) = Result.success(Unit)
        override suspend fun changeQuantity(productId: String, quantity: Int) = Result.success(Unit)
        override suspend fun removeProduct(productId: String) = Result.success(Unit)
        override suspend fun associateCustomer(customer: CartCustomer?) = Result.success(Unit)
        override suspend fun saveDraft() = Result.success(Unit)
        override suspend fun cancelCart() = Result.success(Unit)
        override suspend fun createPendingSale(session: UserSession): Result<SaleTicket> = error("No se usa en Inicio")
    }

    private fun demoDashboard() = Dashboard(
        userName = "Prueba",
        role = UserRole.SALES,
        branchName = "Centro",
        sessionMode = SessionMode.REMOTE,
        canCreateSales = true,
        modules = listOf(DashboardModule("catalog", "Catálogo", "Productos", true)),
    )
}
