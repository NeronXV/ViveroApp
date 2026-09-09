package com.intutec.viveroapp.feature.catalog.admin.presentation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.intutec.viveroapp.core.designsystem.ViveroTopAppBar
import com.intutec.viveroapp.core.designsystem.ViveroCard
import com.intutec.viveroapp.core.designsystem.ViveroSectionIntro
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductAdminScreenRoute(
    onBack: () -> Unit,
    onRegisterStock: (productId: String) -> Unit,
    viewModel: ProductAdminViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onPhotoUriSelected(it.toString()) }
    }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.onCameraPhotoTaken(success, pendingCameraUri)
    }
    fun createTempUri(): Uri {
        val dir = File(context.cacheDir, "product_images").apply { mkdirs() }
        val file = File(dir, "camera_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val launchCameraCapture = {
        try {
            val uri = createTempUri()
            pendingCameraUri = uri.toString()
            takePictureLauncher.launch(uri)
        } catch (_: SecurityException) {
            pendingCameraUri = null
            viewModel.onCameraLaunchFailed()
        } catch (_: ActivityNotFoundException) {
            pendingCameraUri = null
            viewModel.onCameraLaunchFailed()
        } catch (_: IllegalArgumentException) {
            pendingCameraUri = null
            viewModel.onCameraLaunchFailed()
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCameraCapture() else viewModel.onCameraPermissionDenied()
    }
    ProductAdminScreen(
        state = state,
        onBack = onBack,
        onRegisterStock = onRegisterStock,
        onInternalCodeChanged = viewModel::onInternalCodeChanged,
        onBarcodeChanged = viewModel::onBarcodeChanged,
        onCommonNameChanged = viewModel::onCommonNameChanged,
        onScientificNameChanged = viewModel::onScientificNameChanged,
        onCategorySelected = viewModel::onCategorySelected,
        onDescriptionChanged = viewModel::onDescriptionChanged,
        onPriceChanged = viewModel::onPriceChanged,
        onWholesalePriceChanged = viewModel::onWholesalePriceChanged,
        onUnitChanged = viewModel::onUnitChanged,
        onMinimumStockChanged = viewModel::onMinimumStockChanged,
        onWateringChanged = viewModel::onWateringChanged,
        onLightChanged = viewModel::onLightChanged,
        onClimateChanged = viewModel::onClimateChanged,
        onActiveChanged = viewModel::onActiveChanged,
        onNewCategoryNameChanged = viewModel::onNewCategoryNameChanged,
        onCreateCategory = viewModel::createCategory,
        onSave = viewModel::saveProduct,
        onRetryCategories = viewModel::retryCategories,
        onClearSaved = viewModel::clearSavedState,
        onDismissError = viewModel::dismissError,
        onTakePhoto = {
            if (!state.canManageProducts) return@ProductAdminScreen
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCameraCapture()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        onPickPhoto = {
            if (!state.canManageProducts) return@ProductAdminScreen
            pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onRemovePhoto = viewModel::onRemovePhoto,
        onRetryUpload = viewModel::retryImageUpload,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductAdminScreen(
    state: ProductAdminUiState,
    onBack: () -> Unit,
    onRegisterStock: (String) -> Unit,
    onInternalCodeChanged: (String) -> Unit,
    onBarcodeChanged: (String) -> Unit,
    onCommonNameChanged: (String) -> Unit,
    onScientificNameChanged: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onPriceChanged: (String) -> Unit,
    onWholesalePriceChanged: (String) -> Unit,
    onUnitChanged: (String) -> Unit,
    onMinimumStockChanged: (String) -> Unit,
    onWateringChanged: (String) -> Unit,
    onLightChanged: (String) -> Unit,
    onClimateChanged: (String) -> Unit,
    onActiveChanged: (Boolean) -> Unit,
    onNewCategoryNameChanged: (String) -> Unit,
    onCreateCategory: () -> Unit,
    onSave: () -> Unit,
    onRetryCategories: () -> Unit,
    onClearSaved: () -> Unit,
    onDismissError: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onRetryUpload: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.saveError, state.categoriesError) {
        (state.saveError ?: state.categoriesError)?.let {
            snackbar.showSnackbar(it)
            onDismissError()
        }
    }
    // Success dialog after save
    if (state.savedProductId != null) {
        AlertDialog(
            onDismissRequest = onClearSaved,
            title = { Text("Producto guardado") },
            text = { Text("${state.savedProductName ?: "Producto"} se dio de alta correctamente en ${state.branchName.ifBlank { "tu sucursal" }}. ¿Registrar ingreso al inventario?") },
            confirmButton = {
                Button(onClick = { val id = state.savedProductId; onClearSaved(); onRegisterStock(id) }, modifier = Modifier.testTag("product_admin_go_inventory")) { Text("Registrar ingreso") }
            },
            dismissButton = { TextButton(onClick = onClearSaved) { Text("Quedarme aquí") } },
            modifier = Modifier.testTag("product_admin_success_dialog"),
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ViveroTopAppBar(
                title = "Productos y plantas",
                onBack = onBack,
                eyebrow = state.branchName.ifBlank { "ALTA DE PRODUCTO" }.uppercase(),
            )
        },
    ) { padding ->
        when (state.sessionGate) {
            ProductAdminSessionGate.WAITING_SESSION -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp).testTag("product_admin_waiting"), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text("Esperando sesión…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Verificando acceso…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ProductAdminSessionGate.ACCESS_DENIED -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp).testTag("product_admin_denied"), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("No tienes permiso para administrar productos.", style = MaterialTheme.typography.titleMedium)
                    Text("Se requiere capacidad MANAGE_PRODUCTS.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (state.branchName.isNotBlank()) Text("Sucursal: ${state.branchName}", style = MaterialTheme.typography.bodySmall)
                }
            }
            ProductAdminSessionGate.NO_SESSION -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp).testTag("product_admin_no_session"), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sesión no disponible", style = MaterialTheme.typography.titleMedium)
                    Text("Vuelve a iniciar sesión para administrar productos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onBack) { Text("Volver") }
                }
            }
            ProductAdminSessionGate.READY -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ViveroSectionIntro(
                    title = "Nueva planta o producto",
                    subtitle = "Completa la información en bloques. Los campos con asterisco son obligatorios.",
                    eyebrow = "CATÁLOGO INTERNO",
                )
                ProductFormSection("Clasificación", "Selecciona una categoría existente o crea una nueva.") {
                    when {
                        state.isLoadingCategories -> Row(Modifier.fillMaxWidth().testTag("product_admin_loading"), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp))
                            Text("Cargando categorías…")
                        }
                        state.categoriesError != null -> Column(Modifier.testTag("product_admin_categories_error"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(state.categoriesError, color = MaterialTheme.colorScheme.error)
                            Button(onClick = onRetryCategories, modifier = Modifier.testTag("product_admin_retry")) { Text("Reintentar") }
                        }
                        else -> {
                            CategoryDropdown(state.categories, state.selectedCategoryId, onCategorySelected, state.fieldErrors["category"])
                            Spacer(Modifier.height(12.dp))
                            CategoryCreator(state.newCategoryName, onNewCategoryNameChanged, onCreateCategory, state.isCreatingCategory, state.newCategoryError)
                        }
                    }
                }
                ProductFormSection("Fotografía", "Usa una imagen clara para reconocer el producto en catálogo y escáner.") {
                    PhotoSection(state, onTakePhoto, onPickPhoto, onRemovePhoto, onRetryUpload)
                }
                ProductFormSection("Identificación", "Nombres y códigos con los que el equipo encontrará este producto.") {
                    OutlinedTextField(value = state.internalCode, onValueChange = onInternalCodeChanged, label = { Text("Código interno *") }, singleLine = true, isError = state.fieldErrors.containsKey("internalCode"), supportingText = { state.fieldErrors["internalCode"]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth().testTag("product_internal_code"))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = state.barcode, onValueChange = onBarcodeChanged, label = { Text("Código de barras (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("product_barcode"))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = state.commonName, onValueChange = onCommonNameChanged, label = { Text("Nombre común *") }, singleLine = true, isError = state.fieldErrors.containsKey("commonName"), supportingText = { state.fieldErrors["commonName"]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth().testTag("product_common_name"))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = state.scientificName, onValueChange = onScientificNameChanged, label = { Text("Nombre científico (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("product_scientific_name"))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = state.description, onValueChange = onDescriptionChanged, label = { Text("Descripción") }, minLines = 2, modifier = Modifier.fillMaxWidth().testTag("product_description"))
                }
                ProductFormSection("Precio e inventario", "Define la unidad de venta y el nivel mínimo para alertas.") {
                    OutlinedTextField(
                        value = state.priceInput,
                        onValueChange = onPriceChanged,
                        label = { Text("Precio * (ej. 120.50)") },
                        singleLine = true,
                        enabled = state.canManagePrices,
                        isError = state.fieldErrors.containsKey("price"),
                        supportingText = { state.fieldErrors["price"]?.let { Text(it) } ?: if (!state.canManagePrices) Text("Requiere permiso para administrar precios") else null },
                        modifier = Modifier.fillMaxWidth().testTag("product_price"),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = state.wholesalePriceInput, onValueChange = onWholesalePriceChanged, label = { Text("Precio mayoreo (opcional)") }, singleLine = true, enabled = state.canManagePrices, modifier = Modifier.fillMaxWidth().testTag("product_wholesale_price"))
                    Spacer(Modifier.height(12.dp))
                    UnitDropdown(state.unit, onUnitChanged, state.fieldErrors["unit"])
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = state.minimumStockInput, onValueChange = onMinimumStockChanged, label = { Text("Existencia mínima *") }, singleLine = true, isError = state.fieldErrors.containsKey("minimumStock"), supportingText = { state.fieldErrors["minimumStock"]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth().testTag("product_minimum_stock"))
                }
                ProductFormSection("Cuidados", "Información útil para orientar al cliente.") {
                    OutlinedTextField(value = state.wateringAdvice, onValueChange = onWateringChanged, label = { Text("Riego (opcional)") }, modifier = Modifier.fillMaxWidth().testTag("product_watering"))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = state.lightType, onValueChange = onLightChanged, label = { Text("Luz (opcional)") }, modifier = Modifier.fillMaxWidth().testTag("product_light"))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = state.recommendedClimate, onValueChange = onClimateChanged, label = { Text("Clima recomendado (opcional)") }, modifier = Modifier.fillMaxWidth().testTag("product_climate"))
                }
                ProductFormSection("Disponibilidad", "Controla si este producto aparece en los flujos activos.") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Producto activo", style = MaterialTheme.typography.titleSmall)
                            Text("Visible para el personal autorizado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.isActive, onCheckedChange = onActiveChanged, modifier = Modifier.testTag("product_is_active"))
                    }
                    if (!state.canManagePrices) {
                        Spacer(Modifier.height(10.dp))
                        Text("Tu rol no permite modificar precios.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("product_save_error")) }
                Button(
                    onClick = onSave,
                    enabled = !state.isSaving && !state.isLoadingCategories,
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("product_save_btn"),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Guardar producto", fontWeight = FontWeight.Bold)
                }
                Text(
                    "Se registrará en ${state.branchName.ifBlank { "la sucursal asignada a tu perfil" }}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag("product_branch_label"),
                )
            }
        }
    }
}

@Composable
private fun ProductFormSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    ViveroCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(categories: List<com.intutec.viveroapp.feature.catalog.domain.model.Category>, selectedId: String?, onSelected: (String?) -> Unit, error: String?) {
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(value = selected?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Categoría *") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, isError = error != null, supportingText = { error?.let { Text(it) } }, modifier = Modifier.fillMaxWidth().menuAnchor().testTag("product_category_dropdown"))
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { cat ->
                DropdownMenuItem(text = { Text(cat.name) }, onClick = { onSelected(cat.id); expanded = false }, modifier = Modifier.testTag("product_category_${cat.id}"))
            }
        }
    }
}

@Composable
private fun CategoryCreator(newName: String, onNameChanged: (String) -> Unit, onCreate: () -> Unit, isCreating: Boolean, error: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = newName, onValueChange = onNameChanged, label = { Text("Nueva categoría") }, singleLine = true, modifier = Modifier.weight(1f).testTag("new_category_name"))
            Button(onClick = onCreate, enabled = !isCreating, modifier = Modifier.testTag("create_category_btn")) {
                if (isCreating) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Crear")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun PhotoSection(
    state: ProductAdminUiState,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onRetryUpload: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("product_photo_section"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.fillMaxWidth().height(200.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).testTag("product_photo_preview"),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isProcessingPhoto -> Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(Modifier.size(32.dp)); Text("Preparando imagen…", style = MaterialTheme.typography.bodySmall) }
                state.isUploadingPhoto -> Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(Modifier.size(32.dp)); Text("Subiendo fotografía…", style = MaterialTheme.typography.bodySmall) }
                state.pendingPhotoUri != null -> AsyncImage(model = state.pendingPhotoUri, contentDescription = "Vista previa", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                state.existingImagePath != null -> AsyncImage(model = state.existingImagePath, contentDescription = "Imagen principal actual", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.Image, null, Modifier.size(48.dp)); Text("Sin fotografía", style = MaterialTheme.typography.bodySmall) }
            }
        }
        // Estados
        when (state.photoState) {
            PhotoUiState.ERROR -> state.photoError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("product_photo_error")) }
            else -> {}
        }
        state.imageUploadError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("product_upload_error")) }
        if (state.isUploadingPhoto) Text("Subiendo fotografía…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onTakePhoto, enabled = state.canManageProducts && !state.isProcessingPhoto && !state.isUploadingPhoto, modifier = Modifier.weight(1f).testTag("product_take_photo")) { Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Tomar") }
            OutlinedButton(onClick = onPickPhoto, enabled = state.canManageProducts && !state.isProcessingPhoto && !state.isUploadingPhoto, modifier = Modifier.weight(1f).testTag("product_pick_photo")) { Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Elegir") }
        }
        if (state.pendingPhotoUri != null || state.existingImagePath != null) {
            OutlinedButton(onClick = onRemovePhoto, enabled = !state.isUploadingPhoto, modifier = Modifier.fillMaxWidth().testTag("product_remove_photo")) { Text("Eliminar fotografía") }
        }
        if (state.imageUploadError != null) {
            Button(onClick = onRetryUpload, modifier = Modifier.fillMaxWidth().testTag("product_retry_upload")) { Text("Reintentar subida") }
        }
        // Mensajes accesibles
        if (!state.canManageProducts) Text("No tienes permiso para administrar imágenes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(selected: String, onSelected: (String) -> Unit, error: String?) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(value = selected, onValueChange = {}, readOnly = true, label = { Text("Unidad *") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, isError = error != null, supportingText = { error?.let { Text(it) } }, modifier = Modifier.fillMaxWidth().menuAnchor().testTag("product_unit_dropdown"))
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("pieza", "maceta", "charola", "bolsa", "kg").forEach { u ->
                DropdownMenuItem(text = { Text(u) }, onClick = { onSelected(u); expanded = false })
            }
        }
    }
}
