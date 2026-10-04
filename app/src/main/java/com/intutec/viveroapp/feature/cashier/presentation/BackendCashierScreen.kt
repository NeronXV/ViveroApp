package com.intutec.viveroapp.feature.cashier.presentation

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
import com.intutec.viveroapp.feature.cashier.domain.repository.BackendCashierSale

@Composable
fun BackendCashierScreen(onBack: () -> Unit, onHistory: () -> Unit, onDetail: (Long) -> Unit, viewModel: BackendCashierViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sale by remember { mutableStateOf<BackendCashierSale?>(null) }
    var method by remember { mutableStateOf("CASH") }
    var amount by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var retryId by remember { mutableStateOf<Long?>(null) }
    var retireId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.enabled) { if (!state.enabled) { sale = null; retryId = null; retireId = null } }
    Scaffold(topBar = { ViveroTopAppBar(title = "Caja", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                ViveroSectionIntro("Caja de tu sucursal", "Comandas, cobros y comprobantes en un solo lugar.", modifier = Modifier.padding(bottom = 16.dp))
                if (state.loading || state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.message?.let { Text(it) }
                state.receipt?.let { Text("Pago confirmado · ${it.folio}\nTotal: ${it.dueCents.asMxn()} · Recibido: ${it.receivedCents.asMxn()} · Cambio: ${it.changeCents.asMxn()}") }
                OutlinedButton({ viewModel.refresh() }, enabled = !state.loading && !state.working) { Text("Actualizar") }
                TextButton(onHistory, enabled = state.enabled && !state.working) { Text("Mis comprobantes") }
                if (state.pending.isNotEmpty()) Text("Hay un intento guardado. Consulta su resultado antes de iniciar otro cobro.")
            }
            items(state.pending, key = { it.id }) { pending ->
                ViveroCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                    Text("Pago pendiente de confirmar · Venta ${pending.saleId}")
                    TextButton({ viewModel.recover(pending.id) }, enabled = !state.working && !state.loading) { Text("Consultar resultado") }
                    TextButton({ retryId = pending.id }, enabled = state.enabled && !state.working && !state.loading) { Text("Reintentar el mismo cobro") }
                    TextButton({ retireId = pending.id }, enabled = state.enabled && !state.working && !state.loading) { Text("Cerrar intento") }
                } }
            }
            items(state.sales, key = { it.id }) { row ->
                ViveroCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                    Text(row.folio, style = MaterialTheme.typography.titleMedium); Text("Total: ${row.totalCents.asMxn()}")
                    TextButton({ onDetail(row.id) }, enabled = !state.working) { Text("Ver comanda") }
                    Button({ sale = row; amount = ""; reference = ""; method = "CASH" }, enabled = state.enabled && !state.working && !state.loading && state.pending.isEmpty()) { Text("Cobrar") }
                } }
            }
            item {
                if (state.sales.isEmpty() && !state.loading) Text("No hay comandas por cobrar.")
                if (state.next != null) TextButton({ viewModel.refresh(true) }, enabled = !state.working && !state.loading) { Text("Cargar más") }
            }
        }
    }
    retireId?.let { id ->
        AlertDialog(onDismissRequest = { retireId = null }, title = { Text("Cerrar intento de cobro") },
            text = { Text("Si el pago ya existe, se conservará su comprobante. Si no existe, el servidor bloqueará la clave para impedir un cobro tardío. No cancela la venta ni devuelve dinero. Un error deja el intento pendiente.") },
            confirmButton = { TextButton({ retireId = null; viewModel.retire(id) }, enabled = state.enabled && !state.working && !state.loading) { Text("Confirmar cierre") } },
            dismissButton = { TextButton({ retireId = null }) { Text("Conservar pendiente") } })
    }
    retryId?.let { id ->
        AlertDialog(onDismissRequest = { retryId = null }, title = { Text("Reintentar intento guardado") },
            text = { Text("Primero se consultará el resultado. Si no existe, se enviará exactamente el mismo cobro. Si la reserva venció, el intento seguirá guardado para revisión; no inicies otro cobro de esta venta.") },
            confirmButton = { TextButton({ retryId = null; viewModel.retry(id) }, enabled = state.enabled && !state.working && !state.loading) { Text("Reintentar") } },
            dismissButton = { TextButton({ retryId = null }) { Text("Cancelar") } })
    }
    sale?.let { selected ->
        AlertDialog(onDismissRequest = { sale = null }, title = { Text("Confirmar cobro") }, text = { Column {
            Text("${selected.folio}\nTotal: ${selected.totalCents.asMxn()}")
            Row { listOf("CASH" to "Efectivo", "CARD" to "Tarjeta", "TRANSFER" to "Transferencia").forEach { (code, label) ->
                FilterChip(method == code, { method = code }, label = { Text(label) })
            } }
            if (method == "CASH") OutlinedTextField(amount, { if (it.length <= 18) amount = it }, label = { Text("Efectivo recibido (MXN)") }, singleLine = true)
            else OutlinedTextField(reference, { if (it.length <= 120) reference = it }, label = { Text("Referencia de operación; no datos de tarjeta") }, singleLine = true)
        } }, confirmButton = { TextButton({ sale = null; viewModel.pay(selected.id, method, amount, reference) },
            enabled = !state.working && state.pending.isEmpty() && (method != "CASH" || (amount.parseMxnCents() ?: 0) >= selected.totalCents) && (method != "TRANSFER" || reference.isNotBlank())) { Text("Confirmar pago") } },
            dismissButton = { TextButton({ sale = null }) { Text("Cancelar") } })
    }
}
