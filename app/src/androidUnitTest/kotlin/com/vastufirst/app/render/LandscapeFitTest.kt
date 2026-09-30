package com.vastufirst.app.render

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.app.ui.marknorth.MarkNorthContent
import com.vastufirst.app.ui.scan.ScanDoorContent
import com.vastufirst.designsystem.theme.VastuTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * ⭐⭐ ON A PHONE TURNED SIDEWAYS, THE THING YOU HAVE TO TOUCH MUST FIT ON THE SCREEN.
 *
 * Found by looking at the landscape pictures, 30 Sep 2026. The North compass and the front-door plan
 * were both sized by the screen's WIDTH alone. A landscape phone is 854 dp wide and 480 dp tall, so
 * the compass came out about 806 dp tall — nearly twice the height of the screen. Its centre and its
 * lower half were below the bottom edge, and the only way to see the rest of your own plan was to
 * scroll the page while trying to turn the dial on it. The door screen did the same with the plan the
 * reader has to tap a wall of.
 *
 * No gate could see it: both pages scroll, so nothing is "cut off" in the geometry gate's sense — the
 * picture is simply bigger than the window it is in.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class LandscapeFitTest {

    private val landscape = RenderMatrix.configs.first { it.name == "landscape" }

    private val squarePlan
        get() = android.graphics.Bitmap.createBitmap(1399, 1389, android.graphics.Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.rgb(0xEF, 0xE9, 0xDA)) }
            .asImageBitmap()

    /** The window's height, and the node's height as drawn on screen and as laid out, all in dp. */
    private class Fit(val window: Float, val shown: Float, val whole: Float)

    @OptIn(ExperimentalTestApi::class)
    private fun measure(description: String, content: @Composable () -> Unit): Fit {
        RuntimeEnvironment.setQualifiers(landscape.qualifiers)
        var fit: Fit? = null
        runComposeUiTest {
            setContent { VastuTheme { content() } }
            val root = onRoot().fetchSemanticsNode()
            val node = onNodeWithContentDescription(description, substring = true).fetchSemanticsNode()
            val d = root.layoutInfo.density.density
            fit = Fit(
                window = root.size.height / d,
                shown = node.boundsInRoot.height / d,
                whole = node.size.height / d,
            )
        }
        return checkNotNull(fit)
    }

    @Test
    fun theNorthCompassFitsOnALandscapeScreen() {
        val fit = measure("Floor plan compass") {
            MarkNorthContent(
                rooms = RenderFixtures.sampleRooms, north = RenderFixtures.sampleNorth,
                analysis = RenderFixtures.sampleAnalysis,
                onNorthChange = {}, onRead = {}, onBack = {},
                planImage = squarePlan,
            )
        }
        assertTrue(
            fit.whole <= fit.window * 0.75f,
            "In landscape the North compass is ${fit.whole} dp tall on a ${fit.window} dp screen. " +
                "It must fit, with room for its heading, so the whole plan can be seen while turning it.",
        )
        assertTrue(
            fit.shown >= fit.whole - 1f,
            "In landscape only ${fit.shown} of the compass's ${fit.whole} dp is on the first screen. " +
                "The reader would have to scroll to see the plan they are turning.",
        )
    }

    @Test
    fun theFrontDoorPlanFitsOnALandscapeScreen() {
        val outcome = JourneyFixtures.entrance.outcome
        val fit = measure("Your plan") {
            ScanDoorContent(image = squarePlan, rooms = outcome.rooms, door = null)
        }
        assertTrue(
            fit.whole <= fit.window * 0.75f,
            "In landscape the plan on the front-door screen is ${fit.whole} dp tall on a ${fit.window} dp " +
                "screen. The wall a reader must tap can be below the bottom edge.",
        )
        assertTrue(
            fit.shown >= fit.whole - 1f,
            "In landscape only ${fit.shown} of the door plan's ${fit.whole} dp is on the first screen.",
        )
    }
}
