package com.intutec.viveroapp.feature.cart.presentation

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

@Composable
fun BackendPendingSalesPanel(viewModel: BackendPendingSalesViewModel = hiltViewModel(), onJournalChanged: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var retryId by remember { mutableStateOf<Long?>(null) }
    var retireId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.entries) {
        onJournalChanged()
        if (state.entries.none { it.localId == retryId && it.state in setOf("PENDING", "UNCERTAIN") }) retryId = null
        if (state.entries.none { it.localId == retireId && it.state in setOf("PENDING", "UNCERTAIN") }) retireId = null
    }
    LaunchedEffect(state.enabled) { if (!state.enabled) { open = false; retryId = null; retireId = null } }
    if (!state.enabled) { return }
    OutlinedButton(onClick = { open = true; viewModel.refresh() }, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text("Ventas guardadas y pendientes")
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false; retryId = null; retireId = null },
            title = { Text("Ventas guardadas") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("Últimos 100 intentos de tu cuenta y sucursal. El comprobante muestra el último estado confirmado.")
                        if (state.loading || state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        state.message?.let { Text(it) }
                        if (!state.loading && state.entries.isEmpty() && state.error == null) Text("No hay intentos guardados.")
                    }
                    items(state.entries, key = { it.localId }) { row ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val label = when (row.state) {
                                "PENDING" -> "Pendiente de envío"
                                "UNCERTAIN" -> "Resultado sin confirmar"
                                "SYNCING" -> "Envío en curso"
                                "RETIRED" -> "Cerrado sin venta"
                                "SYNCED" -> "Confirmada"
                                else -> "Requiere revisión"
                            }
                            Text("${row.receipt?.folio ?: "Envío guardado"} · $label", style = MaterialTheme.typography.titleSmall)
                            Text("${row.items.sumOf { it.quantity.toLong() }} unidades · ${row.totalCents.asMxn()}")
                            row.lastError?.let { Text(it) }
                            row.receipt?.let {
                                val status = when (it.status) {
                                    "SENT_TO_CASHIER" -> "Enviada a caja"
                                    "PAYMENT_PENDING" -> "Pago en proceso"
                                    "PAID" -> "Pagada"
                                    "CANCELLED" -> "Cancelada"
                                    "DELIVERED" -> "Entregada"
                                    else -> "Requiere revisión"
                                }
                                Text("Folio: ${it.folio}\nEstado confirmado: $status")
                            }
                            if (row.state in setOf("PENDING", "UNCERTAIN")) {
                                TextButton(onClick = { viewModel.checkResult(row.localId) }, enabled = !state.loading && !state.working) { Text("Consultar resultado") }
                                TextButton(onClick = { retryId = row.localId }, enabled = !state.loading && !state.working) { Text("Reintentar envío") }
                                TextButton(onClick = { retireId = row.localId }, enabled = !state.loading && !state.working) { Text("Cerrar intento") }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.refresh() }, enabled = !state.loading && !state.working) { Text("Actualizar") } },
            dismissButton = { TextButton(onClick = { open = false; retryId = null; retireId = null }) { Text("Cerrar") } },
        )
    }
    retryId?.let { id ->
        AlertDialog(onDismissRequest = { retryId = null }, title = { Text("¿Reintentar esta venta?") },
            text = { Text("Se conservarán los productos y total originales. Primero comprobaremos si la venta ya existe; solo si no existe se reenviará. Si cambió el precio, se conservará para revisión.") },
            confirmButton = { TextButton(onClick = { retryId = null; viewModel.retry(id) }) { Text("Reintentar") } },
            dismissButton = { TextButton(onClick = { retryId = null }) { Text("Conservar sin enviar") } })
    }
    retireId?.let { id ->
        AlertDialog(onDismissRequest = { retireId = null }, title = { Text("¿Cerrar este intento?") },
            text = { Text("Si la venta ya existe, conservaremos su comprobante. Si no existe, el servidor cerrará este intento para impedir un envío tardío. El historial se conserva. Un error no desbloquea una nueva venta.") },
            confirmButton = { TextButton(onClick = { retireId = null; viewModel.retire(id) }, enabled = !state.loading && !state.working) { Text("Confirmar cierre") } },
            dismissButton = { TextButton(onClick = { retireId = null }) { Text("Conservar pendiente") } })
    }
}
