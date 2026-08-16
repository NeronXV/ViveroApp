package com.intutec.viveroapp.feature.catalog.presentation

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.common.UiState
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.designsystem.StatusPill
import com.intutec.viveroapp.feature.catalog.domain.model.Product

@Composable
fun ProductDetailScreenRoute(
    productId: String,
    onBack: () -> Unit,
    onScanClick: () -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(productId) { viewModel.load(productId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.notices.collect { snackbar.showSnackbar(it) } }
    ProductDetailScreen(state, onBack, onScanClick, viewModel::retry, viewModel::addCurrentProductToCart, snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    state: UiState<Product>,
    onBack: () -> Unit,
    onScanClick: () -> Unit,
    onRetry: () -> Unit,
    onAddToCart: () -> Unit,
    snackbar: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del producto") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
                actions = { IconButton(onClick = onScanClick) { Icon(Icons.Outlined.QrCodeScanner, "Escanear") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (state) {
                UiState.Loading -> CircularProgressIndicator()
                is UiState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.message, style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onRetry) { Text("Reintentar") }
                }
                is UiState.Empty -> Text(state.message)
                is UiState.Success -> ProductDetailContent(
                    product = state.data,
                    onAddToCart = onAddToCart,
                )
            }
        }
    }
}

@Composable
private fun ProductDetailContent(product: Product, onAddToCart: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.fillMaxWidth().widthIn(max = 980.dp)) {
                CatalogProductImage(
                    product = product,
                    modifier = Modifier.fillMaxWidth().height(390.dp),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.padding(22.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(product.category.name)
                        product.promotion?.let { StatusPill(it.name, containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(product.commonName, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    product.scientificName?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("${product.internalCode} · ${product.unit}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(product.effectivePriceCents.asMxn(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (product.promotion != null) Text(product.priceCents.asMxn(), textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        when {
                            !product.stockKnown -> "Disponibilidad por confirmar"
                            product.isAvailable -> "${product.stockAvailable} piezas disponibles"
                            else -> "Sin existencia"
                        },
                        color = if (product.stockKnown && !product.isAvailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(22.dp))
                    Text(product.description, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(24.dp))
                    Text("Guía de cuidado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    CareCard(Icons.Outlined.WaterDrop, "Riego", product.wateringAdvice)
                    Spacer(Modifier.height(10.dp))
                    CareCard(Icons.Outlined.LightMode, "Iluminación", product.lightType)
                    Spacer(Modifier.height(10.dp))
                    CareCard(Icons.Outlined.Thermostat, "Clima recomendado", product.recommendedClimate)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onAddToCart,
                        enabled = product.isActive && (!product.stockKnown || product.isAvailable),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            if (!product.stockKnown) "Agregar · existencia pendiente"
                            else if (product.isAvailable) "Agregar al carrito"
                            else "Producto sin existencia",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun CareCard(icon: ImageVector, title: String, description: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Column { Text(title, fontWeight = FontWeight.Bold); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
