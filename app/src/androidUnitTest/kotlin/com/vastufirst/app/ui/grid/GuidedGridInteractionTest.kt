package com.vastufirst.app.ui.grid

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.app.ui.newplan.GridDoor
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.resolveGridResize
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.RoomType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The editor's BUTTON paths driven headlessly (UI-POLISH §6 pattern). These are the WCAG 2.2 SC
 * 2.5.7 single-pointer paths a TalkBack or less phone-literate user relies on — the move arrows, the
 * size steppers, remove, the plot-size steppers, and door-mode entry — so they are proven, not just
 * eyeballed. Raw finger drags / door-cell taps on the canvas are gestures Robolectric can't drive
 * faithfully; those live on the Owner device-test checklist. UAT: A2/A4, C4/C6, D3/D4, E7/E8, F1, G5/G6, H(entry), L2/L3.
 *
 * Each test wires GuidedGridContent to hoisted state exactly as a real screen would, so a button tap
 * flows through the real update callbacks and the assertion reads the resulting state.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class GuidedGridInteractionTest {

    /** A live editor over hoisted state; onGridChange runs the REAL resolveGridResize decision. */
    private class Harness(initialRooms: List<GridRoom>, initialDoor: GridDoor? = null) {
        val rooms = mutableStateOf(initialRooms)
        val door = mutableStateOf(initialDoor)
        val cols = mutableStateOf(8)
        val rows = mutableStateOf(8)
    }

    @androidx.compose.runtime.Composable
    private fun Editor(h: Harness) {
        VastuTheme {
            GuidedGridContent(
                rooms = h.rooms.value,
                door = h.door.value,
                onRoomsChange = { h.rooms.value = it },
                onDoorChange = { h.door.value = it },
                onNext = {},
                cols = h.cols.value,
                rows = h.rows.value,
                onGridChange = { c, r ->
                    resolveGridResize(h.rooms.value, h.door.value, h.cols.value, h.rows.value, c, r)?.let { res ->
                        h.cols.value = res.cols; h.rows.value = res.rows
                        h.rooms.value = res.rooms; h.door.value = res.door
                    }
                },
            )
        }
    }

    private fun room(id: String, type: RoomType, col: Int, row: Int, w: Int, h: Int) =
        GridRoom(id, type, col, row, w, h)

    // ── empty state (A2, A4) ─────────────────────────────────────────────────────────────────────

    @Test
    fun `empty state shows the prompt and disables Next`() = runComposeUiTest {
        val h = Harness(emptyList())
        setContent { Editor(h) }
        onNodeWithText("Pick a room below, then press the plan to place it.").assertExists()
        onNodeWithTag("editor.grid").assertExists()
        onNodeWithTag("editor.next").assertIsNotEnabled()
        // S4: no dead-end door button on the empty grid (placeDoor is a no-op with no rooms).
        onNodeWithText("Set the front door").assertDoesNotExist()
    }

    @Test
    fun `Next is enabled once a room exists`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.BEDROOM, 0, 0, 2, 2)))
        setContent { Editor(h) }
        onNodeWithTag("editor.next").assertIsEnabled()
    }

    // ── move by arrows (D3, D4) ──────────────────────────────────────────────────────────────────

    @Test
    fun `the move arrows shift the selected room one cell and clamp at the wall`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.BEDROOM, 0, 0, 2, 2)))
        setContent { Editor(h) }
        selectRoom("2 by 2 cells")

        tapDesc("Move right")
        assertEquals("moved one cell right", 1, h.rooms.value.single().col)
        tapDesc("Move down")
        assertEquals("moved one cell down", 1, h.rooms.value.single().row)
        tapDesc("Move left"); tapDesc("Move left")
        assertEquals("clamped at the west wall", 0, h.rooms.value.single().col)
    }

    // ── resize by steppers (E7) ──────────────────────────────────────────────────────────────────

    @Test
    fun `the size steppers grow and shrink the selected room`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.BEDROOM, 1, 1, 2, 2)))
        setContent { Editor(h) }
        selectRoom("2 by 2 cells")

        tapDesc("Wider")
        assertEquals(3, h.rooms.value.single().w)
        tapDesc("Taller")
        assertEquals(3, h.rooms.value.single().h)
        tapDesc("Narrower")
        assertEquals(2, h.rooms.value.single().w)
    }

    // ── overlap refusal on the button paths (C4, C6) ─────────────────────────────────────────────

    @Test
    fun `an arrow move into a neighbour is refused`() = runComposeUiTest {
        val h = Harness(listOf(
            room("a", RoomType.BEDROOM, 0, 0, 2, 2),
            room("b", RoomType.KITCHEN, 2, 0, 2, 2),   // flush to a's right edge
        ))
        setContent { Editor(h) }
        selectRoom("Bedroom, 2 by 2 cells")
        tapDesc("Move right")
        assertEquals("a move into b must be refused", 0, h.rooms.value.first { it.id == "a" }.col)
    }

    @Test
    fun `a stepper widen into a neighbour is refused`() = runComposeUiTest {
        val h = Harness(listOf(
            room("a", RoomType.BEDROOM, 0, 0, 2, 2),
            room("b", RoomType.KITCHEN, 2, 0, 2, 2),
        ))
        setContent { Editor(h) }
        selectRoom("Bedroom, 2 by 2 cells")
        tapDesc("Wider")
        assertEquals("a widen into b must be refused", 2, h.rooms.value.first { it.id == "a" }.w)
    }

    // ── remove (F1) ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Remove deletes the selected room and returns to the empty prompt`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.BEDROOM, 0, 0, 2, 2)))
        setContent { Editor(h) }
        selectRoom("2 by 2 cells")
        tapText("Remove")
        assertEquals(0, h.rooms.value.size)
        onNodeWithTag("editor.next").assertIsNotEnabled()
    }

    // ── plot size steppers + bounds (G5, G6) ─────────────────────────────────────────────────────

    @Test
    fun `the plot widens and clamps at the max`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.BEDROOM, 0, 0, 2, 2)))
        setContent { Editor(h) }
        repeat(20) { tapDesc("Wider plot") }
        assertEquals("plot width clamps at MAX_GRID", 10, h.cols.value)
    }

    @Test
    fun `the plot narrows and clamps at the min`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.BEDROOM, 0, 0, 2, 2)))
        setContent { Editor(h) }
        repeat(20) { tapDesc("Narrower plot") }
        assertEquals("plot width clamps at MIN_GRID", 4, h.cols.value)
    }

    // ── door mode entry (H) ──────────────────────────────────────────────────────────────────────

    @Test
    fun `entering door mode changes the instruction`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.BEDROOM, 0, 0, 3, 3)))
        setContent { Editor(h) }
        tapText("Set the front door")
        onNodeWithText("Tap the outer wall where your main entrance is.").assertExists()
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** Tap a control by its accessibility label, scrolling it into view first (the editor scrolls). */
    private fun ComposeUiTest.tapDesc(desc: String) {
        onNodeWithContentDescription(desc).performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.tapText(text: String) {
        onNodeWithText(text).performScrollTo().performClick()
        waitForIdle()
    }

    /** Select the placed room by tapping its tile (the tile carries a semantics onClick). */
    private fun ComposeUiTest.selectRoom(descSubstring: String) {
        onNodeWithContentDescription(descSubstring, substring = true).performScrollTo().performClick()
        waitForIdle()
    }
}
