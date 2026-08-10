package com.vastufirst.app.render

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vastufirst.app.ui.common.screenRoot
import com.vastufirst.app.ui.details.MoreDetailsContent
import com.vastufirst.app.ui.details.SiteAnswers
import com.vastufirst.app.ui.details.SiteItem
import com.vastufirst.app.ui.marknorth.MarkNorthContent
import com.vastufirst.app.ui.report.DisputesSection
import com.vastufirst.app.ui.report.ReadingProgress
import com.vastufirst.app.ui.report.ReportContent
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Zone
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.graphics.asImageBitmap
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
        )
    }



    /**
     * ⭐ North set on the user's OWN scanned plan rather than on our redrawing of it (owner,
     * 6 Aug 2026). The dial is unchanged — same ring, same wedges, same drag — and only what sits
     * inside the plan square differs, which is exactly what a picture can check and nothing else
     * can: that the photo lands inside the square, that the wedge tints and the North marker still
     * read over it, and that our room rectangles have stood down rather than being drawn on top of
     * somebody's actual home.
     */
    @Test
    fun markNorth_onPhoto() = render("marknorth-photo") {
        MarkNorthContent(
            rooms = rooms, north = north, analysis = analysis, onNorthChange = {}, onRead = {}, onBack = {},
            planImage = android.graphics.Bitmap
                .createBitmap(1399, 1389, android.graphics.Bitmap.Config.ARGB_8888)
                .apply { eraseColor(android.graphics.Color.rgb(0xEF, 0xE9, 0xDA)) }
                .asImageBitmap(),
        )
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

    /**
     * ⭐ The three states the FREE SCORE screen used to own, now owned by the report.
     *
     * ⚠ These are not decoration. Each replaced a forever spinner or a dead end, and the screen that
     * learned them was deleted on 10 Aug 2026 when North started leading straight here. A recovery
     * card nobody has photographed is a recovery card nobody has checked still appears.
     */
    @Test
    fun report_with_details() = render("report-covered") {
        ReportContent(
            analysis = analysis, intent = Intent.BUILDING, rooms = rooms, north = north,
            siteAnswers = SiteAnswers(declined = SiteItem.entries.toSet()),
        )
    }

    @Test
    fun report_insufficient() = render("report-insufficient") {
        ReportContent(analysis = RenderFixtures.insufficientAnalysis, intent = Intent.BUILDING, rooms = rooms, north = north)
    }

    @Test
    fun report_loading() = render("report-loading") {
        ReportContent(analysis = null, intent = Intent.BUILDING, rooms = rooms, north = north)
    }

    /**
     * ⭐ B1's recovery card: rooms survived a process kill but the intent answer did not — the state
     * that used to be an eternal "Reading your home…" spinner. Renders the guidance card with the
     * "answer the first question" way out, so the trap's replacement is a screen someone has seen.
     */
    /**
     * ⭐ The beat between "read my home" and the report — a whole screen a reader now sees on every
     * single reading, and one no golden would otherwise contain, because the report's own goldens
     * all render with the animation switched off so they photograph the report rather than this.
     *
     * ⚠ Rendered with a duration of ZERO. The bar draws its first frame and settles, which is the
     * frame worth checking: the words, the track, and that nothing is clipped at 200 % font on a
     * 320 dp phone. What a picture cannot check is that it moves.
     */
    @Test
    fun reading_progress() = render("reading") { ReadingProgress(durationMillis = 0L) }

    @Test
    fun report_lost_intent() = render("report-lost-intent") {
        ReportContent(analysis = null, intent = null, rooms = rooms, north = north)
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
     * ⭐⭐ THE FREE REPORT — the screen most readers will actually see, and the one that has to sell.
     *
     * Unpaid, the entrance / kitchen / toilets read in full and every other finding shows its room,
     * its zone and its verdict with the reasoning locked ([FreeTier]). Two things to look for in this
     * picture: no locked row may hide WHICH room it is (that would be the bait Product PRD §6.4
     * forbids), and the sticky pay bar must not cover the last control.
     */
    @Test
    fun report_free() = render("report-free") {
        ReportContent(analysis = analysis, intent = Intent.BUYING, unlocked = false)
    }

    /**
     * ⭐ THE ROOM LIST WITH EVERY ROW OPEN — what replaced the three chapter goldens (10 Aug 2026).
     *
     * `report-right` and `report-more` photographed two chapters that no longer exist. What they
     * were really protecting was the report's DEPTH: that a room which reads well still explains
     * itself, and that a "not ideal" room is not silently downgraded to a row with no reasoning
     * behind it. Opening every row photographs exactly that, on one picture instead of two — and it
     * is the only golden in which an opened room's provenance, zone meaning and remedies appear at
     * all, because a collapsed row's reasoning is not even in the semantics tree.
     *
     * ⚠ The content those chapters uniquely held is still covered: not-ideal rooms by
     * `report-notideal`, the schools-disagree payoff by `report-disputes`, and everything below the
     * fold by the bottom-half pair in LongScreenBottomScreenshotTest.
     */
    @Test
    fun report_rooms_all_open() = render("report-open") {
        ReportContent(analysis = analysis, intent = Intent.BUILDING, expandAll = true)
    }

    /**
     * ⭐⭐ BUYING — the branch that changed in v0.6.6, and the one the owner ranked fourth and fifth.
     *
     * It used to be drawn exactly like BUILDING: "✦ Change the layout — free now" on every problem,
     * and an opening line promising that nothing was built yet. Someone choosing between two flats
     * cannot move a wall in either of them, so the layout blocks are gone and the whole document is
     * remedies. This picture is the proof — if a "change the layout" block is anywhere in it, the
     * fix did not land.
     */
    @Test
    fun report_buying() = render("report-buying") {
        ReportContent(analysis = analysis, intent = Intent.BUYING)
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

    /**
     * ⭐ THE PRAYER-ROOM RULING, ON SCREEN (1 August 2026).
     *
     * ⚠ The same viewport trap, one release later. Ruling W-12 for the modern North-East puts a
     * prayer room in front of the reader with a real verdict for the first time; on the bundled
     * sample that card sits far below the fold. A short home with no problems and no front door
     * lifts it to where a picture can catch it.
     *
     * ⚠ **What this golden does NOT reach, said out loud:** the disputes section still falls off the
     * bottom, because the prayer room's own reason is long. That section has its own render below —
     * assuming this one covered it would be exactly the mistake this comment exists to prevent.
     */
    @Test
    fun report_prayer_room_ruling_and_the_dispute_card() = render("report-prayer") {
        ReportContent(analysis = RenderFixtures.prayerRoomAnalysis.copy(doorResult = null), intent = Intent.BUILDING)
    }

    /**
     * ⭐ "WHERE THE SCHOOLS DISAGREE", ON ITS OWN — because no whole-report golden can reach it.
     *
     * ⚠ Measured, not assumed: `report-prayer` was built to lift the lower sections into frame and
     * still stops inside the not-ideal card, because the prayer room's reason is long. This section
     * is the last block of a long document, so a viewport starting at the top will never contain it —
     * and it is the section carrying the product's central promise: we ruled on this question, and we
     * still show the reader both sides plus which one the number uses.
     *
     * Rendered through the report's own composable with the engine's own disputes, so it is the real
     * thing, laid out exactly as it is in the document.
     */
    @Test
    fun report_disputes_section() = render("report-disputes") {
        Column(
            Modifier.screenRoot(VastuTheme.colors.paper)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VastuTheme.spacing.s6),
        ) {
            DisputesSection(RenderFixtures.prayerRoomAnalysis.disputes)
        }
    }
}
