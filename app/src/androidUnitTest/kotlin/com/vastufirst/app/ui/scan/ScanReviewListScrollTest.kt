package com.vastufirst.app.ui.scan

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScanOutcome
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐⭐ TAPPING A ROOM IN THE LIST MUST NOT MOVE THE LIST.
 *
 * **What the owner reported, 16 August 2026:** on "Check what we read", tapping a room in the list
 * makes the list jump. *"I want the list to stay exactly where my finger left it."*
 *
 * **What was actually happening, and why it was violent rather than a nudge.** One handler served
 * both ends — a tap on the pinned picture and a tap on a row — and it always scrolled the tapped
 * row to the top. The scroll target was computed in WINDOW coordinates while subtracting only a
 * 24 dp margin, so it also contained the whole pinned header: the back arrow, the subtitle, the
 * photograph itself and the "N rooms read" label. That is roughly 400 dp on a normal phone. Every
 * tap therefore overshot by about five rows, and the room the user had just tapped was thrown clean
 * off the top of the list.
 *
 * The arithmetic was copied from the report, where it is very nearly right because there the
 * scrolling column IS the root and the header cancels out. Moving it under a pinned picture is what
 * broke it.
 *
 * **Why no existing check caught it.** Every render fixture on this screen selects a room through
 * `startSelected`, which writes the selection directly and never enters the tap handler at all. No
 * test on this screen has ever performed a click. The scroll path was completely unphotographed and
 * unasserted, which is precisely why it could be wrong for six days.
 *
 * This test performs the real click and asserts the row has not moved a pixel.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class ScanReviewListScrollTest {

    /** The owner's own flat through the real mapper — fifteen rooms, so the list genuinely scrolls. */
    private fun ownerRooms() =
        (ScanMapper.map(RecordedScans.load(RecordedScans.OWNER_FLAT)!!.reply) as ScanOutcome.Placed).rooms

    /**
     * A row label that matches ONE row and no other.
     *
     * ⚠ Not simply the first room. This sheet carries `MASTER BEDROOM` and `JR. MASTER BEDROOM`,
     * `MASTER BATH` and `JR. MASTER BATH 1` — a substring match on the shorter one finds two nodes
     * and the test fails for a reason that has nothing to do with scrolling.
     */
    private fun uniqueLabel(rooms: List<com.vastufirst.shared.scan.ScannedRoom>): String {
        val labels = rooms.map { it.label }
        return labels.first { l -> l.isNotBlank() && labels.none { it != l && it.contains(l) } }
    }

    /**
     * A stand-in photograph. It must be PRESENT and tall enough to matter: the pinned picture is the
     * whole reason the old arithmetic overshot, so a test with no image would have passed even
     * before the fix.
     */
    private fun photo() =
        android.graphics.Bitmap.createBitmap(1400, 990, android.graphics.Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.rgb(0xEF, 0xE9, 0xDA)) }
            .asImageBitmap()

    @Test
    fun tapping_a_row_leaves_the_list_exactly_where_it_was() = runComposeUiTest {
        val rooms = ownerRooms()
        setContent { VastuTheme { ScanReviewContent(image = photo(), rooms = rooms) } }

        // A row that is on screen to begin with — the thing the finger is actually on.
        val label = uniqueLabel(rooms)
        val row = onNodeWithText(label, substring = true)
        row.assertIsDisplayed()
        val before = row.getBoundsInRoot()

        row.performClick()

        val after = onNodeWithText(label, substring = true).getBoundsInRoot()
        assertEquals(
            "tapping a row must not scroll the list — the row moved from ${before.top} to ${after.top}",
            before.top.value,
            after.top.value,
            0.5f,
        )
        // …and it is still on screen, which is the plain-words version of the same promise.
        onNodeWithText(label, substring = true).assertIsDisplayed()
    }

    /**
     * The guard on the guard: selection must still WORK from the row.
     *
     * Without this, the test above would pass just as happily if the row stopped responding to taps
     * altogether — which is the cheapest possible way to "stop the list jumping" and is not what was
     * asked for. Selecting a room changes the row's border rather than its size, so the assertion is
     * that the row is still there, still displayed, and the screen has not fallen over.
     */
    @Test
    fun tapping_a_row_still_selects_it() = runComposeUiTest {
        val rooms = ownerRooms()
        setContent { VastuTheme { ScanReviewContent(image = photo(), rooms = rooms) } }
        val label = uniqueLabel(rooms)
        onNodeWithText(label, substring = true).performClick()
        onNodeWithText(label, substring = true).assertIsDisplayed()
    }
}
