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

    /**
     * ⭐ A saved home this build cannot read. Until now one such row threw inside the list's flow and
     * emptied the whole screen; the other homes now load and the missing one is SAID rather than
     * silently skipped — because a home that vanishes without a word looks exactly like a home the
     * app deleted by itself. No screenshot could ever reach this state by tapping, so it gets one.
     */
    @Test
    fun home_unreadable() {
        val content: @Composable () -> Unit = {
            HomeContent(
                plans = RenderFixtures.savedPlans, onAddHome = {}, onOpenPlan = {}, onSettings = {},
                onRename = { _, _ -> }, now = RenderFixtures.FIXED_NOW, unreadable = 1,
            )
        }
        captureAcrossMatrix("home-unreadable", content)
        writeManifestAcrossMatrix("home-unreadable", content)
    }

    /**
     * ⭐ "WE CHANGED A RULE, AND HERE IS WHAT IT DID TO YOUR NUMBER."
     *
     * The card shown when a Vastu ruling re-scores a home that was already saved — the thing that
     * stops a score moving behind somebody's back. It carries the most text of anything on this
     * screen (a heading, a paragraph of explanation, one line per home and a button), so it is
     * exactly the kind of block that has pushed its own button off the bottom of a 320 dp phone at
     * 200 % font before. No screenshot could reach this state by tapping, so it gets its own.
     *
     * One home moved and one did not, on purpose: both lines are on record in one picture.
     */
    @Test
    fun home_score_changed() {
        val content: @Composable () -> Unit = {
            HomeContent(
                plans = RenderFixtures.savedPlans, onAddHome = {}, onOpenPlan = {}, onSettings = {},
                onRename = { _, _ -> }, now = RenderFixtures.FIXED_NOW,
                scoreChanges = RenderFixtures.scoreChangeNotice,
            )
        }
        captureAcrossMatrix("home-scorechange", content)
        writeManifestAcrossMatrix("home-scorechange", content)
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
