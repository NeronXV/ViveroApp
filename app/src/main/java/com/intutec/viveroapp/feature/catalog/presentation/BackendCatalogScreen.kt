package com.intutec.viveroapp.feature.catalog.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.designsystem.*
import com.intutec.viveroapp.feature.catalog.domain.repository.BackendCatalogProduct
import com.intutec.viveroapp.feature.scanner.presentation.CameraCodeDialog

@Composable
fun BackendCatalogScreen(onBack: () -> Unit, onCart: () -> Unit, viewModel: BackendCatalogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackendCatalogContent(state, viewModel.imageOrigin, onBack, onCart, viewModel::search,
        viewModel::category, viewModel::moreCategories, viewModel::scan, viewModel::add, viewModel::retry, viewModel::more)
}

@Composable
internal fun BackendCatalogContent(state: BackendCatalogUiState, imageOrigin: String, onBack: () -> Unit, onCart: () -> Unit,
    onSearch: (String) -> Unit, onCategory: (Long?) -> Unit, onMoreCategories: () -> Unit, onScan: (String) -> Unit,
    onAdd: (BackendCatalogProduct) -> Unit, onRetry: () -> Unit, onMore: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var manualOpen by rememberSaveable { mutableStateOf(false) }
    var cameraOpen by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<BackendCatalogProduct?>(null) }
    LaunchedEffect(state.enabled, state.canScan) { if (!state.enabled || !state.canScan) { cameraOpen = false; manualOpen = false } }
    if (cameraOpen && state.enabled && state.canScan) CameraCodeDialog({ cameraOpen = false }, {
        cameraOpen = false; code = it; onScan(it)
    })
    Scaffold(modifier = Modifier.imePadding(), topBar = { ViveroTopAppBar("Vender", onBack, "DULCINEA · ${state.branchName}") {
        if (state.canSell) IconButton(onCart) { Icon(Icons.Outlined.ShoppingCart, "Abrir carrito") }
    } }) { padding ->
        LazyVerticalGrid(GridCells.Adaptive(170.dp), Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(state.query, onSearch, Modifier.fillMaxWidth(), label = { Text("Buscar plantas y productos") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, enabled = state.enabled,
                        trailingIcon = { if (state.canScan) IconButton({ cameraOpen = true }, enabled = state.enabled && !state.loading && !state.working) {
                            Icon(Icons.Outlined.QrCodeScanner, "Escanear código")
                        } })
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(state.category == null, { onCategory(null) }, label = { Text("Todas") }, enabled = state.enabled) }
                        items(state.categories, key = { it.id }) { category ->
                            FilterChip(state.category == category.id, { onCategory(category.id) }, label = { Text(category.name) }, enabled = state.enabled)
                        }
                        if (state.nextCategory != null) item { TextButton(onMoreCategories, enabled = state.enabled && !state.loading) { Text("Más categorías") } }
                    }
                    if (state.canScan) TextButton({ manualOpen = true }, enabled = state.enabled && !state.loading) {
                        Icon(Icons.Outlined.Keyboard, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Ingresar código")
                    }
                    if (state.loading || state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.error?.let { SalesNotice(it, true) { TextButton(onRetry, enabled = !state.loading) { Text("Volver a intentar") } } }
                    state.message?.let { SalesNotice(it) }
                    if (!state.enabled) SalesNotice("Actualiza tu sesión y permisos para consultar productos.", true)
                    if (state.enabled && !state.loading && state.products.isEmpty() && state.error == null)
                        SalesEmpty("Sin resultados", "Prueba otro nombre, categoría o código.")
                }
            }
            items(state.products, key = { it.id }) { product ->
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), elevation = CardDefaults.cardElevation(1.dp)) {
                    Box {
                        ProductPhoto(product.image?.let { imageOrigin + it.path }, product.commonName, Modifier.fillMaxWidth().aspectRatio(1.35f))
                        if (product.promotion != null) StatusPill("Promoción", Modifier.align(Alignment.TopStart).padding(8.dp))
                    }
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(product.commonName, style = MaterialTheme.typography.titleMedium)
                        Text(product.effectivePriceCents.asMxn(), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Por ${product.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.canSell) Button({ onAdd(product) }, enabled = state.enabled && !state.working,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Agregar") }
                        TextButton({ detail = product }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ver detalle") }
                    }
                }
            }
            if (state.next != null) item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(onMore, enabled = state.enabled && !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Más productos") }
            }
        }
    }
    if (manualOpen) AlertDialog(onDismissRequest = { manualOpen = false }, title = { Text("Buscar por código") },
        text = { OutlinedTextField(code, { if (it.length <= 128) code = it }, label = { Text("Código de barras o interno") }, singleLine = true) },
        confirmButton = { TextButton({ manualOpen = false; onScan(code) }, enabled = state.enabled && state.canScan && code.trim().length >= 2) { Text("Buscar") } },
        dismissButton = { TextButton({ manualOpen = false }) { Text("Volver") } })
    detail?.let { product -> AlertDialog(onDismissRequest = { detail = null }, title = { Text(product.commonName) },
        text = { androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Código: ${product.internalCode}"); product.scientificName?.let { Text(it) }; Text(product.description)
                Text("Riego: ${product.wateringAdvice}\nLuz: ${product.lightType}\nClima: ${product.recommendedClimate}")
                product.promotion?.let { Text("${it.name} · precio anterior ${product.priceCents.asMxn()}") }
            }
        } }, confirmButton = { TextButton({ detail = null }) { Text("Cerrar") } }) }
}
