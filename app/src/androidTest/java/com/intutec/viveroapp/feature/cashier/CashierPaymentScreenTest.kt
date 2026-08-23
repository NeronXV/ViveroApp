package com.intutec.viveroapp.feature.cashier

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderDetail
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderItem
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderStatus
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderSummary
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentAttempt
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentAttemptState
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentMethod
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentResult
import com.intutec.viveroapp.feature.cashier.presentation.CashierPaymentFlow
import com.intutec.viveroapp.feature.cashier.presentation.CashierPaymentStage
import com.intutec.viveroapp.feature.cashier.presentation.CashierPaymentUiState
import com.intutec.viveroapp.ui.theme.ViveroAppTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class CashierPaymentScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun cashFormShowsServerTotalCountdownAndAccessibleReviewAction() {
        show(CashierPaymentUiState(CashierPaymentStage.FORM, CashierPaymentMethod.CASH, "120.00", attempt = attempt))
        compose.onNodeWithText("\$100.00").assertIsDisplayed()
        compose.onNodeWithText("Reserva visual: 3:58").assertIsDisplayed()
        compose.onNodeWithText("Cambio estimado:", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("payment_review").performScrollTo().assertIsDisplayed().assertHasClickAction()
    }

    @Test fun cardFormWarnsAgainstSensitiveCardData() {
        show(CashierPaymentUiState(CashierPaymentStage.FORM, CashierPaymentMethod.CARD, attempt = attempt))
        compose.onNodeWithText("Cobro realizado en terminal externa.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Folio de operación (opcional)").performScrollTo().assertIsDisplayed()
    }

    @Test fun transferFormRequiresOnlyReferenceField() {
        show(CashierPaymentUiState(CashierPaymentStage.FORM, CashierPaymentMethod.TRANSFER, attempt = attempt))
        compose.onNodeWithText("Referencia de transferencia").assertIsDisplayed()
        compose.onNodeWithText("No ingreses cuentas bancarias", substring = true).assertIsDisplayed()
    }

    @Test fun uncertainStateOffersSameAttemptRetry() {
        show(CashierPaymentUiState(CashierPaymentStage.UNCERTAIN, CashierPaymentMethod.CARD, attempt = attempt.copy(payloadLocked = true)))
        compose.onNodeWithText("Confirmación pendiente").assertIsDisplayed()
        compose.onNodeWithText("Reintentar el mismo intento").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun successShowsDefinitiveServerChangeAndNavigationAction() {
        show(CashierPaymentUiState(CashierPaymentStage.SUCCESS, attempt = attempt, result = result))
        compose.onNodeWithText("Pago confirmado").assertIsDisplayed()
        compose.onNodeWithText("Cambio definitivo: \$20.00").assertIsDisplayed()
        compose.onNodeWithText("Volver a la bandeja").assertHasClickAction()
    }

    private fun show(state: CashierPaymentUiState) {
        compose.setContent {
            ViveroAppTheme(darkTheme = false, dynamicColor = false) {
                CashierPaymentFlow(
                    order, state, { 238 }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                )
            }
        }
    }

    companion object {
        private val now = Instant.parse("2026-08-22T10:00:00Z")
        private val order = CashierOrderDetail(
            CashierOrderSummary(
                "11111111-1111-4111-8111-111111111111", "VD-TEST", "22222222-2222-4222-8222-222222222222",
                "33333333-3333-4333-8333-333333333333", now, now, CashierOrderStatus.SENT_TO_CASHIER, 1, 1, 10_000,
            ), 10_000, 0,
            listOf(CashierOrderItem("44444444-4444-4444-8444-444444444444", "55555555-5555-4555-8555-555555555555", "P-1", "Planta", 1, 10_000, 10_000, 0, 10_000)),
        )
        private val attempt = CashierPaymentAttempt(
            order.summary.id, "66666666-6666-4666-8666-666666666666", "77777777-7777-4777-8777-777777777777", null,
            CashierPaymentAttemptState.CLAIMED, false, now, now.plusSeconds(300), now, now, now, now,
        )
        private val result = CashierPaymentResult(
            order.summary.id, order.summary.folio, order.summary.branchId, order.summary.createdBy,
            "88888888-8888-4888-8888-888888888888", attempt.idempotencyKey, CashierPaymentMethod.CASH,
            10_000, 12_000, 2_000, null, now, false,
        )
    }
}
