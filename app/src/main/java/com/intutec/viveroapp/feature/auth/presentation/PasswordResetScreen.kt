package com.intutec.viveroapp.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intutec.viveroapp.core.designsystem.ViveroMark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordResetScreen(
    state: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val working = state.status == AuthStatus.WORKING
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recuperar acceso") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(Modifier.fillMaxWidth().widthIn(max = 500.dp)) {
                ViveroMark(markSize = 64.dp)
                Spacer(Modifier.height(22.dp))
                Text("Volvamos a tu cuenta", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Escribe tu correo y te enviaremos instrucciones seguras.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChanged,
                    label = { Text("Correo electrónico") },
                    leadingIcon = { Icon(Icons.Outlined.Email, null) },
                    singleLine = true,
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                )
                state.errorMessage?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                state.infoMessage?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onSubmit,
                    enabled = !working && state.remoteConfigured && state.email.contains('@'),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    if (working) CircularProgressIndicator(strokeWidth = 2.dp)
                    else Text("Enviar instrucciones", fontWeight = FontWeight.Bold)
                }
                if (!state.remoteConfigured) {
                    Spacer(Modifier.height(12.dp))
                    Text("Disponible al conectar Supabase.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
