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
import com.vastufirst.app.ui.home.DiscardDraftDialogContent
import com.vastufirst.app.ui.home.HomeContent
import com.vastufirst.app.ui.home.RemoveUnreadableDialogContent
import com.vastufirst.app.ui.home.RenameDialogContent
import com.vastufirst.app.ui.legal.LegalScreen
import com.vastufirst.app.ui.legal.PrivacyScreen
import com.vastufirst.app.ui.marknorth.MarkNorthContent
import com.vastufirst.app.ui.newplan.SamplePlans
import com.vastufirst.app.ui.report.ReportContent
import com.vastufirst.app.ui.settings.SettingsContent
import com.vastufirst.app.billing.BillingState
import com.vastufirst.app.ui.unlock.UnlockContent
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
            // ⭐ The unfinished-home rows (v0.6.6). Each carries a tappable row AND a discard button
            // inside it — the same two-targets-in-one-row shape whose contrast and labelling this
            // pass has already caught once, on the pencil beside a finished home.
            "home-unfinished" to {
                HomeContent(
                    plans = RenderFixtures.savedPlans, onAddHome = {}, onOpenPlan = {}, onSettings = {},
                    onRename = { _, _ -> }, now = RenderFixtures.FIXED_NOW,
                    drafts = RenderFixtures.savedDrafts, onOpenDraft = {}, onDiscardDraft = {},
                )
            },
            "home-rename" to { RenameDialogContent(currentName = "Compact 2BHK flat", onCancel = {}, onSave = {}) },
            // ⭐ The same box refusing a name another home already has. Its own entry because the
            // refusal reddens the label AND adds a sentence — new text, new colour, and a Save
            // button that is now switched off, none of which the ordinary state above can show.
            "home-rename-taken" to {
                RenameDialogContent(
                    currentName = "Dwarka flat", onCancel = {}, onSave = {},
                    otherNames = listOf("Home 1", "Dwarka flat"),
                )
            },
            "home-discard" to { DiscardDraftDialogContent(roomCount = 5, onCancel = {}, onDiscard = {}) },
            // ⭐ The unreadable-home card's per-home remove (audit B7): a ✕ tap target beside a
            // passive "Remove" hint — the same two-targets shape as the rows above, on a card
            // whose warning accent has to survive the contrast pass too.
            "home-unreadable" to {
                HomeContent(
                    plans = RenderFixtures.savedPlans, onAddHome = {}, onOpenPlan = {}, onSettings = {},
                    onRename = { _, _ -> }, now = RenderFixtures.FIXED_NOW,
                    unreadable = listOf(RenderFixtures.unreadableHome),
                )
            },
            "home-remove" to { RemoveUnreadableDialogContent(name = "Home 3", onCancel = {}, onRemove = {}) },
            "settings" to { SettingsContent(onLegal = {}, onBack = {}, onDeleteAll = {}) },
            "legal" to { LegalScreen(onBack = {}) },
            // UnlockContent, not UnlockScreen: the screen now resolves the billing seam from Koin,
            // and the harness deliberately boots a plain Application with no DI.
            "unlock" to { UnlockContent(state = BillingState()) },
            "privacy" to { PrivacyScreen(onBack = {}) },
            "addhome" to { AddHomeScreen(onDrawGrid = {}, onScan = {}, onSample = {}) },
            "scan-idle" to {
                ScanScreen(ScanUiState.Idle, {}, {}, {}, {}, { _, _ -> }, {}, {})
            },
            "scan-assisted" to {
                // ⛔ No reader picker any more (owner, 11 Aug 2026): nothing on a customer's path
                // names a model. `readBy` is still carried on the state and still drawn by nothing.
                // Choosing a reader lives in Settings, which this pass checks under "settings".
                ScanScreen(
                    ScanUiState.Done(
                        // ⚠ real-unsized, not real-dense: real-dense PLACES since the lift rule
                        // went, so this row was drawing the Placed screen under an Assisted name.
                        ScanMapper.map(RecordedScans.load(RecordedScans.UNSIZED)!!.reply),
                        readBy = "openai/gpt-5.6-luna",
                    ),
                    {}, {}, {}, {}, { _, _ -> }, {}, {},
                )
            },
            // The privacy gate carries more prose than any other screen, and the contrast trap this
            // check already caught once on the scan screen lives in exactly that kind of copy.
            "scan-consent" to { ScanConsentScreen(onAgree = {}, onDrawInstead = {}, onBack = {}) },
            // ⭐ The on-photo review: a canvas with a semantic description plus a tappable row per
            // room — the same two shapes (described image, actionable list row) this pass guards
            // everywhere else. Driven by the real recorded clean read through the real mapper.
            // ⭐ With the result and direction pills on every row (11 Aug 2026) — they carry colour,
            // so they face this pass's contrast check like everything else the app tints.
            "scan-review" to {
                com.vastufirst.app.ui.scan.ScanReviewContent(
                    image = null,
                    rooms = RenderFixtures.scannedScanRooms,
                    readings = RenderFixtures.scannedReadings,
                    startSelected = 1,
                )
            },
            // ⭐ Marking the front door on the photo — a described image that is also the screen's
            // only control, plus a line of state under it. The description has to say where the
            // door IS, because a screen reader cannot see a marker on a photograph.
            "scan-door" to {
                com.vastufirst.app.ui.scan.ScanDoorContent(
                    image = null,
                    rooms = (ScanMapper.map(RecordedScans.load(RecordedScans.CLEAN)!!.reply)
                        as com.vastufirst.shared.scan.ScanOutcome.Placed).rooms,
                    door = null,
                )
            },
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
            "report-landing" to {
                // Where the flow lands since 10 Aug 2026, in place of the free score screen.
                ReportContent(
                    analysis = RenderFixtures.sampleAnalysis, intent = RenderFixtures.sampleIntent,
                    rooms = RenderFixtures.sampleRooms, north = RenderFixtures.sampleNorth,
                )
            },
            "report" to { ReportContent(RenderFixtures.sampleAnalysis, Intent.BUILDING) },
            // ⭐ THE MONEY SCREEN, which had never been through this pass. `unlock` was here; the
            // report as a NON-PAYING reader sees it was not — and that is the version with the
            // locked rows, the sticky pay bar and the tap targets a customer meets before deciding
            // whether to spend ₹699. Contrast and touch size on a greyed, locked row are exactly
            // what this pass is for, and exactly where nobody had looked.
            "report-free" to {
                ReportContent(RenderFixtures.sampleAnalysis, Intent.BUYING, unlocked = false)
            },
        )
        screens.forEach { (name, content) -> writeA11yManifest(name, content) }
    }
}
