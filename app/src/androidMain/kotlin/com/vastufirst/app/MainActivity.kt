package com.vastufirst.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.vastufirst.app.navigation.VastuNavHost
import com.vastufirst.designsystem.foundation.LocalVastuHaptics
import com.vastufirst.designsystem.theme.VastuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15 (targetSdk 35) enforces edge-to-edge with no opt-out, so opt IN deliberately
        // and let every screen root handle its own insets (ui/common/ScreenRoot.kt).
        //
        // Both bars are forced to the LIGHT style — i.e. dark icons. VastuTheme has a single
        // light palette (paper #F8F6F0) and no dark variant, so the default `auto` style would
        // follow the *system* theme and paint white icons on our cream background whenever the
        // phone is in dark mode: an invisible clock, battery and signal bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            // Provide the app-wide haptics once at the root; every clickableTap and the North dial
            // read it from LocalVastuHaptics (VastuHaptics.None off-device keeps renders silent).
            CompositionLocalProvider(LocalVastuHaptics provides rememberAndroidVastuHaptics()) {
                VastuTheme {
                    VastuNavHost()
                }
            }
        }
    }
}
