package com.intutec.viveroapp.feature.mysales.domain.model

import java.time.Instant

enum class MySaleStatus {
    DRAFT,
    SENT_TO_CASHIER,
    PAYMENT_PENDING,
    PAID,
    CANCELLED,
    DELIVERED,
}

data class MySale(
    val id: String,
    val folio: String,
    val status: MySaleStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val subtotalCents: Long,
    val discountCents: Long,
    val totalCents: Long,
    val itemCount: Int,
    val totalQuantity: Int,
    val paidAt: Instant?,
)

data class MySalesPage(
    val items: List<MySale>,
    val hasMore: Boolean,
    val nextCursorCreatedAt: Instant?,
    val nextCursorId: String?,
)
