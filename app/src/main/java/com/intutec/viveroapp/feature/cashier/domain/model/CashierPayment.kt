package com.intutec.viveroapp.feature.cashier.domain.model

import java.time.Duration
import java.time.Instant

enum class CashierPaymentMethod { CASH, CARD, TRANSFER }

enum class CashierPaymentAttemptState {
    CLAIMED,
    CONFIRMING,
    UNCERTAIN,
    SUCCEEDED,
    FAILED,
    RELEASED,
    EXPIRED,
}

data class CashierPaymentClaim(
    val saleId: String,
    val branchId: String,
    val cashierId: String,
    val token: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val serverTime: Instant,
    val renewed: Boolean,
) {
    val serverDurationRemaining: Duration
        get() = Duration.between(serverTime, expiresAt).coerceAtLeast(Duration.ZERO)
}

data class CashierPaymentInput(
    val method: CashierPaymentMethod,
    val amountReceivedCents: Long? = null,
    val reference: String? = null,
)

data class CashierPaymentAttempt(
    val saleId: String,
    val claimToken: String,
    val idempotencyKey: String,
    val input: CashierPaymentInput?,
    val state: CashierPaymentAttemptState,
    val payloadLocked: Boolean,
    val claimCreatedAt: Instant,
    val claimExpiresAt: Instant,
    val serverTimeAtClaim: Instant,
    val observedAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastErrorCode: String? = null,
)

data class CashierPaymentResult(
    val saleId: String,
    val folio: String,
    val branchId: String,
    val cashierId: String,
    val paymentId: String,
    val idempotencyKey: String,
    val method: CashierPaymentMethod,
    val amountDueCents: Long,
    val amountReceivedCents: Long,
    val changeCents: Long,
    val reference: String?,
    val createdAt: Instant,
    val idempotentReplay: Boolean,
)

class CashierPaymentValidationException(message: String) : IllegalArgumentException(message)

enum class CashierPaymentFailureCode {
    CASHIER_UNAUTHORIZED,
    SALE_UNAVAILABLE,
    SALE_STATUS_INVALID,
    CLAIM_UNAVAILABLE,
    CLAIM_EXPIRED,
    CLAIM_NOT_OWNED,
    CLAIM_REQUIRED,
    CASH_AMOUNT_INSUFFICIENT,
    PAYMENT_DATA_INVALID,
    TRANSFER_REFERENCE_REQUIRED,
    SALE_ALREADY_PAID,
    IDEMPOTENCY_KEY_INVALID,
    IDEMPOTENCY_CONFLICT,
    PAYMENT_METHOD_INVALID,
    SALE_TOTAL_INVALID,
    INVENTORY_INSUFFICIENT,
    RESPONSE_UNKNOWN,
    RESPONSE_INVALID,
    TEMPORARY,
}

class CashierPaymentException(
    val code: CashierPaymentFailureCode,
    val responseMayBeCommitted: Boolean,
    message: String,
) : IllegalStateException(message)

fun CashierPaymentInput.canonicalFor(totalCents: Long): CashierPaymentInput {
    if (totalCents <= 0L) throw CashierPaymentValidationException("La comanda no tiene un total válido.")
    val normalizedReference = reference?.trim()?.takeIf(String::isNotEmpty)
    if (normalizedReference?.any(Char::isISOControl) == true) {
        throw CashierPaymentValidationException("La referencia contiene caracteres no permitidos.")
    }
    return when (method) {
        CashierPaymentMethod.CASH -> {
            val amount = amountReceivedCents
                ?: throw CashierPaymentValidationException("Ingresa el importe recibido.")
            if (amount < totalCents) {
                throw CashierPaymentValidationException("El importe recibido es menor al total.")
            }
            if (normalizedReference != null) {
                throw CashierPaymentValidationException("El efectivo no utiliza referencia.")
            }
            copy(amountReceivedCents = amount, reference = null)
        }
        CashierPaymentMethod.CARD -> {
            if (amountReceivedCents != null) {
                throw CashierPaymentValidationException("La tarjeta utiliza el total confirmado por el servidor.")
            }
            if (normalizedReference != null && normalizedReference.length > 64) {
                throw CashierPaymentValidationException("La referencia de terminal es demasiado larga.")
            }
            if (normalizedReference?.matches(Regex("^[0-9]{3,4}$")) == true ||
                normalizedReference?.filter(Char::isDigit)?.matches(Regex("^[0-9]{13,19}$")) == true
            ) {
                throw CashierPaymentValidationException("La referencia no debe contener datos de tarjeta.")
            }
            copy(amountReceivedCents = null, reference = normalizedReference)
        }
        CashierPaymentMethod.TRANSFER -> {
            if (amountReceivedCents != null) {
                throw CashierPaymentValidationException("La transferencia utiliza el total confirmado por el servidor.")
            }
            val requiredReference = normalizedReference
                ?: throw CashierPaymentValidationException("Ingresa la referencia de la transferencia.")
            if (requiredReference.length > 120) {
                throw CashierPaymentValidationException("La referencia de transferencia es demasiado larga.")
            }
            copy(amountReceivedCents = null, reference = requiredReference)
        }
    }
}
