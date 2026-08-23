package com.intutec.viveroapp.feature.cashier.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteCashierClaimDto(
    @SerialName("sale_id") val saleId: String,
    @SerialName("branch_id") val branchId: String,
    @SerialName("cashier_id") val cashierId: String,
    @SerialName("claim_token") val claimToken: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("server_time") val serverTime: String,
    val renewed: Boolean,
)

@Serializable
data class RemoteCashierClaimReleaseDto(
    @SerialName("sale_id") val saleId: String,
    @SerialName("claim_token") val claimToken: String,
    @SerialName("released_at") val releasedAt: String,
    @SerialName("closed_reason") val closedReason: String,
)

@Serializable
data class RemoteCashierPaymentResultDto(
    @SerialName("idempotent_replay") val idempotentReplay: Boolean,
    val sale: RemotePaidSaleDto,
    val payment: RemotePaymentDto,
)

@Serializable
data class RemotePaidSaleDto(
    val id: String,
    val folio: String,
    @SerialName("branch_id") val branchId: String,
    val status: String,
    @SerialName("total_cents") val totalCents: Long,
)

@Serializable
data class RemotePaymentDto(
    val id: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("cashier_id") val cashierId: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    val method: String,
    @SerialName("amount_due_cents") val amountDueCents: Long,
    @SerialName("amount_received_cents") val amountReceivedCents: Long,
    @SerialName("change_cents") val changeCents: Long,
    val reference: String? = null,
    @SerialName("created_at") val createdAt: String,
)
