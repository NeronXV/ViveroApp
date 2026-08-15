package com.intutec.viveroapp.feature.cart.domain.model

import java.time.Instant

enum class SaleStatus {
    DRAFT,
    SENT_TO_CASHIER,
    PAYMENT_PENDING,
    PAID,
    CANCELLED,
    DELIVERED,
}

data class SaleTicket(
    val id: String,
    val folio: String,
    val items: List<CartItem>,
    val customer: CartCustomer?,
    val subtotalCents: Long,
    val discountCents: Long,
    val totalCents: Long,
    val status: SaleStatus,
    val createdBy: String,
    val createdAt: Instant,
    val syncPending: Boolean,
    val history: List<SaleStatusChange>,
)

data class SaleStatusChange(
    val id: String,
    val previousStatus: SaleStatus?,
    val newStatus: SaleStatus,
    val userId: String,
    val changedAt: Instant,
    val note: String? = null,
)
