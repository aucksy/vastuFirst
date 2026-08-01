package com.vastufirst.app.render

import android.app.Application
import androidx.compose.runtime.Composable
import com.vastufirst.app.ui.details.MoreDetailsContent
import com.vastufirst.app.ui.details.SiteAnswers
import com.vastufirst.app.ui.details.SiteItem
import com.vastufirst.app.ui.marknorth.CompassQuality
import com.vastufirst.app.ui.marknorth.CompassState
import com.vastufirst.app.ui.marknorth.MarkNorthContent
import com.vastufirst.app.ui.report.ReportContent
import com.vastufirst.app.ui.score.ScoreContent
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Zone
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The score-driven screens — Mark North, Score, Report — rendered + measured across the §6.4 matrix
 * for the FIRST time (UI-POLISH §6). Each is driven through its new stateless `…Content(state,
 * callbacks)` seam with a fixture built from the bundled sample home, converted with the app's own
 * grid→Plan function and scored by the REAL engine (RenderFixtures) — so the zone map, ranked
 * defects and remedies are genuine engine output, not a hand-faked stand-in.
 *
 * The states that most often ship broken get their own render:
 *   - `score-insufficient` — the "let's finish your plan" guidance card (never a bare red 0).
 *   - `score-loading`      — the calm loading line while the engine computes.
 *   - `report-living`      — the remedy-led branch (BUILDING/BUYING lead with layout instead).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class ScoreDrivenScreensScreenshotTest {

    private val rooms = RenderFixtures.sampleRooms
    private val north = RenderFixtures.sampleNorth
    private val intent = RenderFixtures.sampleIntent
    private val analysis = RenderFixtures.sampleAnalysis

    private fun render(screen: String, content: @Composable () -> Unit) {
        captureAcrossMatrix(screen, content)
        writeManifestAcrossMatrix(screen, content)
    }

    @Test
    fun markNorth() = render("marknorth") {
        MarkNorthContent(
            rooms = rooms, north = north, analysis = analysis, onNorthChange = {}, onRead = {}, onBack = {},
            // A phone WITH a compass, helper closed — i.e. what almost every user sees on arrival: the
            // one-line offer above the dial and the double-check card above the button.
            compass = CompassState(supported = true),
        )
    }

    /**
     * ⭐ The compass helper OPEN — three lines of instruction, a live reading and two buttons, on a
     * screen that already carries a dial, a slider, four chips and a stepper. It is the tallest state
     * Mark North has ever had, and no screenshot can press the button that opens it, so it gets its
     * own golden or it ships unseen (CLAUDE.md §2b).
     */
    @Test
    fun markNorth_compass() = render("marknorth-compass") {
        MarkNorthContent(
            rooms = rooms, north = north, analysis = analysis, onNorthChange = {}, onRead = {}, onBack = {},
            compass = CompassState(supported = true, live = true, headingDeg = 42f),
        )
    }

    /** The same helper with the phone asking to be calibrated — the warning has to be readable too. */
    @Test
    fun markNorth_compass_calibrating() = render("marknorth-compass-calibrating") {
        MarkNorthContent(
            rooms = rooms, north = north, analysis = analysis, onNorthChange = {}, onRead = {}, onBack = {},
            compass = CompassState(
                supported = true, live = true, headingDeg = 187f,
                quality = CompassQuality.NEEDS_CALIBRATION,
            ),
        )
    }

    @Test
    fun score() = render("score") {
        ScoreContent(rooms = rooms, north = north, intent = intent, analysis = analysis, onUnlock = {}, onFix = {})
    }

    /**
     * ⭐ The optional "a few more things" step, in both the states that matter: nothing answered (the
     * state everyone arrives in) and a couple answered. Four cards of eight direction chips each is a
     * lot of wrapping, and 200 % font on a 320 dp phone is where that shatters.
     */
    @Test
    fun more_details_empty() = render("more-details") {
        MoreDetailsContent(answers = SiteAnswers(), onAnswer = { _, _ -> }, onDecline = {}, onDone = {}, onBack = {})
    }

    @Test
    fun more_details_answered() = render("more-details-answered") {
        MoreDetailsContent(
            answers = SiteAnswers(
                answers = mapOf(SiteItem.OVERHEAD_TANK to Zone.SW, SiteItem.HEAVY_TREE to Zone.NE),
                declined = setOf(SiteItem.ROAD),
            ),
            onAnswer = { _, _ -> }, onDecline = {}, onDone = {}, onBack = {},
        )
    }

    /** The score once the extras are in — the "what this covers" line has to change with them. */
    @Test
    fun score_with_details() = render("score-covered") {
        ScoreContent(
            rooms = rooms, north = north, intent = intent, analysis = analysis, onUnlock = {}, onFix = {},
            siteAnswers = SiteAnswers(declined = SiteItem.entries.toSet()),
        )
    }

    @Test
    fun score_insufficient() = render("score-insufficient") {
        ScoreContent(rooms = rooms, north = north, intent = intent, analysis = RenderFixtures.insufficientAnalysis, onUnlock = {}, onFix = {})
    }

    @Test
    fun score_loading() = render("score-loading") {
        ScoreContent(rooms = rooms, north = north, intent = intent, analysis = null, onUnlock = {}, onFix = {})
    }

    @Test
    fun report_building() = render("report") {
        ReportContent(analysis = analysis, intent = Intent.BUILDING)
    }

    @Test
    fun report_living() = render("report-living") {
        ReportContent(analysis = analysis, intent = Intent.LIVING)
    }

    /**
     * ⭐ THE TWO SECTIONS NO PICTURE HAS EVER SHOWN.
     *
     * ⚠ A golden is a viewport, not a whole document. On the bundled sample the report's "Not ideal"
     * and "Already right" sections sit far below the fold, so they have never appeared in a
     * screenshot — and "rooms rated not ideal appear nowhere" is the very defect this release exists
     * to fix. Rendering a home with NO problems lifts both sections to the top of the screen, where
     * the harness can capture them and the geometry gate can measure them.
     */
    @Test
    fun report_no_problems() = render("report-clean") {
        ReportContent(analysis = RenderFixtures.cleanAnalysis, intent = Intent.BUILDING)
    }

    /**
     * ⭐ The same home with **no front door flagged** — which lifts the not-ideal card and the
     * already-right cards clear of the fold, so their bodies can be read rather than inferred.
     *
     * Not a contrivance: `doorResult` is genuinely null whenever no main entrance is marked, the
     * report already branches on it, and that branch had never been rendered either.
     */
    @Test
    fun report_not_ideal_and_already_right() = render("report-notideal") {
        ReportContent(analysis = RenderFixtures.cleanAnalysis.copy(doorResult = null), intent = Intent.BUILDING)
    }
}
