package com.intutec.viveroapp.feature.cashier.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.designsystem.ViveroMark
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderDetail
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderItem
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderSummary
import java.time.Duration
import java.time.Instant

private val CashierForest = Color(0xFF234D3C)
private val CashierSage = Color(0xFF789B78)
private val CashierPaleSage = Color(0xFFDDE9DB)
private val CashierCream = Color(0xFFF7F2E8)
private val CashierPaper = Color(0xFFFFFCF6)
private val CashierTerracotta = Color(0xFFC97754)
private val CashierInk = Color(0xFF24312B)
private val CashierMuted = Color(0xFF637068)

@Composable
fun CashierQueueScreenRoute(
    onBack: () -> Unit,
    onOrderClick: (String) -> Unit,
    viewModel: CashierQueueViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onVisible()
        onPauseOrDispose { viewModel.onHidden() }
    }
    CashierQueueScreen(state, onBack, onOrderClick, viewModel::refresh)
}

@Composable
fun CashierQueueScreen(
    state: CashierQueueUiState,
    onBack: () -> Unit,
    onOrderClick: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = CashierCream) {
        Column(Modifier.fillMaxSize()) {
            CashierHeader(title = "Caja", subtitle = state.branchNameOrFallback(), onBack = onBack)
            when (state) {
                CashierQueueUiState.Loading -> CashierLoading("Actualizando comandas…")
                is CashierQueueUiState.Error -> CashierMessage(
                    icon = Icons.Rounded.WifiOff,
                    title = "No pudimos actualizar la bandeja",
                    message = state.message,
                    action = "Reintentar",
                    onAction = onRefresh,
                    isError = true,
                )
                is CashierQueueUiState.Empty -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
                        state.refreshError?.let { message ->
                            item { CashierRefreshWarning(message) }
                        }
                        item {
                            CashierMessageContent(
                                icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                                title = "Caja al día",
                                message = "No hay comandas esperando cobro en esta sucursal.",
                                action = "Actualizar",
                                onAction = onRefresh,
                            )
                        }
                    }
                }
                is CashierQueueUiState.Content -> CashierQueueContent(state, onOrderClick, onRefresh)
            }
        }
    }
}

@Composable
private fun CashierQueueContent(
    state: CashierQueueUiState.Content,
    onOrderClick: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = if (maxWidth >= 760.dp) 2 else 1
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("cashier_queue"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Comandas por atender", style = MaterialTheme.typography.headlineMedium, color = CashierInk)
                            Text(
                                "Ordenadas de la más antigua a la más reciente",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CashierMuted,
                            )
                        }
                        IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp).testTag("cashier_refresh")) {
                            Icon(Icons.Rounded.Refresh, "Actualizar bandeja", tint = CashierForest)
                        }
                    }
                }
                state.refreshError?.let { message ->
                    item { CashierRefreshWarning(message) }
                }
                items(state.orders.chunked(columns)) { rowOrders ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        rowOrders.forEach { order ->
                            CashierOrderCard(order, onOrderClick, Modifier.weight(1f))
                        }
                        repeat(columns - rowOrders.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CashierOrderCard(
    order: CashierOrderSummary,
    onOrderClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.testTag("cashier_order_${order.id}"),
        colors = CardDefaults.cardColors(containerColor = CashierPaper),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = CashierPaleSage) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ReceiptLong,
                        contentDescription = null,
                        tint = CashierForest,
                        modifier = Modifier.padding(12.dp).size(25.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(order.folio, style = MaterialTheme.typography.titleMedium, color = CashierInk)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Schedule, null, tint = CashierTerracotta, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(order.createdAt.waitingLabel(), style = MaterialTheme.typography.labelMedium, color = CashierTerracotta)
                    }
                }
                CashierStatusLabel()
            }
            HorizontalDivider(color = CashierForest.copy(alpha = .10f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${order.productCount} ${if (order.productCount == 1) "producto" else "productos"} · " +
                            "${order.unitCount} ${if (order.unitCount == 1) "unidad" else "unidades"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CashierMuted,
                    )
                    Text(order.totalCents.asMxn(), style = MaterialTheme.typography.headlineSmall, color = CashierForest)
                }
                FilledTonalButton(
                    onClick = { onOrderClick(order.id) },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = CashierPaleSage, contentColor = CashierForest),
                ) {
                    Text("Ver comanda")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun CashierStatusLabel() {
    Surface(shape = CircleShape, color = Color(0xFFFFE4D6), contentColor = Color(0xFF7C321F)) {
        Text(
            "Esperando en caja",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun CashierDetailScreenRoute(
    orderId: String,
    onBack: () -> Unit,
    onPaymentFinished: () -> Unit,
    viewModel: CashierDetailViewModel = hiltViewModel(),
    paymentViewModel: CashierPaymentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val paymentState by paymentViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(orderId) { viewModel.load(orderId) }
    val order = (state as? CashierDetailUiState.Content)?.order
    LaunchedEffect(order?.summary?.id) { order?.let(paymentViewModel::attachOrder) }
    CashierDetailScreen(state, onBack, viewModel::refresh, paymentViewModel::start)
    order?.let {
        CashierPaymentFlow(
            order = it,
            state = paymentState,
            secondsRemaining = paymentViewModel::secondsRemaining,
            onMethod = paymentViewModel::selectMethod,
            onCashAmount = paymentViewModel::updateCashAmount,
            onReference = paymentViewModel::updateReference,
            onRequestConfirmation = paymentViewModel::requestConfirmation,
            onDismissConfirmation = paymentViewModel::dismissConfirmation,
            onConfirm = paymentViewModel::confirm,
            onRetry = paymentViewModel::retryUncertain,
            onRenew = paymentViewModel::renew,
            onCancel = { paymentViewModel.cancel(onPaymentFinished) },
            onDone = onPaymentFinished,
        )
    }
}

@Composable
fun CashierDetailScreen(
    state: CashierDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onStartPayment: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = CashierCream) {
        Column(Modifier.fillMaxSize()) {
            CashierHeader("Detalle de comanda", "Consulta de solo lectura", onBack)
            when (state) {
                CashierDetailUiState.Loading -> CashierLoading("Cargando detalle…")
                is CashierDetailUiState.Error -> CashierMessage(
                    icon = Icons.Rounded.WifiOff,
                    title = "Comanda no disponible",
                    message = state.message,
                    action = "Actualizar",
                    onAction = onRefresh,
                    isError = true,
                )
                is CashierDetailUiState.Content -> CashierDetailContent(state, onRefresh, onStartPayment)
            }
        }
    }
}

@Composable
private fun CashierDetailContent(
    state: CashierDetailUiState.Content,
    onRefresh: () -> Unit,
    onStartPayment: () -> Unit,
) {
    val order = state.order
    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 760.dp
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    CashierDetailItems(order, state.refreshError, Modifier.weight(1.35f).fillMaxHeight())
                    CashierDetailSummary(order, onStartPayment, Modifier.weight(.65f).fillMaxHeight())
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().testTag("cashier_detail"),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { CashierDetailHeading(order.summary) }
                    state.refreshError?.let { message ->
                        item { CashierRefreshWarning(message) }
                    }
                    item { CashierItemsCard(order.items) }
                    item { CashierTotalsCard(order) }
                    item { StartPaymentAction(onStartPayment) }
                }
            }
        }
    }
}

@Composable
private fun CashierDetailItems(order: CashierOrderDetail, refreshError: String?, modifier: Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { CashierDetailHeading(order.summary) }
        refreshError?.let { message ->
            item { CashierRefreshWarning(message) }
        }
        item { CashierItemsCard(order.items) }
    }
}

@Composable
private fun CashierDetailSummary(order: CashierOrderDetail, onStartPayment: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CashierTotalsCard(order)
        StartPaymentAction(onStartPayment)
    }
}

@Composable
private fun CashierDetailHeading(order: CashierOrderSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(order.folio, style = MaterialTheme.typography.headlineMedium, color = CashierInk, modifier = Modifier.weight(1f))
            CashierStatusLabel()
        }
        Text(
            "Recibida ${order.createdAt.waitingLabel().lowercase()} · ${order.productCount} " +
                if (order.productCount == 1) "producto" else "productos",
            style = MaterialTheme.typography.bodyMedium,
            color = CashierMuted,
        )
    }
}

@Composable
private fun CashierItemsCard(items: List<CashierOrderItem>) {
    Card(colors = CardDefaults.cardColors(containerColor = CashierPaper), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) {
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 14.dp), color = CashierForest.copy(alpha = .10f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = CashierPaleSage) {
                        Icon(Icons.Rounded.LocalFlorist, null, tint = CashierForest, modifier = Modifier.padding(10.dp).size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.productName, style = MaterialTheme.typography.titleMedium, color = CashierInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${item.internalCode} · ${item.quantity} × ${item.unitPriceCents.asMxn()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = CashierMuted,
                        )
                    }
                    Text(item.lineTotalCents.asMxn(), style = MaterialTheme.typography.titleMedium, color = CashierForest)
                }
            }
        }
    }
}

@Composable
private fun CashierTotalsCard(order: CashierOrderDetail) {
    Card(colors = CardDefaults.cardColors(containerColor = CashierPaleSage), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TotalRow("Subtotal", order.subtotalCents)
            TotalRow("Descuento", -order.discountCents)
            HorizontalDivider(color = CashierForest.copy(alpha = .15f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Total del servidor", style = MaterialTheme.typography.titleMedium, color = CashierForest, modifier = Modifier.weight(1f))
                Text(order.summary.totalCents.asMxn(), style = MaterialTheme.typography.headlineSmall, color = CashierForest)
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, amount: Long) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CashierMuted, modifier = Modifier.weight(1f))
        Text(amount.asMxn(), style = MaterialTheme.typography.bodyMedium, color = CashierInk)
    }
}

@Composable
private fun StartPaymentAction(onStartPayment: () -> Unit) {
    Button(
        onClick = onStartPayment,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("cashier_start_payment"),
        colors = ButtonDefaults.buttonColors(containerColor = CashierForest, contentColor = Color.White),
    ) {
        Icon(Icons.Rounded.PointOfSale, null)
        Spacer(Modifier.width(10.dp))
        Text("Iniciar cobro")
    }
}

@Composable
private fun CashierRefreshWarning(message: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFFE4D6)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.WarningAmber, null, tint = Color(0xFF8A3822))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6D2B1B))
        }
    }
}

@Composable
private fun CashierHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(CashierForest).statusBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = .05f), size.minDimension * .8f, Offset(size.width * .92f, 0f))
            drawOval(
                CashierSage.copy(alpha = .22f),
                Offset(size.width * .72f, size.height * .12f),
                Size(size.width * .2f, size.height * .52f),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", tint = Color.White)
            }
            ViveroMark(markSize = 42.dp, dark = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .78f))
            }
            Icon(Icons.Rounded.PointOfSale, null, tint = Color.White, modifier = Modifier.padding(10.dp).size(26.dp))
        }
    }
}

@Composable
private fun CashierLoading(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = CashierForest)
            Text(label, style = MaterialTheme.typography.titleMedium, color = CashierInk)
        }
    }
}

@Composable
private fun CashierMessage(
    icon: ImageVector,
    title: String,
    message: String,
    action: String,
    onAction: () -> Unit,
    isError: Boolean = false,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        CashierMessageContent(icon, title, message, action, onAction, isError)
    }
}

@Composable
private fun CashierMessageContent(
    icon: ImageVector,
    title: String,
    message: String,
    action: String,
    onAction: () -> Unit,
    isError: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CashierPaper),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = CircleShape, color = if (isError) Color(0xFFFFE4D6) else CashierPaleSage) {
                Icon(icon, null, tint = if (isError) Color(0xFF8A3822) else CashierForest, modifier = Modifier.padding(18.dp).size(34.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, color = CashierInk, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyLarge, color = CashierMuted, textAlign = TextAlign.Center)
            OutlinedButton(onClick = onAction, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text(action)
            }
        }
    }
}

private fun CashierQueueUiState.branchNameOrFallback(): String = when (this) {
    is CashierQueueUiState.Content -> branchName
    is CashierQueueUiState.Empty -> branchName
    else -> "Sucursal asignada"
}

internal fun Instant.waitingLabel(now: Instant = Instant.now()): String {
    val elapsed = Duration.between(this, now).coerceAtLeast(Duration.ZERO)
    val minutes = elapsed.toMinutes()
    return when {
        minutes < 1 -> "Hace menos de 1 min"
        minutes < 60 -> "Hace $minutes min"
        minutes < 1_440 -> "Hace ${elapsed.toHours()} h"
        else -> "Hace ${elapsed.toDays()} d"
    }
}
