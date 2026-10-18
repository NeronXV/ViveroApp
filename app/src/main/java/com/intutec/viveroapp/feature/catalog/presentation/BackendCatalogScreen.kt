package com.intutec.viveroapp.feature.catalog.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.designsystem.*
import com.intutec.viveroapp.feature.catalog.domain.repository.BackendCatalogProduct
import com.intutec.viveroapp.feature.scanner.presentation.CameraCodeDialog

@Composable
fun BackendCatalogScreen(
    onBack: () -> Unit,
    onCart: () -> Unit,
    viewModel: BackendCatalogViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackendCatalogContent(
        state = state,
        imageOrigin = viewModel.imageOrigin,
        onBack = onBack,
        onCart = onCart,
        onSearch = viewModel::search,
        onCategory = viewModel::category,
        onMoreCategories = viewModel::moreCategories,
        onScan = viewModel::scan,
        onAdd = viewModel::add,
        onRetry = viewModel::retry,
        onMore = viewModel::more
    )
}

@Composable
internal fun BackendCatalogContent(
    state: BackendCatalogUiState,
    imageOrigin: String,
    onBack: () -> Unit,
    onCart: () -> Unit,
    onSearch: (String) -> Unit,
    onCategory: (Long?) -> Unit,
    onMoreCategories: () -> Unit,
    onScan: (String) -> Unit,
    onAdd: (BackendCatalogProduct) -> Unit,
    onRetry: () -> Unit,
    onMore: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    var manualOpen by rememberSaveable { mutableStateOf(false) }
    var cameraOpen by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<BackendCatalogProduct?>(null) }

    LaunchedEffect(state.enabled, state.canScan) {
        if (!state.enabled || !state.canScan) {
            cameraOpen = false
            manualOpen = false
        }
    }

    if (cameraOpen && state.enabled && state.canScan) {
        CameraCodeDialog(
            onDismiss = { cameraOpen = false },
            onCode = { scannedCode ->
                cameraOpen = false
                code = scannedCode
                onScan(scannedCode)
            }
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViveroTopAppBar(
                title = if (state.canSell) "Vender" else "Catálogo",
                onBack = onBack,
                eyebrow = "SUCURSAL · ${state.branchName.uppercase()}"
            ) {
                if (state.canSell) {
                    IconButton(onClick = onCart) {
                        Icon(
                            Icons.Outlined.ShoppingCart,
                            contentDescription = "Abrir carrito",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(260.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Header title
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Colección Botánica",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Explora plantas y productos disponibles en tu sucursal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Search box with premium styling
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = state.query,
                                onValueChange = onSearch,
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        "Buscar planta o código...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                },
                                singleLine = true,
                                enabled = state.enabled,
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    if (state.query.isNotEmpty()) {
                                        IconButton(onClick = { onSearch("") }) {
                                            Icon(
                                                Icons.Outlined.Clear,
                                                contentDescription = "Limpiar",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    disabledBorderColor = Color.Transparent
                                )
                            )

                            if (state.canScan) {
                                Surface(
                                    onClick = { cameraOpen = true },
                                    enabled = state.enabled && !state.loading && !state.working,
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Outlined.QrCodeScanner,
                                            contentDescription = "Escanear código",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Categories pills
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            CategoryPill(
                                label = "Todas",
                                selected = state.category == null,
                                onClick = { onCategory(null) },
                                enabled = state.enabled
                            )
                        }
                        items(state.categories, key = { it.id }) { category ->
                            CategoryPill(
                                label = category.name,
                                selected = state.category == category.id,
                                onClick = { onCategory(category.id) },
                                enabled = state.enabled
                            )
                        }
                        if (state.nextCategory != null) {
                            item {
                                TextButton(
                                    onClick = onMoreCategories,
                                    enabled = state.enabled && !state.loading
                                ) {
                                    Text("Más categorías")
                                }
                            }
                        }
                    }

                    if (state.canScan) {
                        TextButton(
                            onClick = { manualOpen = true },
                            enabled = state.enabled && !state.loading
                        ) {
                            Icon(
                                Icons.Outlined.Keyboard,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Ingresar código manualmente")
                        }
                    }

                    if (state.loading || state.working) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    state.error?.let {
                        SalesNotice(it, critical = true) {
                            TextButton(onClick = onRetry, enabled = !state.loading) {
                                Text("Volver a intentar")
                            }
                        }
                    }

                    state.message?.let { SalesNotice(it) }

                    if (!state.enabled) {
                        SalesNotice(
                            "Actualiza tu sesión y permisos para consultar productos.",
                            critical = true
                        )
                    }

                    if (state.enabled && !state.loading && state.products.isEmpty() && state.error == null) {
                        SalesEmpty(
                            title = "Sin resultados",
                            message = "Prueba otro nombre, categoría o código."
                        )
                    }
                }
            }

            // Products grid with Premium Cards
            items(state.products, key = { it.id }) { product ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box {
                        ProductPhoto(
                            model = product.image?.let { imageOrigin + it.path },
                            name = product.commonName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.35f)
                        )
                        if (product.promotion != null) {
                            StatusPill(
                                text = "Promoción",
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp)
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = product.commonName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        product.scientificName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = product.effectivePriceCents.asMxn(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (product.promotion != null) {
                                Text(
                                    text = product.priceCents.asMxn(),
                                    textDecoration = TextDecoration.LineThrough,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "/ ${product.unit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        if (state.canSell) {
                            Button(
                                onClick = { onAdd(product) },
                                enabled = state.enabled && !state.working,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    Icons.Outlined.ShoppingCart,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Agregar al carrito", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        TextButton(
                            onClick = { detail = product },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 42.dp)
                        ) {
                            Text("Ver detalle completo")
                        }
                    }
                }
            }

            if (state.next != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedButton(
                        onClick = onMore,
                        enabled = state.enabled && !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Text("Cargar más productos")
                    }
                }
            }
        }
    }

    if (manualOpen) {
        AlertDialog(
            onDismissRequest = { manualOpen = false },
            title = { Text("Buscar por código") },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 128) code = it },
                    label = { Text("Código de barras o interno") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { manualOpen = false; onScan(code) },
                    enabled = state.enabled && state.canScan && code.trim().length >= 2
                ) {
                    Text("Buscar")
                }
            },
            dismissButton = {
                TextButton(onClick = { manualOpen = false }) {
                    Text("Volver")
                }
            }
        )
    }

    detail?.let { product ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(product.commonName, style = MaterialTheme.typography.titleLarge) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text("Código: ${product.internalCode}", fontWeight = FontWeight.SemiBold)
                        product.scientificName?.let {
                            Text("Nombre científico: $it", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(product.description, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Riego: ${product.wateringAdvice}", style = MaterialTheme.typography.bodySmall)
                                Text("Luz: ${product.lightType}", style = MaterialTheme.typography.bodySmall)
                                Text("Clima: ${product.recommendedClimate}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        product.promotion?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Promoción: ${it.name} (precio anterior: ${product.priceCents.asMxn()})",
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detail = null }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun CategoryPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (selected) 2.dp else 1.dp
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}