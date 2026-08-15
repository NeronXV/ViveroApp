package com.intutec.viveroapp.feature.scanner.presentation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.designsystem.StatusPill
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.catalog.presentation.productImageResource
import com.intutec.viveroapp.feature.scanner.camera.BarcodeAnalyzer
import com.intutec.viveroapp.feature.scanner.domain.model.ScanFormat
import java.util.concurrent.Executors

@Composable
fun ScannerScreenRoute(
    onBack: () -> Unit,
    onProductDetails: (String) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.notices.collect { snackbar.showSnackbar(it) } }
    ScannerScreen(
        state = state,
        onBack = onBack,
        onCodeDetected = viewModel::onCodeDetected,
        onScannerFailure = { viewModel.onScannerFailure("No fue posible iniciar el escáner. Puedes ingresar el código manualmente.") },
        onScanAgain = viewModel::scanAgain,
        onProductDetails = onProductDetails,
        onAddToCart = viewModel::addCurrentProductToCart,
        snackbar = snackbar,
    )
}

@Composable
private fun ScannerScreen(
    state: ScannerUiState,
    onBack: () -> Unit,
    onCodeDetected: (String, ScanFormat) -> Unit,
    onScannerFailure: () -> Unit,
    onScanAgain: () -> Unit,
    onProductDetails: (String) -> Unit,
    onAddToCart: () -> Unit,
    snackbar: SnackbarHostState,
) {
    var showManualEntry by rememberSaveable { mutableStateOf(false) }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFF07130C))) {
            CameraPermissionGate(
                modifier = Modifier.fillMaxSize(),
                onManualEntry = { showManualEntry = true },
            ) {
                CameraPreview(
                    resetKey = state.resetKey,
                    onCodeDetected = onCodeDetected,
                    onFailure = onScannerFailure,
                )
                ScanFrame(state.result == ScanResultState.Ready)
            }

            ScannerHeader(
                onBack = onBack,
                onManualEntry = { showManualEntry = true },
                modifier = Modifier.align(Alignment.TopCenter),
            )

            ScanResultCard(
                result = state.result,
                onScanAgain = onScanAgain,
                onProductDetails = onProductDetails,
                onAddToCart = onAddToCart,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (showManualEntry) {
        ManualCodeDialog(
            onDismiss = { showManualEntry = false },
            onSubmit = {
                showManualEntry = false
                onScanAgain()
                onCodeDetected(it, ScanFormat.MANUAL)
            },
        )
    }
}

@Composable
private fun CameraPermissionGate(
    modifier: Modifier,
    onManualEntry: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRequested = true
        permissionGranted = it
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (permissionGranted) {
        Box(modifier) { content() }
    } else {
        val activity = context.findActivity()
        val permanentlyDenied = permissionRequested && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
        Column(
            modifier = modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Outlined.QrCodeScanner, null, Modifier.padding(20.dp).size(42.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(20.dp))
            Text("Escanea productos al instante", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "Usamos la cámara únicamente para reconocer códigos QR y de barras. No guardamos fotografías ni video.",
                color = Color.White.copy(alpha = .76f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (permanentlyDenied) context.openAppSettings() else launcher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(if (permanentlyDenied) Icons.Outlined.Settings else Icons.Outlined.QrCodeScanner, null)
                Spacer(Modifier.width(8.dp))
                Text(if (permanentlyDenied) "Abrir configuración" else "Permitir cámara")
            }
            OutlinedButton(onClick = onManualEntry, modifier = Modifier.fillMaxWidth()) {
                Text("Ingresar código manualmente")
            }
        }
    }
}

@Composable
private fun CameraPreview(
    resetKey: Int,
    onCodeDetected: (String, ScanFormat) -> Unit,
    onFailure: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnDetected by rememberUpdatedState(onCodeDetected)
    val currentOnFailure by rememberUpdatedState(onFailure)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { BarcodeAnalyzer({ code, format -> currentOnDetected(code, format) }, { currentOnFailure() }) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            setImageAnalysisAnalyzer(executor, analyzer)
        }
    }
    LaunchedEffect(resetKey) { analyzer.reset() }
    DisposableEffect(lifecycleOwner) {
        runCatching { controller.bindToLifecycle(lifecycleOwner) }.onFailure { currentOnFailure() }
        onDispose {
            controller.unbind()
            analyzer.close()
            executor.shutdown()
        }
    }
    AndroidView(
        factory = { PreviewView(it).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; this.controller = controller } },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ScanFrame(isReady: Boolean) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(.76f)
                .height(230.dp)
                .border(2.dp, if (isReady) Color(0xFFB7F1B3) else Color.White.copy(alpha = .45f), RoundedCornerShape(28.dp)),
        )
        if (isReady) {
            Text(
                "Alinea el código dentro del marco",
                modifier = Modifier.align(Alignment.Center).padding(top = 280.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ScannerHeader(onBack: () -> Unit, onManualEntry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Surface(shape = CircleShape, color = Color.Black.copy(alpha = .42f)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = Color.White) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Escáner inteligente", color = Color.White, fontWeight = FontWeight.Bold)
            Text("QR · EAN · Code 128", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall)
        }
        Surface(shape = CircleShape, color = Color.Black.copy(alpha = .42f)) {
            IconButton(onClick = onManualEntry) { Icon(Icons.Outlined.Keyboard, "Ingresar código", tint = Color.White) }
        }
    }
}

@Composable
private fun ScanResultCard(
    result: ScanResultState,
    onScanAgain: () -> Unit,
    onProductDetails: (String) -> Unit,
    onAddToCart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (result == ScanResultState.Ready) return
    Surface(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .97f),
        shadowElevation = 20.dp,
    ) {
        when (result) {
            ScanResultState.Ready -> Unit
            is ScanResultState.Searching -> Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                Column { Text("Consultando catálogo", fontWeight = FontWeight.Bold); Text(result.code, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            is ScanResultState.Found -> FoundProduct(result.product, result.format, onScanAgain, onProductDetails, onAddToCart)
            is ScanResultState.NotFound -> MessageResult(
                title = "Producto no encontrado",
                body = "El código ${result.code} no está registrado en el catálogo actual.",
                action = "Escanear otro",
                onAction = onScanAgain,
            )
            is ScanResultState.Error -> MessageResult("No pudimos completar la consulta", result.message, "Intentar de nuevo", onScanAgain)
        }
    }
}

@Composable
private fun FoundProduct(
    product: Product,
    format: ScanFormat,
    onScanAgain: () -> Unit,
    onProductDetails: (String) -> Unit,
    onAddToCart: () -> Unit,
) {
    Column(Modifier.padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(productImageResource(product.imageKey)),
                contentDescription = null,
                modifier = Modifier.size(88.dp),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(format.label)
                    product.promotion?.let { StatusPill("Oferta") }
                }
                Text(product.commonName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(product.effectivePriceCents.asMxn(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        !product.stockKnown -> "Disponibilidad por confirmar"
                        product.isAvailable -> "${product.stockAvailable} disponibles"
                        else -> "Sin existencia"
                    },
                    color = if (product.stockKnown && !product.isAvailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = onAddToCart, enabled = product.isAvailable, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.ShoppingBag, null)
            Spacer(Modifier.width(8.dp))
            Text(if (product.isAvailable) "Agregar al carrito" else "Sin existencia")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onScanAgain, modifier = Modifier.weight(1f)) { Text("Otro código") }
            FilledTonalButton(onClick = { onProductDetails(product.id) }, modifier = Modifier.weight(1f)) { Text("Ver detalle") }
        }
    }
}

@Composable
private fun MessageResult(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
    }
}

@Composable
private fun ManualCodeDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Keyboard, null) },
        title = { Text("Buscar por código") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Código de barras o interno") },
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { onSubmit(code) }, enabled = code.isNotBlank()) { Text("Buscar") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        },
    )
}
