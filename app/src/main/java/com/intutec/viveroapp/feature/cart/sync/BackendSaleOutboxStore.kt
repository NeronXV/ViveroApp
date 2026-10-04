package com.intutec.viveroapp.feature.cart.sync

import com.intutec.viveroapp.feature.cart.data.local.BackendSaleAttemptDao
import com.intutec.viveroapp.feature.cart.data.local.BackendSaleAttemptEntity
import com.intutec.viveroapp.feature.cart.domain.repository.*
import javax.inject.Inject

data class StoredBackendSaleAttempt(val id: Long, val attempt: BackendSaleAttempt, val state: String)
interface BackendSaleOutboxStore {
    suspend fun journal(identity: BackendSaleIdentity): List<BackendSaleJournalEntry> = throw UnsupportedOperationException()
    suspend fun enqueue(attempt: BackendSaleAttempt): Long
    suspend fun enqueueFromCart(attempt: BackendSaleAttempt, cart: BackendCartSnapshot): Long
    suspend fun load(id: Long): StoredBackendSaleAttempt?
    suspend fun pending(identity: BackendSaleIdentity): List<Long>
    suspend fun claim(id: Long): Boolean
    suspend fun uncertain(id: Long, message: String): Boolean
    suspend fun complete(id: Long, receipt: BackendSaleReceipt): Boolean
    suspend fun retire(id: Long): Boolean
}
class RoomBackendSaleOutboxStore @Inject constructor(private val dao: BackendSaleAttemptDao) : BackendSaleOutboxStore {
    override suspend fun journal(identity: BackendSaleIdentity): List<BackendSaleJournalEntry> = dao.journal(identity.userId, identity.branchId).map { row ->
        val header = row.attempt
        check(header.state in setOf("PENDING", "UNCERTAIN", "SYNCING", "SYNCED", "RETIRED"))
        check(header.expectedTotalCents in 1..MAX_BACKEND_CENTS)
        val receipt = if (header.state == "SYNCED") {
            val saleId = checkNotNull(header.serverSaleId); val folio = checkNotNull(header.serverFolio); val status = checkNotNull(header.serverStatus)
            check(saleId in 1..MAX_BACKEND_ID && Regex("^VD-[A-F0-9]{24}$").matches(folio))
            check(status in setOf("SENT_TO_CASHIER", "PAYMENT_PENDING", "PAID", "CANCELLED", "DELIVERED"))
            BackendStoredSaleReceipt(saleId, folio, status)
        } else { check(header.serverSaleId == null && header.serverFolio == null && header.serverStatus == null); null }
        BackendSaleJournalEntry(header.id, header.state, header.expectedTotalCents,
            java.util.Collections.unmodifiableList(row.items.sortedBy { it.productId }.map { BackendSaleLine(it.productId, it.quantity) }), header.lastError, receipt)
    }
    override suspend fun enqueue(attempt: BackendSaleAttempt): Long = dao.insert(
        BackendSaleAttemptEntity(key = attempt.key, actorId = attempt.identity.userId, branchId = attempt.identity.branchId, expectedTotalCents = attempt.expectedTotalCents),
        attempt.items.map { it.productId to it.quantity },
    )
    override suspend fun enqueueFromCart(attempt: BackendSaleAttempt, cart: BackendCartSnapshot): Long {
        check(cart.identity == attempt.identity && cart.saleLines().sortedBy { it.productId } == attempt.items)
        return dao.insertFromCart(
            BackendSaleAttemptEntity(key = attempt.key, actorId = attempt.identity.userId, branchId = attempt.identity.branchId, expectedTotalCents = attempt.expectedTotalCents),
            attempt.items.map { it.productId to it.quantity }, cart.id, cart.revision,
        )
    }
    override suspend fun load(id: Long): StoredBackendSaleAttempt? = dao.load(id)?.let { row ->
        StoredBackendSaleAttempt(row.attempt.id, BackendSaleAttempt.restore(
            BackendSaleIdentity(row.attempt.actorId, row.attempt.branchId), row.attempt.key,
            row.items.map { BackendSaleLine(it.productId, it.quantity) }, row.attempt.expectedTotalCents,
        ), row.attempt.state)
    }
    override suspend fun pending(identity: BackendSaleIdentity): List<Long> = dao.pending(identity.userId, identity.branchId)
    override suspend fun claim(id: Long) = dao.claim(id) == 1
    override suspend fun uncertain(id: Long, message: String) = dao.release(id, "UNCERTAIN", message) == 1
    override suspend fun complete(id: Long, receipt: BackendSaleReceipt) = dao.complete(id, receipt.id, receipt.folio, receipt.status) == 1
    override suspend fun retire(id: Long) = dao.release(id, "RETIRED", "Intento cerrado por el servidor; no creó una venta.") == 1
}
