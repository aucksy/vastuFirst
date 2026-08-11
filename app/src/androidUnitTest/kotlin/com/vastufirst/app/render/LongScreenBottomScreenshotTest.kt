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
        anchor = hasText("Your front door by name, and the source behind every rule"),
    ) {
        UnlockContent(state = BillingState(mode = BillingMode.READY, price = "₹699.00"))
    }
}
