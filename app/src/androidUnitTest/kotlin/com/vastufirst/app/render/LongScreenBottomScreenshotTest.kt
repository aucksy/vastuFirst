package com.vastufirst.app.render

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.vastufirst.app.billing.BillingMode
import com.vastufirst.app.billing.BillingState
import com.vastufirst.app.ui.grid.GuidedGridContent
import com.vastufirst.app.ui.newplan.SamplePlans
import com.vastufirst.app.ui.report.ReportContent
import com.vastufirst.app.ui.report.TAG_PAY_CLEARANCE
import com.vastufirst.app.ui.report.TAG_ROOMS_END
import com.vastufirst.app.ui.unlock.UnlockContent
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Intent
import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.ScanMapper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐ THE BOTTOM HALF OF EVERY LONG DOCUMENT, FINALLY PHOTOGRAPHED (audit D2).
 *
 * A golden is a viewport, not a document. Every capture in [captureAcrossMatrix] starts at the top
 * of the screen, so the report's lower sections (the room list, the disputes payoff, the
 * disclaimer, the way out) and the unlock screen's feature list had never appeared in ANY
 * golden at ANY config — §6.7b's "producing them is not optional and neither is looking" could not
 * apply to the half of the page it had never seen. Fixture tricks (report-clean, report-prayer)
 * lift SOME lower sections into frame; this test does the general thing instead: render the real
 * screen in the real baseline window, SCROLL to its last element the way a reader would, and keep
 * that picture as a golden.
 *
 * Two configs per screen: the baseline, and 200 % font — the size where the bottom of a page is
 * historically where defects go to hide. The anchor string in each test is the screen's true last
 * element; if a screen's ending changes, the test fails loudly rather than quietly photographing
 * the wrong place.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class LongScreenBottomScreenshotTest {

    /** Scroll [anchor] (the screen's bottom-most element) into view, then photograph the window. */
    private fun captureBottom(
        screen: String,
        config: String,
        anchor: SemanticsMatcher,
        fontScale: Float,
        qualifiers: String = RenderMatrix.BASE,
        content: @Composable () -> Unit,
    ) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        runComposeUiTest {
            setContent {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density, fontScale),
                ) {
                    VastuTheme { content() }
                }
            }
            onNode(anchor).performScrollTo()
            onRoot().captureRoboImage(goldenPath(screen, config))
        }
    }

    private fun captureBottomPair(
        screen: String,
        anchor: SemanticsMatcher,
        content: @Composable () -> Unit,
    ) {
        // ⚠ Named, not positional: `qualifiers` now sits between the font scale and the content, so a
        // positional call would hand the composable to it and fail to compile.
        captureBottom(screen, "bottom", anchor, 1.0f, content = content)
        captureBottom(screen, "bottom_font2_0", anchor, 2.0f, content = content)
    }

    /** The paid document's ending: disputes, the disclaimer, and the only way out of the flow. */
    @Test
    fun report_bottom() = captureBottomPair("report", anchor = hasText("Done — see all my plans")) {
        ReportContent(analysis = RenderFixtures.sampleAnalysis, intent = Intent.BUILDING)
    }

    /**
     * ⭐⭐ THE ROOM LIST ITSELF — the screen's whole point, and photographed nowhere until this test.
     *
     * The list sits BETWEEN the top-of-page goldens and the bottom-of-page ones: every report golden
     * stops above it and every bottom golden starts below it. On the run that introduced the list it
     * therefore appeared in none of the eight configurations, on a change that was entirely about it.
     *
     * ⚠ Three configs, and each earns its place. The room row is the most shatter-prone thing in the
     * app — a circle, a name, a direction pill, a one-word verdict and an arrow on ONE line — and
     * §6.7b is explicit that a row can draw its ink wider than its box with every measurement green.
     * So: the baseline to see it as designed, 200 % font because that is where a row runs out of
     * width, and 320 dp because that is the narrowest phone we support.
     */
    @Test
    fun report_rooms() {
        val content: @Composable () -> Unit = {
            ReportContent(analysis = RenderFixtures.sampleAnalysis, intent = Intent.BUILDING)
        }
        captureBottom("report", "rooms", hasTestTag(TAG_ROOMS_END), 1.0f, content = content)
        captureBottom("report", "rooms_font2_0", hasTestTag(TAG_ROOMS_END), 2.0f, content = content)
        captureBottom("report", "rooms_w320", hasTestTag(TAG_ROOMS_END), 1.0f, "+w320dp-h711dp-port-xhdpi", content)
    }

    /**
     * ⭐⭐ THE ROOM LIST OF A SCANNED HOME — the ONE picture in which the plan's own printed names
     * appear, and therefore the only proof that the 11 Aug 2026 fix landed.
     *
     * The owner's report showed "Master" and "Bedroom 2" beside a photograph printing "MASTER
     * BEDROOM" and "BEDROOM 2" in as many letters, because the caption was dropped on the way into
     * the engine. It is carried through now — and every other report golden renders a hand-drawn
     * home, which has no printed captions at all, so not one of them could ever show the difference.
     *
     * ⚠ Two configs, and the second is not decoration. A printed caption is LONGER than our own name
     * for the same room ("ATTACHED TOILET 1" against "Toilet"), and a room row is the most
     * shatter-prone thing in this app — a circle, a name, a direction pill, a word and an arrow on
     * one line. 200 % font is where that runs out of width, and §6.7b is explicit that the ink can
     * overflow while every measurement stays green. So the long names get photographed at the size
     * that breaks rows.
     */
    @Test
    fun report_scanned_rooms() {
        val content: @Composable () -> Unit = {
            ReportContent(
                analysis = RenderFixtures.scannedAnalysis,
                intent = Intent.BUILDING,
                rooms = RenderFixtures.scannedRooms,
                cols = RenderFixtures.scannedCols,
                rows = RenderFixtures.scannedRows,
                planRooms = RenderFixtures.scannedPlanRooms,
            )
        }
        captureBottom("report-scanned", "rooms", hasTestTag(TAG_ROOMS_END), 1.0f, content = content)
        captureBottom("report-scanned", "rooms_font2_0", hasTestTag(TAG_ROOMS_END), 2.0f, content = content)
    }

    /** The remedies-only branch ends on the same elements but gets there through different cards. */
    @Test
    fun report_living_bottom() = captureBottomPair("report-living", anchor = hasText("Done — see all my plans")) {
        ReportContent(analysis = RenderFixtures.sampleAnalysis, intent = Intent.LIVING)
    }

    /**
     * ⭐⭐ THE FREE REPORT'S ENDING — the half of the screen most readers will actually see, and
     * until now the half NO photograph contained at any size.
     *
     * Every free-report golden starts at the top and stops just below the chapter chips. Everything
     * under them — the locked rows that name each room and hide only the reasoning, which IS the
     * free tier — plus the way out and the pay bar's edge, had never been rendered into an image.
     * Two things go unwatched without this picture. First, a locked row must still say WHICH room it
     * is; hiding that would be the bait Product PRD §6.4 forbids, and no gate can see it. Second,
     * the pay bar floats over this column, so whether the last control clears it is a question only
     * a picture can answer — it was answered wrongly once already.
     *
     * ⚠ Anchored on the clearance, NOT on the button. See [TAG_PAY_CLEARANCE] for why the obvious
     * anchor photographs a defect that is not there.
     */
    @Test
    fun report_free_bottom() = captureBottomPair("report-free", anchor = hasTestTag(TAG_PAY_CLEARANCE)) {
        ReportContent(
            analysis = RenderFixtures.sampleAnalysis,
            intent = Intent.BUYING,
            unlocked = false,
        )
    }


    /**
     * ⛔ REMOVED 10 Aug 2026 — "scan-review-door bottom". The review screen no longer scrolls as a
     * whole (owner: "The floor plan on this screen should not scroll upwards, only the list of rooms
     * should be scrollable"), so its buttons are pinned and always on screen: there is no bottom half
     * to scroll to, and `performScrollTo` on a node with no scrolling ancestor throws rather than
     * photographing anything. The matrix goldens for scan-review-door now contain those buttons at
     * every configuration, which is what this capture existed to guarantee.
     */

    /** The ₹699 screen's ending: the feature list under the buy button — the paid promise itself. */
    @Test
    fun unlock_paid_bottom() = captureBottomPair(
        "unlock-paid",
        anchor = hasText("Whether each rule is classical, traditional or modern — marked on every finding, with both sides where the schools disagree"),
    ) {
        UnlockContent(state = BillingState(mode = BillingMode.READY, price = "₹699.00"))
    }

    /**
     * ⭐⭐ THE STRETCH OF SCREEN THE ₹699 DECISION IS ACTUALLY MADE ON — and it was in no picture.
     *
     * Every free-report golden either stops just above the room list or starts just below it, so the
     * greyed, LOCKED room rows — the ones reading "Reasoning and remedies — in the full report" —
     * had never been drawn at any width or font size. That is precisely the part a non-paying reader
     * scrolls through while deciding whether to spend money, and the part where the product's own
     * rule (a locked row is not a blurred teaser — it still names the room, the zone and the verdict)
     * is either kept or broken. Nobody could tell which.
     */
    @Test
    fun report_free_rooms() {
        val content: @Composable () -> Unit = {
            ReportContent(
                analysis = RenderFixtures.sampleAnalysis,
                intent = Intent.BUYING,
                unlocked = false,
            )
        }
        captureBottom("report-free", "rooms", hasTestTag(TAG_ROOMS_END), 1.0f, content = content)
        captureBottom("report-free", "rooms_font2_0", hasTestTag(TAG_ROOMS_END), 2.0f, content = content)
        captureBottom(
            "report-free", "rooms_w320", hasTestTag(TAG_ROOMS_END), 1.0f,
            "+w320dp-h711dp-port-xhdpi", content,
        )
    }

    /**
     * ⭐⭐ THE SAME STRETCH WITH THE ROWS **OPEN** — where the locked rows now carry a control.
     *
     * ⚠ Added 17 Aug 2026 with the button itself, and the gap it closes is the one this file exists
     * for. `report-free-open` is a full-screen golden, so it starts at the top of the report — and
     * the room list is four sections down. The unlock button now sitting inside every locked row
     * would therefore have appeared in NO picture at ANY size, on the very release that added it,
     * which is "a golden is a viewport, not a document" landing on brand-new UI for the third time.
     *
     * The two sizes are the two that break buttons: 200 % font, where a label can outgrow its own
     * card, and 320 dp, where a full-width control inside an already-indented row has least room.
     */
    @Test
    fun report_free_open_rooms() {
        val content: @Composable () -> Unit = {
            ReportContent(
                analysis = RenderFixtures.sampleAnalysis,
                intent = Intent.BUYING,
                unlocked = false,
                expandAll = true,
            )
        }
        captureBottom("report-free-open", "rooms", hasTestTag(TAG_ROOMS_END), 1.0f, content = content)
        captureBottom("report-free-open", "rooms_font2_0", hasTestTag(TAG_ROOMS_END), 2.0f, content = content)
        captureBottom(
            "report-free-open", "rooms_w320", hasTestTag(TAG_ROOMS_END), 1.0f,
            "+w320dp-h711dp-port-xhdpi", content,
        )
    }

    /**
     * ⭐ THE DRAWING SCREEN'S ENDING — the half of it nobody had ever seen.
     *
     * The guided grid is the longest screen in the app: a title, the plan, the shape question, the
     * plot-size steppers, the room palette, the selected-room tools, and only then the button that
     * leaves. Every existing golden of it starts at the top, so at 200 % font — where this screen is
     * several windows tall — the controls a reader has to reach appeared in NO picture at ANY
     * configuration. The report and the unlock screen already had this capture; the screen where the
     * customer does the most work did not.
     *
     * Anchored on the button that leaves, because that is the screen's true last element and the one
     * whose reachability is the whole question.
     */
    @Test
    fun editor_bottom() = captureBottomPair("editor", anchor = hasTestTag("editor.next")) {
        val sample = SamplePlans.all.first()
        GuidedGridContent(
            rooms = sample.rooms,
            door = sample.door,
            onRoomsChange = {},
            onDoorChange = {},
            onNext = {},
        )
    }

    /**
     * The same ending from the EMPTY grid — the state every hand-drawn home actually starts in, and
     * the one where the palette a reader must reach sits furthest down the page.
     */
    @Test
    fun editor_empty_bottom() = captureBottomPair("editor-empty", anchor = hasTestTag("editor.next")) {
        GuidedGridContent(
            rooms = emptyList(),
            door = null,
            onRoomsChange = {},
            onDoorChange = {},
            onNext = {},
        )
    }

    /* ─────────── the three endings the reorder rewrote, 11 Aug 2026 ─────────── */
    //
    // ⭐⭐ WHY THESE THREE EXIST. Moving Mark North in front of "Check what we read" changed the
    // button at the BOTTOM of three scrolling screens — the one place a golden that starts at the
    // top can never see. Every one of those buttons names the screen it opens, and getting that
    // wrong is a defect this project has already logged twice; the first draft of this very change
    // shipped one ("check what YOU read") past a full render matrix, because no picture contained
    // it. A golden is a viewport, not a document — so these scroll to the ending and photograph it.
    //
    // ⚠ Each anchor is the screen's own last control. If an ending changes, the anchor stops
    // matching and the test fails loudly instead of quietly photographing the wrong place.

    /**
     * "Check what we read", scrolled to its end — the screen that now finishes the scan flow when
     * the plan named its own entrance. What has to be legible: the result and direction pills on the
     * last rows, the line stating the door we read, and a button that says "read my home" rather
     * than promising a North step that has already happened.
     */
    @Test
    fun scan_review_bottom() = captureBottomPair(
        "scan-review-door",
        anchor = hasText("Put the front door somewhere else"),
    ) {
        val outcome = ScanMapper.map(
            RecordedScans.load(RecordedScans.PLAN_020)!!.reply,
            imageAspect = 1399.0 / 1389.0,
        ) as com.vastufirst.shared.scan.ScanOutcome.Placed
        com.vastufirst.app.ui.scan.ScanReviewContent(
            image = null,
            rooms = outcome.rooms,
            door = com.vastufirst.app.ui.newplan.frontDoorFromEntrance(
                com.vastufirst.app.ui.scan.toGridRooms(outcome.rooms, outcome.cols, outcome.rows),
            ),
            readings = RenderFixtures.scannedReadings,
        )
    }

    /**
     * Mark North on a scanned plan, scrolled to its end. The button here opens the checking screen
     * now, not the report, and must say so — the double-check card above it is what the "Yes —" is
     * agreeing with, so the two have to read as one sentence.
     */
    @Test
    fun mark_north_scan_bottom() = captureBottomPair(
        "marknorth-photo",
        anchor = hasText("Yes — check what we read"),
    ) {
        com.vastufirst.app.ui.marknorth.MarkNorthContent(
            rooms = RenderFixtures.sampleRooms,
            north = RenderFixtures.sampleNorth,
            analysis = RenderFixtures.sampleAnalysis,
            onNorthChange = {},
            onRead = {},
            onBack = {},
            nextIsCheck = true,
        )
    }

    /**
     * Marking the front door on the photo, scrolled to its end — the last step for a plan that named
     * no entrance. Its button used to say "which way is North?"; North is two screens behind the
     * reader now, so it opens the report.
     */
    @Test
    fun scan_door_bottom() = captureBottomPair("scan-door", anchor = hasText("Read my home")) {
        val outcome = ScanMapper.map(
            RecordedScans.load(RecordedScans.PLAN_020)!!.reply,
            imageAspect = 1399.0 / 1389.0,
        ) as com.vastufirst.shared.scan.ScanOutcome.Placed
        com.vastufirst.app.ui.scan.ScanDoorContent(
            image = null,
            rooms = outcome.rooms,
            door = com.vastufirst.app.ui.newplan.frontDoorFromEntrance(
                com.vastufirst.app.ui.scan.toGridRooms(outcome.rooms, outcome.cols, outcome.rows),
            ),
        )
    }

    /**
     * ⭐⭐ THE SCAN RESULT SCREEN SAYING A ROOM WILL NOT FIT ON THE DRAWING GRID.
     *
     * The holding layout an unplaced reading is drawn with fits twenty-five rooms, and every room
     * after that used to be returned as nothing at all: the screen said "we found 28 rooms", the
     * next screen drew twenty-five, and no sentence anywhere joined the two.
     *
     * ⚠⚠ THIS IS THE ONLY PICTURE THAT CAN EVER SHOW IT, which is why it is here and not in the
     * ordinary matrix. A golden is a viewport: every matrix capture starts at the top, and with
     * twenty-eight room rows stacked above it this card is below the fold at every size. A card that
     * ships to a real customer and is drawn in no picture is the exact failure this file exists for.
     *
     * ⚠ The reading is a real recorded reply PADDED to exactly [OVERFLOW_ROOMS] rooms — pinned,
     * because the card names a COUNT. A fixture whose size drifted with the recorded reply would
     * change that count and fail for a reason that has nothing to do with the screen.
     *
     * ⚠⚠ THE ANCHOR IS THE CARD'S LAST LINE, NOT ITS HEADING. Anchored on the heading, the first
     * recording framed the heading at the very bottom edge and cut off every word underneath it —
     * so the picture proved only that a card exists, not that it says anything. Scrolling to the
     * card's final sentence brings the whole card into the window. The lesson is the same one this
     * file was built for: what a golden proves is exactly what is inside its frame.
     */
    @Test
    fun scan_assisted_overflow_bottom() = captureBottomPair(
        "scan-assisted-overflow",
        anchor = hasText(
            "A room that is not on the grid is not scored. Make space on the next screen and add them yourself.",
        ),
    ) {
        com.vastufirst.app.ui.scan.ScanScreen(
            state = com.vastufirst.app.ui.scan.ScanUiState.Done(assistedOverflow(), readBy = null),
            onPickImage = {}, onTakePhoto = {}, onRetry = {},
            onUseRooms = {}, onCorrectRoom = { _, _ -> }, onDrawInstead = {}, onBack = {},
        )
    }

    private fun assistedOverflow(): com.vastufirst.shared.scan.ScanOutcome.Assisted {
        // ⚠ Was real-dense until 16 Aug 2026. Removing the lift rule made real-dense PLACE, which
        // turned this cast into a crash — the Assisted screen simply had no fixture left. See
        // RecordedScans.UNSIZED: a real sheet printing no size anywhere, twenty-one spaces.
        val real = ScanMapper.map(RecordedScans.load(RecordedScans.UNSIZED)!!.reply)
            as com.vastufirst.shared.scan.ScanOutcome.Assisted
        val padding = (1..OVERFLOW_ROOMS).map {
            com.vastufirst.shared.scan.ScannedRoom(
                type = com.vastufirst.shared.RoomType.STORE, label = "STORE $it", rect = null,
            )
        }
        val rooms = (real.rooms + padding).take(OVERFLOW_ROOMS)
        check(rooms.size == OVERFLOW_ROOMS) {
            "The overflow fixture is ${rooms.size} rooms, not $OVERFLOW_ROOMS."
        }
        // ⚠ Pins the NUMBER the card's heading prints. Without this the fixture could still overflow
        // by some other amount and the picture would quietly photograph a different sentence.
        val offGrid = com.vastufirst.app.ui.scan.roomsOffTheGrid(real.copy(rooms = rooms)).size
        check(offGrid == OVERFLOW_OFF_GRID) {
            "The overflow fixture leaves $offGrid rooms off the grid, not $OVERFLOW_OFF_GRID, so the " +
                "heading in this picture would not be the one anybody reviewed."
        }
        return real.copy(rooms = rooms)
    }

    private companion object {
        /** Rooms in the overflow fixture — comfortably past the twenty-five the grid holds. */
        const val OVERFLOW_ROOMS = 28

        /** …and therefore how many the screen must say will not fit: 28 read, 25 placed. Not used by
         *  the anchor any more, but kept because it is the number a reader of this picture is
         *  checking the heading against, and it is what makes 28 the right fixture size. */
        const val OVERFLOW_OFF_GRID = 3
    }
}
