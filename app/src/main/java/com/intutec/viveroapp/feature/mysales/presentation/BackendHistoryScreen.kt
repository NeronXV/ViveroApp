package com.intutec.viveroapp.feature.mysales.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.designsystem.ViveroTopAppBar
import com.intutec.viveroapp.core.designsystem.ViveroCard
import com.intutec.viveroapp.core.designsystem.ViveroSectionIntro
import com.intutec.viveroapp.feature.mysales.domain.repository.*

internal fun historyLabel(value: String) = when (value) {
    "DRAFT" -> "Borrador"; "SENT_TO_CASHIER" -> "Enviada a caja"; "PAYMENT_PENDING" -> "Pago pendiente"
    "PAID" -> "Pagada"; "DELIVERED" -> "Entregada"; "CANCELLED" -> "Cancelada"
    "CASH" -> "Efectivo"; "CARD" -> "Tarjeta"; "TRANSFER" -> "Transferencia"; else -> value
}
@Composable
fun BackendHistoryScreen(onBack: () -> Unit, viewModel: BackendHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val back = { if (state.document != null && state.hasList) viewModel.closeDetail() else onBack() }
    BackHandler(enabled = state.document != null && state.hasList) { viewModel.closeDetail() }
    Scaffold(topBar = { ViveroTopAppBar(title = if (state.kind == BackendHistoryKind.SALES) "Mis ventas" else if (state.kind == BackendHistoryKind.PAYMENTS) "Mis comprobantes" else "Detalle de comanda", onBack = back) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                ViveroSectionIntro("Cada operación, a detalle", "Consulta el estado confirmado de tus ventas y comprobantes.", modifier = Modifier.padding(bottom = 16.dp))
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!state.enabled) Text("Tus permisos no permiten consultar este historial.")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(viewModel::retry, enabled = state.enabled && !state.loading) { Text("Actualizar") }
                if (state.document == null && state.hasList) Text("Registros de tu cuenta y sucursal actual. Los importes corresponden a lo guardado en cada operación.")
            }
            state.document?.let { doc ->
                item {
                    Text(doc.sale.folio, style = MaterialTheme.typography.titleLarge)
                    Text(historyLabel(doc.sale.status)); doc.branchName?.let { Text(it) }
                    Text("Subtotal: ${doc.sale.subtotalCents.asMxn()}\nDescuento: ${doc.sale.discountCents.asMxn()}\nTotal: ${doc.sale.totalCents.asMxn()}")
                    doc.payment?.let { Text("Pago ${it.id} · ${historyLabel(it.method)}\n${it.createdAt}\nRecibido: ${it.receivedCents.asMxn()} · Cambio: ${it.changeCents.asMxn()}"); it.reference?.let { ref -> Text("Referencia: $ref") } }
                    doc.refund?.let { Text("Devolución registrada: ${it.amountCents.asMxn()} · ${historyLabel(it.method)}") }
                }
                items(doc.lines, key = { it.id }) { line -> ViveroCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                    Text(line.name, style = MaterialTheme.typography.titleMedium)
                    line.code?.let { Text(it) }; Text("${line.quantity} × ${line.unitPriceCents.asMxn()} = ${line.totalCents.asMxn()}")
                    line.promotion?.let { Text(it) }
                } } }
                items(doc.events) { event -> Text("${event.createdAt}\n${historyLabel(event.status)} · ${event.observation}") }
            } ?: run {
                items(state.entries, key = { it.id }) { entry -> ViveroCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                    Text(entry.folio, style = MaterialTheme.typography.titleMedium)
                    Text("${historyLabel(entry.label)} · ${entry.totalCents.asMxn()}\n${entry.createdAt}")
                    TextButton({ viewModel.detail(entry.id) }, enabled = !state.loading) { Text("Ver detalle") }
                } } }
                item {
                    if (state.hasList && state.entries.isEmpty() && !state.loading && state.error == null) Text("No hay registros disponibles.")
                    if (state.next != null) TextButton({ viewModel.refresh(true) }, enabled = !state.loading) { Text("Cargar más") }
                }
            }
        }
    }
}
