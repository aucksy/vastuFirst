package com.vastufirst.app.ui.report

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.app.render.RenderFixtures
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐⭐ THE REPORT'S ROOM LIST MUST NOT MOVE UNDER THE READER'S FINGER.
 *
 * **What the owner reported, 17 August 2026:** *"Same issue with list auto-scrolling on 'Your
 * Report' screen also"* — the same complaint the check-what-we-read screen had a day earlier, about
 * the other screen carrying the same list.
 *
 * **Why it was still here after that fix.** The report has ONE handler for both ends and it always
 * scrolled. That was deliberate once: the owner had asked for one shared behaviour whether a room is
 * tapped on the picture or in the list. But "open the same room" is the shared part; SCROLLING is
 * not. Only the picture can open a room whose row is off the screen — a row you have your finger on
 * is already in front of you, and moving it is the jump.
 *
 * **And why no picture could have caught it.** Every report golden is a still frame, and no test on
 * this screen had ever performed a click — the identical hole the review screen's own scroll test was
 * written to close. Only a tap shows this, so this test taps.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class ReportListScrollTest {

    /**
     * ⚠ A REAL PHONE'S WINDOW, set explicitly — the same string the render matrix calls its baseline.
     * Robolectric's default window is small enough that this page's proportions stop resembling the
     * screen the owner is describing.
     *
     * ⚠ The leading "+" MERGES with the default qualifiers rather than replacing them.
     */
    private fun phoneSized() = RuntimeEnvironment.setQualifiers("+w412dp-h915dp-port-xhdpi")

    /**
     * The one room row that is OPEN, found by the sentence a screen reader is given for it.
     *
     * ⚠ Not by room name. Names come from the fixture's own rooms and several of them also appear in
     * the finding card above the list, so a name matches more than one node and the test would fail
     * for a reason that has nothing to do with scrolling. Exactly one row is ever open, and only an
     * open row offers to close.
     */
    private val openRow = SemanticsMatcher("the room row that is open") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label == "close this room"
    }

    @Test
    fun tapping_a_room_row_does_not_scroll_the_report() = runComposeUiTest {
        phoneSized()
        setContent {
            VastuTheme {
                ReportContent(
                    analysis = RenderFixtures.sampleAnalysis,
                    intent = Intent.BUILDING,
                    unlocked = true,
                    rooms = RenderFixtures.sampleRooms,
                )
            }
        }

        // Bring the list into the window — it sits below the score, the meter, the plan and the
        // first finding, so on a real phone the reader has already scrolled by the time they tap.
        onNode(openRow).performScrollTo().assertIsDisplayed()

        // ⚠ The reference is the list's own heading, ABOVE the rows. A row's own bounds are the
        // wrong thing to measure: opening or closing a room legitimately moves everything BELOW it,
        // so a moving row proves nothing. Nothing below the heading can move the heading — only the
        // page scrolling can.
        val before = onNodeWithText("Worst first", substring = true).getBoundsInRoot()
        onNode(openRow).performClick()
        val after = onNodeWithText("Worst first", substring = true).getBoundsInRoot()

        assertEquals(
            "tapping a room in the list must leave the page exactly where the finger left it",
            before.top.value,
            after.top.value,
            0.5f,
        )
    }

    // ⚠ NO "and the row still closes" test here, and the reason is worth writing down rather than
    // discovering twice: on this screen the FIRST readable row is open whenever nothing else is, so
    // closing it immediately reopens it. That is deliberate — a reader must meet the depth of the
    // report rather than a column of shut rows — and a test asserting it closes would be asserting
    // the opposite of the design. The test above still proves the row responds: its matcher only
    // finds a row that carries a click action at all.

    /**
     * ⭐ AND THE PAY BAR NAMES THE STATE, NOT A DIFFERENCE (owner: *"there is line above Unlock the
     * full report which says 4 more problems.. but I see more than 4"*).
     *
     * The number was never wrong — it counts what is LOCKED, and the warnings he could see belonged
     * to the entrance, kitchen and toilets, which are free. "More" was the word that could not be
     * checked from the page. This pins the replacement, because a sentence about money that a reader
     * can disprove by counting is worse than no sentence at all.
     */
    @Test
    fun the_pay_bar_says_what_is_locked_rather_than_what_is_extra() {
        assertEquals(
            "Still locked: 4 problems with their remedies, and 7 rooms read in full",
            payBarPromise(problems = 4, rooms = 7),
        )
        assertEquals(
            "Still locked: 1 problem with its remedies, and 1 room read in full",
            payBarPromise(problems = 1, rooms = 1),
        )
    }
}
