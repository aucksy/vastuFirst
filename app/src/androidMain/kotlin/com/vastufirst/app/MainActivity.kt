package com.vastufirst.app

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.vastufirst.app.navigation.VastuNavHost
import com.vastufirst.app.ui.common.deviceDecimalMark
import com.vastufirst.designsystem.components.LocalDecimalMark
import com.vastufirst.designsystem.foundation.LocalVastuHaptics
import com.vastufirst.designsystem.theme.VastuTheme

class MainActivity : ComponentActivity() {
    /** Set by the launch decider once it knows which screen comes first; the splash lets go then. */
    private var firstScreenDecided = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // ⭐ THE OPENING SCREEN. Must run before super.onCreate (the library's contract): it swaps
        // the manifest's Theme.VastuFirst.Starting — the splash — for Theme.VastuFirst, and hands
        // back the splash so it can be held. On Android 12+ that is the system splash; on 8 to 11
        // the library draws the identical picture itself, so the launch looks the same everywhere.
        val splash = installSplashScreen()
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
        // Hold the splash until the launch decider (VastuNav's LAUNCH route) has read the database
        // and chosen the first screen, so the first frame anyone sees IS that screen — not a bare
        // cream window, and not the brand mark drawn a second time at a different size for the one
        // frame the read used to take.
        //
        // Only on a cold start. When Android is restoring the activity after killing the process,
        // the navigation stack comes back from saved state and the launch route never runs, so
        // nothing would ever set the flag; the app must simply appear.
        //
        // And capped: a splash that waits on a read is a splash that can wedge the app if the read
        // ever stalls, and that failure would be invisible to every test. After the cap the splash
        // lets go regardless and the launch route's own fallback picture takes over.
        if (savedInstanceState == null) {
            val shownAt = SystemClock.uptimeMillis()
            splash.setKeepOnScreenCondition {
                !firstScreenDecided && SystemClock.uptimeMillis() - shownAt < SPLASH_HOLD_CAP_MILLIS
            }
        }
        setContent {
            // Provide the app-wide haptics once at the root; every clickableTap and the North dial
            // read it from LocalVastuHaptics (VastuHaptics.None off-device keeps renders silent).
            //
            // The decimal mark is provided here too, and ONLY here: the score is written "4.7" in
            // this reader's own language, and this is the single place that asks the phone which
            // mark that is. Every screen and the screenshot harness read it from LocalDecimalMark.
            CompositionLocalProvider(
                LocalVastuHaptics provides rememberAndroidVastuHaptics(),
                LocalDecimalMark provides deviceDecimalMark(),
            ) {
                VastuTheme {
                    VastuNavHost(onFirstScreenDecided = { firstScreenDecided = true })
                }
            }
        }
    }

    private companion object {
        /** The longest the splash may wait for the launch decider before letting go anyway. */
        const val SPLASH_HOLD_CAP_MILLIS = 1_500L
    }
}
