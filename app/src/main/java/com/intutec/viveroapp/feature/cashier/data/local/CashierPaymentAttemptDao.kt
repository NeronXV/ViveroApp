package com.intutec.viveroapp.feature.cashier.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CashierPaymentAttemptDao {
    @Query("SELECT * FROM cashier_payment_attempts WHERE sale_id = :saleId")
    suspend fun get(saleId: String): CashierPaymentAttemptEntity?

    @Query("SELECT * FROM cashier_payment_attempts WHERE sale_id = :saleId")
    fun observe(saleId: String): Flow<CashierPaymentAttemptEntity?>

    @Upsert
    suspend fun upsert(entity: CashierPaymentAttemptEntity)

    @Query(
        """
        UPDATE cashier_payment_attempts
        SET method = :method, amount_received_cents = :amountReceivedCents,
            reference = :reference, updated_at_epoch_ms = :updatedAtEpochMs,
            last_error_code = NULL
        WHERE sale_id = :saleId AND state = 'CLAIMED' AND payload_locked = 0
        """,
    )
    suspend fun updateDraft(
        saleId: String,
        method: String,
        amountReceivedCents: Long?,
        reference: String?,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE cashier_payment_attempts
        SET state = 'CONFIRMING', payload_locked = 1,
            updated_at_epoch_ms = :updatedAtEpochMs, last_error_code = NULL
        WHERE sale_id = :saleId AND state = 'CLAIMED' AND payload_locked = 0
            AND method IS NOT NULL
        """,
    )
    suspend fun lockDraft(saleId: String, updatedAtEpochMs: Long): Int

    @Query(
        """
        UPDATE cashier_payment_attempts
        SET state = 'CONFIRMING', updated_at_epoch_ms = :updatedAtEpochMs,
            last_error_code = NULL
        WHERE sale_id = :saleId AND state = 'UNCERTAIN' AND payload_locked = 1
        """,
    )
    suspend fun lockUncertainRetry(saleId: String, updatedAtEpochMs: Long): Int

    @Query(
        """
        UPDATE cashier_payment_attempts
        SET state = :state, updated_at_epoch_ms = :updatedAtEpochMs,
            last_error_code = :errorCode
        WHERE sale_id = :saleId
        """,
    )
    suspend fun updateState(saleId: String, state: String, updatedAtEpochMs: Long, errorCode: String?): Int

    @Query(
        """
        UPDATE cashier_payment_attempts
        SET claim_expires_at_epoch_ms = :expiresAtEpochMs,
            server_time_at_claim_epoch_ms = :serverTimeEpochMs,
            observed_at_epoch_ms = :observedAtEpochMs,
            updated_at_epoch_ms = :observedAtEpochMs
        WHERE sale_id = :saleId AND claim_token = :claimToken
            AND state IN ('CLAIMED', 'UNCERTAIN')
        """,
    )
    suspend fun updateClaimExpiry(
        saleId: String,
        claimToken: String,
        expiresAtEpochMs: Long,
        serverTimeEpochMs: Long,
        observedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE sales
        SET status = 'PAID', sync_state = 'SYNCED', sync_pending = 0,
            sync_last_error = NULL
        WHERE id = :saleId AND status = 'SENT_TO_CASHIER'
          AND EXISTS (
              SELECT 1 FROM cashier_payment_attempts attempt
              WHERE attempt.sale_id = sales.id AND attempt.state = 'SUCCEEDED'
          )
        """,
    )
    suspend fun reconcileSucceededSale(saleId: String): Int

    @Query(
        """
        UPDATE sales
        SET status = 'PAID', sync_state = 'SYNCED', sync_pending = 0,
            sync_last_error = NULL
        WHERE status = 'SENT_TO_CASHIER'
          AND EXISTS (
              SELECT 1 FROM cashier_payment_attempts attempt
              WHERE attempt.sale_id = sales.id AND attempt.state = 'SUCCEEDED'
          )
        """,
    )
    suspend fun reconcileAllSucceededSales(): Int

    @Transaction
    suspend fun persistAndLock(
        saleId: String,
        method: String,
        amountReceivedCents: Long?,
        reference: String?,
        updatedAtEpochMs: Long,
    ): CashierPaymentAttemptEntity {
        check(updateDraft(saleId, method, amountReceivedCents, reference, updatedAtEpochMs) == 1) {
            "El intento ya no admite cambios."
        }
        check(lockDraft(saleId, updatedAtEpochMs) == 1) {
            "No se pudo bloquear el intento antes de enviarlo."
        }
        return checkNotNull(get(saleId)) { "El intento no está disponible." }
    }
}
