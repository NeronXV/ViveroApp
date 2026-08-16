package com.intutec.viveroapp.feature.catalog.presentation

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.designsystem.StatusPill
import com.intutec.viveroapp.feature.catalog.domain.model.Category
import com.intutec.viveroapp.feature.catalog.domain.model.Product

@Composable
fun CatalogScreenRoute(
    onBack: () -> Unit,
    onProductClick: (String) -> Unit,
    onScanClick: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.notices.collect { snackbar.showSnackbar(it) } }
    CatalogScreen(
        state = state,
        onBack = onBack,
        onProductClick = onProductClick,
        onScanClick = onScanClick,
        onQueryChanged = viewModel::onQueryChanged,
        onCategorySelected = viewModel::onCategorySelected,
        onAvailableOnlyChanged = viewModel::onAvailableOnlyChanged,
        onRetry = viewModel::retry,
        onAddToCart = viewModel::addToCart,
        snackbar = snackbar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    state: CatalogUiState,
    onBack: () -> Unit,
    onProductClick: (String) -> Unit,
    onScanClick: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onAvailableOnlyChanged: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onAddToCart: (Product) -> Unit,
    snackbar: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Catálogo"); Text("Plantas seleccionadas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
                actions = { IconButton(onClick = onScanClick) { Icon(Icons.Outlined.QrCodeScanner, "Escanear producto") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (state) {
            CatalogUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is CatalogUiState.Error -> CatalogMessage(state.message, onRetry, Modifier.padding(padding))
            is CatalogUiState.Content -> ProductGrid(state, onProductClick, onAddToCart, onQueryChanged, onCategorySelected, onAvailableOnlyChanged, padding)
            is CatalogUiState.Empty -> EmptyCatalog(state, onQueryChanged, onCategorySelected, onAvailableOnlyChanged, padding)
        }
    }
}

@Composable
private fun ProductGrid(
    state: CatalogUiState.Content,
    onProductClick: (String) -> Unit,
    onAddToCart: (Product) -> Unit,
    onQueryChanged: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onAvailableOnlyChanged: (Boolean) -> Unit,
    padding: PaddingValues,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(260.dp),
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            CatalogControls(state.query, state.categories, state.selectedCategoryId, state.availableOnly, onQueryChanged, onCategorySelected, onAvailableOnlyChanged)
        }
        items(state.products, key = Product::id) { product ->
            ProductCard(product, { onProductClick(product.id) }, { onAddToCart(product) })
        }
    }
}

@Composable
private fun EmptyCatalog(
    state: CatalogUiState.Empty,
    onQueryChanged: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onAvailableOnlyChanged: (Boolean) -> Unit,
    padding: PaddingValues,
) {
    Column(Modifier.fillMaxSize().padding(padding).padding(18.dp)) {
        CatalogControls(state.query, state.categories, state.selectedCategoryId, state.availableOnly, onQueryChanged, onCategorySelected, onAvailableOnlyChanged)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (state.catalogIsEmpty) "Catálogo sin productos" else "No encontramos plantas",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (state.catalogIsEmpty) "Aún no hay productos activos disponibles." else "Prueba otro nombre, código o categoría.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.catalogIsEmpty) {
                    Button(onClick = { onQueryChanged(""); onCategorySelected(null); onAvailableOnlyChanged(false) }) {
                        Text("Limpiar filtros")
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogControls(
    query: String,
    categories: List<Category>,
    selectedCategoryId: String?,
    availableOnly: Boolean,
    onQueryChanged: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onAvailableOnlyChanged: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nuestra colección", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text("Seleccionada para cada espacio", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar nombre o código") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQueryChanged("") }) { Icon(Icons.Outlined.Clear, "Limpiar búsqueda") } },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = selectedCategoryId == null, onClick = { onCategorySelected(null) }, label = { Text("Todas") }) }
            items(categories, key = Category::id) { category ->
                FilterChip(selected = selectedCategoryId == category.id, onClick = { onCategorySelected(category.id) }, label = { Text(category.name) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Solo disponibles", fontWeight = FontWeight.SemiBold); Text("Oculta productos sin existencia", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked = availableOnly, onCheckedChange = onAvailableOnlyChanged)
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun ProductCard(product: Product, onClick: () -> Unit, onAddToCart: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box {
            CatalogProductImage(
                product = product,
                modifier = Modifier.fillMaxWidth().height(218.dp),
                contentScale = ContentScale.Crop,
            )
            product.promotion?.let {
                StatusPill("Promoción", Modifier.align(Alignment.TopStart).padding(12.dp), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            }
            StatusPill(
                text = when {
                    !product.stockKnown -> "Existencia pendiente"
                    product.isAvailable -> "${product.stockAvailable} disponibles"
                    else -> "Sin existencia"
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                containerColor = if (product.isAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (product.isAvailable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Column(Modifier.padding(18.dp)) {
            Text(product.category.name.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(product.commonName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            product.scientificName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(product.effectivePriceCents.asMxn(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (product.promotion != null) Text(product.priceCents.asMxn(), textDecoration = TextDecoration.LineThrough, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("/ ${product.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAddToCart, enabled = product.isAvailable && product.stockKnown, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.ShoppingCart, null)
                Spacer(Modifier.size(8.dp))
                Text(
                    when {
                        !product.stockKnown -> "Existencia pendiente"
                        product.isAvailable -> "Agregar al carrito"
                        else -> "Sin existencia"
                    },
                )
            }
        }
    }
}

@Composable
private fun CatalogMessage(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.LocalOffer, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
            Text(message, style = MaterialTheme.typography.titleMedium)
            Button(onClick = onRetry) { Text("Reintentar") }
        }
    }
}
