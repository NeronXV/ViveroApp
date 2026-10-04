package com.intutec.viveroapp.feature.cart.domain.repository

import java.security.SecureRandom
import java.util.Collections

const val MAX_BACKEND_CENTS = 9007199254740991L
const val MAX_BACKEND_ID = 4294967295L

data class BackendSaleIdentity(val userId: Long, val branchId: Long) {
    init { require(userId in 1..MAX_BACKEND_ID && branchId in 1..MAX_BACKEND_ID) }
}
data class BackendSaleLine(val productId: Long, val quantity: Int) {
    init { require(productId in 1..MAX_BACKEND_ID && quantity in 1..100000) }
}
class BackendSaleAttempt private constructor(
    val identity: BackendSaleIdentity,
    val key: String,
    val items: List<BackendSaleLine>,
    val expectedTotalCents: Long,
) {
    companion object {
        fun create(identity: BackendSaleIdentity, items: List<BackendSaleLine>, expectedTotalCents: Long): BackendSaleAttempt {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            return restore(identity, bytes.joinToString("") { "%02x".format(it.toInt() and 255) }, items, expectedTotalCents)
        }
        // Restore only the original persisted key and payload after interruption.
        fun restore(identity: BackendSaleIdentity, key: String, items: List<BackendSaleLine>, expectedTotalCents: Long): BackendSaleAttempt {
            require(Regex("^[a-f0-9]{64}$").matches(key))
            require(items.size in 1..25 && items.map { it.productId }.distinct().size == items.size)
            require(expectedTotalCents in 1..MAX_BACKEND_CENTS)
            return BackendSaleAttempt(identity, key, Collections.unmodifiableList(items.sortedBy { it.productId }), expectedTotalCents)
        }
    }
}
data class BackendSaleReceipt(val id: Long, val folio: String, val identity: BackendSaleIdentity, val status: String,
    val totalCents: Long, val createdAt: String, val replay: Boolean)
data class BackendSaleQuotedLine(val productId: Long, val quantity: Int, val productName: String, val internalCode: String,
    val listPriceCents: Long, val unitPriceCents: Long, val lineTotalCents: Long)
data class BackendSaleQuote(val branchId: Long, val subtotalCents: Long, val discountCents: Long, val totalCents: Long, val items: List<BackendSaleQuotedLine>)
class BackendSaleException(val status: Int, val code: String, val resultUncertain: Boolean = false) : IllegalStateException(
    if (resultUncertain) "No se confirmó la venta. Conserva el intento y recupera su resultado."
    else if (code == "SALE_PRICE_CHANGED") "El precio cambió. Solicita una nueva cotización."
    else if (status == 401) "La sesión dejó de ser válida."
    else "No fue posible completar la operación de venta.",
)
interface BackendSaleGateway {
    suspend fun quote(token: String, identity: BackendSaleIdentity, items: List<BackendSaleLine>): BackendSaleQuote
    suspend fun submit(token: String, attempt: BackendSaleAttempt): BackendSaleReceipt
    suspend fun recover(token: String, attempt: BackendSaleAttempt): BackendSaleReceipt
    suspend fun retire(token: String, attempt: BackendSaleAttempt): BackendSaleRetirement
}

sealed interface BackendSaleRetirement {
    data object Retired : BackendSaleRetirement
    data class Committed(val receipt: BackendSaleReceipt) : BackendSaleRetirement
}
