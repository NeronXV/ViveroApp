package com.intutec.viveroapp.feature.plants.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.designsystem.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun PlantScreen(
    onBack: () -> Unit,
    vm: PlantViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val p = state.selected
    val context = LocalContext.current
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var photoTarget by rememberSaveable { mutableStateOf("") }
    var chooseCategory by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraPath?.let { vm.photo(Uri.parse(it), photoTarget) }
    }

    fun takePhoto() {
        try {
            val dir = File(context.filesDir, "product_images").apply { mkdirs() }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(dir, "camera-${UUID.randomUUID()}.jpg"))
            cameraPath = uri.toString()
            photoTarget = vm.photoTarget()
            camera.launch(uri)
        } catch (_: Exception) {
            photoError = "No se pudo abrir la cámara. Puedes elegir una foto de galería."
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePhoto() else photoError = "Permite usar la cámara o elige una foto de galería."
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.photo(it, photoTarget) }
    }

    val editable = !state.busy && !state.pending && (p?.status != "REVIEW" || state.canPublish) && p?.status !in listOf("LINKED", "PREPARED")

    BackHandler(p != null || state.busy) {
        if (!state.busy) leaving = true
    }

    Scaffold(
        topBar = {
            ViveroTopAppBar(
                title = if (p == null) "Plantas" else "Paso ${state.step} de 3",
                onBack = { if (p != null && !state.busy) leaving = true else onBack() },
                eyebrow = "ADMINISTRACIÓN · FICHAS DE PLANTAS"
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ViveroSectionIntro(
                title = if (p == null) "Plantas por completar" else p.name.ifBlank { "Nueva planta" },
                subtitle = if (p == null) "Registra y completa información de especímenes para el catálogo." else "Paso ${state.step} de 3 · Completa los datos requeridos."
            )

            state.error?.let { SalesNotice(it, critical = true) }
            state.message?.let { SalesNotice(it) }

            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (state.pending) {
                Button(
                    onClick = vm::recover,
                    enabled = !state.busy,
                    shape = CircleShape
                ) {
                    Text("Comprobar guardado")
                }
            }

            if (!state.enabled) {
                SalesNotice("Inicia sesión con acceso a Plantas para continuar.", critical = true)
            } else if (p == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = vm::create,
                        enabled = !state.busy && !state.pending,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Añadir planta", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = vm::retry,
                        enabled = !state.busy,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Outlined.Refresh, null)
                    }
                }

                if (state.plants.isEmpty() && !state.busy) {
                    SalesEmpty(
                        title = "Sin plantas pendientes",
                        message = "No hay fichas pendientes por completar en esta lista."
                    )
                }

                state.plants.forEach { plant ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    plant.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (plant.hasImage) "Completar información" else "Falta fotografía",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (plant.hasImage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            Button(
                                onClick = { vm.select(plant) },
                                enabled = !state.busy && !state.pending,
                                shape = CircleShape
                            ) {
                                Text("Editar")
                            }
                        }
                    }
                }
            } else {
                // Step 1
                if (state.step == 1) {
                    Text(
                        "1. Identificación y coincidencia",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = p.name,
                        onValueChange = { vm.change(p.copy(name = it)) },
                        label = { Text("Nombre común") },
                        singleLine = true,
                        enabled = editable,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = p.presentation,
                        onValueChange = { vm.change(p.copy(presentation = it)) },
                        label = { Text("Presentación (ej. Maceta 6\")") },
                        singleLine = true,
                        enabled = editable,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { chooseCategory = true },
                        enabled = editable,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(state.categories.find { it.id == p.categoryId }?.name ?: "Elegir categoría")
                    }

                    if (!p.product && p.name.trim().length >= 3) {
                        val term = p.name.trim().split(Regex("\\s+")).firstOrNull { it.length >= 4 } ?: p.name.trim()
                        val matches = state.catalogProducts.filter {
                            it.name.contains(p.name.trim(), ignoreCase = true) || it.name.contains(term, ignoreCase = true)
                        }.take(4)

                        if (matches.isNotEmpty()) {
                            Text(
                                "¿Ya existe en el catálogo? Selecciona la coincidencia",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            matches.forEach { candidate ->
                                val thumbnail by produceState<android.graphics.Bitmap?>(null, candidate.imageId) {
                                    value = candidate.imageId?.let { runCatching { vm.preview(it) }.getOrNull() }
                                }
                                OutlinedButton(
                                    onClick = { vm.change(p.copy(linkProductId = if (p.linkProductId == candidate.id) null else candidate.id)) },
                                    enabled = editable,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (thumbnail != null) {
                                            Image(
                                                thumbnail!!.asImageBitmap(),
                                                contentDescription = candidate.name,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                        } else {
                                            Text("🌿")
                                        }
                                        Text(
                                            "${candidate.name} · ${candidate.presentation}${if (p.linkProductId == candidate.id) " ✓" else ""}",
                                            fontWeight = if (p.linkProductId == candidate.id) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    var details by remember { mutableStateOf(false) }
                    TextButton(onClick = { details = !details }) {
                        Text(if (details) "Menos opciones" else "Más opciones (descripción y código)")
                    }
                    if (details) {
                        OutlinedTextField(
                            value = p.description,
                            onValueChange = { vm.change(p.copy(description = it)) },
                            label = { Text("Cuidados o descripción") },
                            enabled = editable,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!p.product) {
                            OutlinedTextField(
                                value = p.code,
                                onValueChange = { vm.change(p.copy(code = it)) },
                                label = { Text("Código interno (opcional)") },
                                singleLine = true,
                                enabled = editable,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Step 2
                if (state.step == 2) {
                    Text(
                        "2. Fotografía del espécimen",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (p.photoPath != null) "Fotografía seleccionada; se enviará al guardar."
                        else if (p.hasImage) "La ficha ya tiene una fotografía. Puedes conservarla o reemplazarla."
                        else "Añade una foto clara para reconocer esta planta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val preview by produceState<android.graphics.Bitmap?>(null, p.photoPath) {
                        value = withContext(Dispatchers.IO) {
                            p.photoPath?.let { android.graphics.BitmapFactory.decodeFile(it) }
                        }
                    }

                    preview?.let {
                        Image(
                            it.asImageBitmap(),
                            contentDescription = "Foto seleccionada",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }

                    photoError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                                    takePhoto()
                                else
                                    cameraPermission.launch(Manifest.permission.CAMERA)
                            },
                            enabled = editable,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Tomar foto")
                        }
                        OutlinedButton(
                            onClick = {
                                photoTarget = vm.photoTarget()
                                gallery.launch("image/*")
                            },
                            enabled = editable,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Galería")
                        }
                    }
                }

                // Step 3
                if (state.step == 3) {
                    Text(
                        "3. Precio y publicación",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(p.presentation, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    OutlinedTextField(
                        value = p.price,
                        onValueChange = { vm.change(p.copy(price = it)) },
                        label = { Text("Precio de venta (opcional MXN)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        enabled = editable && (!p.product || state.canPrice),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.canPublish && p.status !in listOf("DISABLED", "LINKED", "PREPARED")) {
                        Button(
                            onClick = { vm.save(publish = true) },
                            enabled = editable && state.canPrice,
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Publish, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Confirmar y publicar", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Navigation buttons between steps
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.step > 1) {
                        TextButton(
                            onClick = { vm.step(state.step - 1) },
                            enabled = !state.busy
                        ) {
                            Text("← Paso anterior")
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    if (state.step < 3) {
                        Button(
                            onClick = { vm.step(state.step + 1) },
                            enabled = editable,
                            shape = CircleShape
                        ) {
                            Text("Continuar →")
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { vm.save(exit = true) }, enabled = editable) {
                        Text("Guardar borrador")
                    }
                    TextButton(onClick = { leaving = true }, enabled = !state.busy) {
                        Text("Volver a lista")
                    }
                }

                if (state.error != null && !state.pending) {
                    TextButton(onClick = { reload = true }, enabled = !state.busy) {
                        Text("Consultar versión guardada")
                    }
                }
            }
        }
    }

    if (chooseCategory) {
        AlertDialog(
            onDismissRequest = { chooseCategory = false },
            title = { Text("Categoría") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    state.categories.forEach { c ->
                        TextButton(
                            onClick = {
                                p?.let { vm.change(it.copy(categoryId = c.id)) }
                                chooseCategory = false
                            }
                        ) {
                            Text(c.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { chooseCategory = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("Volver a la lista de plantas") },
            text = { Text("Puedes guardar antes de salir. Descartar elimina solo el avance local, nunca el inventario.") },
            confirmButton = {
                TextButton(
                    onClick = { leaving = false; vm.save(exit = true) },
                    enabled = editable
                ) {
                    Text("Guardar y salir")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { leaving = false; vm.close() },
                    enabled = !state.busy && !state.pending
                ) {
                    Text("Descartar avance local")
                }
            }
        )
    }

    if (reload) {
        AlertDialog(
            onDismissRequest = { reload = false },
            title = { Text("Cargar versión guardada") },
            text = { Text("Reemplazará los cambios locales por la ficha guardada en el servidor.") },
            confirmButton = {
                TextButton(onClick = { reload = false; vm.reload() }) {
                    Text("Cargar")
                }
            },
            dismissButton = {
                TextButton(onClick = { reload = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
