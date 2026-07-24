package com.vastufirst.app.render

import android.app.Application
import androidx.compose.runtime.Composable
import com.vastufirst.app.ui.marknorth.MarkNorthContent
import com.vastufirst.app.ui.report.ReportContent
import com.vastufirst.app.ui.score.ScoreContent
import com.vastufirst.shared.Intent
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
        MarkNorthContent(rooms = rooms, north = north, analysis = analysis, onNorthChange = {}, onRead = {}, onBack = {})
    }

    @Test
    fun score() = render("score") {
        ScoreContent(rooms = rooms, north = north, intent = intent, analysis = analysis, onUnlock = {}, onFix = {})
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
}
