package com.intutec.viveroapp.feature.inventory.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.designsystem.*
import com.intutec.viveroapp.feature.inventory.domain.repository.*

internal fun inventoryQuantity(milli: Long): String =
    java.math.BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString()

@Composable
fun BackendInventoryScreen(
    onBack: () -> Unit,
    onWeb: (() -> Unit)? = null,
    viewModel: BackendInventoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<BackendInventoryItem?>(null) }
    var action by remember { mutableStateOf(BackendInventoryAction.RECEPTION) }
    var quantity by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var retryId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(state.identity, state.canWrite) {
        selected = null
        retryId = null
    }

    BackHandler(enabled = state.historyProduct != null) {
        viewModel.closeHistory()
    }

    Scaffold(
        topBar = {
            ViveroTopAppBar(
                title = "Inventario",
                onBack = { if (state.historyProduct != null) viewModel.closeHistory() else onBack() },
                eyebrow = "SUCURSAL · EXISTENCIAS Y MOVIMIENTOS"
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ViveroSectionIntro(
                    title = "Existencias y movimientos",
                    subtitle = "Consulta las existencias y el historial de tu sucursal.",
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                onWeb?.let { open ->
                    OutlinedButton(
                        onClick = open,
                        enabled = state.enabled,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir conteos en Web")
                    }
                }

                if (state.loading || state.working) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                state.error?.let {
                    SalesNotice(it, critical = true)
                }

                state.receipt?.let { r ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                if (r.action == BackendInventoryAction.RECEPTION)
                                    "Recepción ${r.id} confirmada: ${inventoryQuantity(r.quantityMilli)} unidades."
                                else
                                    "Conteo ${r.id} confirmado: ${inventoryQuantity(r.quantityMilli)} unidades registradas; diferencia ${inventoryQuantity(requireNotNull(r.adjustmentMilli))}.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = viewModel::retryRead,
                        enabled = state.enabled && !state.loading && !state.working,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Actualizar")
                    }
                }

                if (!state.enabled) {
                    SalesNotice("Actualiza tus permisos para consultar el inventario.", critical = true)
                }
            }

            // Pending operations
            items(state.pending, key = { "attempt-${it.id}" }) { pending ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Intento ${pending.id} sin confirmar · Producto ID ${pending.productId}", fontWeight = FontWeight.Bold)
                        Text("${if (pending.action == BackendInventoryAction.RECEPTION) "Recepción" else "Conteo"}: ${pending.quantity} unidades")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { viewModel.recover(pending.id) },
                                enabled = state.canWrite && !state.loading && !state.working
                            ) {
                                Text("Consultar resultado")
                            }
                            TextButton(
                                onClick = { retryId = pending.id },
                                enabled = state.canWrite && !state.loading && !state.working
                            ) {
                                Text("Reintentar")
                            }
                        }
                    }
                }
            }

            val product = state.historyProduct
            if (product != null) {
                item {
                    Text(
                        "Historial · ${product.name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(state.movements, key = { "movement-${it.id}" }) { movement ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val type = when (movement.type) {
                                "OPENING" -> "Apertura"
                                "RECEPTION" -> "Recepción"
                                "ADJUSTMENT_ADD" -> "Ajuste positivo"
                                "ADJUSTMENT_SUB" -> "Ajuste negativo"
                                "SALE" -> "Venta"
                                "REFUND" -> "Devolución"
                                else -> movement.type
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(type, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        "${inventoryQuantity(movement.quantityMilli)} unidades",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(movement.createdAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            movement.actorLabel?.let { Text("Por: $it", style = MaterialTheme.typography.bodySmall) }
                            movement.notes?.let { Text("Notas: $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }

                item {
                    if (state.movements.isEmpty() && !state.loading && state.error == null) {
                        SalesEmpty(title = "Sin movimientos", message = "No hay registro de movimientos para este producto.")
                    }
                    if (state.nextMovement != null) {
                        OutlinedButton(
                            onClick = { viewModel.history(product, true) },
                            enabled = !state.loading && !state.working,
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape
                        ) {
                            Text("Más movimientos")
                        }
                    }
                }
            } else {
                items(state.products, key = { "product-${it.id}" }) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        row.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Código: ${row.code}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (row.low) {
                                    StatusPill(
                                        text = "Inventario bajo",
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Existencia actual", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${inventoryQuantity(row.quantityMilli)} ${row.unit}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column {
                                    Text("Mínimo requerido", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${inventoryQuantity(row.minimumMilli)} ${row.unit}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { viewModel.history(row) },
                                    enabled = !state.loading && !state.working
                                ) {
                                    Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Ver historial")
                                }

                                if (state.canWrite) {
                                    Button(
                                        onClick = {
                                            selected = row
                                            action = BackendInventoryAction.RECEPTION
                                            quantity = ""
                                            notes = ""
                                        },
                                        enabled = !state.loading && !state.working && state.pending.isEmpty(),
                                        shape = CircleShape
                                    ) {
                                        Icon(Icons.Outlined.MoveToInbox, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Recibir")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    if (state.products.isEmpty() && !state.loading && state.error == null) {
                        SalesEmpty(title = "Sin productos", message = "No hay productos disponibles en el inventario.")
                    }
                    if (state.next != null) {
                        OutlinedButton(
                            onClick = { viewModel.refresh(true) },
                            enabled = !state.loading && !state.working,
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape
                        ) {
                            Text("Más productos")
                        }
                    }
                }
            }
        }
    }

    selected?.let { product ->
        val count = quantity.toLongOrNull()
        val valid = Regex("^(?:0|[1-9][0-9]{0,10})$").matches(quantity) && count != null && (action == BackendInventoryAction.COUNT || count > 0) && (action != BackendInventoryAction.COUNT || notes.trim().codePointCount(0, notes.trim().length) >= 3)
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(if (action == BackendInventoryAction.RECEPTION) "Confirmar recepción" else "Confirmar conteo físico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(product.name, fontWeight = FontWeight.Bold)
                    Text(
                        if (action == BackendInventoryAction.RECEPTION)
                            "La cantidad se sumará a la existencia de tu sucursal."
                        else
                            "Indica las unidades reales. El servidor registrará la diferencia, que puede disminuir la existencia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { if (it.length <= 11) quantity = it },
                        label = { Text("Unidades enteras") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { if (it.codePointCount(0, it.length) <= 240) notes = it },
                        label = { Text(if (action == BackendInventoryAction.COUNT) "Motivo obligatorio" else "Notas opcionales") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selected = null
                        viewModel.start(action, product.id, quantity, notes)
                    },
                    enabled = valid && state.canWrite && !state.working && !state.loading && state.pending.isEmpty(),
                    shape = CircleShape
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    retryId?.let { id ->
        AlertDialog(
            onDismissRequest = { retryId = null },
            title = { Text("Reintentar intento guardado") },
            text = { Text("Se consultará primero el resultado. Solo si no existe se enviarán la cantidad, motivo y clave originales. Un error conserva el intento; no registres otra operación para sustituirlo.") },
            confirmButton = {
                TextButton(
                    onClick = { retryId = null; viewModel.recover(id, true) },
                    enabled = state.canWrite && !state.working && !state.loading
                ) {
                    Text("Reintentar")
                }
            },
            dismissButton = {
                TextButton(onClick = { retryId = null }) {
                    Text("Conservar")
                }
            }
        )
    }
}
