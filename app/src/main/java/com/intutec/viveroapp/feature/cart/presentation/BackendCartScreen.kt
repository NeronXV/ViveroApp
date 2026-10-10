package com.intutec.viveroapp.feature.cart.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.designsystem.*
import com.intutec.viveroapp.feature.cart.domain.repository.BackendCartLine

@Composable
fun BackendCartScreen(onBack: () -> Unit, onCatalog: () -> Unit, onHistory: () -> Unit = {}, viewModel: BackendCartViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackendCartContent(state, onBack, onCatalog, onHistory, viewModel::quantity, viewModel::remove,
        viewModel::clear, viewModel::quote, viewModel::send, viewModel::dismissQuote,
        { viewModel.newSale(); onCatalog() }, viewModel::checkResult, viewModel::refreshJournal) {
        BackendPendingSalesPanel(onJournalChanged = viewModel::refreshJournal)
    }
}

@Composable
internal fun BackendCartContent(state: BackendCartUiState, onBack: () -> Unit, onCatalog: () -> Unit, onHistory: () -> Unit,
    onQuantity: (Long, Int) -> Unit, onRemove: (Long) -> Unit, onClear: () -> Unit, onQuote: () -> Unit,
    onSend: () -> Unit, onDismissQuote: () -> Unit, onNewSale: () -> Unit, onCheck: (Long) -> Unit,
    onRefresh: () -> Unit, pendingContent: @Composable () -> Unit = {}) {
    var menu by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<BackendCartLine?>(null) }
    val rows = state.cart?.items.orEmpty()
    val estimated = rows.fold(0L) { sum, row -> Math.addExact(sum, Math.multiplyExact(row.priceCents, row.quantity.toLong())) }
    val editable = state.enabled && !state.working && !state.loading && state.quote == null
    val canSend = editable && state.journalReady && !state.hasUnresolved && state.sent == null
    LaunchedEffect(state.cart?.identity, state.enabled) { remove = null; clear = false; menu = false }
    Scaffold(topBar = { ViveroTopAppBar(if (state.sent != null) "Venta enviada" else "Carrito", onBack, "DULCINEA · ${state.branchName}") {
        if (state.sent == null) Box {
            IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "Más opciones del carrito") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Agregar productos") }, { menu = false; onCatalog() }, enabled = editable)
                if (state.canViewHistory) DropdownMenuItem({ Text("Mis ventas") }, { menu = false; onHistory() })
                DropdownMenuItem({ Text("Vaciar carrito") }, { menu = false; clear = true }, enabled = editable && rows.isNotEmpty())
            }
        }
    } }, bottomBar = {
        if (rows.isNotEmpty() && state.sent == null) Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Total estimado", style = MaterialTheme.typography.bodyMedium)
                    Text(estimated.asMxn(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
                Button(onQuote, enabled = canSend, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (state.working) "Comprobando…" else "Enviar a caja") }
                Text("Confirmarás el precio vigente antes de enviar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                if (state.loading || state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { SalesNotice(it, true) { TextButton(onRefresh, enabled = !state.loading && !state.working) { Text("Consultar ventas guardadas") } } }
                if (!state.enabled) SalesNotice("Actualiza tu sesión y permisos para operar en esta sucursal.", true)
                if (state.sent == null) state.message?.let { SalesNotice(it) }
                if (state.hasUnresolved) SalesNotice("Estamos comprobando el envío. No envíes otra venta hasta conocer el resultado.", true) {
                    val pending = state.unresolved.firstOrNull()
                    val id = pending?.localId ?: state.pendingIds.first()
                    Button({ onCheck(id) }, enabled = !state.loading && !state.working && pending?.state != "SYNCING") { Text("Consultar resultado") }
                }
            }
            if (state.sent != null) item {
                val sent = state.sent
                ViveroCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(60.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Venta enviada a Caja", style = MaterialTheme.typography.headlineSmall)
                        Text(sent.receipt?.folio.orEmpty(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Text(sent.totalCents.asMxn(), style = MaterialTheme.typography.headlineSmall)
                        Text(when (sent.receipt?.status) {
                            "SENT_TO_CASHIER" -> "Pendiente de pago en sucursal"
                            "PAID" -> "Pago confirmado"
                            "PAYMENT_PENDING" -> "Pago por comprobar"
                            "CANCELLED" -> "Venta cancelada"
                            "DELIVERED" -> "Venta entregada"
                            else -> "Consulta el estado en Mis ventas"
                        }, style = MaterialTheme.typography.bodyMedium)
                        Button(onNewSale, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !state.hasUnresolved && !state.working) { Text("Nueva venta") }
                        if (state.canViewHistory) TextButton(onHistory) { Text("Mis ventas") }
                    }
                }
            } else {
                if (rows.isEmpty() && !state.loading) item {
                    SalesEmpty("Tu carrito está vacío", "Encuentra la planta perfecta para tu cliente.") { Button(onCatalog, enabled = state.enabled) { Text("Buscar productos") } }
                }
                items(rows, key = { it.productId }) { row ->
                    ViveroCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ProductPhoto(state.photos[row.productId], row.name, Modifier.size(60.dp).clip(MaterialTheme.shapes.medium))
                                Column(Modifier.weight(1f)) { Text(row.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${row.priceCents.asMxn()} por ${row.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                IconButton({ remove = row }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), enabled = editable) { Icon(Icons.Outlined.DeleteOutline, "Quitar ${row.name}") }
                            }
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedIconButton({ onQuantity(row.productId, row.quantity - 1) }, Modifier.size(48.dp), enabled = editable && row.quantity > 1) { Icon(Icons.Outlined.Remove, "Disminuir ${row.name}") }
                                    Text(row.quantity.toString(), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
                                    OutlinedIconButton({ onQuantity(row.productId, row.quantity + 1) }, Modifier.size(48.dp), enabled = editable && row.quantity < 100000) { Icon(Icons.Outlined.Add, "Aumentar ${row.name}") }
                                }
                                Text(Math.multiplyExact(row.priceCents, row.quantity.toLong()).asMxn(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            item { pendingContent() }
        }
    }
    state.quote?.let { quote -> AlertDialog(onDismissRequest = { if (!state.working) onDismissQuote() }, title = { Text("Confirmar envío a Caja", style = MaterialTheme.typography.titleLarge) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${state.branchName} · Precios confirmados por el vivero", style = MaterialTheme.typography.bodySmall)
            if (quote.totalCents != estimated) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text("El importe se actualizó. Revisa los precios antes de confirmar.",
                    Modifier.padding(8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false).heightIn(max = 280.dp)) {
                items(quote.items, key = { it.productId }) { Text("${it.productName}\n${it.quantity} × ${it.unitPriceCents.asMxn()} · ${it.lineTotalCents.asMxn()}", style = MaterialTheme.typography.bodyMedium) }
            }
            Text("Total: ${quote.totalCents.asMxn()}", style = MaterialTheme.typography.titleLarge)
        } }, confirmButton = { Button(onSend, enabled = !state.working, modifier = Modifier.heightIn(min = 48.dp)) { Text("Confirmar y enviar") } },
        dismissButton = { TextButton(onDismissQuote, enabled = !state.working, modifier = Modifier.heightIn(min = 48.dp)) { Text("Volver al carrito") } }) }
    remove?.let { row -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("¿Quitar ${row.name}?") },
        text = { Text("Se quitará este producto del carrito.") }, confirmButton = { TextButton({ remove = null; onRemove(row.productId) }, enabled = editable) { Text("Quitar producto") } },
        dismissButton = { TextButton({ remove = null }) { Text("Conservar") } }) }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("¿Vaciar el carrito?") },
        text = { Text("Solo se quitarán los productos de este borrador. Las ventas guardadas se conservan.") },
        confirmButton = { TextButton({ clear = false; onClear() }, enabled = editable) { Text("Vaciar") } },
        dismissButton = { TextButton({ clear = false }) { Text("Conservar") } })
}
