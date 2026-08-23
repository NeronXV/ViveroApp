package com.intutec.viveroapp.feature.cashier.prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.intutec.viveroapp.ui.theme.ViveroAppTheme
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderDetail
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderItem
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderStatus
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderSummary
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentAttempt
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentAttemptState
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentInput
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentMethod
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentResult
import com.intutec.viveroapp.feature.cashier.presentation.CashierPaymentFlow
import com.intutec.viveroapp.feature.cashier.presentation.CashierPaymentStage
import com.intutec.viveroapp.feature.cashier.presentation.CashierPaymentUiState
import java.time.Instant

/** Debug-only visual harness. It owns no repository and cannot call Supabase. */
class CashierPaymentPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra("scenario") ?: "cash"
        setContent { ViveroAppTheme(darkTheme = false, dynamicColor = false) { PaymentPreview(scenarioState(scenario)) } }
    }
}

@Composable
private fun PaymentPreview(state: CashierPaymentUiState) {
    CashierPaymentFlow(
        order = previewOrder,
        state = state,
        secondsRemaining = { 238 },
        onMethod = {}, onCashAmount = {}, onReference = {}, onRequestConfirmation = {},
        onDismissConfirmation = {}, onConfirm = {}, onRetry = {}, onRenew = {}, onCancel = {}, onDone = {},
    )
}

@Preview(name = "Efectivo teléfono", widthDp = 412, heightDp = 915, showBackground = true)
@Composable private fun CashPhonePreview() = ViveroAppTheme(darkTheme = false, dynamicColor = false) { PaymentPreview(scenarioState("cash")) }

@Preview(name = "Tarjeta tablet vertical", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable private fun CardTabletPreview() = ViveroAppTheme(darkTheme = false, dynamicColor = false) { PaymentPreview(scenarioState("card")) }

@Preview(name = "Transferencia tablet horizontal", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable private fun TransferLandscapePreview() = ViveroAppTheme(darkTheme = false, dynamicColor = false) { PaymentPreview(scenarioState("transfer")) }

@Preview(name = "Confirmación pendiente", widthDp = 412, heightDp = 915, showBackground = true)
@Composable private fun UncertainPreview() = ViveroAppTheme(darkTheme = false, dynamicColor = false) { PaymentPreview(scenarioState("uncertain")) }

@Preview(name = "Confirmación de cobro", widthDp = 412, heightDp = 915, showBackground = true)
@Composable private fun ConfirmationPreview() = ViveroAppTheme(darkTheme = false, dynamicColor = false) { PaymentPreview(scenarioState("confirmation")) }

@Preview(name = "Pago exitoso", widthDp = 412, heightDp = 915, showBackground = true)
@Composable private fun SuccessPreview() = ViveroAppTheme(darkTheme = false, dynamicColor = false) { PaymentPreview(scenarioState("success")) }

private fun scenarioState(scenario: String): CashierPaymentUiState {
    val method = when (scenario) {
        "card" -> CashierPaymentMethod.CARD
        "transfer" -> CashierPaymentMethod.TRANSFER
        else -> CashierPaymentMethod.CASH
    }
    val attempt = previewAttempt.copy(
        input = CashierPaymentInput(method, if (method == CashierPaymentMethod.CASH) 12_000 else null, if (method == CashierPaymentMethod.TRANSFER) "SPEI-DEMO" else null),
        payloadLocked = scenario in setOf("uncertain", "success"),
        state = if (scenario == "uncertain") CashierPaymentAttemptState.UNCERTAIN else CashierPaymentAttemptState.CLAIMED,
    )
    return when (scenario) {
        "claiming" -> CashierPaymentUiState(CashierPaymentStage.CLAIMING)
        "confirming" -> CashierPaymentUiState(CashierPaymentStage.CONFIRMING, method, "120.00", attempt = attempt.copy(payloadLocked = true))
        "confirmation" -> CashierPaymentUiState(CashierPaymentStage.CONFIRMATION, CashierPaymentMethod.CASH, "120.00", attempt = attempt)
        "uncertain" -> CashierPaymentUiState(
            CashierPaymentStage.UNCERTAIN, method, "120.00", "", attempt,
            message = "No recibimos una confirmación definitiva. Conservamos este intento para conciliarlo.",
        )
        "success" -> CashierPaymentUiState(
            CashierPaymentStage.SUCCESS, method, "120.00", "", attempt,
            result = previewResult,
        )
        "conflict" -> CashierPaymentUiState(
            CashierPaymentStage.CONFLICT, method, attempt = attempt,
            message = "Otra caja tomó o cobró esta comanda.",
        )
        "expired" -> CashierPaymentUiState(
            CashierPaymentStage.EXPIRED, method, attempt = attempt,
            message = "La reserva venció. Actualiza la comanda antes de continuar.",
        )
        else -> CashierPaymentUiState(
            CashierPaymentStage.FORM, method,
            cashAmount = if (method == CashierPaymentMethod.CASH) "120.00" else "",
            reference = if (method == CashierPaymentMethod.TRANSFER) "SPEI-DEMO" else "",
            attempt = attempt,
        )
    }
}

private val previewOrder = CashierOrderDetail(
    CashierOrderSummary(
        "11111111-1111-4111-8111-111111111111", "VD-VISUAL-001",
        "22222222-2222-4222-8222-222222222222", "33333333-3333-4333-8333-333333333333",
        Instant.parse("2026-08-22T10:00:00Z"), Instant.parse("2026-08-22T10:00:00Z"),
        CashierOrderStatus.SENT_TO_CASHIER, 1, 1, 10_000,
    ),
    10_000, 0,
    listOf(CashierOrderItem(
        "44444444-4444-4444-8444-444444444444", "55555555-5555-4555-8555-555555555555",
        "VISUAL-001", "Planta de muestra visual", 1, 10_000, 10_000, 0, 10_000,
    )),
)

private val previewAttempt = CashierPaymentAttempt(
    previewOrder.summary.id, "66666666-6666-4666-8666-666666666666", "77777777-7777-4777-8777-777777777777",
    null, CashierPaymentAttemptState.CLAIMED, false,
    Instant.parse("2026-08-22T10:00:00Z"), Instant.parse("2026-08-22T10:05:00Z"),
    Instant.parse("2026-08-22T10:00:00Z"), Instant.parse("2026-08-22T10:00:00Z"),
    Instant.parse("2026-08-22T10:00:00Z"), Instant.parse("2026-08-22T10:00:00Z"),
)

private val previewResult = CashierPaymentResult(
    previewOrder.summary.id, previewOrder.summary.folio, previewOrder.summary.branchId,
    previewOrder.summary.createdBy, "88888888-8888-4888-8888-888888888888", previewAttempt.idempotencyKey,
    CashierPaymentMethod.CASH, 10_000, 12_000, 2_000, null, Instant.parse("2026-08-22T10:01:00Z"), false,
)
