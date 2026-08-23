package com.intutec.viveroapp.feature.cashier.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cashier_payment_attempts",
    indices = [
        Index(value = ["idempotency_key"], unique = true),
        Index(value = ["state", "updated_at_epoch_ms"]),
    ],
)
data class CashierPaymentAttemptEntity(
    @PrimaryKey @ColumnInfo("sale_id") val saleId: String,
    @ColumnInfo("claim_token") val claimToken: String,
    @ColumnInfo("idempotency_key") val idempotencyKey: String,
    val method: String?,
    @ColumnInfo("amount_received_cents") val amountReceivedCents: Long?,
    val reference: String?,
    val state: String,
    @ColumnInfo("payload_locked") val payloadLocked: Boolean,
    @ColumnInfo("claim_created_at_epoch_ms") val claimCreatedAtEpochMs: Long,
    @ColumnInfo("claim_expires_at_epoch_ms") val claimExpiresAtEpochMs: Long,
    @ColumnInfo("server_time_at_claim_epoch_ms") val serverTimeAtClaimEpochMs: Long,
    @ColumnInfo("observed_at_epoch_ms") val observedAtEpochMs: Long,
    @ColumnInfo("created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo("updated_at_epoch_ms") val updatedAtEpochMs: Long,
    @ColumnInfo("last_error_code") val lastErrorCode: String?,
)
