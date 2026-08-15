package com.intutec.viveroapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.intutec.viveroapp.navigation.ViveroApp
import com.intutec.viveroapp.ui.theme.ViveroAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ViveroAppTheme(dynamicColor = false) {
                ViveroApp()
            }
        }
    }
}
