package com.intutec.viveroapp.feature.splash.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.intutec.viveroapp.core.designsystem.BotanicalBackdrop
import com.intutec.viveroapp.core.designsystem.DulcineaLogo
import com.intutec.viveroapp.core.designsystem.StatusPill

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BotanicalBackdrop(Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(
                modifier = Modifier.size(width = 220.dp, height = 252.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 22.dp,
            ) {
                DulcineaLogo(Modifier.padding(22.dp))
            }
            Spacer(Modifier.height(22.dp))
            StatusPill(
                text = "Operación en armonía",
                containerColor = Color.White.copy(alpha = .12f),
                contentColor = Color.White,
            )
            Spacer(Modifier.height(14.dp))
            Text("Preparando tu espacio de trabajo", color = Color.White.copy(alpha = .78f))
            Spacer(Modifier.height(26.dp))
            CircularProgressIndicator(color = Color.White, trackColor = Color.White.copy(alpha = .18f))
        }
    }
}
