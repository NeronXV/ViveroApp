package com.intutec.viveroapp.feature.catalog

import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.cart.domain.model.Cart
import com.intutec.viveroapp.feature.cart.domain.model.CartCustomer
import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import com.intutec.viveroapp.feature.cart.domain.repository.CartRepository
import com.intutec.viveroapp.feature.cart.domain.usecase.AddProductToCartUseCase
import com.intutec.viveroapp.feature.catalog.domain.model.CatalogSnapshot
import com.intutec.viveroapp.feature.catalog.domain.model.Category
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.catalog.domain.repository.CatalogRepository
import com.intutec.viveroapp.feature.catalog.domain.usecase.FilterProductsUseCase
import com.intutec.viveroapp.feature.catalog.domain.usecase.ObserveCatalogUseCase
import com.intutec.viveroapp.feature.catalog.presentation.CatalogUiState
import com.intutec.viveroapp.feature.catalog.presentation.CatalogViewModel
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun `initial catalog shows products even when branch stock is zero`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = CatalogViewModel(
            observeCatalog = ObserveCatalogUseCase(StubCatalogRepository()),
            filterProducts = FilterProductsUseCase(),
            addProductToCart = AddProductToCartUseCase(StubCartRepository()),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CatalogUiState.Content)
        state as CatalogUiState.Content
        assertFalse(state.availableOnly)
        assertEquals(listOf("available", "without-stock"), state.products.map(Product::id))
    }

    private class StubCatalogRepository : CatalogRepository {
        override fun observeCatalog(): Flow<CatalogSnapshot> = flowOf(
            CatalogSnapshot(
                categories = listOf(CATEGORY),
                products = listOf(product("available", 4), product("without-stock", 0)),
            ),
        )

        override suspend fun getProduct(productId: String): Result<Product> = error("No se usa")
        override suspend fun findProductByCode(code: String): Result<Product?> = error("No se usa")
    }

    private class StubCartRepository : CartRepository {
        override fun observeCart(): Flow<Cart> = flowOf(Cart())
        override suspend fun addProduct(product: Product) = Result.success(Unit)
        override suspend fun changeQuantity(productId: String, quantity: Int) = Result.success(Unit)
        override suspend fun removeProduct(productId: String) = Result.success(Unit)
        override suspend fun associateCustomer(customer: CartCustomer?) = Result.success(Unit)
        override suspend fun saveDraft() = Result.success(Unit)
        override suspend fun cancelCart() = Result.success(Unit)
        override suspend fun createPendingSale(session: UserSession): Result<SaleTicket> = error("No se usa")
    }

    private companion object {
        val CATEGORY = Category("category", "Interior")

        fun product(id: String, stock: Int) = Product(
            id = id,
            internalCode = id,
            barcode = null,
            commonName = id,
            scientificName = null,
            description = "",
            category = CATEGORY,
            priceCents = 10_000,
            wholesalePriceCents = null,
            unit = "pieza",
            stockAvailable = stock,
            minimumStock = 0,
            imageKey = "",
            wateringAdvice = "",
            lightType = "",
            recommendedClimate = "",
            isActive = true,
            promotion = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
