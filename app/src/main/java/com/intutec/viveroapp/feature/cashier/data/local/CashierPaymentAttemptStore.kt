package com.intutec.viveroapp.feature.cashier.data.local

import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentAttempt
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentAttemptState
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentClaim
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentInput
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface CashierPaymentAttemptStore {
    suspend fun get(saleId: String): CashierPaymentAttempt?
    suspend fun saveClaim(claim: CashierPaymentClaim): CashierPaymentAttempt
    suspend fun renewClaim(claim: CashierPaymentClaim): CashierPaymentAttempt
    suspend fun lockDraft(saleId: String, input: CashierPaymentInput): CashierPaymentAttempt
    suspend fun lockUncertainRetry(saleId: String): CashierPaymentAttempt
    suspend fun markState(
        saleId: String,
        state: CashierPaymentAttemptState,
        errorCode: String? = null,
    ): CashierPaymentAttempt
}

@Singleton
class RoomCashierPaymentAttemptStore @Inject constructor(
    private val dao: CashierPaymentAttemptDao,
) : CashierPaymentAttemptStore {
    private val clock: Clock = Clock.systemUTC()

    override suspend fun get(saleId: String): CashierPaymentAttempt? = dao.get(saleId)?.toDomain()

    override suspend fun saveClaim(claim: CashierPaymentClaim): CashierPaymentAttempt {
        val existing = dao.get(claim.saleId)
        if (existing?.claimToken == claim.token && existing.state !in TERMINAL_STATES) {
            return existing.toDomain()
        }
        val observedAt = clock.instant()
        val entity = CashierPaymentAttemptEntity(
            saleId = claim.saleId,
            claimToken = claim.token,
            idempotencyKey = UUID.randomUUID().toString(),
            method = null,
            amountReceivedCents = null,
            reference = null,
            state = CashierPaymentAttemptState.CLAIMED.name,
            payloadLocked = false,
            claimCreatedAtEpochMs = claim.createdAt.toEpochMilli(),
            claimExpiresAtEpochMs = claim.expiresAt.toEpochMilli(),
            serverTimeAtClaimEpochMs = claim.serverTime.toEpochMilli(),
            observedAtEpochMs = observedAt.toEpochMilli(),
            createdAtEpochMs = observedAt.toEpochMilli(),
            updatedAtEpochMs = observedAt.toEpochMilli(),
            lastErrorCode = null,
        )
        dao.upsert(entity)
        return entity.toDomain()
    }

    override suspend fun renewClaim(claim: CashierPaymentClaim): CashierPaymentAttempt {
        val observedAt = clock.instant()
        check(
            dao.updateClaimExpiry(
                claim.saleId,
                claim.token,
                claim.expiresAt.toEpochMilli(),
                claim.serverTime.toEpochMilli(),
                observedAt.toEpochMilli(),
            ) == 1,
        ) { "El intento ya no admite renovación." }
        return checkNotNull(get(claim.saleId))
    }

    override suspend fun lockDraft(saleId: String, input: CashierPaymentInput): CashierPaymentAttempt {
        val now = clock.instant().toEpochMilli()
        return dao.persistAndLock(
            saleId,
            input.method.name,
            input.amountReceivedCents,
            input.reference,
            now,
        ).toDomain()
    }

    override suspend fun lockUncertainRetry(saleId: String): CashierPaymentAttempt {
        val now = clock.instant().toEpochMilli()
        check(dao.lockUncertainRetry(saleId, now) == 1) { "El intento pendiente no puede reintentarse." }
        return checkNotNull(get(saleId))
    }

    override suspend fun markState(
        saleId: String,
        state: CashierPaymentAttemptState,
        errorCode: String?,
    ): CashierPaymentAttempt {
        check(dao.updateState(saleId, state.name, clock.instant().toEpochMilli(), errorCode) == 1) {
            "El intento local ya no está disponible."
        }
        return checkNotNull(get(saleId))
    }

    private companion object {
        val TERMINAL_STATES = setOf(
            CashierPaymentAttemptState.SUCCEEDED.name,
            CashierPaymentAttemptState.FAILED.name,
            CashierPaymentAttemptState.RELEASED.name,
            CashierPaymentAttemptState.EXPIRED.name,
        )
    }
}

private fun CashierPaymentAttemptEntity.toDomain(): CashierPaymentAttempt = CashierPaymentAttempt(
    saleId = saleId,
    claimToken = claimToken,
    idempotencyKey = idempotencyKey,
    input = method?.let {
        CashierPaymentInput(
            method = enumValueOf(it),
            amountReceivedCents = amountReceivedCents,
            reference = reference,
        )
    },
    state = enumValueOf(state),
    payloadLocked = payloadLocked,
    claimCreatedAt = Instant.ofEpochMilli(claimCreatedAtEpochMs),
    claimExpiresAt = Instant.ofEpochMilli(claimExpiresAtEpochMs),
    serverTimeAtClaim = Instant.ofEpochMilli(serverTimeAtClaimEpochMs),
    observedAt = Instant.ofEpochMilli(observedAtEpochMs),
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
    lastErrorCode = lastErrorCode,
)
