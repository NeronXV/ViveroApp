package com.intutec.viveroapp.feature.cart.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.designsystem.StatusPill
import com.intutec.viveroapp.feature.cart.domain.model.Cart
import com.intutec.viveroapp.feature.cart.domain.model.CartItem
import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import com.intutec.viveroapp.feature.cart.domain.model.SaleSyncState
import com.intutec.viveroapp.feature.customer.domain.model.Customer
import com.intutec.viveroapp.feature.catalog.presentation.productImageResource

@Composable
fun CartScreenRoute(
    onBack: () -> Unit,
    onBrowseCatalog: () -> Unit,
    onMySales: () -> Unit,
    viewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CartScreen(
        state = state,
        onBack = onBack,
        onBrowseCatalog = onBrowseCatalog,
        onMySales = onMySales,
        onIncrement = viewModel::increment,
        onDecrement = viewModel::decrement,
        onRemove = viewModel::remove,
        onAssociateCustomer = viewModel::openCustomerSearch,
        onRemoveCustomer = viewModel::removeCustomer,
        onSaveDraft = viewModel::saveDraft,
        onCancel = viewModel::cancelCart,
        onSend = viewModel::sendToCashier,
        onRetrySaleSync = viewModel::retrySaleSync,
        onStartNew = viewModel::startNewCart,
        onNoticeShown = viewModel::clearNotice,
        onCustomerQueryChanged = viewModel::updateCustomerQuery,
        onCustomerSelected = viewModel::associateCustomer,
        onCloseCustomerSearch = viewModel::closeCustomerSearch,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartScreen(
    state: CartUiState,
    onBack: () -> Unit,
    onBrowseCatalog: () -> Unit,
    onMySales: () -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAssociateCustomer: () -> Unit,
    onRemoveCustomer: () -> Unit,
    onSaveDraft: () -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    onRetrySaleSync: () -> Unit,
    onStartNew: () -> Unit,
    onNoticeShown: () -> Unit,
    onCustomerQueryChanged: (String) -> Unit,
    onCustomerSelected: (Customer) -> Unit,
    onCloseCustomerSearch: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message, state.error) {
        (state.error ?: state.message)?.let { snackbar.showSnackbar(it); onNoticeShown() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Carrito actual"); Text("Venta en preparación", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.sentTicket != null -> SentTicketContent(
                    ticket = state.sentTicket,
                    working = state.working,
                    onRetry = onRetrySaleSync,
                    onStartNew = onStartNew,
                    onMySales = onMySales,
                    onBack = onBack,
                )
                state.cart.items.isEmpty() -> EmptyCart(onBrowseCatalog)
                else -> CartContent(
                    cart = state.cart,
                    working = state.working,
                    onIncrement = onIncrement,
                    onDecrement = onDecrement,
                    onRemove = onRemove,
                    onAssociateCustomer = onAssociateCustomer,
                    onRemoveCustomer = onRemoveCustomer,
                    onSaveDraft = onSaveDraft,
                    onCancel = { confirmCancel = true },
                    onSend = onSend,
                )
            }
        }
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("¿Cancelar este carrito?") },
            text = { Text("Se eliminará el borrador y todos sus productos. Esta acción no afecta tickets ya enviados.") },
            confirmButton = { Button(onClick = { confirmCancel = false; onCancel() }) { Text("Cancelar carrito") } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Conservar") } },
        )
    }

    if (state.showCustomerSearch) {
        CustomerSearchDialog(
            query = state.customerQuery,
            searching = state.searchingCustomers,
            results = state.customerResults,
            error = state.customerSearchError,
            onQueryChange = onCustomerQueryChanged,
            onSelected = onCustomerSelected,
            onDismiss = onCloseCustomerSearch,
        )
    }
}

@Composable
private fun CartContent(
    cart: Cart,
    working: Boolean,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAssociateCustomer: () -> Unit,
    onRemoveCustomer: () -> Unit,
    onSaveDraft: () -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("Orden botánica", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text("${cart.itemCount} unidades listas para enviar a caja", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(cart.items, key = CartItem::productId) { item ->
            CartItemCard(item, working, onIncrement, onDecrement, onRemove)
        }
        item { CustomerCard(cart, onAssociateCustomer, onRemoveCustomer, working) }
        item { TotalsCard(cart) }
        item {
            Button(onClick = onSend, enabled = !working, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                if (working) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else { Icon(Icons.AutoMirrored.Outlined.Send, null); Spacer(Modifier.width(8.dp)); Text("Enviar orden a caja") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onSaveDraft, enabled = !working, modifier = Modifier.weight(1f)) { Text("Guardar borrador") }
                TextButton(onClick = onCancel, enabled = !working, modifier = Modifier.weight(1f)) { Text("Cancelar", color = MaterialTheme.colorScheme.error) }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun CartItemCard(item: CartItem, working: Boolean, onIncrement: (String) -> Unit, onDecrement: (String) -> Unit, onRemove: (String) -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 5.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(productImageResource(item.imageKey)),
                contentDescription = null,
                modifier = Modifier.size(88.dp),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.internalCode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.unitPriceCents.asMxn(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    if (item.promotionName != null) Text(item.listPriceCents.asMxn(), textDecoration = TextDecoration.LineThrough, style = MaterialTheme.typography.bodySmall)
                }
                if (!item.stockKnown) {
                    Text(
                        "Existencia aún no sincronizada",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onDecrement(item.productId) }, enabled = !working) { Icon(Icons.Outlined.Remove, "Disminuir") }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(item.quantity.toString(), Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = { onIncrement(item.productId) },
                        enabled = !working && (!item.stockKnown || item.quantity < item.stockAvailable),
                    ) { Icon(Icons.Outlined.Add, "Aumentar") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onRemove(item.productId) }, enabled = !working) { Icon(Icons.Outlined.DeleteOutline, "Eliminar", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun CustomerCard(cart: Cart, onAssociate: () -> Unit, onRemove: () -> Unit, working: Boolean) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PersonAdd, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(cart.customer?.name ?: "Cliente opcional", fontWeight = FontWeight.Bold)
                Text(
                    cart.customer?.let { "Identificado para esta orden" } ?: "Identifica al cliente para asignar la venta",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = if (cart.customer == null) onAssociate else onRemove, enabled = !working) {
                Text(if (cart.customer == null) "Asociar" else "Quitar")
            }
        }
    }
}

@Composable
private fun TotalsCard(cart: Cart) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, shadowElevation = 9.dp) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Resumen", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            MoneyRow("Subtotal", cart.subtotalCents)
            MoneyRow("Descuentos autorizados", -cart.discountCents, emphasized = cart.discountCents > 0)
            HorizontalDivider()
            MoneyRow("Total", cart.totalCents, total = true)
            Text("El backend volverá a validar precios, descuentos y existencia antes del cobro.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MoneyRow(label: String, amount: Long, emphasized: Boolean = false, total: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = if (total) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge, fontWeight = if (total) FontWeight.Bold else FontWeight.Normal)
        Text(amount.asMxn(), style = if (total) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, color = if (emphasized || total) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyCart(onBrowseCatalog: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(28.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Outlined.ShoppingCart, null, Modifier.padding(22.dp).size(44.dp), tint = MaterialTheme.colorScheme.primary) }
        Text("Tu carrito está vacío", style = MaterialTheme.typography.headlineSmall)
        Text("Agrega plantas desde el catálogo o el escáner.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onBrowseCatalog) { Icon(Icons.Outlined.LocalFlorist, null); Spacer(Modifier.width(8.dp)); Text("Explorar catálogo") }
    }
}

@Composable
private fun SentTicketContent(
    ticket: SaleTicket,
    working: Boolean,
    onRetry: () -> Unit,
    onStartNew: () -> Unit,
    onMySales: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(76.dp), tint = MaterialTheme.colorScheme.primary)
        StatusPill(if (ticket.syncState == SaleSyncState.SYNCED) "Enviado a caja" else "Guardado localmente")
        Text("Orden preparada", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text(ticket.folio, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${ticket.items.sumOf(CartItem::quantity)} unidades · ${ticket.totalCents.asMxn()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (ticket.syncState) {
            SaleSyncState.PENDING -> Text("Pendiente de sincronizar con Supabase", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            SaleSyncState.FAILED -> Text(ticket.syncLastError ?: "La sincronización requiere revisión.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
        Spacer(Modifier.height(10.dp))
        if (ticket.offersMySalesNavigation()) {
            Button(onClick = onMySales, modifier = Modifier.fillMaxWidth()) { Text("Ver mis comandas") }
        } else if (ticket.syncState == SaleSyncState.PENDING || ticket.syncState == SaleSyncState.FAILED) {
            Button(onClick = onRetry, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                Text(if (working) "Reintentando…" else "Reintentar envío a caja")
            }
        }
        OutlinedButton(onClick = onStartNew, modifier = Modifier.fillMaxWidth()) { Text("Preparar otra venta") }
        FilledTonalButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio") }
    }
}

internal fun SaleTicket.offersMySalesNavigation(): Boolean = syncState == SaleSyncState.SYNCED

@Composable
private fun CustomerSearchDialog(
    query: String,
    searching: Boolean,
    results: List<Customer>,
    error: String?,
    onQueryChange: (String) -> Unit,
    onSelected: (Customer) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text("Asociar cliente") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Nombre, email o teléfono") },
                    placeholder = { Text("Escribe al menos 2 letras…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("customer_search_input"),
                )

                Box(Modifier.fillMaxWidth().height(240.dp)) {
                    when {
                        searching -> CircularProgressIndicator(Modifier.align(Alignment.Center).testTag("customer_search_loading"))
                        error != null -> Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                        query.trim().length < 2 -> Text("Escribe para buscar clientes registrados.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.Center))
                        results.isEmpty() -> Text("No encontramos coincidencias.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.Center))
                        else -> LazyColumn(Modifier.fillMaxSize().testTag("customer_search_results")) {
                            items(results, key = Customer::id) { customer ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("customer_result_${customer.id}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(customer.fullName, fontWeight = FontWeight.SemiBold)
                                        if (customer.email != null || customer.phone != null) {
                                            Text(
                                                listOfNotNull(customer.email, customer.phone).joinToString(" · "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    TextButton(onClick = { onSelected(customer) }) { Text("Seleccionar") }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
