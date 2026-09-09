package com.intutec.viveroapp.feature.cashier.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Money
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderDetail
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentMethod
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentResult
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val PaymentForest = Color(0xFF075E45)
private val PaymentSage = Color(0xFFE6F4EE)
private val PaymentTerracotta = Color(0xFFC97E14)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierPaymentFlow(
    order: CashierOrderDetail,
    state: CashierPaymentUiState,
    secondsRemaining: () -> Long,
    onMethod: (CashierPaymentMethod) -> Unit,
    onCashAmount: (String) -> Unit,
    onReference: (String) -> Unit,
    onRequestConfirmation: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onRenew: () -> Unit,
    onCancel: () -> Unit,
    onDone: (String?) -> Unit,
) {
    if (state.stage == CashierPaymentStage.IDLE) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (state.stage == CashierPaymentStage.FORM) onCancel() },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("cashier_payment_sheet"),
        sheetState = sheetState,
    ) {
        PaymentContent(
            order, state, secondsRemaining, onMethod, onCashAmount, onReference,
            onRequestConfirmation, onRetry, onRenew, onCancel, onDone,
        )
    }
    if (state.stage == CashierPaymentStage.CONFIRMATION) {
        AlertDialog(
            onDismissRequest = onDismissConfirmation,
            title = { Text("Confirmar cobro", color = MaterialTheme.colorScheme.primary) },
            text = {
                PaymentConfirmationSummary(order, state)
            },
            dismissButton = { OutlinedButton(onClick = onDismissConfirmation) { Text("Revisar") } },
            confirmButton = {
                Button(onClick = onConfirm, modifier = Modifier.height(48.dp).testTag("payment_confirm_final")) {
                    Text("Confirmar pago")
                }
            },
        )
    }
}

@Composable
private fun PaymentContent(
    order: CashierOrderDetail,
    state: CashierPaymentUiState,
    secondsRemaining: () -> Long,
    onMethod: (CashierPaymentMethod) -> Unit,
    onCashAmount: (String) -> Unit,
    onReference: (String) -> Unit,
    onRequestConfirmation: () -> Unit,
    onRetry: () -> Unit,
    onRenew: () -> Unit,
    onCancel: () -> Unit,
    onDone: (String?) -> Unit,
) {
    var remaining by remember(state.attempt?.claimExpiresAt) { mutableLongStateOf(secondsRemaining()) }
    LaunchedEffect(state.attempt?.claimExpiresAt, state.stage) {
        while (state.stage == CashierPaymentStage.FORM && remaining > 0) {
            delay(1_000)
            remaining = secondsRemaining()
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PaymentHeader(order)
        when (state.stage) {
            CashierPaymentStage.CLAIMING, CashierPaymentStage.CONFIRMING -> PaymentProgress(
                if (state.stage == CashierPaymentStage.CLAIMING) "Reservando comanda…" else "Confirmando con Caja…",
            )
            CashierPaymentStage.FORM, CashierPaymentStage.CONFIRMATION -> {
                ReservationBanner(remaining, onRenew)
                PaymentMethodSelector(state.method, onMethod)
                PaymentFields(order, state, onCashAmount, onReference)
                state.message?.let { InlineMessage(it) }
                Button(
                    onClick = onRequestConfirmation,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("payment_review"),
                ) { Text("Revisar cobro") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Cancelar y liberar reserva")
                }
            }
            CashierPaymentStage.UNCERTAIN -> PaymentStatus(
                Icons.Rounded.HourglassBottom,
                "Confirmación pendiente",
                state.message ?: "La operación quedó guardada para conciliación.",
                "Reintentar el mismo intento",
                onRetry,
            )
            CashierPaymentStage.SUCCESS -> state.result?.let { result ->
                PaymentSuccessStatus(result) { onDone(result.folio) }
            } ?: PaymentStatus(
                Icons.Rounded.ErrorOutline,
                "No pudimos mostrar el comprobante",
                "El pago fue confirmado, pero falta el resumen canónico.",
                "Volver a comandas",
            ) { onDone(order.summary.folio) }
            CashierPaymentStage.CONFLICT -> PaymentStatus(
                Icons.Rounded.ErrorOutline, "Comanda no disponible",
                state.message ?: "Otra caja modificó esta comanda.", "Volver a la bandeja",
            ) { onDone(null) }
            CashierPaymentStage.EXPIRED -> PaymentStatus(
                Icons.Rounded.HourglassBottom, "Reserva vencida",
                state.message ?: "Actualiza la comanda antes de intentar nuevamente.", "Cerrar",
            ) { onDone(null) }
            CashierPaymentStage.ERROR -> PaymentStatus(
                Icons.Rounded.ErrorOutline, "No pudimos continuar",
                state.message ?: "Revisa tu conexión e intenta nuevamente.", "Cerrar",
            ) { onDone(null) }
            CashierPaymentStage.IDLE -> Unit
        }
    }
}

@Composable
private fun PaymentConfirmationSummary(order: CashierOrderDetail, state: CashierPaymentUiState) {
    val method = state.method
    val received = if (method == CashierPaymentMethod.CASH) state.cashAmount.parseMxnCents() else null
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Confirma que estás cobrando la comanda ${order.summary.folio}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            order.summary.folio,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = PaymentForest,
            modifier = Modifier.testTag("payment_confirmation_folio"),
        )
        PaymentSummaryRow("Total", order.summary.totalCents.asMxn())
        PaymentSummaryRow("Método", method?.label.orEmpty())
        if (method == CashierPaymentMethod.CASH && received != null) {
            PaymentSummaryRow("Importe recibido", received.asMxn())
            PaymentSummaryRow("Cambio estimado", (received - order.summary.totalCents).coerceAtLeast(0).asMxn())
        }
    }
}

@Composable
private fun PaymentSuccessStatus(result: CashierPaymentResult, onDone: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(22.dp),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.CheckCircle, null, tint = PaymentForest, modifier = Modifier.size(52.dp))
            Text("Pago confirmado", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                result.folio,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PaymentForest,
                modifier = Modifier.testTag("payment_success_folio"),
            )
            HorizontalDivider()
            PaymentSummaryRow("Total cobrado", result.amountDueCents.asMxn())
            PaymentSummaryRow("Método", result.method.label)
            if (result.method == CashierPaymentMethod.CASH) {
                PaymentSummaryRow("Importe recibido", result.amountReceivedCents.asMxn())
                PaymentSummaryRow("Cambio confirmado por servidor", result.changeCents.asMxn())
            }
            PaymentSummaryRow("Hora confirmada por servidor", PAYMENT_TIME_FORMAT.format(result.createdAt))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("payment_done"),
            ) { Text("Volver a comandas") }
        }
    }
}

@Composable
private fun PaymentSummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = Color(0xFF4A6359))
        Text(value, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun ReservationBanner(seconds: Long, onRenew: () -> Unit) {
    Surface(color = PaymentSage, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.HourglassBottom, null, tint = PaymentForest)
            Spacer(Modifier.width(10.dp))
            Text("Reserva de cobro: ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", Modifier.weight(1f))
            if (seconds in 1..60) FilledTonalButton(onClick = onRenew, modifier = Modifier.height(48.dp)) { Text("Renovar") }
        }
    }
}

@Composable
private fun PaymentMethodSelector(selected: CashierPaymentMethod?, onMethod: (CashierPaymentMethod) -> Unit) {
    Text("Método de pago", style = MaterialTheme.typography.titleMedium)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val phone = maxWidth < 600.dp
        val content: @Composable (CashierPaymentMethod, Modifier) -> Unit = { method, modifier ->
            val icon = when (method) {
                CashierPaymentMethod.CASH -> Icons.Rounded.Money
                CashierPaymentMethod.CARD -> Icons.Rounded.CreditCard
                CashierPaymentMethod.TRANSFER -> Icons.Rounded.SwapHoriz
            }
            val action = @Composable {
                Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(4.dp)); Text(method.label)
            }
            if (selected == method) Button({ onMethod(method) }, modifier.height(52.dp), content = { action() })
            else OutlinedButton({ onMethod(method) }, modifier.height(52.dp), content = { action() })
        }
        if (phone) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    content(CashierPaymentMethod.CASH, Modifier.weight(1f))
                    content(CashierPaymentMethod.CARD, Modifier.weight(1f))
                }
                content(CashierPaymentMethod.TRANSFER, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CashierPaymentMethod.entries.forEach { content(it, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PaymentHeader(order: CashierOrderDetail) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 600.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Cobrar ${order.summary.folio}", style = MaterialTheme.typography.headlineSmall, color = PaymentForest)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Total confirmado por el servidor", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(order.summary.totalCents.asMxn(), style = MaterialTheme.typography.headlineSmall, color = PaymentForest)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cobrar ${order.summary.folio}", style = MaterialTheme.typography.headlineSmall, color = PaymentForest)
                    Text("Total confirmado por el servidor", style = MaterialTheme.typography.bodyMedium)
                }
                Text(order.summary.totalCents.asMxn(), style = MaterialTheme.typography.headlineSmall, color = PaymentForest)
            }
        }
    }
}

@Composable
private fun PaymentFields(
    order: CashierOrderDetail,
    state: CashierPaymentUiState,
    onCashAmount: (String) -> Unit,
    onReference: (String) -> Unit,
) {
    when (state.method) {
        CashierPaymentMethod.CASH -> {
            OutlinedTextField(
                value = state.cashAmount,
                onValueChange = onCashAmount,
                label = { Text("Importe recibido") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("cash_amount"),
                supportingText = {
                    val amount = state.cashAmount.parseMxnCents()
                    if (amount != null && amount >= order.summary.totalCents) {
                        Text("Cambio estimado: ${(amount - order.summary.totalCents).asMxn()}. El servidor confirmará el cambio definitivo.")
                    }
                },
            )
        }
        CashierPaymentMethod.CARD -> {
            InlineMessage("Cobro realizado en terminal externa. No captures número de tarjeta, vencimiento, CVV, NIP ni nombre del titular.")
            OutlinedTextField(
                value = state.reference,
                onValueChange = onReference,
                label = { Text("Folio de operación (opcional)") },
                modifier = Modifier.fillMaxWidth().testTag("payment_reference"),
            )
        }
        CashierPaymentMethod.TRANSFER -> OutlinedTextField(
            value = state.reference,
            onValueChange = onReference,
            label = { Text("Referencia de transferencia") },
            supportingText = { Text("No ingreses cuentas bancarias ni información sensible.") },
            modifier = Modifier.fillMaxWidth().testTag("payment_reference"),
        )
        null -> Text("Selecciona cómo se recibió el pago.", color = Color(0xFF4A6359))
    }
}

@Composable
private fun PaymentProgress(label: String) {
    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = PaymentForest)
            Text(label)
        }
    }
}

@Composable
private fun PaymentStatus(icon: ImageVector, title: String, message: String, action: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(22.dp),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, null, tint = if (title == "Pago confirmado") PaymentForest else PaymentTerracotta, modifier = Modifier.size(52.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(message, textAlign = TextAlign.Center)
            HorizontalDivider()
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(action) }
        }
    }
}

@Composable
private fun InlineMessage(message: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
        Text(message, Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private val CashierPaymentMethod.label: String
    get() = when (this) {
        CashierPaymentMethod.CASH -> "Efectivo"
        CashierPaymentMethod.CARD -> "Tarjeta"
        CashierPaymentMethod.TRANSFER -> "Transferencia"
    }

private val PAYMENT_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z")
    .withZone(ZoneId.systemDefault())
