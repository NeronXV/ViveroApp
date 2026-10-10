package com.intutec.viveroapp.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** A real catalog image, with the same fallback for absent or unavailable photos. */
@Composable
fun ProductPhoto(model: Any?, name: String, modifier: Modifier = Modifier) {
    var unavailable by remember(model) { mutableStateOf(false) }
    var loaded by remember(model) { mutableStateOf(false) }
    val missing = model == null || unavailable
    Box(modifier.background(MaterialTheme.colorScheme.primaryContainer).semantics {
        stateDescription = if (missing) "Sin fotografía" else if (loaded) "Fotografía disponible" else "Cargando fotografía"
    }, contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.LocalFlorist, if (missing) "Producto sin fotografía: $name" else null,
            Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        if (model != null && !unavailable) AsyncImage(model, name, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, onError = { unavailable = true }, onSuccess = { loaded = true })
    }
}

@Composable
fun SalesNotice(text: String, critical: Boolean = false, action: @Composable (() -> Unit)? = null) {
    Surface(color = if (critical) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (critical) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            action?.invoke()
        }
    }
}

@Composable
fun SalesEmpty(title: String, message: String, action: @Composable (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.LocalFlorist, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(message, style = MaterialTheme.typography.bodyMedium)
        action?.invoke()
    }
}
