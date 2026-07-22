package com.yhdista.dosetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.yhdista.dosetracker.ui.app.DoseTrackerAppMain
import com.yhdista.dosetracker.ui.theme.DoseTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val doseId = intent.getLongExtra("doseId", -1L).takeIf { it != -1L }
        setContent {
            DoseTrackerTheme {
                DoseTrackerAppMain(initialConfirmDoseId = doseId)
            }
        }
    }
}
