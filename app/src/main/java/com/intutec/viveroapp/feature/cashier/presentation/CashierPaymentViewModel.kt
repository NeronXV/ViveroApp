package com.intutec.viveroapp.feature.cashier.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderDetail
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentAttempt
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentAttemptState
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentException
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentFailureCode
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentInput
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentMethod
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentResult
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentValidationException
import com.intutec.viveroapp.feature.cashier.domain.model.canonicalFor
import com.intutec.viveroapp.feature.cashier.domain.usecase.ClaimCashierPaymentUseCase
import com.intutec.viveroapp.feature.cashier.domain.usecase.ConfirmCashierPaymentUseCase
import com.intutec.viveroapp.feature.cashier.domain.usecase.ReleaseCashierPaymentClaimUseCase
import com.intutec.viveroapp.feature.cashier.domain.usecase.RenewCashierPaymentClaimUseCase
import com.intutec.viveroapp.feature.cashier.domain.usecase.RestoreCashierPaymentAttemptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CashierPaymentStage {
    IDLE, CLAIMING, FORM, CONFIRMATION, CONFIRMING, UNCERTAIN, SUCCESS, CONFLICT, EXPIRED, ERROR
}

data class CashierPaymentUiState(
    val stage: CashierPaymentStage = CashierPaymentStage.IDLE,
    val method: CashierPaymentMethod? = null,
    val cashAmount: String = "",
    val reference: String = "",
    val attempt: CashierPaymentAttempt? = null,
    val result: CashierPaymentResult? = null,
    val message: String? = null,
) {
    val payloadLocked: Boolean get() = attempt?.payloadLocked == true
}

@HiltViewModel
class CashierPaymentViewModel @Inject constructor(
    private val restoreAttempt: RestoreCashierPaymentAttemptUseCase,
    private val claimPayment: ClaimCashierPaymentUseCase,
    private val renewClaim: RenewCashierPaymentClaimUseCase,
    private val releaseClaim: ReleaseCashierPaymentClaimUseCase,
    private val confirmPayment: ConfirmCashierPaymentUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CashierPaymentUiState())
    val uiState: StateFlow<CashierPaymentUiState> = _uiState.asStateFlow()
    private var order: CashierOrderDetail? = null
    private var renewalInFlight = false

    fun attachOrder(value: CashierOrderDetail) {
        if (order?.summary?.id == value.summary.id) return
        order = value
        viewModelScope.launch {
            restoreAttempt(value).fold(
                onSuccess = { attempt ->
                    _uiState.value = attempt?.toUiState() ?: CashierPaymentUiState()
                },
                onFailure = { _uiState.value = CashierPaymentUiState(message = friendly(it)) },
            )
        }
    }

    fun start() {
        val currentOrder = order ?: return
        if (_uiState.value.stage !in setOf(CashierPaymentStage.IDLE, CashierPaymentStage.ERROR, CashierPaymentStage.EXPIRED)) return
        _uiState.value = CashierPaymentUiState(stage = CashierPaymentStage.CLAIMING)
        viewModelScope.launch {
            claimPayment(currentOrder).fold(
                onSuccess = { _uiState.value = it.toUiState() },
                onFailure = { showFailure(it) },
            )
        }
    }

    fun selectMethod(method: CashierPaymentMethod) {
        if (_uiState.value.payloadLocked) return
        _uiState.update { it.copy(method = method, cashAmount = "", reference = "", message = null) }
    }

    fun updateCashAmount(value: String) {
        if (_uiState.value.payloadLocked || !value.matches(Regex("^[0-9]{0,9}(\\.[0-9]{0,2})?$"))) return
        _uiState.update { it.copy(cashAmount = value, message = null) }
    }

    fun updateReference(value: String) {
        if (_uiState.value.payloadLocked || value.length > 120) return
        _uiState.update { it.copy(reference = value, message = null) }
    }

    fun requestConfirmation() {
        val currentOrder = order ?: return
        if (_uiState.value.stage != CashierPaymentStage.FORM) return
        if (secondsRemaining() == 0L) {
            _uiState.update {
                it.copy(
                    stage = CashierPaymentStage.EXPIRED,
                    message = "La reserva llegó a su vencimiento estimado. Actualiza la comanda antes de continuar.",
                )
            }
            return
        }
        runCatching { _uiState.value.toInput().canonicalFor(currentOrder.summary.totalCents) }
            .onSuccess { _uiState.update { state -> state.copy(stage = CashierPaymentStage.CONFIRMATION, message = null) } }
            .onFailure { _uiState.update { state -> state.copy(message = friendly(it)) } }
    }

    fun dismissConfirmation() {
        if (!_uiState.value.payloadLocked) _uiState.update { it.copy(stage = CashierPaymentStage.FORM) }
    }

    fun confirm() = submit(retryUncertain = false)

    fun retryUncertain() = submit(retryUncertain = true)

    private fun submit(retryUncertain: Boolean) {
        val currentOrder = order ?: return
        val expectedStage = if (retryUncertain) CashierPaymentStage.UNCERTAIN else CashierPaymentStage.CONFIRMATION
        if (_uiState.value.stage != expectedStage) return
        val input = if (retryUncertain) null else runCatching { _uiState.value.toInput() }
            .getOrElse {
                _uiState.update { state -> state.copy(stage = CashierPaymentStage.FORM, message = friendly(it)) }
                return
            }
        _uiState.update { it.copy(stage = CashierPaymentStage.CONFIRMING, message = null) }
        viewModelScope.launch {
            confirmPayment(currentOrder, input, retryUncertain).fold(
                onSuccess = { result ->
                    _uiState.update { it.copy(stage = CashierPaymentStage.SUCCESS, result = result, message = null) }
                },
                onFailure = { showFailure(it) },
            )
        }
    }

    fun renew() {
        val currentOrder = order ?: return
        if (_uiState.value.stage != CashierPaymentStage.FORM || _uiState.value.payloadLocked || renewalInFlight) return
        renewalInFlight = true
        viewModelScope.launch {
            try {
                renewClaim(currentOrder).fold(
                    onSuccess = { renewed -> _uiState.update { it.copy(attempt = renewed, message = "Reserva renovada.") } },
                    onFailure = { showFailure(it) },
                )
            } finally {
                renewalInFlight = false
            }
        }
    }

    fun cancel(onFinished: () -> Unit) {
        val currentOrder = order
        val state = _uiState.value
        if (currentOrder == null || state.attempt == null || state.payloadLocked || state.stage != CashierPaymentStage.FORM) {
            onFinished()
            return
        }
        viewModelScope.launch {
            releaseClaim(currentOrder)
            onFinished()
        }
    }

    fun secondsRemaining(now: Instant = Instant.now()): Long {
        val attempt = _uiState.value.attempt ?: return 0
        val authoritativeDuration = Duration.between(attempt.serverTimeAtClaim, attempt.claimExpiresAt)
        return (authoritativeDuration - Duration.between(attempt.observedAt, now)).seconds.coerceAtLeast(0)
    }

    private fun showFailure(error: Throwable) {
        val paymentError = error as? CashierPaymentException
        val stage = when {
            paymentError?.responseMayBeCommitted == true -> CashierPaymentStage.UNCERTAIN
            paymentError?.code == CashierPaymentFailureCode.CLAIM_EXPIRED -> CashierPaymentStage.EXPIRED
            paymentError?.code in CONFLICT_CODES -> CashierPaymentStage.CONFLICT
            else -> CashierPaymentStage.ERROR
        }
        _uiState.update { it.copy(stage = stage, message = friendly(error)) }
    }

    private fun CashierPaymentUiState.toInput(): CashierPaymentInput {
        val selected = checkNotNull(method) { "Selecciona un método de pago." }
        return CashierPaymentInput(
            method = selected,
            amountReceivedCents = if (selected == CashierPaymentMethod.CASH) cashAmount.parseMxnCents() else null,
            reference = reference,
        )
    }

    private fun CashierPaymentAttempt.toUiState(): CashierPaymentUiState {
        val restoredInput = input
        return CashierPaymentUiState(
            stage = when (state) {
                CashierPaymentAttemptState.CLAIMED -> CashierPaymentStage.FORM
                CashierPaymentAttemptState.CONFIRMING, CashierPaymentAttemptState.UNCERTAIN -> CashierPaymentStage.UNCERTAIN
                CashierPaymentAttemptState.SUCCEEDED -> CashierPaymentStage.SUCCESS
                CashierPaymentAttemptState.EXPIRED -> CashierPaymentStage.EXPIRED
                CashierPaymentAttemptState.FAILED -> CashierPaymentStage.ERROR
                CashierPaymentAttemptState.RELEASED -> CashierPaymentStage.IDLE
            },
            method = restoredInput?.method,
            cashAmount = restoredInput?.amountReceivedCents?.let {
                "${it / 100}.${(it % 100).toString().padStart(2, '0')}"
            }.orEmpty(),
            reference = restoredInput?.reference.orEmpty(),
            attempt = this,
            message = if (state == CashierPaymentAttemptState.UNCERTAIN || state == CashierPaymentAttemptState.CONFIRMING) {
                "No recibimos una confirmación definitiva. Concilia usando el mismo intento."
            } else null,
        )
    }

    private fun friendly(error: Throwable): String = when ((error as? CashierPaymentException)?.code) {
        CashierPaymentFailureCode.CLAIM_UNAVAILABLE -> "Otra caja está atendiendo esta comanda."
        CashierPaymentFailureCode.CLAIM_EXPIRED, CashierPaymentFailureCode.CLAIM_REQUIRED -> "La reserva venció. Actualiza la comanda antes de continuar."
        CashierPaymentFailureCode.SALE_ALREADY_PAID -> "La comanda ya fue cobrada en otra caja."
        CashierPaymentFailureCode.IDEMPOTENCY_CONFLICT -> "El intento guardado no coincide con la operación. No se realizó otro cobro."
        CashierPaymentFailureCode.CASH_AMOUNT_INSUFFICIENT -> "El importe recibido es menor al total."
        CashierPaymentFailureCode.TRANSFER_REFERENCE_REQUIRED -> "Ingresa la referencia de la transferencia."
        CashierPaymentFailureCode.PAYMENT_DATA_INVALID -> "Revisa los datos del pago."
        CashierPaymentFailureCode.RESPONSE_UNKNOWN, CashierPaymentFailureCode.RESPONSE_INVALID,
        CashierPaymentFailureCode.TEMPORARY -> "No recibimos una confirmación definitiva. Conservamos este intento para conciliarlo."
        else -> if (error is CashierPaymentValidationException) {
            error.message ?: "Revisa los datos del cobro."
        } else {
            "No pudimos continuar con el cobro. Actualiza la comanda e intenta nuevamente."
        }
    }

    private companion object {
        val CONFLICT_CODES = setOf(
            CashierPaymentFailureCode.SALE_ALREADY_PAID,
            CashierPaymentFailureCode.CLAIM_UNAVAILABLE,
            CashierPaymentFailureCode.CLAIM_NOT_OWNED,
            CashierPaymentFailureCode.SALE_STATUS_INVALID,
            CashierPaymentFailureCode.SALE_UNAVAILABLE,
        )
    }
}

internal fun String.parseMxnCents(): Long? {
    if (isBlank()) return null
    val parts = split('.')
    if (parts.size > 2 || parts[0].isEmpty() || parts.any { part -> part.any { !it.isDigit() } }) return null
    val pesos = parts[0].toLongOrNull() ?: return null
    val cents = when (val fraction = parts.getOrNull(1).orEmpty()) {
        "" -> 0L
        else -> fraction.padEnd(2, '0').takeIf { it.length == 2 }?.toLongOrNull() ?: return null
    }
    return runCatching { Math.addExact(Math.multiplyExact(pesos, 100L), cents) }.getOrNull()
}
