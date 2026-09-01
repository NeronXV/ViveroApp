package com.intutec.viveroapp.feature.cashier.domain.model

import java.time.Instant

enum class CashierOrderStatus { SENT_TO_CASHIER }

data class CashierOrderSummary(
    val id: String,
    val folio: String,
    val branchId: String,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val status: CashierOrderStatus,
    val productCount: Int,
    val unitCount: Int,
    val totalCents: Long,
)

data class CashierOrderDetail(
    val summary: CashierOrderSummary,
    val subtotalCents: Long,
    val discountCents: Long,
    val items: List<CashierOrderItem>,
)

data class CashierOrderItem(
    val id: String,
    val productId: String,
    val internalCode: String,
    val productName: String,
    val quantity: Int,
    val listPriceCents: Long,
    val unitPriceCents: Long,
    val discountCents: Long,
    val lineTotalCents: Long,
    val promotionName: String? = null,
)
