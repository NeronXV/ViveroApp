package com.intutec.viveroapp.feature.cart.domain.usecase

import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import com.intutec.viveroapp.feature.cart.domain.repository.CartRepository
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import javax.inject.Inject

class AddProductToCartUseCase @Inject constructor(private val repository: CartRepository) {
    suspend operator fun invoke(product: Product): Result<Unit> {
        if (!product.stockKnown) return Result.failure(IllegalStateException("La existencia debe confirmarse antes de agregar el producto."))
        if (!product.isAvailable) return Result.failure(IllegalStateException("Este producto no tiene existencia disponible."))
        return repository.addProduct(product)
    }
}

class ChangeCartQuantityUseCase @Inject constructor(private val repository: CartRepository) {
    suspend operator fun invoke(productId: String, quantity: Int): Result<Unit> {
        if (quantity <= 0) return Result.failure(IllegalArgumentException("La cantidad debe ser mayor que cero."))
        return repository.changeQuantity(productId, quantity)
    }
}

class SendCartToCashierUseCase @Inject constructor(private val repository: CartRepository) {
    suspend operator fun invoke(userId: String): Result<SaleTicket> {
        if (userId.isBlank()) return Result.failure(IllegalArgumentException("No encontramos el usuario que prepara la venta."))
        return repository.sendToCashier(userId)
    }
}
