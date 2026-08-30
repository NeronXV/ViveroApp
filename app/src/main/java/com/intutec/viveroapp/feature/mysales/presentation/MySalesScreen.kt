package com.intutec.viveroapp.feature.mysales.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Payment
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.designsystem.StatusPill
import com.intutec.viveroapp.feature.mysales.domain.model.MySale
import com.intutec.viveroapp.feature.mysales.domain.model.MySaleStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySalesScreenRoute(
    onBack: () -> Unit,
    viewModel: MySalesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis comandas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isLoading && !state.isRefreshing) {
                        Icon(Icons.Outlined.Refresh, "Actualizar")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.items.isEmpty() -> ErrorContent(state.error!!, viewModel::loadInitial)
                state.items.isEmpty() -> EmptyContent()
                else -> SalesList(
                    items = state.items,
                    hasMore = state.hasMore,
                    isMoreLoading = state.isMoreLoading,
                    onLoadMore = viewModel::loadMore
                )
            }
        }
    }
}

@Composable
private fun SalesList(
    items: List<MySale>,
    hasMore: Boolean,
    isMoreLoading: Boolean,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = MySale::id) { sale ->
            SaleCard(sale)
        }

        if (hasMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (isMoreLoading) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    } else {
                        TextButton(onClick = onLoadMore) { Text("Cargar más") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaleCard(sale: MySale) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(sale.folio, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusPill(
                    text = sale.status.toDisplayLabel(),
                    containerColor = sale.status.toColor(),
                    contentColor = Color.White
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Event, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(dateTimeFormatter.format(sale.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("${sale.itemCount} partidas · ${sale.totalQuantity} uds.", style = MaterialTheme.typography.bodySmall)
                    if (sale.paidAt != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.Payment, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Pagada: ${dateTimeFormatter.format(sale.paidAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Text(sale.totalCents.asMxn(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun EmptyContent() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.History, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Text("No tienes comandas recientes.", style = MaterialTheme.typography.bodyLarge)
    }
}

private fun MySaleStatus.toDisplayLabel(): String = when (this) {
    MySaleStatus.SENT_TO_CASHIER -> "En Caja"
    MySaleStatus.PAYMENT_PENDING -> "Pago en proceso"
    MySaleStatus.PAID -> "Pagada"
    MySaleStatus.CANCELLED -> "Cancelada"
    MySaleStatus.DELIVERED -> "Entregada"
    MySaleStatus.DRAFT -> "Borrador"
}

private fun MySaleStatus.toColor(): Color = when (this) {
    MySaleStatus.PAID, MySaleStatus.DELIVERED -> Color(0xFF2E7D32)
    MySaleStatus.CANCELLED -> Color(0xFFC62828)
    MySaleStatus.SENT_TO_CASHIER -> Color(0xFF1976D2)
    MySaleStatus.PAYMENT_PENDING -> Color(0xFFF57C00)
    else -> Color(0xFF757575)
}
