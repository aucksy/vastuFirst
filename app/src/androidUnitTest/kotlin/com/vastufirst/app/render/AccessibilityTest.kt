package com.vastufirst.app.render

import android.app.Application
import androidx.compose.runtime.Composable
import com.vastufirst.app.ui.addhome.AddHomeScreen
import com.vastufirst.app.ui.scan.ScanConsentScreen
import com.vastufirst.app.ui.scan.ScanScreen
import com.vastufirst.app.ui.scan.ScanUiState
import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.app.ui.grid.GuidedGridContent
import com.vastufirst.app.ui.home.HomeContent
import com.vastufirst.app.ui.home.RenameDialogContent
import com.vastufirst.app.ui.legal.LegalScreen
import com.vastufirst.app.ui.marknorth.MarkNorthContent
import com.vastufirst.app.ui.newplan.SamplePlans
import com.vastufirst.app.ui.report.ReportContent
import com.vastufirst.app.ui.score.ScoreContent
import com.vastufirst.app.ui.settings.SettingsContent
import com.vastufirst.app.ui.unlock.UnlockScreen
import com.vastufirst.app.ui.welcome.WelcomeContent
import com.vastufirst.shared.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The accessibility pass over every screen (UI-POLISH.md §6.6) — Google's ATF (contrast, touch
 * targets, missing/duplicate labels, traversal) run headless in the same JVM render, ratcheted like
 * L1 (never a hard gate). One config per screen (baseline); see A11yHarness for why. This never fails
 * the build itself — the ratchet script scripts/check-a11y-manifest.mjs decides.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class AccessibilityTest {

    private val sample = SamplePlans.all.first()

    @Test
    fun accessibility() {
        val screens: List<Pair<String, @Composable () -> Unit>> = listOf(
            "welcome" to { WelcomeContent(intent = Intent.BUILDING, onIntentChange = {}, onContinue = {}) },
            "home" to { HomeContent(RenderFixtures.savedPlans, {}, {}, {}, { _, _ -> }, RenderFixtures.FIXED_NOW) },
            "home-empty" to { HomeContent(emptyList(), {}, {}, {}, { _, _ -> }, RenderFixtures.FIXED_NOW) },
            "home-rename" to { RenameDialogContent(currentName = "Compact 2BHK flat", onCancel = {}, onSave = {}) },
            "settings" to { SettingsContent(onLegal = {}, onBack = {}, onDeleteAll = {}) },
            "legal" to { LegalScreen(onBack = {}) },
            "unlock" to { UnlockScreen(onUnlocked = {}) },
            "addhome" to { AddHomeScreen(onDrawGrid = {}, onScan = {}, onSample = {}) },
            "scan-idle" to {
                ScanScreen(ScanUiState.Idle, {}, {}, {}, {}, { _, _ -> }, {}, {})
            },
            "scan-assisted" to {
                ScanScreen(
                    ScanUiState.Done(ScanMapper.map(RecordedScans.load(RecordedScans.DENSE)!!.reply)),
                    {}, {}, {}, {}, { _, _ -> }, {}, {},
                )
            },
            // The privacy gate carries more prose than any other screen, and the contrast trap this
            // check already caught once on the scan screen lives in exactly that kind of copy.
            "scan-consent" to { ScanConsentScreen(onAgree = {}, onDrawInstead = {}, onBack = {}) },
            "editor" to { GuidedGridContent(sample.rooms, sample.door, {}, {}, {}) },
            "editor-empty" to { GuidedGridContent(emptyList(), null, {}, {}, {}) },
            // ⭐ The selected-room panel had never reached this pass, because it appears only once a
            // room is selected and nothing here could select one. It carries the remove/done buttons,
            // the move arrows, the size steppers and now the room-type control — the densest cluster
            // of touch targets in the app, and the one the older users this audience contains rely on.
            "editor-selected" to {
                GuidedGridContent(
                    sample.rooms, sample.door, {}, {}, {},
                    startSelectedId = sample.rooms.first().id,
                )
            },
            "marknorth" to {
                MarkNorthContent(RenderFixtures.sampleRooms, RenderFixtures.sampleNorth, RenderFixtures.sampleAnalysis, {}, {}, {})
            },
            "score" to {
                ScoreContent(RenderFixtures.sampleRooms, RenderFixtures.sampleNorth, RenderFixtures.sampleIntent, RenderFixtures.sampleAnalysis, {}, {})
            },
            "report" to { ReportContent(RenderFixtures.sampleAnalysis, Intent.BUILDING) },
        )
        screens.forEach { (name, content) -> writeA11yManifest(name, content) }
    }
}
