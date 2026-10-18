package com.intutec.viveroapp.feature.cart.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.designsystem.*
import com.intutec.viveroapp.feature.cart.domain.repository.BackendCartLine

@Composable
fun BackendCartScreen(
    onBack: () -> Unit,
    onCatalog: () -> Unit,
    onHistory: () -> Unit = {},
    viewModel: BackendCartViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackendCartContent(
        state = state,
        onBack = onBack,
        onCatalog = onCatalog,
        onHistory = onHistory,
        onQuantity = viewModel::quantity,
        onRemove = viewModel::remove,
        onClear = viewModel::clear,
        onPatioNote = viewModel::patioNote,
        onQuote = viewModel::quote,
        onSend = viewModel::send,
        onDismissQuote = viewModel::dismissQuote,
        onNewSale = { viewModel.newSale(); onCatalog() },
        onCheck = viewModel::checkResult,
        onRefresh = viewModel::refreshJournal
    ) {
        BackendPendingSalesPanel(onJournalChanged = viewModel::refreshJournal)
    }
}

@Composable
internal fun BackendCartContent(
    state: BackendCartUiState,
    onBack: () -> Unit,
    onCatalog: () -> Unit,
    onHistory: () -> Unit,
    onQuantity: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onClear: () -> Unit,
    onPatioNote: (String) -> Unit,
    onQuote: () -> Unit,
    onSend: () -> Unit,
    onDismissQuote: () -> Unit,
    onNewSale: () -> Unit,
    onCheck: (Long) -> Unit,
    onRefresh: () -> Unit,
    pendingContent: @Composable () -> Unit = {}
) {
    var menu by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<BackendCartLine?>(null) }
    val rows = state.cart?.items.orEmpty()
    val estimated = rows.fold(0L) { sum, row ->
        Math.addExact(sum, Math.multiplyExact(row.priceCents, row.quantity.toLong()))
    }
    val editable = state.enabled && !state.working && !state.loading && state.quote == null
    val canSend = editable && state.journalReady && !state.hasUnresolved && state.sent == null

    LaunchedEffect(state.cart?.identity, state.enabled) {
        remove = null
        clear = false
        menu = false
    }

    Scaffold(
        topBar = {
            ViveroTopAppBar(
                title = if (state.sent != null) "Venta enviada" else "Carrito",
                onBack = onBack,
                eyebrow = "MI COMANDA · ${state.branchName.uppercase()}"
            ) {
                if (state.sent == null) {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "Más opciones del carrito",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Agregar productos") },
                                onClick = { menu = false; onCatalog() },
                                enabled = editable,
                                leadingIcon = { Icon(Icons.Outlined.AddShoppingCart, null) }
                            )
                            if (state.canViewHistory) {
                                DropdownMenuItem(
                                    text = { Text("Mis ventas") },
                                    onClick = { menu = false; onHistory() },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Vaciar carrito") },
                                onClick = { menu = false; clear = true },
                                enabled = editable && rows.isNotEmpty(),
                                leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null) }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (rows.isNotEmpty() && state.sent == null) {
                Surface(
                    shadowElevation = 12.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Total estimado",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                estimated.asMxn(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(
                            onClick = onQuote,
                            enabled = canSend,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                        ) {
                            Icon(Icons.Outlined.PointOfSale, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (state.working) "Comprobando..." else "Enviar comanda a Caja",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            "Confirmarás el precio vigente antes de enviar a caja.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                if (state.loading || state.working) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                state.error?.let {
                    SalesNotice(it, critical = true) {
                        TextButton(
                            onClick = onRefresh,
                            enabled = !state.loading && !state.working
                        ) {
                            Text("Consultar ventas guardadas")
                        }
                    }
                }
                if (!state.enabled) {
                    SalesNotice(
                        "Actualiza tu sesión y permisos para operar en esta sucursal.",
                        critical = true
                    )
                }
                if (state.sent == null) {
                    state.message?.let { SalesNotice(it) }
                }
                if (state.hasUnresolved) {
                    SalesNotice(
                        "Estamos comprobando el envío. No envíes otra venta hasta conocer el resultado.",
                        critical = true
                    ) {
                        val pending = state.unresolved.firstOrNull()
                        val id = pending?.localId ?: state.pendingIds.first()
                        Button(
                            onClick = { onCheck(id) },
                            enabled = !state.loading && !state.working && pending?.state != "SYNCING"
                        ) {
                            Text("Consultar resultado")
                        }
                    }
                }
            }

            if (state.sent != null) {
                item {
                    val sent = state.sent
                    ViveroCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Venta enviada a Caja",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ) {
                                Text(
                                    sent.receipt?.folio.orEmpty(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                )
                            }
                            Text(
                                sent.totalCents.asMxn(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            StatusPill(
                                text = when (sent.receipt?.status) {
                                    "SENT_TO_CASHIER" -> "Pendiente de pago en sucursal"
                                    "PAID" -> "Pago confirmado"
                                    "PAYMENT_PENDING" -> "Pago por comprobar"
                                    "CANCELLED" -> "Venta cancelada"
                                    "DELIVERED" -> "Venta entregada"
                                    else -> "Consulta el estado en Mis ventas"
                                }
                            )
                            Button(
                                onClick = onNewSale,
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp),
                                enabled = !state.hasUnresolved && !state.working
                            ) {
                                Icon(Icons.Outlined.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Nueva venta", fontWeight = FontWeight.Bold)
                            }
                            if (state.canViewHistory) {
                                TextButton(onClick = onHistory) {
                                    Text("Ver mis ventas")
                                }
                            }
                        }
                    }
                }
            } else {
                if (rows.isEmpty() && !state.loading) {
                    item {
                        SalesEmpty(
                            title = "Tu carrito está vacío",
                            message = "Encuentra la planta perfecta para tu cliente en el catálogo."
                        ) {
                            Button(
                                onClick = onCatalog,
                                enabled = state.enabled,
                                shape = CircleShape
                            ) {
                                Icon(Icons.Outlined.Search, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Explorar productos")
                            }
                        }
                    }
                }

                if (rows.isNotEmpty()) {
                    item {
                        OutlinedTextField(
                            value = state.patioNote,
                            onValueChange = onPatioNote,
                            label = { Text("Referencia de patio (opcional)") },
                            supportingText = { Text("Para identificar al cliente en Caja; máximo 120 caracteres.") },
                            leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                            singleLine = true,
                            enabled = editable,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                items(rows, key = { it.productId }) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ProductPhoto(
                                    model = state.photos[row.productId],
                                    name = row.name,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        row.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${row.priceCents.asMxn()} / ${row.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { remove = row },
                                    enabled = editable
                                ) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "Quitar ${row.name}",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onQuantity(row.productId, row.quantity - 1) },
                                            enabled = editable && row.quantity > 1,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Remove,
                                                contentDescription = "Disminuir ${row.name}"
                                            )
                                        }
                                        Text(
                                            row.quantity.toString(),
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(
                                            onClick = { onQuantity(row.productId, row.quantity + 1) },
                                            enabled = editable && row.quantity < 100000,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Add,
                                                contentDescription = "Aumentar ${row.name}"
                                            )
                                        }
                                    }
                                }

                                Text(
                                    Math.multiplyExact(row.priceCents, row.quantity.toLong()).asMxn(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            item { pendingContent() }
        }
    }

    state.quote?.let { quote ->
        AlertDialog(
            onDismissRequest = { if (!state.working) onDismissQuote() },
            title = {
                Text(
                    "Confirmar envío a Caja",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "${state.branchName} · Precios confirmados por el vivero",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.patioNote.trim().takeIf { it.isNotEmpty() }?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "Referencia de patio: $it",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (quote.totalCents != estimated) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                "El importe se actualizó. Revisa los precios antes de confirmar.",
                                modifier = Modifier
                                    .padding(10.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 280.dp)
                    ) {
                        items(quote.items, key = { it.productId }) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.productName,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "${item.quantity} × ${item.unitPriceCents.asMxn()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    item.lineTotalCents.asMxn(),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total a confirmar:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            quote.totalCents.asMxn(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onSend,
                    enabled = !state.working,
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Confirmar y enviar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissQuote,
                    enabled = !state.working,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Volver al carrito")
                }
            }
        )
    }

    remove?.let { row ->
        AlertDialog(
            onDismissRequest = { remove = null },
            title = { Text("¿Quitar ${row.name}?") },
            text = { Text("Se quitará este producto del carrito de la comanda.") },
            confirmButton = {
                TextButton(
                    onClick = { remove = null; onRemove(row.productId) },
                    enabled = editable
                ) {
                    Text("Quitar producto", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { remove = null }) {
                    Text("Conservar")
                }
            }
        )
    }

    if (clear) {
        AlertDialog(
            onDismissRequest = { clear = false },
            title = { Text("¿Vaciar el carrito?") },
            text = { Text("Solo se quitarán los productos de este borrador. Las ventas guardadas se conservan.") },
            confirmButton = {
                TextButton(
                    onClick = { clear = false; onClear() },
                    enabled = editable
                ) {
                    Text("Vaciar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { clear = false }) {
                    Text("Conservar")
                }
            }
        )
    }
}
