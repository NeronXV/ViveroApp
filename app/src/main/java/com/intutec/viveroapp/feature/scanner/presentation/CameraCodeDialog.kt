package com.intutec.viveroapp.feature.scanner.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.intutec.viveroapp.core.designsystem.ViveroTopAppBar
import java.util.concurrent.atomic.AtomicBoolean

/** Camera input only: product lookup and cart changes stay with the catalog ViewModel. */
@Composable
fun CameraCodeDialog(onDismiss: () -> Unit, onCode: (String) -> Unit) {
    var failed by remember { mutableStateOf(false) }
    val delivered = remember { AtomicBoolean(false) }
    val currentOnCode by rememberUpdatedState(onCode)
    val dismiss = { delivered.set(true); onDismiss() }
    DisposableEffect(Unit) { onDispose { delivered.set(true) } }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = { ViveroTopAppBar(title = "Escanear producto", onBack = dismiss) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFF07130C))) {
                if (failed) {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("No se pudo iniciar la cámara. Puedes reintentar o ingresar el código en el catálogo.", color = Color.White)
                        Button({ failed = false }) { Text("Reintentar cámara") }
                        FilledTonalButton(dismiss) { Text("Ingresar código manualmente") }
                    }
                } else {
                    CameraPermissionGate(Modifier.fillMaxSize(), onManualEntry = dismiss) {
                        CameraPreview(
                            resetKey = 0,
                            onCodeDetected = { code, _ ->
                                if (delivered.compareAndSet(false, true)) currentOnCode(code)
                            },
                            onFailure = { failed = true },
                        )
                        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Alinea un código QR, EAN o Code 128 frente a la cámara.")
                                Text("Después podrás revisar el producto y agregarlo al carrito.", style = MaterialTheme.typography.bodySmall)
                                TextButton(dismiss) { Text("Ingresar código manualmente") }
                            }
                        }
                    }
                }
            }
        }
    }
}
