package com.vastufirst.app.ui.welcome

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.app.ui.addhome.AddHomeScreen
import com.vastufirst.app.ui.scan.WHAT_WORKS_BEST
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐⭐ THE FIRST TWO SCREENS, ONE TAP EACH (owner, 30 Sep 2026: *"let the user get to the point quickly
 * and lets not bombard them with so much to read and choose"*).
 *
 *  · Welcome: the answer IS the question, so tapping it moves on — there is no separate Continue.
 *  · Add a home: "Upload a plan" and "Photograph your plan" each open their own picker (the upload
 *    screen that asked "PDF or photo?" a second time is skipped), and the advice that used to fill a
 *    card on that screen is behind the i on "How would you like to add it?".
 *
 * ⚠ org.junit.Assert: the MESSAGE comes FIRST.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class OneTapStartTest {

    @Test
    fun tapping_an_answer_on_welcome_chooses_it_and_moves_on() = runComposeUiTest {
        var chosen: Intent? = null
        var moved = 0
        setContent {
            VastuTheme { WelcomeContent(intent = null, onIntentChange = { chosen = it }, onContinue = { moved++ }) }
        }
        assertEquals("there must be no separate Continue button any more", 0, onAllNodesWithText("Continue").fetchSemanticsNodes().size)
        onNodeWithText("I am buying a home").performScrollTo().performClick()
        assertEquals("the tapped answer must be the one chosen", Intent.BUYING, chosen)
        assertEquals("one tap must move on, exactly once", 1, moved)
    }

    @Test
    fun add_a_home_offers_the_camera_as_its_own_card() = runComposeUiTest {
        var scans = 0
        var photos = 0
        setContent {
            VastuTheme { AddHomeScreen(onDrawGrid = {}, onScan = { scans++ }, onSample = {}, onPhotograph = { photos++ }) }
        }
        onNodeWithText("Photograph your plan").performScrollTo().performClick()
        onNodeWithText("Upload a plan").performScrollTo().performClick()
        assertEquals("the camera card must open the camera", 1, photos)
        assertEquals("the upload card must open the picker", 1, scans)
    }

    @Test
    fun the_advice_on_what_reads_best_is_behind_the_i() = runComposeUiTest {
        setContent { VastuTheme { AddHomeScreen(onDrawGrid = {}, onScan = {}, onSample = {}) } }
        val fragment = "hold the phone flat above it"
        assertFalse(
            "the advice is printed on the page; it belongs behind the i",
            onAllNodesWithText(fragment, substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
        onNodeWithTag("addhome.method.header").performScrollTo().performClick()
        assertTrue(
            "the advice has been DELETED, not moved — every word of the old card must be one tap away",
            onAllNodesWithText(WHAT_WORKS_BEST, substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
