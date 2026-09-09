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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ButtonDefaults
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.designsystem.StatusPill
import com.intutec.viveroapp.core.designsystem.ViveroTopAppBar
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViveroTopAppBar(title = "Catálogo", onBack = onBack, eyebrow = "COLECCIÓN BOTÁNICA") {
                IconButton(onClick = onScanClick) {
                    Icon(Icons.Outlined.QrCodeScanner, "Escanear producto", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (state) {
            CatalogUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
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
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (state.catalogIsEmpty) "Aún no hay productos activos disponibles." else "Prueba otro nombre, código o categoría.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.catalogIsEmpty) {
                    Button(
                        onClick = { onQueryChanged(""); onCategorySelected(null); onAvailableOnlyChanged(false) },
                        shape = CircleShape,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
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
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nuestra Colección", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text("Filtrado por las categorías más deseadas de la temporada", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                "Busca tu planta favorita (ej. Monstera, Suculenta)...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    },
                )
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChanged("") },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Outlined.Clear, "Limpiar búsqueda", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Search, "Buscar", modifier = Modifier.size(20.dp), tint = Color.White)
                    }
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                CategoryPillChip(
                    label = "Todas",
                    selected = selectedCategoryId == null,
                    onClick = { onCategorySelected(null) },
                )
            }
            items(categories, key = Category::id) { category ->
                CategoryPillChip(
                    label = category.name,
                    selected = selectedCategoryId == category.id,
                    onClick = { onCategorySelected(category.id) },
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Solo disponibles", fontWeight = FontWeight.SemiBold)
                Text("Oculta productos sin existencia", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = availableOnly,
                onCheckedChange = onAvailableOnlyChanged,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun CategoryPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (selected) 2.dp else 1.dp,
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 9.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun ProductCard(product: Product, onClick: () -> Unit, onAddToCart: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box {
            CatalogProductImage(
                product = product,
                modifier = Modifier.fillMaxWidth().height(210.dp),
                contentScale = ContentScale.Crop,
            )
            product.promotion?.let {
                StatusPill(
                    text = "Promoción",
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.secondary,
                )
            }
            StatusPill(
                text = when {
                    !product.stockKnown -> "Existencia pendiente"
                    product.isAvailable -> "${product.stockAvailable} disponibles"
                    else -> "Sin existencia"
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                containerColor = if (product.isAvailable) MaterialTheme.colorScheme.primaryContainer else Color(0xFFFFECEC),
                contentColor = if (product.isAvailable) MaterialTheme.colorScheme.primary else Color(0xFFB91C1C),
            )
        }
        Column(Modifier.padding(18.dp)) {
            Text(
                text = product.category.name.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = product.commonName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            product.scientificName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = product.effectivePriceCents.asMxn(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (product.promotion != null) {
                    Text(
                        text = product.priceCents.asMxn(),
                        textDecoration = TextDecoration.LineThrough,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("/ ${product.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onAddToCart,
                enabled = product.isActive && (!product.stockKnown || product.isAvailable),
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Outlined.ShoppingCart, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    when {
                        !product.stockKnown -> "Agregar · existencia pendiente"
                        product.isAvailable -> "Agregar al carrito"
                        else -> "Sin existencia"
                    },
                    fontWeight = FontWeight.SemiBold,
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
