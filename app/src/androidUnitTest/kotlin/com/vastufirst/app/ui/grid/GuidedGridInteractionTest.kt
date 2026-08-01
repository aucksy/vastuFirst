package com.vastufirst.app.ui.grid

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.app.ui.common.ALL_ROOM_TYPES
import com.vastufirst.app.ui.common.label
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
        /** How many plot-key presses came back refused — the screen turns each into a "no" buzz. */
        val refusals = mutableStateOf(0)
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
                // Mirrors NewPlanViewModel.updateGrid, including its Boolean "was this honoured?"
                // contract — a refused key (rooms won't fit, or at the 4/10 limit) returns false so
                // the screen can buzz instead of doing nothing.
                onGridChange = { c, r ->
                    val res = resolveGridResize(h.rooms.value, h.door.value, h.cols.value, h.rows.value, c, r)
                    if (res == null) {
                        h.refusals.value++
                        false
                    } else {
                        h.cols.value = res.cols; h.rows.value = res.rows
                        h.rooms.value = res.rooms; h.door.value = res.door
                        if (!res.honoured) h.refusals.value++
                        res.honoured
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

    // ── plot keys report a refusal instead of failing silently ───────────────────────────────────

    @Test
    fun `a plot key at the limit reports a refusal`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.BEDROOM, 0, 0, 2, 2)))
        setContent { Editor(h) }
        repeat(4) { tapDesc("Narrower plot") }          // 8 → 7 → 6 → 5 → 4, all honoured
        assertEquals("reached the minimum without a refusal", 4, h.cols.value)
        assertEquals("no refusal on the way down", 0, h.refusals.value)

        tapDesc("Narrower plot")                        // the 5th press cannot act
        assertEquals("still at the minimum", 4, h.cols.value)
        assertEquals("the key that cannot act says so", 1, h.refusals.value)
    }

    @Test
    fun `a plot shrink the rooms cannot fit is refused, not forced`() = runComposeUiTest {
        // Two 4-wide, full-depth rooms fill the 8×8 plot exactly, so a 7-wide plot cannot hold them
        // at any arrangement — fitWithoutOverlap returns null and the whole resize is refused rather
        // than overlapping them (an overlap would make the engine score the buried room twice).
        val h = Harness(
            listOf(
                room("a", RoomType.LIVING, 0, 0, 4, 8),
                room("b", RoomType.BEDROOM, 4, 0, 4, 8),
            ),
        )
        setContent { Editor(h) }
        tapDesc("Narrower plot")
        assertEquals("the plot must not shrink past what the rooms need", 8, h.cols.value)
        assertEquals("and the refusal is reported so the key can buzz", 1, h.refusals.value)
        // The refusal must leave the rooms exactly as they were — never overlapped, never shrunk.
        assertEquals(listOf(0, 4), h.rooms.value.map { it.col })
        assertEquals(listOf(4, 4), h.rooms.value.map { it.w })
    }

    // ── changing a room's KIND ───────────────────────────────────────────────────────────────────

    @Test
    fun `the room-type picker changes the kind and moves nothing`() = runComposeUiTest {
        // The owner's Gurgaon case, driven end to end through the real screen: a room the reader
        // called a corridor, corrected to the living room it actually is.
        val h = Harness(listOf(
            room("a", RoomType.CORRIDOR, 1, 1, 3, 3),
            room("b", RoomType.KITCHEN, 4, 1, 2, 2),
        ))
        setContent { Editor(h) }
        selectRoom("Corridor, 3 by 3 cells")

        // By accessibility label throughout: the word "Bedroom" alone appears on the room's tile, in
        // the panel's heading AND on a chip, so matching by visible text picks three nodes and throws.
        tapDescPart("Change room type")
        tapDesc("Change to Living")

        val a = h.rooms.value.first { it.id == "a" }
        assertEquals("the kind is what the user picked", RoomType.LIVING, a.type)
        assertEquals("nothing may move", listOf(1, 1, 3, 3), listOf(a.col, a.row, a.w, a.h))
        val b = h.rooms.value.first { it.id == "b" }
        assertEquals("the neighbour keeps its kind", RoomType.KITCHEN, b.type)
        assertEquals("and its place", listOf(4, 1, 2, 2), listOf(b.col, b.row, b.w, b.h))
        assertEquals("no room may appear or vanish", 2, h.rooms.value.size)
    }

    @Test
    fun `a room can be changed to any kind, Corridor included`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.BEDROOM, 0, 0, 3, 3)))
        setContent { Editor(h) }
        selectRoom("Bedroom, 3 by 3 cells")

        tapDescPart("Change room type")
        tapDesc("Change to Corridor")
        assertEquals(RoomType.CORRIDOR, h.rooms.value.single().type)
    }

    @Test
    fun `the add-a-room palette offers exactly the same kinds as changing a room`() = runComposeUiTest {
        // ⭐ The owner's report: "Draw room on grid does not have same options as when you replace
        // the room… should be consistent, corridor should be there too." It was true — the palette
        // offered eleven kinds and the change-type control nineteen, so the app answered "what kinds
        // of room are there?" differently depending on which control you were looking at, and eight
        // kinds a scanned plan can produce could be deleted and never placed again by hand.
        //
        // Asserted on the RENDERED palette, not on the list constant, so this fails if the screen
        // ever goes back to reading a shorter list of its own. An empty home is used so the only
        // place a room name can appear is a palette chip.
        val h = Harness(emptyList())
        setContent { Editor(h) }
        ALL_ROOM_TYPES.forEach { type ->
            onNodeWithText(type.label()).assertExists()
        }
    }

    @Test
    fun `picking the kind it already is closes the list and changes nothing`() = runComposeUiTest {
        // The way out without committing to a change — so opening the list is never a trap.
        val h = Harness(listOf(room("a", RoomType.BEDROOM, 2, 2, 2, 2)))
        setContent { Editor(h) }
        selectRoom("Bedroom, 2 by 2 cells")

        tapDescPart("Change room type")
        tapDesc("Bedroom, the current room type")
        // The trigger is back, so the list closed rather than leaving the user stuck in it.
        onNodeWithContentDescription("Change room type", substring = true).assertExists()
        assertEquals(RoomType.BEDROOM, h.rooms.value.single().type)
        assertEquals(listOf(2, 2, 2, 2), h.rooms.value.single().let { listOf(it.col, it.row, it.w, it.h) })
    }

    @Test
    fun `a selected room shows its kind and the way to change it`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.CORRIDOR, 0, 0, 2, 2)))
        setContent { Editor(h) }
        // Nothing selected: no panel, so no picker.
        onNodeWithContentDescription("Change room type", substring = true).assertDoesNotExist()
        selectRoom("Corridor, 2 by 2 cells")
        onNodeWithText("ROOM TYPE").assertExists()   // SectionLabel uppercases its text
        // Closed by default: nineteen chips permanently open would push move and size off a phone.
        onNodeWithContentDescription("Change room type", substring = true).assertExists()
        onNodeWithContentDescription("Change to Living").assertDoesNotExist()
    }

    // ── door mode entry (H) ──────────────────────────────────────────────────────────────────────

    @Test
    fun `entering door mode changes the instruction`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.BEDROOM, 0, 0, 3, 3)))
        setContent { Editor(h) }
        tapText("Set the front door")
        onNodeWithText("Your home is outlined below. Tap the wall where your main entrance is.").assertExists()
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** Tap a control by its accessibility label, scrolling it into view first (the editor scrolls). */
    private fun ComposeUiTest.tapDesc(desc: String) {
        onNodeWithContentDescription(desc).performScrollTo().performClick()
        waitForIdle()
    }

    /** Tap by PART of an accessibility label, for controls whose label carries live state. */
    private fun ComposeUiTest.tapDescPart(part: String) {
        onNodeWithContentDescription(part, substring = true).performScrollTo().performClick()
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
