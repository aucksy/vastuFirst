package com.vastufirst.app.ui.report

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.app.render.RenderFixtures
import com.vastufirst.app.ui.newplan.DoorSide
import com.vastufirst.app.ui.newplan.GridDoor
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
 * ⭐⭐ TWO SENTENCES MOVED ON 30 SEP 2026 — both halves pinned, as QuietCopyTest pins the others: gone
 * from the page when it first draws, and reachable with one real tap.
 *
 *  · **"Entrance, kitchen and toilets are below in full, free."** It sat in the verdict at the very top
 *    of a free report, explaining the list rather than answering "how is my home?". It now waits behind
 *    the i on "Your rooms" — the list it explains.
 *  · **Where the front door came from.** It was the note on the E of "Check what we read", which is
 *    gone. For a read home the report is now the first place a reader can learn that the heaviest input
 *    in their score was READ, not asked — so it is behind the i on "Your front door", in the words that
 *    note used, with the drag instruction replaced by the button that moves the door.
 *
 * ⚠ org.junit.Assert: the MESSAGE comes FIRST.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class ReportMovedLinesTest {

    private val analysis = RenderFixtures.sampleAnalysis
    private val rooms = RenderFixtures.sampleRooms
    private val north = RenderFixtures.sampleNorth

    private fun SemanticsNodeInteractionsProvider.has(fragment: String): Boolean =
        onAllNodesWithText(fragment, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun the_free_line_waits_behind_the_rooms_heading_and_opens_with_a_tap() = runComposeUiTest {
        setContent {
            VastuTheme {
                ReportContent(analysis = analysis, intent = Intent.BUYING, unlocked = false, rooms = rooms, north = north)
            }
        }
        assertFalse(
            "the free-tier line is printed in the verdict again; it belongs behind the i on \"Your rooms\"",
            has("below in full, free"),
        )
        // ⚠ The heading's own action, not a tap: on a free report the pay bar floats over the foot of
        // the window, and a heading scrolled just into view sits under it — a tap there lands on the
        // bar. The action is what a screen reader uses, so it is still a real way in.
        onNodeWithTag("report.rooms.header").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        assertTrue(
            "the free-tier line has been DELETED, not moved — CLAUDE.md §2h",
            has("Entrance, kitchen and toilets are below in full, free."),
        )
    }

    @Test
    fun a_paid_report_never_says_what_is_free() = runComposeUiTest {
        setContent {
            VastuTheme {
                ReportContent(analysis = analysis, intent = Intent.BUYING, unlocked = true, rooms = rooms, north = north)
            }
        }
        onNodeWithTag("report.rooms.header").performScrollTo().performClick()
        assertFalse("a paid reader has every room in full; the free line must not appear", has("below in full, free"))
    }

    @Test
    fun the_three_states_of_where_the_door_came_from() {
        val door = GridDoor(DoorSide.E, 2)
        assertEquals(
            "Your plan prints \"FOYER\" on the right wall of your plan, so we put it there. To move it, tap Change front door.",
            frontDoorProvenance(door, doorFromCaption = "FOYER", doorIsOurs = true),
        )
        assertEquals(
            "We read it from your plan's own entrance, on the right wall of your plan. To move it, tap Change front door.",
            frontDoorProvenance(door, doorFromCaption = null, doorIsOurs = true),
        )
        assertEquals(
            "You put it on the right wall of your plan. To move it, tap Change front door.",
            frontDoorProvenance(door, doorFromCaption = "FOYER", doorIsOurs = false),
        )
    }

    @Test
    fun where_the_door_came_from_waits_behind_the_front_door_heading() = runComposeUiTest {
        val note = frontDoorProvenance(GridDoor(DoorSide.E, 2), doorFromCaption = "FOYER", doorIsOurs = true)
        setContent {
            VastuTheme {
                ReportContent(
                    analysis = analysis, intent = Intent.BUYING, unlocked = true,
                    rooms = rooms, north = north, doorNote = note,
                )
            }
        }
        assertFalse("the note must start shut", has("so we put it there"))
        onNodeWithTag("report.door.header").performScrollTo().performClick()
        assertTrue("one tap on \"Your front door\" must show where the door came from", has(note))
    }

    @Test
    fun a_home_drawn_by_hand_has_no_door_note_and_no_i() = runComposeUiTest {
        setContent {
            VastuTheme {
                ReportContent(analysis = analysis, intent = Intent.BUYING, unlocked = true, rooms = rooms, north = north)
            }
        }
        assertEquals(
            "without a note the heading must stay a plain label — an i that opens nothing is a dead control",
            0, onAllNodesWithTagCount("report.door.header"),
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
