package com.intutec.viveroapp.feature.cart

import com.intutec.viveroapp.feature.cart.domain.model.Cart
import com.intutec.viveroapp.feature.cart.domain.model.CartCustomer
import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import com.intutec.viveroapp.feature.cart.domain.repository.CartRepository
import com.intutec.viveroapp.feature.cart.domain.usecase.AddProductToCartUseCase
import com.intutec.viveroapp.feature.cart.domain.usecase.SendCartToCashierUseCase
import com.intutec.viveroapp.feature.catalog.domain.model.Category
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CartUseCasesTest {
    @Test
    fun `does not add product with unknown stock`() = runTest {
        val repository = RecordingCartRepository()
        val result = AddProductToCartUseCase(repository)(product(stockKnown = false))

        assertTrue(result.isFailure)
        assertFalse(repository.addCalled)
    }

    @Test
    fun `adds available product`() = runTest {
        val repository = RecordingCartRepository()
        val result = AddProductToCartUseCase(repository)(product())

        assertTrue(result.isSuccess)
        assertTrue(repository.addCalled)
    }

    @Test
    fun `send requires authenticated user id`() = runTest {
        val repository = RecordingCartRepository()
        val result = SendCartToCashierUseCase(repository)("  ")

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
    override suspend fun sendToCashier(userId: String): Result<SaleTicket> {
        sendCalled = true
        return Result.failure(NotImplementedError())
    }
}
