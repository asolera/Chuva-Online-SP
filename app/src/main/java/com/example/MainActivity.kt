package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chuvaradar.ui.RadarMainScreen
import com.example.chuvaradar.ui.RadarViewModel
import com.example.ui.theme.ChuvaRadarTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ChuvaRadarTheme(darkTheme = true) {
                // Solicita permissão de notificações em tempo de execução no Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val notifPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { /* resultado avaliado silenciosamente */ }

                    LaunchedEffect(Unit) {
                        try {
                            kotlinx.coroutines.delay(600)
                            val hasPermission = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED

                            if (!hasPermission) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Aviso ao solicitar notificação: ${e.message}")
                        }
                    }
                }

                val radarViewModel: RadarViewModel = viewModel()
                RadarMainScreen(
                    viewModel = radarViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
