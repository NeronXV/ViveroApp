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
import com.intutec.viveroapp.core.designsystem.ViveroTopAppBar
import com.intutec.viveroapp.core.designsystem.ViveroCard
import com.intutec.viveroapp.core.designsystem.ViveroSectionIntro

@Composable
fun BackendCartScreen(onBack: () -> Unit, onCatalog: () -> Unit, viewModel: BackendCartViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var cancel by remember { mutableStateOf(false) }
    Scaffold(topBar = { ViveroTopAppBar(title = "Carrito", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                ViveroSectionIntro("Tu próxima venta", "Organiza la comanda y confirma el total antes de enviarla a caja.", modifier = Modifier.padding(bottom = 16.dp))
                if (state.loading || state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.message?.let { Text(it) }
                if (!state.enabled) Text("Actualiza la sesión y los permisos para operar en tu sucursal.")
                Text("Tu comanda se guarda por cuenta y sucursal. Confirma precios y existencias antes de cobrar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onCatalog) { Text("Agregar productos") }
                BackendPendingSalesPanel()
            }
            items(state.cart?.items.orEmpty(), key = { it.productId }) { row ->
                ViveroCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                    Text(row.name, style = MaterialTheme.typography.titleMedium)
                    Text("${row.priceCents.asMxn()} / ${row.unit} · ${row.quantity} unidades")
                    Row {
                        TextButton({ viewModel.quantity(row.productId, row.quantity - 1) }, enabled = state.enabled && !state.working && state.quote == null && row.quantity > 1) { Text("−") }
                        TextButton({ viewModel.quantity(row.productId, row.quantity + 1) }, enabled = state.enabled && !state.working && state.quote == null && row.quantity < 100000) { Text("+") }
                        TextButton({ viewModel.remove(row.productId) }, enabled = state.enabled && !state.working && state.quote == null) { Text("Quitar") }
                    }
                } }
            }
            item {
                if (state.cart?.items.isNullOrEmpty()) Text("Tu carrito está vacío.")
                else {
                    ViveroCard(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Resumen de tu comanda", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text("${state.cart?.items.orEmpty().sumOf { it.quantity }} artículos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.cart?.items.orEmpty().fold(0L) { total, row -> Math.addExact(total, Math.multiplyExact(row.priceCents, row.quantity.toLong())) }.asMxn(),
                                style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Total estimado. La cotización confirmará el importe vigente.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(viewModel::quote, enabled = state.enabled && !state.working && !state.loading && state.quote == null, modifier = Modifier.fillMaxWidth()) { Text("Cotizar antes de enviar a caja") }
                    TextButton({ cancel = true }, enabled = state.enabled && !state.working && state.quote == null) { Text("Vaciar carrito") }
                }
            }
        }
    }
    state.quote?.let { quote ->
        AlertDialog(onDismissRequest = viewModel::dismissQuote, title = { Text("Confirmar comanda") },
            text = { Column { quote.items.forEach { Text("${it.quantity} × ${it.productName}: ${it.lineTotalCents.asMxn()}") }; Text("Total cotizado: ${quote.totalCents.asMxn()}") } },
            confirmButton = { TextButton(viewModel::send, enabled = !state.working) { Text("Confirmar y enviar a caja") } },
            dismissButton = { TextButton(viewModel::dismissQuote, enabled = !state.working) { Text("Volver al carrito") } })
    }
    if (cancel) AlertDialog(onDismissRequest = { cancel = false }, title = { Text("¿Vaciar el carrito?") },
        text = { Text("Se eliminará solo este borrador. Los intentos de venta y su historial se conservan.") },
        confirmButton = { TextButton({ cancel = false; viewModel.clear() }) { Text("Vaciar") } },
        dismissButton = { TextButton({ cancel = false }) { Text("Conservar") } })
}
