package com.intutec.viveroapp.feature.cart.domain.usecase

import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import com.intutec.viveroapp.feature.cart.domain.repository.CartRepository
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.sync.PendingSaleSynchronizer
import javax.inject.Inject

class AddProductToCartUseCase @Inject constructor(private val repository: CartRepository) {
    suspend operator fun invoke(product: Product): Result<Unit> {
        if (!product.isActive) return Result.failure(IllegalStateException("Este producto no está activo."))
        if (product.stockKnown && !product.isAvailable) {
            return Result.failure(IllegalStateException("Este producto no tiene existencia disponible."))
        }
        return repository.addProduct(product)
    }
}

class ChangeCartQuantityUseCase @Inject constructor(private val repository: CartRepository) {
    suspend operator fun invoke(productId: String, quantity: Int): Result<Unit> {
        if (quantity <= 0) return Result.failure(IllegalArgumentException("La cantidad debe ser mayor que cero."))
        return repository.changeQuantity(productId, quantity)
    }
}

class SendCartToCashierUseCase @Inject constructor(
    private val repository: CartRepository,
    private val synchronizer: PendingSaleSynchronizer,
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(): Result<SaleTicket> {
        val session = sessionStore.session.value
            ?: return Result.failure(IllegalStateException("La sesión todavía no está lista o ya fue cerrada."))
        return repository.createPendingSale(session).mapCatching { pending ->
            synchronizer.synchronize(pending.id)
            synchronizer.loadSale(pending.id) ?: pending
        }
    }
}
