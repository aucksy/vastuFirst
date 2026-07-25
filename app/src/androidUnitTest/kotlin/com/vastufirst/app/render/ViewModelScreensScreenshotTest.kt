package com.vastufirst.app.render

import android.app.Application
import androidx.compose.runtime.Composable
import com.vastufirst.app.ui.home.HomeContent
import com.vastufirst.app.ui.home.RenameDialogContent
import com.vastufirst.app.ui.settings.SettingsContent
import com.vastufirst.app.ui.welcome.WelcomeContent
import com.vastufirst.shared.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The first batch of ViewModel-backed screens — Welcome, Home, Settings — rendered + measured
 * across the §6.4 matrix for the FIRST time (UI-POLISH §6). Each is driven through its new stateless
 * `…Content(state, callbacks)` seam with a fixture and no-op callbacks; the ViewModel is never
 * constructed here (it needs DI + a Main dispatcher, fragile headless — stateless-content template).
 *
 * `home-empty` is rendered as its own screen because the empty state is the one that most often ships
 * broken (a list container that measures to nothing with no children), and it is what a first-time
 * user lands on.
 *
 * NATIVE graphics mode is mandatory (LEGACY renders a blank canvas); a PLAIN Application skips the
 * app's startKoin() so a second test in the shared JVM does not throw KoinApplicationAlreadyStarted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class ViewModelScreensScreenshotTest {

    @Test
    fun welcome() {
        val content: @Composable () -> Unit = {
            WelcomeContent(intent = Intent.BUILDING, onIntentChange = {}, onContinue = {})
        }
        captureAcrossMatrix("welcome", content)
        writeManifestAcrossMatrix("welcome", content)
    }

    @Test
    fun home() {
        val content: @Composable () -> Unit = {
            HomeContent(
                plans = RenderFixtures.savedPlans, onAddHome = {}, onOpenPlan = {}, onSettings = {},
                onRename = { _, _ -> }, now = RenderFixtures.FIXED_NOW,
            )
        }
        captureAcrossMatrix("home", content)
        writeManifestAcrossMatrix("home", content)
    }

    @Test
    fun home_empty() {
        val content: @Composable () -> Unit = {
            HomeContent(
                plans = emptyList(), onAddHome = {}, onOpenPlan = {}, onSettings = {},
                onRename = { _, _ -> }, now = RenderFixtures.FIXED_NOW,
            )
        }
        captureAcrossMatrix("home-empty", content)
        writeManifestAcrossMatrix("home-empty", content)
    }

    @Test
    fun home_rename() {
        // The rename box (B12) rendered as its own screen so it is actually SEEN before shipping
        // (UI-POLISH §6) — pre-filled with a home's current name.
        val content: @Composable () -> Unit = {
            RenameDialogContent(currentName = "Compact 2BHK flat", onCancel = {}, onSave = {})
        }
        captureAcrossMatrix("home-rename", content)
        writeManifestAcrossMatrix("home-rename", content)
    }

    @Test
    fun settings() {
        val content: @Composable () -> Unit = {
            SettingsContent(onLegal = {}, onBack = {}, onDeleteAll = {})
        }
        captureAcrossMatrix("settings", content)
        writeManifestAcrossMatrix("settings", content)
    }
}
