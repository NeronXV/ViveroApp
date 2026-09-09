package com.intutec.viveroapp.feature.reports.presentation

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.intutec.viveroapp.core.designsystem.ViveroTopAppBar
import com.intutec.viveroapp.core.designsystem.ViveroSectionIntro
import java.time.format.DateTimeFormatter

@Composable
fun ReportsScreenRoute(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViveroTopAppBar(title = "Ventas y reportes", onBack = onBack, eyebrow = "RESULTADOS")
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text("Importes cobrados antes de devoluciones. Consulta los ajustes en los cortes de Caja web.", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            RangeSelector(state.range, viewModel::setRange)

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> ErrorContent(state.error!!, viewModel::loadReports)
                state.dailySales.isEmpty() && state.topProducts.isEmpty() -> EmptyContent()
                else -> ReportsContent(state)
            }
        }
    }
}

@Composable
private fun RangeSelector(current: ReportRange, onRangeSelected: (ReportRange) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = current == ReportRange.LAST_7_DAYS,
            onClick = { onRangeSelected(ReportRange.LAST_7_DAYS) },
            label = { Text("7 días") }
        )
        FilterChip(
            selected = current == ReportRange.LAST_30_DAYS,
            onClick = { onRangeSelected(ReportRange.LAST_30_DAYS) },
            label = { Text("30 días") }
        )
        // Custom range button could open a date picker, but for now we keep it simple
    }
}

@Composable
private fun ReportsContent(state: ReportsUiState) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ViveroSectionIntro(
                title = "Resumen del periodo",
                subtitle = "Consulta ingresos, ventas y productos con mayor movimiento.",
                eyebrow = "DESEMPEÑO DE LA SUCURSAL",
            )
        }
        item { SummaryCards(state) }
        
        item { SectionTitle("Ventas Diarias") }
        items(state.dailySales) { daily ->
            DailySaleRow(daily)
        }

        item { Spacer(Modifier.height(8.dp)) }
        item { SectionTitle("Más Vendidos") }
        items(state.topProducts) { product ->
            TopProductRow(product)
        }
    }
}

@Composable
private fun SummaryCards(state: ReportsUiState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryCard(
            Modifier.weight(1f),
            "Ingresos",
            state.totalRevenueCents.asMxn(),
            MaterialTheme.colorScheme.primaryContainer
        )
        SummaryCard(
            Modifier.weight(1f),
            "Ventas",
            state.totalSalesCount.toString(),
            MaterialTheme.colorScheme.secondaryContainer
        )
    }
}

@Composable
private fun SummaryCard(modifier: Modifier, label: String, value: String, containerColor: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.large,
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DailySaleRow(daily: com.intutec.viveroapp.feature.reports.domain.model.DailySales) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(daily.day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("${daily.salesCount} ventas", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(daily.revenueCents.asMxn(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (daily.discountCents > 0) {
                    Text("-${daily.discountCents.asMxn()} desc.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun TopProductRow(product: com.intutec.viveroapp.feature.reports.domain.model.TopProduct) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(product.productName, fontWeight = FontWeight.SemiBold)
            Text(product.productCode, style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${product.totalQuantity} uds", fontWeight = FontWeight.Bold)
            Text(product.totalRevenueCents.asMxn(), style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
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
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Assessment, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Text("No hay datos para este periodo.", style = MaterialTheme.typography.bodyLarge)
    }
}
