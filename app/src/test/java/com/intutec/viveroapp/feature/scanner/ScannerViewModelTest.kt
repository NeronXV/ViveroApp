package com.intutec.viveroapp.feature.scanner

import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.feature.cart.domain.model.Cart
import com.intutec.viveroapp.feature.cart.domain.model.CartCustomer
import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import com.intutec.viveroapp.feature.cart.domain.repository.CartRepository
import com.intutec.viveroapp.feature.cart.domain.usecase.AddProductToCartUseCase
import com.intutec.viveroapp.feature.catalog.domain.model.CatalogSnapshot
import com.intutec.viveroapp.feature.catalog.domain.model.Category
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.catalog.domain.repository.CatalogRepository
import com.intutec.viveroapp.feature.scanner.domain.model.ScanFormat
import com.intutec.viveroapp.feature.scanner.domain.usecase.FindProductByCodeUseCase
import com.intutec.viveroapp.feature.scanner.presentation.ScanResultState
import com.intutec.viveroapp.feature.scanner.presentation.ScannerViewModel
import com.intutec.viveroapp.core.session.UserSession
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `valid QR and manual entry use the same normalized internal code lookup`() = runTest {
        val catalog = RecordingCatalogRepository { Result.success(product()) }
        val viewModel = viewModel(catalog)

        viewModel.onCodeDetected("  PL-ALOE-001  ", ScanFormat.QR)
        advanceUntilIdle()
        val qrResult = viewModel.uiState.value.result as ScanResultState.Found

        viewModel.scanAgain()
        viewModel.onCodeDetected("PL-ALOE-001", ScanFormat.MANUAL)
        advanceUntilIdle()
        val manualResult = viewModel.uiState.value.result as ScanResultState.Found

        assertEquals(listOf("PL-ALOE-001", "PL-ALOE-001"), catalog.codes)
        assertEquals(ScanFormat.QR, qrResult.format)
        assertEquals(ScanFormat.MANUAL, manualResult.format)
        assertEquals(qrResult.product, manualResult.product)
    }

    @Test
    fun `unknown internal code presents not found and remains retryable`() = runTest {
        val viewModel = viewModel(RecordingCatalogRepository { Result.success(null) })

        viewModel.onCodeDetected("PL-NO-EXISTE", ScanFormat.QR)
        advanceUntilIdle()

        val result = viewModel.uiState.value.result as ScanResultState.NotFound
        assertEquals("PL-NO-EXISTE", result.code)
        viewModel.scanAgain()
        assertEquals(ScanResultState.Ready, viewModel.uiState.value.result)
    }

    @Test
    fun `intentional rescan can add the same product a second time`() = runTest {
        val cart = RecordingCartRepository()
        val viewModel = viewModel(RecordingCatalogRepository { Result.success(product()) }, cart)

        repeat(2) {
            viewModel.onCodeDetected("PL-ALOE-001", ScanFormat.QR)
            advanceUntilIdle()
            viewModel.addCurrentProductToCart()
            advanceUntilIdle()
            viewModel.scanAgain()
        }

        assertEquals(listOf("aloe", "aloe"), cart.addedProductIds)
    }

    @Test
    fun `reset cancels stale lookup and only the latest code updates state`() = runTest {
        var staleLookupCancelled = false
        val catalog = RecordingCatalogRepository { code ->
            if (code == "PL-ANTERIOR") {
                try {
                    awaitCancellation()
                } finally {
                    staleLookupCancelled = true
                }
            }
            Result.success(product().copy(internalCode = code))
        }
        val viewModel = viewModel(catalog)

        viewModel.onCodeDetected("PL-ANTERIOR", ScanFormat.QR)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        viewModel.scanAgain()
        viewModel.onCodeDetected("PL-ALOE-001", ScanFormat.MANUAL)
        advanceUntilIdle()

        val result = viewModel.uiState.value.result as ScanResultState.Found
        assertTrue(staleLookupCancelled)
        assertEquals("PL-ALOE-001", result.product.internalCode)
    }

    @Test
    fun `cancellation returned by catalog is rethrown`() = runTest {
        val useCase = FindProductByCodeUseCase(
            RecordingCatalogRepository { Result.failure(CancellationException("cancelled")) },
        )
        var caught: CancellationException? = null

        try {
            useCase("PL-ALOE-001")
        } catch (error: CancellationException) {
            caught = error
        }

        assertEquals("cancelled", caught?.message)
    }

    private fun viewModel(
        catalog: CatalogRepository,
        cart: RecordingCartRepository = RecordingCartRepository(),
    ) = ScannerViewModel(FindProductByCodeUseCase(catalog), AddProductToCartUseCase(cart))

    private class RecordingCatalogRepository(
        private val result: suspend (String) -> Result<Product?>,
    ) : CatalogRepository {
        val codes = mutableListOf<String>()
        override fun observeCatalog(): Flow<CatalogSnapshot> = flowOf(CatalogSnapshot(emptyList(), emptyList()))
        override suspend fun getProduct(productId: String): Result<Product> = Result.failure(NoSuchElementException())
        override suspend fun findProductByCode(code: String): Result<Product?> {
            codes += code
            return result(code)
        }
    }

    private class RecordingCartRepository : CartRepository {
        val addedProductIds = mutableListOf<String>()
        override fun observeCart(): Flow<Cart> = flowOf(Cart())
        override suspend fun addProduct(product: Product): Result<Unit> {
            addedProductIds += product.id
            return Result.success(Unit)
        }
        override suspend fun changeQuantity(productId: String, quantity: Int) = Result.success(Unit)
        override suspend fun removeProduct(productId: String) = Result.success(Unit)
        override suspend fun associateCustomer(customer: CartCustomer?) = Result.success(Unit)
        override suspend fun saveDraft() = Result.success(Unit)
        override suspend fun cancelCart() = Result.success(Unit)
        override suspend fun createPendingSale(session: UserSession): Result<SaleTicket> = Result.failure(UnsupportedOperationException())
    }

    companion object {
        private fun product() = Product(
            id = "aloe",
            internalCode = "PL-ALOE-001",
            barcode = null,
            commonName = "Aloe vera",
            scientificName = "Aloe barbadensis",
            description = "",
            category = Category("suculentas", "Suculentas"),
            priceCents = 12_500,
            wholesalePriceCents = null,
            unit = "pieza",
            stockAvailable = 5,
            minimumStock = 1,
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
