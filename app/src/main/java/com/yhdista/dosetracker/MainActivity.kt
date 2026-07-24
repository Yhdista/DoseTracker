package com.yhdista.dosetracker

import android.content.pm.ApplicationInfo
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
        val isDebugBuild = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        setContent {
            DoseTrackerTheme {
                DoseTrackerAppMain(
                    initialConfirmDoseId = doseId,
                    isDebugBuild = isDebugBuild,
                )
            }
        }
    }
}
