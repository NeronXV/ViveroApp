package com.intutec.viveroapp.feature.cashier.data.repository

import com.intutec.viveroapp.feature.cashier.data.local.CashierPaymentAttemptStore
import com.intutec.viveroapp.feature.cashier.data.remote.CashierPaymentRemoteDataSource
import com.intutec.viveroapp.feature.cashier.data.remote.RemoteCashierClaimDto
import com.intutec.viveroapp.feature.cashier.data.remote.RemoteCashierPaymentResultDto
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderDetail
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentAttempt
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentAttemptState
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentClaim
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentException
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentFailureCode
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentInput
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentMethod
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentResult
import com.intutec.viveroapp.feature.cashier.domain.model.canonicalFor
import com.intutec.viveroapp.feature.cashier.domain.repository.CashierPaymentRepository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class SupabaseCashierPaymentRepository @Inject constructor(
    private val remote: CashierPaymentRemoteDataSource,
    private val store: CashierPaymentAttemptStore,
) : CashierPaymentRepository {
    override suspend fun restore(saleId: String): CashierPaymentAttempt? {
        val canonicalSaleId = saleId.requireUuid("comanda")
        runCatching { store.reconcileAllSucceededSales() }
        return store.get(canonicalSaleId)
    }

    override suspend fun claim(order: CashierOrderDetail, cashierId: String): CashierPaymentAttempt {
        val saleId = order.summary.id.requireUuid("comanda")
        val canonicalCashier = cashierId.requireUuid("cajero")
        val response = remote.claim(saleId)
        val claim = response.toDomain()
        check(claim.saleId == saleId && claim.branchId == order.summary.branchId && claim.cashierId == canonicalCashier) {
            "Caja devolvió una reserva inconsistente."
        }
        return store.saveClaim(claim)
    }

    override suspend fun renew(saleId: String): CashierPaymentAttempt {
        val current = checkNotNull(store.get(saleId.requireUuid("comanda"))) {
            "No existe una reserva local para renovar."
        }
        check(!current.payloadLocked && current.state == CashierPaymentAttemptState.CLAIMED) {
            "La operación ya no permite renovar la reserva."
        }
        val claim = remote.claim(current.saleId, current.claimToken).toDomain()
        check(claim.token == current.claimToken && claim.renewed) {
            "Caja devolvió una renovación inconsistente."
        }
        return store.renewClaim(claim)
    }

    override suspend fun release(saleId: String) {
        val current = checkNotNull(store.get(saleId.requireUuid("comanda"))) {
            "No existe una reserva local para liberar."
        }
        check(!current.payloadLocked && current.state == CashierPaymentAttemptState.CLAIMED) {
            "La operación ya no puede liberar su reserva."
        }
        val response = remote.release(current.saleId, current.claimToken)
        check(response.saleId == current.saleId && response.claimToken == current.claimToken) {
            "Caja devolvió una liberación inconsistente."
        }
        store.markState(current.saleId, CashierPaymentAttemptState.RELEASED)
    }

    override suspend fun confirm(
        order: CashierOrderDetail,
        cashierId: String,
        input: CashierPaymentInput?,
        retryUncertain: Boolean,
    ): CashierPaymentResult {
        val attempt = if (retryUncertain) {
            store.lockUncertainRetry(order.summary.id)
        } else {
            store.lockDraft(
                order.summary.id,
                checkNotNull(input) { "Selecciona un método de pago." }.canonicalFor(order.summary.totalCents),
            )
        }
        val lockedInput = checkNotNull(attempt.input) { "El intento no tiene un payload guardado." }
        return try {
            val result = remote.confirm(
                attempt.saleId,
                attempt.claimToken,
                attempt.idempotencyKey,
                lockedInput,
            ).toDomain()
            result.requireMatches(order, cashierId, attempt, lockedInput)
            store.markState(attempt.saleId, CashierPaymentAttemptState.SUCCEEDED)
            // The canonical remote result remains successful even if the optional local projection needs repair later.
            runCatching { store.reconcileSucceededSale(attempt.saleId) }
            result
        } catch (error: CashierPaymentException) {
            store.markState(
                attempt.saleId,
                when {
                    error.responseMayBeCommitted -> CashierPaymentAttemptState.UNCERTAIN
                    error.code == CashierPaymentFailureCode.CLAIM_EXPIRED -> CashierPaymentAttemptState.EXPIRED
                    else -> CashierPaymentAttemptState.FAILED
                },
                error.code.name,
            )
            throw error
        } catch (_: Throwable) {
            store.markState(
                attempt.saleId,
                CashierPaymentAttemptState.UNCERTAIN,
                CashierPaymentFailureCode.RESPONSE_INVALID.name,
            )
            throw CashierPaymentException(
                CashierPaymentFailureCode.RESPONSE_INVALID,
                responseMayBeCommitted = true,
                message = "No pudimos validar la confirmación de Caja.",
            )
        }
    }
}

private fun RemoteCashierClaimDto.toDomain() = CashierPaymentClaim(
    saleId = saleId.requireUuid("venta de reserva"),
    branchId = branchId.requireUuid("sucursal de reserva"),
    cashierId = cashierId.requireUuid("cajero de reserva"),
    token = claimToken.requireUuid("token de reserva"),
    createdAt = createdAt.parseInstant("creación de reserva"),
    expiresAt = expiresAt.parseInstant("vencimiento de reserva"),
    serverTime = serverTime.parseInstant("hora del servidor"),
    renewed = renewed,
).also {
    check(it.expiresAt.isAfter(it.createdAt) && !it.expiresAt.isBefore(it.serverTime)) {
        "Caja devolvió tiempos de reserva inválidos."
    }
}

private fun RemoteCashierPaymentResultDto.toDomain(): CashierPaymentResult {
    val method = runCatching { CashierPaymentMethod.valueOf(payment.method) }
        .getOrElse { error("Caja devolvió un método de pago inválido.") }
    return CashierPaymentResult(
        saleId = payment.saleId.requireUuid("venta pagada"),
        folio = sale.folio,
        branchId = sale.branchId.requireUuid("sucursal pagada"),
        cashierId = payment.cashierId.requireUuid("cajero del pago"),
        paymentId = payment.id.requireUuid("pago"),
        idempotencyKey = payment.idempotencyKey.requireUuid("idempotencia"),
        method = method,
        amountDueCents = payment.amountDueCents,
        amountReceivedCents = payment.amountReceivedCents,
        changeCents = payment.changeCents,
        reference = payment.reference,
        createdAt = payment.createdAt.parseInstant("fecha de pago"),
        idempotentReplay = idempotentReplay,
    ).also {
        check(sale.id == it.saleId && sale.status == "PAID" && sale.totalCents == it.amountDueCents) {
            "Caja devolvió una venta pagada inconsistente."
        }
    }
}

private fun CashierPaymentResult.requireMatches(
    order: CashierOrderDetail,
    expectedCashierId: String,
    attempt: CashierPaymentAttempt,
    input: CashierPaymentInput,
) {
    check(saleId == order.summary.id && folio == order.summary.folio)
    check(branchId == order.summary.branchId && cashierId == expectedCashierId.requireUuid("cajero"))
    check(idempotencyKey == attempt.idempotencyKey && method == input.method)
    check(amountDueCents == order.summary.totalCents && amountDueCents > 0L)
    check(changeCents == amountReceivedCents - amountDueCents && reference == input.reference)
    when (input.method) {
        CashierPaymentMethod.CASH -> check(amountReceivedCents == input.amountReceivedCents && amountReceivedCents >= amountDueCents)
        CashierPaymentMethod.CARD,
        CashierPaymentMethod.TRANSFER -> check(amountReceivedCents == amountDueCents && changeCents == 0L)
    }
}

private fun String.requireUuid(field: String): String = try {
    UUID.fromString(this).toString()
} catch (_: IllegalArgumentException) {
    throw IllegalStateException("Caja recibió un $field inválido.")
}

private fun String.parseInstant(field: String): Instant = try {
    OffsetDateTime.parse(this, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
} catch (_: RuntimeException) {
    throw IllegalStateException("Caja recibió una $field inválida.")
}
