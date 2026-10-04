package com.intutec.viveroapp.feature.cashier.domain.repository

import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleIdentity

data class BackendCashierSale(val id: Long, val folio: String, val totalCents: Long)
data class BackendCashierPage(val items: List<BackendCashierSale>, val next: Long?)
class BackendCashierClaim(val saleId: Long, val identity: BackendSaleIdentity, val token: String) {
    override fun toString() = "BackendCashierClaim(saleId=$saleId, token=<redacted>)"
}
data class BackendPaymentReceipt(val id: Long, val saleId: Long, val folio: String, val dueCents: Long, val receivedCents: Long, val changeCents: Long)
sealed interface BackendPaymentRetirement {
    data object Retired : BackendPaymentRetirement
    data class Committed(val receipt: BackendPaymentReceipt) : BackendPaymentRetirement
}
interface BackendCashierGateway {
    suspend fun list(token: String, before: Long? = null): BackendCashierPage
    suspend fun claim(token: String, identity: BackendSaleIdentity, sale: Long): BackendCashierClaim
    suspend fun pay(token: String, identity: BackendSaleIdentity, sale: Long, key: String, body: String): BackendPaymentReceipt
    suspend fun recover(token: String, identity: BackendSaleIdentity, sale: Long, key: String, body: String): BackendPaymentReceipt
    suspend fun retire(token: String, identity: BackendSaleIdentity, sale: Long, key: String, body: String): BackendPaymentRetirement = throw UnsupportedOperationException()
}
data class BackendPendingPayment(val id: Long, val saleId: Long, val state: String)
class BackendPaymentNotFound : IllegalStateException("No se encontró el resultado de este intento.")
interface BackendCashierPaymentRepository {
    suspend fun pending(): List<BackendPendingPayment>
    suspend fun start(sale: Long, method: String, received: Long?, reference: String?): BackendPaymentReceipt
    suspend fun recover(id: Long): BackendPaymentReceipt
    suspend fun retry(id: Long): BackendPaymentReceipt
    suspend fun retire(id: Long): BackendPaymentRetirement
}
