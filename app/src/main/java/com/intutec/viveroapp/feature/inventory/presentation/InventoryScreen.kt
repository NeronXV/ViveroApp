package com.intutec.viveroapp.feature.inventory.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.feature.inventory.domain.model.InventoryItem
import com.intutec.viveroapp.feature.inventory.domain.model.InventoryMovement
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun InventoryScreenRoute(
    onBack: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InventoryScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onQueryChanged = viewModel::updateQuery,
        onOpenAction = viewModel::openAction,
        onOpenHistory = viewModel::openHistory,
        onQuantityChanged = viewModel::updateQuantity,
        onDetailChanged = viewModel::updateDetail,
        onSubmit = viewModel::submit,
        onCloseDialog = viewModel::closeDialog,
        onCloseHistory = viewModel::closeHistory,
        onDismissMessage = viewModel::dismissMessage,
    )
}

@Composable
fun InventoryScreen(
    state: InventoryUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onOpenAction: (InventoryItem, InventoryAction) -> Unit,
    onOpenHistory: (InventoryItem) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onDetailChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onCloseDialog: () -> Unit,
    onCloseHistory: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error, state.notice) {
        (state.notice ?: state.error?.takeIf { state.items.isNotEmpty() || state.selectedItem != null })?.let {
            snackbar.showSnackbar(it)
            onDismissMessage()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
                Column(Modifier.weight(1f)) {
                    Text("Inventario", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Existencias de tu sucursal", style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Outlined.Inventory2, null, Modifier.padding(end = 12.dp).size(28.dp).alpha(.4f))
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                label = { Text("Buscar producto o código") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("inventory_search"),
            )
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.testTag("inventory_loading"))
                }
                state.error != null && state.items.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp).testTag("inventory_error"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry, modifier = Modifier.testTag("inventory_retry")) { Text("Reintentar") }
                }
                state.visibleItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay productos para mostrar.", Modifier.padding(24.dp))
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 340.dp),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize().testTag("inventory_list"),
                    ) {
                        items(state.visibleItems, key = InventoryItem::productId) { item ->
                            InventoryCard(item, onOpenAction, onOpenHistory)
                        }
                    }
                }
            }
        }
    }
    if (state.action != null && state.selectedItem != null) {
        OperationDialog(state, onQuantityChanged, onDetailChanged, onSubmit, onCloseDialog)
    } else if (state.selectedItem != null && (state.history != null || state.isHistoryLoading)) {
        HistoryDialog(state.selectedItem, state.history, state.isHistoryLoading, onCloseHistory)
    }
}

@Composable
private fun InventoryCard(
    item: InventoryItem,
    onOpenAction: (InventoryItem, InventoryAction) -> Unit,
    onOpenHistory: (InventoryItem) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (item.isLowStock) Color(0xFFFFF1EA) else Color(0xFFF7FAF5)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.testTag("inventory_card_${item.productId}").semantics {
            contentDescription = "Producto ${item.productName}, código ${item.productCode}, existencia ${item.totalQuantity} ${item.productUnit}"
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(item.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(item.productCode, style = MaterialTheme.typography.bodySmall)
                }
                Text("${item.totalQuantity} ${item.productUnit}", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                if (item.isLowStock) "Existencia baja · mínimo ${item.minimumStock}" else "Mínimo ${item.minimumStock}",
                color = if (item.isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (item.isLowStock) FontWeight.Medium else FontWeight.Normal,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onOpenAction(item, InventoryAction.RECEPTION) },
                    modifier = Modifier.weight(1f).testTag("inventory_reception_${item.productId}")
                ) { Text("Recibir") }
                OutlinedButton(
                    onClick = { onOpenAction(item, InventoryAction.COUNT) },
                    modifier = Modifier.weight(1f).testTag("inventory_count_${item.productId}")
                ) { Text("Contar") }
                IconButton(
                    onClick = { onOpenHistory(item) },
                    modifier = Modifier.testTag("inventory_history_${item.productId}")
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Ver historial", Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
private fun OperationDialog(
    state: InventoryUiState,
    onQuantityChanged: (String) -> Unit,
    onDetailChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    val isCount = state.action == InventoryAction.COUNT
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag("inventory_op_dialog"),
        title = { Text(if (isCount) "Conciliar conteo" else "Registrar recepción") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(checkNotNull(state.selectedItem).productName, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = state.quantityInput,
                    onValueChange = onQuantityChanged,
                    label = { Text(if (isCount) "Existencia contada" else "Cantidad recibida") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("inventory_quantity_input"),
                )
                OutlinedTextField(
                    value = state.detailInput,
                    onValueChange = onDetailChanged,
                    label = { Text(if (isCount) "Motivo obligatorio" else "Nota opcional") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("inventory_detail_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = !state.isSubmitting,
                modifier = Modifier.testTag("inventory_confirm_btn")
            ) {
                Text(if (state.isSubmitting) "Guardando…" else "Confirmar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onClose,
                enabled = !state.isSubmitting,
                modifier = Modifier.testTag("inventory_cancel_btn")
            ) { Text("Cancelar") }
        },
    )
}

@Composable
private fun HistoryDialog(
    item: InventoryItem,
    history: List<InventoryMovement>?,
    loading: Boolean,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag("inventory_history_dialog"),
        title = { Text("Historial · ${item.productName}") },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(min = 100.dp)) {
                when {
                    loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).testTag("inventory_history_loading"))
                    history.isNullOrEmpty() -> Text("Aún no hay movimientos.", Modifier.align(Alignment.Center))
                    else -> LazyColumn(
                        Modifier.testTag("inventory_history_list"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(history, key = InventoryMovement::id) { movement ->
                            Column {
                                Text("${movement.movementType.displayName()} · ${movement.quantity.withSign()}", fontWeight = FontWeight.SemiBold)
                                Text(historyDate.format(movement.createdAt.atZone(ZoneId.systemDefault())), style = MaterialTheme.typography.bodySmall)
                                movement.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                movement.createdByLabel?.let { Text("Registró: $it", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } },
    )
}

private fun String.displayName(): String = when (this) {
    "RECEPTION" -> "Recepción"
    "ADJUSTMENT_ADD" -> "Ajuste de entrada"
    "ADJUSTMENT_SUB" -> "Ajuste de salida"
    "SALE" -> "Venta"
    else -> "Movimiento"
}

private fun Int.withSign(): String = if (this > 0) "+$this" else toString()
private val historyDate = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
