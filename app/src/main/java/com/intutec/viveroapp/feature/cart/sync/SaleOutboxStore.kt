package com.intutec.viveroapp.feature.cart.sync

import com.intutec.viveroapp.feature.cart.data.local.CartDao
import com.intutec.viveroapp.feature.cart.data.local.SaleWithItems
import com.intutec.viveroapp.feature.cart.domain.model.CartItem
import com.intutec.viveroapp.feature.cart.domain.model.SaleStatus
import com.intutec.viveroapp.feature.cart.domain.model.SaleSyncState
import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import java.time.Instant
import javax.inject.Inject

interface SaleOutboxStore {
    suspend fun load(saleId: String): SaleTicket?
    suspend fun claimPending(saleId: String, attemptedAt: Instant): Boolean
    suspend fun markPending(saleId: String, message: String): Boolean
    suspend fun markFailed(saleId: String, message: String): Boolean
    suspend fun markSynced(saleId: String, serverStatus: SaleStatus): Boolean
    suspend fun recoverInterrupted(): Int
}

class RoomSaleOutboxStore @Inject constructor(
    private val dao: CartDao,
) : SaleOutboxStore {
    override suspend fun load(saleId: String): SaleTicket? = dao.getSale(saleId)?.toDomain()

    override suspend fun claimPending(saleId: String, attemptedAt: Instant): Boolean =
        dao.claimPendingSale(saleId, attemptedAt.toEpochMilli()) == 1

    override suspend fun markPending(saleId: String, message: String): Boolean =
        dao.markSalePending(saleId, message) == 1

    override suspend fun markFailed(saleId: String, message: String): Boolean =
        dao.markSaleFailed(saleId, message) == 1

    override suspend fun markSynced(saleId: String, serverStatus: SaleStatus): Boolean =
        dao.markSaleSynced(saleId, serverStatus.name) == 1

    override suspend fun recoverInterrupted(): Int = dao.recoverInterruptedSales()
}

private fun SaleWithItems.toDomain(): SaleTicket {
    val branch = requireNotNull(sale.branchId) { "La venta local no tiene sucursal." }
    return SaleTicket(
        id = sale.id,
        folio = sale.folio,
        items = items.map { item ->
            CartItem(
                productId = item.productId,
                internalCode = item.internalCode,
                name = item.name,
                imageKey = item.imageKey,
                unit = item.unit,
                listPriceCents = item.listPriceCents,
                unitPriceCents = item.unitPriceCents,
                quantity = item.quantity,
                stockAvailable = item.stockAvailable,
                stockKnown = item.stockKnown,
                promotionName = item.promotionName,
            )
        },
        customer = null,
        subtotalCents = sale.subtotalCents,
        discountCents = sale.discountCents,
        totalCents = sale.totalCents,
        status = SaleStatus.valueOf(sale.status),
        createdBy = sale.createdBy,
        branchId = branch,
        createdAt = Instant.ofEpochMilli(sale.createdAtEpochMs),
        syncState = SaleSyncState.valueOf(sale.syncState),
        syncAttemptCount = sale.syncAttemptCount,
        syncLastError = sale.syncLastError,
        syncLastAttemptAt = sale.syncLastAttemptAtEpochMs?.let(Instant::ofEpochMilli),
        history = emptyList(),
    )
}
