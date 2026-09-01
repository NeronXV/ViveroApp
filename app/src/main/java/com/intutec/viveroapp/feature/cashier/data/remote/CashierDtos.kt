package com.intutec.viveroapp.feature.cashier.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteCashierOrderDto(
    val id: String,
    val folio: String,
    @SerialName("branch_id") val branchId: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("subtotal_cents") val subtotalCents: Long,
    @SerialName("discount_cents") val discountCents: Long,
    @SerialName("total_cents") val totalCents: Long,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val items: List<RemoteCashierOrderItemDto> = emptyList(),
)

@Serializable
data class RemoteCashierOrderItemDto(
    val id: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("product_name") val productName: String,
    @SerialName("internal_code") val internalCode: String,
    val quantity: Int,
    @SerialName("list_price_cents") val listPriceCents: Long,
    @SerialName("unit_price_cents") val unitPriceCents: Long,
    @SerialName("discount_cents") val discountCents: Long,
    @SerialName("line_total_cents") val lineTotalCents: Long,
    @SerialName("promotion_name") val promotionName: String? = null,
)
