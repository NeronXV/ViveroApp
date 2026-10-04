package com.intutec.viveroapp.feature.mysales.domain.repository

import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleIdentity

enum class BackendHistoryKind { SALES, PAYMENTS, QUEUE }
data class BackendHistoryEntry(val id: Long, val saleId: Long, val folio: String, val totalCents: Long, val label: String, val createdAt: String)
data class BackendHistoryPage(val items: List<BackendHistoryEntry>, val next: Long?)
data class BackendSaleSnapshot(val id: Long, val folio: String, val branchId: Long, val status: String, val subtotalCents: Long, val discountCents: Long, val totalCents: Long)
data class BackendHistoricalLine(val id: Long, val productId: Long, val name: String, val code: String?, val quantity: String, val listPriceCents: Long?, val unitPriceCents: Long, val totalCents: Long, val promotion: String?)
data class BackendHistoricalEvent(val previous: String?, val status: String, val observation: String, val createdAt: String)
data class BackendHistoricalPayment(val id: Long, val method: String, val receivedCents: Long, val changeCents: Long, val reference: String?, val createdAt: String)
data class BackendHistoricalRefund(val id: Long, val amountCents: Long, val method: String)
data class BackendSaleDocument(val sale: BackendSaleSnapshot, val lines: List<BackendHistoricalLine>, val events: List<BackendHistoricalEvent> = emptyList(), val payment: BackendHistoricalPayment? = null, val refund: BackendHistoricalRefund? = null, val branchName: String? = null)

interface BackendHistoryGateway {
    suspend fun list(token: String, identity: BackendSaleIdentity, kind: BackendHistoryKind, before: Long? = null): BackendHistoryPage
    suspend fun detail(token: String, identity: BackendSaleIdentity, kind: BackendHistoryKind, id: Long): BackendSaleDocument
}
