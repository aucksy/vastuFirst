package com.vastufirst.app.ui.grid

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.editor.CellRect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐⭐ "SHOW US ON YOUR PLAN", DRIVEN AS A REAL FINGER (tap-to-place, 14 Oct 2026).
 *
 * Four rooms a scan read but could not place, parked in a row along the top of a 10 × 10 grid. The user
 * taps the photograph where each one is — the kitchen top right, the master bedroom bottom left, the
 * living room top left, the prayer room bottom right — and presses "Put them on the grid". Each room
 * must land in the corner of the grid it was tapped in, and the parked state must end.
 *
 * The photograph is square and plain, so a fraction of the picture is a fraction of its box.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class TapToPlaceInteractionTest {

    private val parked = listOf(
        GridRoom("scan-0", RoomType.KITCHEN, 0, 0, 2, 2, label = "KITCHEN"),
        GridRoom("scan-1", RoomType.MASTER_BEDROOM, 2, 0, 2, 2, label = "MASTER BEDROOM"),
        GridRoom("scan-2", RoomType.LIVING, 4, 0, 2, 2, label = "LIVING"),
        GridRoom("scan-3", RoomType.POOJA, 6, 0, 2, 2, label = "POOJA"),
    )

    private fun photo() =
        android.graphics.Bitmap.createBitmap(1000, 1000, android.graphics.Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.rgb(0xEF, 0xE9, 0xDA)) }
            .asImageBitmap()

    private object Silent : EditorFeedback {
        override fun grab() {}
        override fun tick() {}
        override fun reject() {}
        override fun confirm() {}
    }

    private class Harness(rooms: List<GridRoom>) {
        val rooms = mutableStateOf(rooms)
        val unplaced = mutableStateOf(true)
    }

    private fun ComposeUiTest.show(h: Harness) {
        val image = photo()
        setContent {
            VastuTheme {
                GuidedGridContent(
                    rooms = h.rooms.value,
                    door = null,
                    // As the ViewModel does: any change to where a room is ends the parked state.
                    onRoomsChange = { h.rooms.value = it; h.unplaced.value = false },
                    onDoorChange = {},
                    onNext = {},
                    cols = 10,
                    rows = 10,
                    roomsUnplaced = h.unplaced.value,
                    planPhoto = image,
                    feedback = Silent,
                )
            }
        }
    }

    private fun ComposeUiTest.tapPlan(fx: Float, fy: Float) {
        onNodeWithTag(TAP_PLAN_TAG).performScrollTo().performTouchInput {
            click(Offset(width * fx, height * fy))
        }
        waitForIdle()
    }

    @Test
    fun `tapping each room on the plan puts it in that corner of the grid`() = runComposeUiTest {
        val h = Harness(parked)
        show(h)

        onNodeWithText("Tap your plan where it says “KITCHEN”.").assertExists()
        tapPlan(0.85f, 0.15f)
        onNodeWithText("Tap your plan where it says “MASTER BEDROOM”.").assertExists()
        onNodeWithText("1 of 4 rooms shown").assertExists()
        tapPlan(0.15f, 0.85f)
        tapPlan(0.15f, 0.15f)
        onNodeWithText("Put them on the grid").assertDoesNotExist()
        tapPlan(0.85f, 0.85f)

        onNodeWithText("Put them on the grid").performScrollTo().performClick()
        waitForIdle()

        val at = h.rooms.value.associate { it.id to CellRect(it.col, it.row, it.w, it.h) }
        assertEquals("kitchen tapped top right = north-east", CellRect(8, 0, 2, 2), at["scan-0"])
        assertEquals("master tapped bottom left = south-west", CellRect(0, 8, 2, 2), at["scan-1"])
        assertEquals("living tapped top left = north-west", CellRect(0, 0, 2, 2), at["scan-2"])
        assertEquals("pooja tapped bottom right = south-east", CellRect(8, 8, 2, 2), at["scan-3"])
        // The rooms are placed, so the screen is the ordinary editor again and the card is gone.
        onNodeWithText("Show us on your plan").assertDoesNotExist()
        onNodeWithText("Place your rooms").assertExists()
    }

    @Test
    fun `a skipped room is asked for again after the others and undo takes back the last tap`() = runComposeUiTest {
        val h = Harness(parked.take(3))
        show(h)

        onNodeWithText("Skip for now").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Tap your plan where it says “MASTER BEDROOM”.").assertExists()
        tapPlan(0.2f, 0.8f)
        onNodeWithText("Undo last tap").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Tap your plan where it says “MASTER BEDROOM”.").assertExists()
        onNodeWithText("0 of 3 rooms shown").assertExists()
        tapPlan(0.2f, 0.8f)
        tapPlan(0.8f, 0.8f)
        onNodeWithText("Tap your plan where it says “KITCHEN”.").assertExists()
        assertEquals("nothing moves on the grid until the button", parked.take(3), h.rooms.value)
    }
}
