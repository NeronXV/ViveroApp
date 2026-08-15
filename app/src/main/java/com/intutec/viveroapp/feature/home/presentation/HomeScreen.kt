package com.intutec.viveroapp.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.common.UiState
import com.intutec.viveroapp.core.designsystem.BotanicalBackdrop
import com.intutec.viveroapp.core.designsystem.StatusPill
import com.intutec.viveroapp.core.designsystem.DulcineaWordmark
import com.intutec.viveroapp.core.designsystem.ViveroMark
import com.intutec.viveroapp.feature.home.domain.model.Dashboard
import com.intutec.viveroapp.feature.home.domain.model.DashboardModule

@Composable
fun HomeScreenRoute(
    onCatalogClick: () -> Unit,
    onCartClick: () -> Unit,
    onProfileClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onRetry = viewModel::retry,
        onModuleClick = {
            when (it.id) {
                "catalog" -> onCatalogClick()
                "cart" -> onCartClick()
            }
        },
        onProfileClick = onProfileClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState<Dashboard>,
    onRetry: () -> Unit,
    onModuleClick: (DashboardModule) -> Unit,
    onProfileClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Surface(shape = MaterialTheme.shapes.medium, color = Color.White) {
                        DulcineaWordmark(Modifier.padding(horizontal = 6.dp))
                    }
                },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Outlined.AccountCircle, contentDescription = "Abrir perfil", tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (state) {
                UiState.Loading -> LoadingContent()
                is UiState.Empty -> MessageContent(state.message, onRetry)
                is UiState.Error -> MessageContent(state.message, onRetry)
                is UiState.Success -> DashboardContent(state.data, onModuleClick)
            }
        }
    }
}

@Composable
private fun DashboardContent(dashboard: Dashboard, onModuleClick: (DashboardModule) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 260.dp),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            WelcomeCard(dashboard)
        }
        item { StatCard("Tickets pendientes", dashboard.pendingTickets.toString()) }
        item { StatCard("Stock bajo", dashboard.lowStockProducts.toString()) }
        item { StatCard("Promociones activas", dashboard.activePromotions.toString()) }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text("Herramientas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(dashboard.modules, key = DashboardModule::id) { module ->
            ModuleCard(module = module, onClick = { onModuleClick(module) })
        }
    }
}

@Composable
private fun WelcomeCard(dashboard: Dashboard) {
    Box(modifier = Modifier.fillMaxWidth().height(188.dp).clip(MaterialTheme.shapes.extraLarge)) {
        BotanicalBackdrop(Modifier.fillMaxSize())
        Row(
            modifier = Modifier.fillMaxSize().padding(26.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Buenos días,", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.titleMedium)
                Text(dashboard.userName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(10.dp))
                StatusPill(
                    text = "${dashboard.role.displayName} · ${dashboard.branchName}",
                    containerColor = Color.White.copy(alpha = .14f),
                    contentColor = Color.White,
                )
            }
            ViveroMark(markSize = 72.dp, dark = true)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModuleCard(module: DashboardModule, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = module.enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Box(
                modifier = Modifier.size(46.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(module.icon(), null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
            Text(module.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(module.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(if (module.enabled) "Abrir" else "Próximamente", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun DashboardModule.icon(): ImageVector = when (id) {
    "catalog" -> Icons.Outlined.LocalFlorist
    "scanner" -> Icons.Outlined.QrCodeScanner
    "cart" -> Icons.Outlined.ShoppingCart
    "tickets", "pending" -> Icons.AutoMirrored.Outlined.ReceiptLong
    "cashier", "shift_sales" -> Icons.Outlined.PointOfSale
    "inventory", "movement", "alerts" -> Icons.Outlined.Inventory2
    "reports", "sales" -> Icons.Outlined.BarChart
    "users" -> Icons.Outlined.VerifiedUser
    "settings" -> Icons.Outlined.Settings
    else -> Icons.Outlined.Storefront
}

@Composable
private fun LoadingContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text("Preparando tu espacio de trabajo…")
    }
}

@Composable
private fun MessageContent(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(24.dp)) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}
