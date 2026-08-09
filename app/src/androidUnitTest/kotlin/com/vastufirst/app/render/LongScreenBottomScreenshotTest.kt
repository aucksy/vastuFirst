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
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.vastufirst.app.billing.BillingMode
import com.vastufirst.app.billing.BillingState
import com.vastufirst.app.ui.report.ReportContent
import com.vastufirst.app.ui.report.TAG_PAY_CLEARANCE
import com.vastufirst.app.ui.score.ScoreContent
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
 * of the screen, so the report's lower sections (the disputes payoff, the disclaimer, the way out),
 * the score's "biggest problems" list and the unlock screen's feature list had never appeared in ANY
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
        content: @Composable () -> Unit,
    ) {
        RuntimeEnvironment.setQualifiers(RenderMatrix.BASE)
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
        captureBottom(screen, "bottom", anchor, 1.0f, content)
        captureBottom(screen, "bottom_font2_0", anchor, 2.0f, content)
    }

    /** The paid document's ending: disputes, the disclaimer, and the only way out of the flow. */
    @Test
    fun report_bottom() = captureBottomPair("report", anchor = hasText("Done — see all my plans")) {
        ReportContent(analysis = RenderFixtures.sampleAnalysis, intent = Intent.BUILDING)
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

    /** The free score's ending: the ranked problems, the unlock card and the way back to the list. */
    @Test
    fun score_bottom() = captureBottomPair("score", anchor = hasText("See all my plans")) {
        ScoreContent(
            rooms = RenderFixtures.sampleRooms,
            north = RenderFixtures.sampleNorth,
            intent = RenderFixtures.sampleIntent,
            analysis = RenderFixtures.sampleAnalysis,
            onUnlock = {},
            onFix = {},
        )
    }

    /**
     * ⭐⭐ THE BOTTOM OF THE OWNER'S OWN ROOM LIST — the one picture this whole 6 Aug 2026 change
     * turns on, and the one no top-of-screen golden can ever contain.
     *
     * His plan reads eleven rooms. The fix he asked for — one continuous balcony captioned three
     * times becoming ONE room that still prints all three of its sizes — lands near the END of that
     * list, because rooms are ordered down the sheet and his long balcony runs along the bottom of
     * it. Photographing only the top of this screen would have shown the change nowhere at all,
     * which is exactly the trap this file exists for.
     */
    @Test
    fun scanReviewDoor_bottom() {
        val outcome = ScanMapper.map(
            RecordedScans.load(RecordedScans.PLAN_020)!!.reply,
            imageAspect = 1399.0 / 1389.0,
        ) as com.vastufirst.shared.scan.ScanOutcome.Placed
        val door = com.vastufirst.app.ui.newplan.frontDoorFromEntrance(
            com.vastufirst.app.ui.scan.toGridRooms(outcome.rooms, outcome.cols, outcome.rows),
        )
        captureBottomPair("scan-review-door", anchor = hasText("Put the front door somewhere else")) {
            com.vastufirst.app.ui.scan.ScanReviewContent(
                image = android.graphics.Bitmap
                    .createBitmap(1399, 1389, android.graphics.Bitmap.Config.ARGB_8888)
                    .apply { eraseColor(android.graphics.Color.rgb(0xEF, 0xE9, 0xDA)) }
                    .asImageBitmap(),
                rooms = outcome.rooms,
                door = door,
            )
        }
    }

    /** The ₹699 screen's ending: the feature list under the buy button — the paid promise itself. */
    @Test
    fun unlock_paid_bottom() = captureBottomPair(
        "unlock-paid",
        anchor = hasText("Your front door by name, and the source behind every rule"),
    ) {
        UnlockContent(state = BillingState(mode = BillingMode.READY, price = "₹699.00"))
    }
}
