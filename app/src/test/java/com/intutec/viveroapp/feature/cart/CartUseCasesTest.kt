package com.intutec.viveroapp.feature.cart

import com.intutec.viveroapp.feature.cart.domain.model.Cart
import com.intutec.viveroapp.feature.cart.domain.model.CartCustomer
import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import com.intutec.viveroapp.feature.cart.domain.repository.CartRepository
import com.intutec.viveroapp.feature.cart.domain.usecase.AddProductToCartUseCase
import com.intutec.viveroapp.feature.cart.domain.usecase.SendCartToCashierUseCase
import com.intutec.viveroapp.feature.catalog.domain.model.Category
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.cart.domain.model.SaleStatus
import com.intutec.viveroapp.feature.cart.sync.PendingSaleSynchronizer
import com.intutec.viveroapp.feature.cart.sync.SaleOutboxStore
import com.intutec.viveroapp.feature.cart.sync.SaleSyncRemoteDataSource
import com.intutec.viveroapp.feature.cart.sync.SaleSyncRequest
import com.intutec.viveroapp.feature.cart.sync.SaleSyncResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CartUseCasesTest {
    @Test
    fun `remote active product with unknown stock can be added`() = runTest {
        val repository = RecordingCartRepository()
        val result = AddProductToCartUseCase(repository)(product(stockKnown = false))

        assertTrue(result.isSuccess)
        assertTrue(repository.addCalled)
    }

    @Test
    fun `adds available product`() = runTest {
        val repository = RecordingCartRepository()
        val result = AddProductToCartUseCase(repository)(product())

        assertTrue(result.isSuccess)
        assertTrue(repository.addCalled)
    }

    @Test
    fun `inactive product is rejected without changing cart`() = runTest {
        val repository = RecordingCartRepository()
        val result = AddProductToCartUseCase(repository)(product().copy(isActive = false))

        assertTrue(result.isFailure)
        assertFalse(repository.addCalled)
    }

    @Test
    fun `product with known insufficient stock is rejected`() = runTest {
        val repository = RecordingCartRepository()
        val result = AddProductToCartUseCase(repository)(product().copy(stockAvailable = 0))

        assertTrue(result.isFailure)
        assertFalse(repository.addCalled)
    }

    @Test
    fun `send requires authenticated user id`() = runTest {
        val repository = RecordingCartRepository()
        val store = SessionStore()
        val synchronizer = PendingSaleSynchronizer(EmptyOutboxStore, UnusedRemote, store)
        val result = SendCartToCashierUseCase(repository, synchronizer, store)()

        assertTrue(result.isFailure)
        assertFalse(repository.sendCalled)
    }

    private fun product(stockKnown: Boolean = true) = Product(
        id = "monstera", internalCode = "PL-001", barcode = "750100000001",
        commonName = "Monstera", scientificName = null, description = "", category = Category("interior", "Interior"),
        priceCents = 58_900, wholesalePriceCents = null, unit = "pieza", stockAvailable = 4, minimumStock = 1,
        imageKey = "monstera", wateringAdvice = "", lightType = "", recommendedClimate = "", isActive = true,
        promotion = null, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH, stockKnown = stockKnown,
    )
}

private class RecordingCartRepository : CartRepository {
    var addCalled = false
    var sendCalled = false
    override fun observeCart(): Flow<Cart> = flowOf(Cart())
    override suspend fun addProduct(product: Product): Result<Unit> { addCalled = true; return Result.success(Unit) }
    override suspend fun changeQuantity(productId: String, quantity: Int) = Result.success(Unit)
    override suspend fun removeProduct(productId: String) = Result.success(Unit)
    override suspend fun associateCustomer(customer: CartCustomer?) = Result.success(Unit)
    override suspend fun saveDraft() = Result.success(Unit)
    override suspend fun cancelCart() = Result.success(Unit)
    override suspend fun createPendingSale(session: UserSession): Result<SaleTicket> {
        sendCalled = true
        return Result.failure(NotImplementedError())
    }
}

private object EmptyOutboxStore : SaleOutboxStore {
    override suspend fun load(saleId: String) = null
    override suspend fun claimPending(saleId: String, attemptedAt: Instant) = false
    override suspend fun markPending(saleId: String, message: String) = false
    override suspend fun markFailed(saleId: String, message: String) = false
    override suspend fun markSynced(saleId: String, serverStatus: SaleStatus) = false
    override suspend fun recoverInterrupted() = 0
}

private object UnusedRemote : SaleSyncRemoteDataSource {
    override suspend fun submitSale(request: SaleSyncRequest): SaleSyncResponse = error("No debe llamarse")
}
