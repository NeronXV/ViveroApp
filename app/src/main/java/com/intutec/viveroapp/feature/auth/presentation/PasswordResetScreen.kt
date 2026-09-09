package com.intutec.viveroapp.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intutec.viveroapp.core.designsystem.DulcineaWordmark
import com.intutec.viveroapp.core.designsystem.LightBotanicalBackdrop
import com.intutec.viveroapp.core.designsystem.ViveroCard
import com.intutec.viveroapp.core.designsystem.ViveroSectionIntro
import com.intutec.viveroapp.core.designsystem.ViveroTopAppBar

@Composable
fun PasswordResetScreen(
    state: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val working = state.status == AuthStatus.WORKING
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViveroTopAppBar(title = "Recuperar acceso", onBack = onBack, eyebrow = "SEGURIDAD")
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LightBotanicalBackdrop(Modifier.fillMaxSize())
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Column(Modifier.fillMaxWidth().widthIn(max = 500.dp)) {
                    DulcineaWordmark()
                    Spacer(Modifier.height(32.dp))
                    ViveroCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp)) {
                            ViveroSectionIntro(
                                title = "Volvamos a tu cuenta",
                                subtitle = "Escribe tu correo y te enviaremos instrucciones seguras.",
                                eyebrow = "RECUPERACIÓN",
                            )
                            Spacer(Modifier.height(24.dp))
                            OutlinedTextField(
                                value = state.email,
                                onValueChange = onEmailChanged,
                                label = { Text("Correo electrónico") },
                                leadingIcon = { Icon(Icons.Outlined.Email, null) },
                                singleLine = true,
                                enabled = !working,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
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
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                if (working) CircularProgressIndicator(strokeWidth = 2.dp)
                                else Text("Enviar instrucciones", fontWeight = FontWeight.Bold)
                            }
                            if (!state.remoteConfigured) {
                                Spacer(Modifier.height(12.dp))
                                Text("Disponible al conectar el servicio.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
