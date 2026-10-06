package com.intutec.viveroapp.feature.catalog.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.designsystem.ViveroTopAppBar
import com.intutec.viveroapp.core.designsystem.ViveroSectionIntro
import com.intutec.viveroapp.core.designsystem.StatusPill
import com.intutec.viveroapp.feature.scanner.presentation.CameraCodeDialog

@Composable
fun BackendCatalogScreen(onBack: () -> Unit, onCart: () -> Unit, viewModel: BackendCatalogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    var cameraOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.enabled, state.canScan) {
        if (!state.enabled || !state.canScan) cameraOpen = false
    }
    if (cameraOpen && state.enabled && state.canScan) {
        CameraCodeDialog(onDismiss = { cameraOpen = false }, onCode = {
            cameraOpen = false
            code = it
            viewModel.scan(it)
        })
    }
    Scaffold(topBar = { ViveroTopAppBar(title = "Catálogo", onBack = onBack, eyebrow = "COLECCIÓN BOTÁNICA") {
            if (state.canSell) IconButton(onCart) { Icon(Icons.Outlined.ShoppingCart, "Abrir carrito") }
        } }) { padding ->
        LazyVerticalGrid(columns = GridCells.Adaptive(260.dp), modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ViveroSectionIntro("Nuestra colección", "Plantas y productos para dar vida a tu espacio.", Modifier.padding(bottom = 18.dp))
                OutlinedTextField(state.query, viewModel::search, label = { Text("Buscar por nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = state.enabled)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(state.category == null, { viewModel.category(null) }, label = { Text("Todas") }) }
                    items(state.categories, key = { it.id }) { category ->
                        FilterChip(state.category == category.id, { viewModel.category(category.id) }, label = { Text(category.name) })
                    }
                    if (state.nextCategory != null) item { TextButton(viewModel::moreCategories, enabled = !state.loading) { Text("Más categorías") } }
                }
                OutlinedTextField(code, { if (it.length <= 128) code = it }, label = { Text("Código de barras o código interno") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ viewModel.scan(code) }, enabled = state.enabled && state.canScan && !state.loading && code.trim().length >= 2) { Text("Buscar código") }
                    if (state.canSell) TextButton(onCart) { Text("Abrir carrito") }
                }
                OutlinedButton({ cameraOpen = true }, enabled = state.enabled && state.canScan && !state.loading && !state.working, modifier = Modifier.fillMaxWidth()) { Text("Escanear con cámara") }
                if (state.loading || state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(viewModel::retry) { Text("Reintentar") } }
                state.message?.let { Text(it) }
                if (!state.enabled) Text("Actualiza tus permisos para consultar el catálogo.")
                if (state.enabled && !state.loading && state.products.isEmpty() && state.error == null) Text("No hay productos para esta búsqueda.")
                }
            }
            items(state.products, key = { it.id }) { product ->
                var expanded by remember(product.id) { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Box(Modifier.fillMaxWidth().height(184.dp).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        if (product.image != null) AsyncImage(viewModel.imageOrigin + product.image.path,
                            product.image.altText.ifBlank { product.commonName }, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.LocalFlorist, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Fotografía pendiente", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        product.promotion?.let { StatusPill("Promoción", Modifier.align(Alignment.TopStart).padding(12.dp)) }
                    }
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.categories.firstOrNull { it.id == product.categoryId }?.name?.uppercase().orEmpty(),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text(product.commonName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text(product.internalCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${product.effectivePriceCents.asMxn()} / ${product.unit}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    product.promotion?.let { Text("${it.name} · precio anterior ${product.priceCents.asMxn()}") }
                    TextButton({ expanded = !expanded }) { Text(if (expanded) "Ocultar detalle" else "Ver detalle") }
                    if (expanded) {
                        product.scientificName?.let { Text(it) }
                        Text(product.description)
                        Text("Riego: ${product.wateringAdvice}\nLuz: ${product.lightType}\nClima: ${product.recommendedClimate}")
                    }
                    if (state.canSell) Button({ viewModel.add(product) }, enabled = !state.working, modifier = Modifier.fillMaxWidth(), shape = CircleShape) { Text("Agregar al carrito") }
                } }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                if (state.next != null) OutlinedButton(viewModel::more, enabled = !state.loading) { Text("Cargar más productos") }
                Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}
