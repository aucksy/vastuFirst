package com.vastufirst.app.render

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.vastufirst.app.billing.BillingMode
import com.vastufirst.app.billing.BillingState
import com.vastufirst.app.ui.report.ReportContent
import com.vastufirst.app.ui.score.ScoreContent
import com.vastufirst.app.ui.unlock.UnlockContent
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Intent
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
        anchor: String,
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
            onNodeWithText(anchor).performScrollTo()
            onRoot().captureRoboImage(goldenPath(screen, config))
        }
    }

    private fun captureBottomPair(screen: String, anchor: String, content: @Composable () -> Unit) {
        captureBottom(screen, "bottom", anchor, 1.0f, content)
        captureBottom(screen, "bottom_font2_0", anchor, 2.0f, content)
    }

    /** The paid document's ending: disputes, the disclaimer, and the only way out of the flow. */
    @Test
    fun report_bottom() = captureBottomPair("report", anchor = "Done — see all my plans") {
        ReportContent(analysis = RenderFixtures.sampleAnalysis, intent = Intent.BUILDING)
    }

    /** The remedies-only branch ends on the same elements but gets there through different cards. */
    @Test
    fun report_living_bottom() = captureBottomPair("report-living", anchor = "Done — see all my plans") {
        ReportContent(analysis = RenderFixtures.sampleAnalysis, intent = Intent.LIVING)
    }

    /** The free score's ending: the ranked problems, the unlock card and the way back to the list. */
    @Test
    fun score_bottom() = captureBottomPair("score", anchor = "See all my plans") {
        ScoreContent(
            rooms = RenderFixtures.sampleRooms,
            north = RenderFixtures.sampleNorth,
            intent = RenderFixtures.sampleIntent,
            analysis = RenderFixtures.sampleAnalysis,
            onUnlock = {},
            onFix = {},
        )
    }

    /** The ₹699 screen's ending: the feature list under the buy button — the paid promise itself. */
    @Test
    fun unlock_paid_bottom() = captureBottomPair(
        "unlock-paid",
        anchor = "Your front door by name, and the source behind every rule",
    ) {
        UnlockContent(state = BillingState(mode = BillingMode.READY, price = "₹699.00"))
    }
}
