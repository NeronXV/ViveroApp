package com.intutec.viveroapp.feature.mysales.presentation

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
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.designsystem.*
import com.intutec.viveroapp.feature.mysales.domain.repository.*

internal fun historyLabel(value: String) = when (value) {
    "DRAFT" -> "Borrador"
    "SENT_TO_CASHIER" -> "Enviada a caja"
    "PAYMENT_PENDING" -> "Pago pendiente"
    "PAID" -> "Pagada"
    "DELIVERED" -> "Entregada"
    "CANCELLED" -> "Cancelada"
    "CASH" -> "Efectivo"
    "CARD" -> "Tarjeta"
    "TRANSFER" -> "Transferencia"
    else -> value
}

@Composable
fun BackendHistoryScreen(
    onBack: () -> Unit,
    viewModel: BackendHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val back = {
        if (state.document != null && state.hasList) viewModel.closeDetail() else onBack()
    }

    BackHandler(enabled = state.document != null && state.hasList) {
        viewModel.closeDetail()
    }

    Scaffold(
        topBar = {
            ViveroTopAppBar(
                title = if (state.kind == BackendHistoryKind.SALES) "Mis ventas"
                else if (state.kind == BackendHistoryKind.PAYMENTS) "Mis comprobantes"
                else "Detalle de comanda",
                onBack = back,
                eyebrow = "REGISTRO · HISTORIAL"
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
                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (!state.enabled) {
                    SalesNotice("Tus permisos no permiten consultar este historial.", critical = true)
                }
                state.error?.let { SalesNotice(it, critical = true) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.document == null && state.hasList) {
                        Text(
                            "Registros de tu cuenta y sucursal actual.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::retry,
                        enabled = state.enabled && !state.loading,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Actualizar")
                    }
                }
            }

            state.document?.let { doc ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    doc.sale.folio,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                StatusPill(text = historyLabel(doc.sale.status))
                            }

                            doc.branchName?.let {
                                Text(
                                    "Sucursal: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Subtotal:", style = MaterialTheme.typography.bodyMedium)
                                Text(doc.sale.subtotalCents.asMxn(), style = MaterialTheme.typography.bodyMedium)
                            }

                            if (doc.sale.discountCents > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Descuento:", style = MaterialTheme.typography.bodyMedium)
                                    Text("-${doc.sale.discountCents.asMxn()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    doc.sale.totalCents.asMxn(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            doc.payment?.let { pay ->
                                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                Text(
                                    "Pago ID ${pay.id} · ${historyLabel(pay.method)}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text("Fecha: ${pay.createdAt}", style = MaterialTheme.typography.bodySmall)
                                Text("Recibido: ${pay.receivedCents.asMxn()} · Cambio: ${pay.changeCents.asMxn()}", style = MaterialTheme.typography.bodySmall)
                                pay.reference?.let { ref ->
                                    Text("Referencia: $ref", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            doc.refund?.let { ref ->
                                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                Text(
                                    "Devolución: ${ref.amountCents.asMxn()} (${historyLabel(ref.method)})",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Productos en la comanda",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(doc.lines, key = { it.id }) { line ->
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
                            Text(
                                line.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            line.code?.let {
                                Text(
                                    "Código: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${line.quantity} × ${line.unitPriceCents.asMxn()}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    line.totalCents.asMxn(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            line.promotion?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                if (doc.events.isNotEmpty()) {
                    item {
                        Text(
                            "Historial de eventos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(doc.events) { event ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "${event.createdAt} · ${historyLabel(event.status)}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (event.observation.isNotBlank()) {
                                    Text(
                                        event.observation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } ?: run {
                items(state.entries, key = { it.id }) { entry ->
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
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    entry.folio,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                StatusPill(text = historyLabel(entry.label))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    entry.createdAt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    entry.totalCents.asMxn(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { viewModel.detail(entry.id) },
                                    enabled = !state.loading
                                ) {
                                    Text("Ver detalle completo")
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                item {
                    if (state.hasList && state.entries.isEmpty() && !state.loading && state.error == null) {
                        SalesEmpty(
                            title = "Sin registros",
                            message = "No hay registros disponibles en el historial."
                        )
                    }
                    if (state.next != null) {
                        OutlinedButton(
                            onClick = { viewModel.refresh(true) },
                            enabled = !state.loading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape
                        ) {
                            Text("Cargar más registros")
                        }
                    }
                }
            }
        }
    }
}
