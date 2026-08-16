package com.intutec.viveroapp.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.intutec.viveroapp.core.designsystem.BotanicalBackdrop
import com.intutec.viveroapp.core.designsystem.PremiumCard
import com.intutec.viveroapp.core.designsystem.StatusPill
import com.intutec.viveroapp.core.designsystem.DulcineaWordmark

@Composable
fun LoginScreen(
    state: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSignIn: () -> Unit,
    onDemoLogin: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                BrandPanel(Modifier.weight(.92f).fillMaxHeight())
                Box(
                    modifier = Modifier.weight(1.08f).fillMaxHeight().padding(WindowInsets.safeDrawing.asPaddingValues()),
                    contentAlignment = Alignment.Center,
                ) {
                    LoginForm(state, onEmailChanged, onPasswordChanged, onTogglePassword, onSignIn, onDemoLogin, onForgotPassword)
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                BotanicalBackdrop(Modifier.fillMaxSize())
                Column(
                    modifier = Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues()).verticalScroll(rememberScrollState()).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(34.dp))
                    BrandPlate()
                    Spacer(Modifier.height(18.dp))
                    Text("Cultivamos mejores operaciones", color = Color.White.copy(alpha = .76f))
                    Spacer(Modifier.height(28.dp))
                    LoginForm(state, onEmailChanged, onPasswordChanged, onTogglePassword, onSignIn, onDemoLogin, onForgotPassword)
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
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
            BrandPlate()
            Column(Modifier.widthIn(max = 520.dp)) {
                Text("Cada planta cuenta.\nCada operación también.", style = MaterialTheme.typography.displayMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Text(
                    "Catálogo, ventas e inventario en un espacio diseñado para que tu equipo trabaje con claridad.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = .78f),
                )
            }
            StatusPill(
                text = "Operación conectada",
                containerColor = Color.White.copy(alpha = .12f),
                contentColor = Color.White,
            )
        }
    }
}

@Composable
private fun BrandPlate() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = Color.White.copy(alpha = .96f),
        shadowElevation = 8.dp,
    ) {
        Box(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            DulcineaWordmark()
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
    onDemoLogin: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    val working = state.status == AuthStatus.WORKING
    PremiumCard(modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
        Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp)) {
            StatusPill(if (state.remoteConfigured) "Supabase conectado" else "Configuración requerida")
            Spacer(Modifier.height(20.dp))
            Text("Bienvenido de nuevo", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Ingresa con tu cuenta de trabajo", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(26.dp))
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChanged,
                label = { Text("Correo electrónico") },
                leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                singleLine = true,
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            )
            Spacer(Modifier.height(14.dp))
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
                shape = MaterialTheme.shapes.large,
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
                shape = MaterialTheme.shapes.large,
            ) {
                if (working) CircularProgressIndicator(modifier = Modifier.width(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Iniciar sesión", fontWeight = FontWeight.Bold)
            }
            if (state.demoAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 18.dp)) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text("  o  ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    HorizontalDivider(Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = onDemoLogin,
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text("Explorar demostración", fontWeight = FontWeight.SemiBold)
                }
            }
            if (!state.remoteConfigured) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Configura SUPABASE_URL y SUPABASE_PUBLISHABLE_KEY para habilitar cuentas reales.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
