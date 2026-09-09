package com.intutec.viveroapp.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.intutec.viveroapp.core.designsystem.BotanicalBackdrop
import com.intutec.viveroapp.core.designsystem.DulcineaWordmark
import com.intutec.viveroapp.core.designsystem.LightBotanicalBackdrop
import com.intutec.viveroapp.core.designsystem.StatusPill
import com.intutec.viveroapp.core.designsystem.ViveroMark

@Composable
fun LoginScreen(
    state: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSignIn: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 840.dp
            if (expanded) {
                Row(Modifier.fillMaxSize()) {
                    BrandPanel(Modifier.weight(.9f).fillMaxHeight())
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .padding(WindowInsets.safeDrawing.asPaddingValues())
                            .padding(horizontal = 56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
                            CompactBrand()
                            Spacer(Modifier.height(48.dp))
                            LoginForm(state, onEmailChanged, onPasswordChanged, onTogglePassword, onSignIn, onForgotPassword)
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    LightBotanicalBackdrop(Modifier.fillMaxSize())
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(WindowInsets.safeDrawing.asPaddingValues())
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 26.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(Modifier.fillMaxWidth().widthIn(max = 500.dp)) {
                            CompactBrand()
                            Spacer(Modifier.height(52.dp))
                            LoginForm(state, onEmailChanged, onPasswordChanged, onTogglePassword, onSignIn, onForgotPassword)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactBrand() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ViveroMark(markSize = 46.dp)
        DulcineaWordmark()
    }
}

@Composable
private fun BrandPanel(modifier: Modifier = Modifier) {
    Box(modifier) {
        BotanicalBackdrop(Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().padding(56.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(shape = MaterialTheme.shapes.large, color = Color.White.copy(alpha = .96f)) {
                DulcineaWordmark(Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            Column(Modifier.widthIn(max = 520.dp)) {
                Text(
                    "Cada planta cuenta.\nCada operación también.",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "Catálogo, ventas e inventario en un espacio diseñado para trabajar con claridad.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = .78f),
                )
            }
            StatusPill(
                text = "Operación en armonía",
                containerColor = Color.White.copy(alpha = .12f),
                contentColor = Color.White,
            )
        }
    }
}

@Composable
private fun LoginForm(
    state: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSignIn: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    val working = state.status == AuthStatus.WORKING
    Column(Modifier.fillMaxWidth()) {
        Text("ACCESO DE PERSONAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(10.dp))
        Text("Bienvenido\nde nuevo", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Text(
            "Ingresa con tu cuenta de trabajo para continuar.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChanged,
            label = { Text("Correo electrónico") },
            leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
            singleLine = true,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChanged,
            label = { Text("Contraseña") },
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        if (state.passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (state.passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                    )
                }
            },
            visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.End), enabled = !working) {
            Text("¿Olvidaste tu contraseña?")
        }
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
        }
        state.infoMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
        }
        Button(
            onClick = onSignIn,
            enabled = !working && state.remoteConfigured && state.email.isNotBlank() && state.password.length >= 6,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            if (working) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Iniciar sesión", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(26.dp))
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "Acceso exclusivo para personal autorizado",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!state.remoteConfigured) {
            Spacer(Modifier.height(18.dp))
            StatusPill(
                text = "Configuración requerida",
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
