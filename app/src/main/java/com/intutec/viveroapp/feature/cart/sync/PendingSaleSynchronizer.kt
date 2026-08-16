package com.intutec.viveroapp.feature.cart.sync

import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.domain.model.SaleStatus
import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SaleSyncOutcome {
    data object Synced : SaleSyncOutcome
    data object AlreadyClaimed : SaleSyncOutcome
    data object SessionUnavailable : SaleSyncOutcome
    data class Pending(val message: String) : SaleSyncOutcome
    data class Failed(val message: String) : SaleSyncOutcome
}

@Singleton
class PendingSaleSynchronizer @Inject constructor(
    private val store: SaleOutboxStore,
    private val remote: SaleSyncRemoteDataSource,
    private val sessionStore: SessionStore,
) {
    suspend fun synchronize(saleId: String): SaleSyncOutcome {
        val session = sessionStore.session.value
        if (session == null || session.mode != SessionMode.REMOTE || session.branch?.isActive != true) {
            return SaleSyncOutcome.SessionUnavailable
        }
        val branchId = session.branch.id
        if (!session.userId.isUuid() || !branchId.isUuid()) return SaleSyncOutcome.SessionUnavailable

        val sale = store.load(saleId) ?: return SaleSyncOutcome.Failed("La venta local no existe.")
        if (!store.claimPending(saleId, Instant.now())) return SaleSyncOutcome.AlreadyClaimed

        val localError = when {
            sale.createdBy != session.userId -> "La venta pertenece a otro usuario."
            sale.branchId != branchId -> "La venta pertenece a otra sucursal."
            !sale.id.isUuid() || sale.items.any { !it.productId.isUuid() || it.quantity <= 0 } ->
                "La venta contiene identificadores o cantidades no válidos."
            else -> null
        }
        if (localError != null) {
            store.markFailed(saleId, localError)
            return SaleSyncOutcome.Failed(localError)
        }

        return try {
            val response = remote.submitSale(
                SaleSyncRequest(
                    saleId = sale.id,
                    folio = sale.folio,
                    items = sale.items.map { SaleSyncItem(it.productId, it.quantity) },
                ),
            )
            val responseError = validateResponse(response, sale)
            if (responseError != null) {
                store.markFailed(saleId, responseError)
                SaleSyncOutcome.Failed(responseError)
            } else {
                check(store.markSynced(saleId, SaleStatus.SENT_TO_CASHIER)) {
                    "La venta cambió de estado durante la sincronización."
                }
                SaleSyncOutcome.Synced
            }
        } catch (error: SaleSyncRemoteException) {
            if (error.type == SaleSyncFailureType.TEMPORARY) {
                store.markPending(saleId, error.message.orEmpty())
                SaleSyncOutcome.Pending(error.message.orEmpty())
            } else {
                store.markFailed(saleId, error.message.orEmpty())
                SaleSyncOutcome.Failed(error.message.orEmpty())
            }
        } catch (error: CancellationException) {
            store.markPending(saleId, "Sincronización interrumpida; lista para reintentar.")
            throw error
        } catch (_: Throwable) {
            val message = "No se pudo confirmar la comanda por un problema temporal."
            store.markPending(saleId, message)
            SaleSyncOutcome.Pending(message)
        }
    }

    suspend fun loadSale(saleId: String): SaleTicket? = store.load(saleId)

    private fun validateResponse(response: SaleSyncResponse, sale: SaleTicket): String? = when {
        response.id != sale.id -> "El servidor devolvió una venta diferente."
        response.createdBy != sale.createdBy -> "El servidor devolvió un creador diferente."
        response.branchId != sale.branchId -> "El servidor devolvió una sucursal diferente."
        response.status != SaleStatus.SENT_TO_CASHIER.name -> "El servidor devolvió un estado inesperado."
        else -> null
    }
}

private fun String.isUuid(): Boolean = runCatching {
    UUID.fromString(this).toString().equals(this, ignoreCase = true)
}.getOrDefault(false)
