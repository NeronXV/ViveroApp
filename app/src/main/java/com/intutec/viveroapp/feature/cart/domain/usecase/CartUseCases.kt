package com.intutec.viveroapp.feature.cart.domain.usecase

import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import com.intutec.viveroapp.feature.cart.domain.repository.CartRepository
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.sync.PendingSaleSynchronizer
import com.intutec.viveroapp.feature.cart.sync.SaleSyncOutcome
import kotlinx.coroutines.CancellationException
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
        val pending = repository.createPendingSale(session).getOrElse { error ->
            if (error is CancellationException) throw error
            return Result.failure(error)
        }
        return synchronize(pending.id, pending)
    }

    suspend fun retry(saleId: String): Result<SaleTicket> {
        val ticket = synchronizer.loadSale(saleId)
            ?: return Result.failure(IllegalStateException("La venta local ya no está disponible para reintentar."))
        return synchronize(saleId, ticket)
    }

    private suspend fun synchronize(saleId: String, fallback: SaleTicket): Result<SaleTicket> =
        try {
            val outcome = synchronizer.synchronize(saleId)
            val ticket = synchronizer.loadSale(saleId) ?: fallback
            when (outcome) {
                SaleSyncOutcome.Synced -> Result.success(ticket)
                SaleSyncOutcome.AlreadyClaimed -> Result.failure(
                    SaleSubmissionException(ticket, "Esta venta ya se está sincronizando."),
                )
                SaleSyncOutcome.SessionUnavailable -> Result.failure(
                    SaleSubmissionException(ticket, "La sesión remota o la sucursal ya no están disponibles."),
                )
                is SaleSyncOutcome.Pending -> Result.failure(SaleSubmissionException(ticket, outcome.message))
                is SaleSyncOutcome.Failed -> Result.failure(SaleSubmissionException(ticket, outcome.message))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
}

class SaleSubmissionException(
    val ticket: SaleTicket,
    message: String,
) : IllegalStateException(message)
