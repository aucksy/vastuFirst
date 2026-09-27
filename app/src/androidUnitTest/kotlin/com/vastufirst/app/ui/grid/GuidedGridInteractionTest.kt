package com.vastufirst.app.ui.grid

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import com.vastufirst.app.ui.common.DOOR_MARK_TAG
import com.vastufirst.app.ui.common.DOOR_NOTE_TAG
import org.junit.Assert.assertTrue
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
import com.vastufirst.app.ui.newplan.DoorSide
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
 * eyeballed. UAT: A2/A4, C4/C6, D3/D4, E7/E8, F1, G5/G6, H(entry), L2/L3.
 *
 * ⭐ AND, SINCE 27 SEP 2026, A REAL FINGER ON THE PLAN. This header used to say raw finger drags
 * could not be driven faithfully here; they can — `performTouchInput` with `down`, `moveBy` and
 * `up` split across calls holds the finger down between assertions — and the very first one caught
 * a bug no button test could: setting the door while the E was still held shifted the page under a
 * finger that had not moved. See the front-door tests below.
 *
 * Each test wires GuidedGridContent to hoisted state exactly as a real screen would, so a button tap
 * flows through the real update callbacks and the assertion reads the resulting state.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class GuidedGridInteractionTest {

    /**
     * The editor's touches, COUNTED. Haptics are silent in this harness by design, so until this
     * fake existed "the key buzzes when it cannot act" was a claim only a finger could check — and
     * the arrows and size steppers in fact did not buzz while the plot keys did.
     */
    private class CountingFeedback : EditorFeedback {
        var rejects = 0
        var confirms = 0
        override fun grab() {}
        override fun tick() {}
        override fun reject() { rejects++ }
        override fun confirm() { confirms++ }
    }

    /** A live editor over hoisted state; onGridChange runs the REAL resolveGridResize decision. */
    private class Harness(initialRooms: List<GridRoom>, initialDoor: GridDoor? = null) {
        val rooms = mutableStateOf(initialRooms)
        val door = mutableStateOf(initialDoor)
        val cols = mutableStateOf(8)
        val rows = mutableStateOf(8)
        /** How many plot-key presses came back refused — the screen turns each into a "no" buzz. */
        val refusals = mutableStateOf(0)
        /** Every buzz the screen actually gave, so a refusal is proven to reach the hand. */
        val feedback = CountingFeedback()
    }

    @androidx.compose.runtime.Composable
    private fun Editor(h: Harness, onNext: () -> Unit = {}) {
        VastuTheme {
            GuidedGridContent(
                rooms = h.rooms.value,
                door = h.door.value,
                onRoomsChange = { h.rooms.value = it },
                onDoorChange = { h.door.value = it },
                onNext = onNext,
                feedback = h.feedback,
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
        // The door is now a step of the Next button, which names it even here — but with no room to
        // hang a wall on it stays off (S4: placeDoor is a no-op with no rooms), and the old
        // stand-alone door button must not come back as a dead end.
        onNodeWithText("Next — set the front door").assertIsNotEnabled()
        onNodeWithText("Set the front door").assertDoesNotExist()
        onNodeWithText("Move the front door").assertDoesNotExist()
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
        assertEquals("and the screen turned that into a buzz", 1, h.feedback.rejects)
    }

    // ── the move arrows and size keys say no at a wall, like the plot keys do ────────────────────

    @Test
    fun `a move arrow pressed against the wall says no instead of doing nothing`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.BEDROOM, 0, 0, 2, 2)))   // on the west wall
        setContent { Editor(h) }
        selectRoom("Bedroom, 2 by 2")
        tapDesc("Move left")
        assertEquals("the room stays where it was", 0, h.rooms.value.single().col)
        assertEquals("and the key says it could not act", 1, h.feedback.rejects)
        tapDesc("Move right")                           // a key that CAN act stays silent
        assertEquals(1, h.rooms.value.single().col)
        assertEquals("no buzz for a key that worked", 1, h.feedback.rejects)
    }

    @Test
    fun `a size key pressed against the wall says no instead of doing nothing`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.KITCHEN, 6, 0, 2, 2)))   // flush with the east wall
        setContent { Editor(h) }
        selectRoom("Kitchen, 2 by 2")
        tapDesc("Wider")
        assertEquals("the room cannot grow past the wall", 2, h.rooms.value.single().w)
        assertEquals("so the key says no", 1, h.feedback.rejects)
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
    fun `with no door yet, Next opens the door step instead of leaving`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.BEDROOM, 0, 0, 3, 3)))
        var left = 0
        setContent { Editor(h, onNext = { left++ }) }
        // The button names the step it opens, and the old stand-alone door button is gone with it.
        onNodeWithText("Set the front door").assertDoesNotExist()
        onNodeWithText("Move the front door").assertDoesNotExist()
        tapText("Next — set the front door")
        assertEquals("Next must not leave the screen while the door is unset", 0, left)
        onNodeWithText("Your home is outlined below. Tap the wall where your front door is.").assertExists()
        // The way past it is explicit, in the photograph path's own words — never silent.
        onNodeWithText("You can carry on without marking the door — we will say so on your score.").assertExists()
        onNodeWithText("Back to my rooms").assertExists()
        tapText("Skip the door — mark North")
        assertEquals("skipping is a real, named choice that does leave", 1, left)
    }

    @Test
    fun `placing the door is answered in words, and Next then goes to North`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.BEDROOM, 0, 0, 3, 3)))
        var left = 0
        setContent { Editor(h, onNext = { left++ }) }
        tapText("Next — set the front door")
        // The door arrives the way the plan's gesture reader hands it over.
        runOnIdle { h.door.value = GridDoor(DoorSide.N, 1) }
        waitForIdle()
        onNodeWithText("Your front door is on the north wall. Drag the E to any wall to move it.").assertExists()
        onNodeWithText("You can carry on without marking the door — we will say so on your score.").assertDoesNotExist()
        onNodeWithText("Done placing door").assertExists()
        tapText("Next — mark North")
        assertEquals("with a door on the plan, Next leaves for North", 1, left)
    }

    @Test
    fun `with a door already set, Next goes straight to North and no button stands in front of the E`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.BEDROOM, 0, 0, 3, 3)), GridDoor(DoorSide.S, 1))
        var left = 0
        setContent { Editor(h, onNext = { left++ }) }
        // The owner, 27 Sep 2026: "it should not be locked behind a button". The E itself moves now.
        onNodeWithText("Move the front door").assertDoesNotExist()
        onNodeWithTag(DOOR_MARK_TAG).assertExists()
        tapText("Next — mark North")
        assertEquals(1, left)
    }

    // ── the front door moves under the finger (owner, 27 Sep 2026) ───────────────────────────────
    //
    // ⭐ The owner's report, driven as a real finger: "its not moving real-time when holding and
    // dragging it.. currently it follows after you have dragged it.. and I am also not able to move
    // it to other walls.. its stuck on same wall where you put it.. it should not be locked behind a
    // button". Each test below FAILED on the build before this change: the E ignored a drag outside
    // the door step, and inside it the door moved only when the finger lifted.

    @Test
    fun `the E moves with the finger while it is still held, with no button first`() = runComposeUiTest {
        // A home 4 wide and 3 deep; the door on the top wall, first cell.
        val h = Harness(listOf(room("a", RoomType.LIVING, 0, 0, 4, 3)), GridDoor(DoorSide.N, 0))
        setContent { Editor(h) }
        val g = gridGeometry()
        val start = eCentreInGrid(g)

        onNodeWithTag("editor.grid").performTouchInput {
            down(start)
            // Two and a quarter cells to the right, in small steps — a finger, not a jump. The
            // finger ends at 2.75 cells: inside cell 2, and a quarter-cell past that cell's centre.
            repeat(9) { moveBy(Offset(g.cell * 0.25f, 0f)) }
        }
        waitForIdle()
        // ⭐ STILL HELD. The E is drawn where the finger has it — between whole cells, not waiting
        // for a lift and not snapped to the cell it will settle in.
        val midX = eCentreInGrid(g).x / g.cell
        assertTrue(
            "the E is drawn under the finger (≈2.75 cells), not in the cell's centre (2.5): was $midX",
            midX > 2.65f && midX < 2.85f,
        )
        // …and nothing else on the screen has changed yet: the door itself is set on the lift. Set
        // while held, it changed the words under the plan, a scrolled page shifted, and the plan
        // moved under the finger — the E hopped walls mid-drag (the first build of this, caught here).
        assertEquals("the door is set on the lift, not while held", GridDoor(DoorSide.N, 0), h.door.value)

        onNodeWithTag("editor.grid").performTouchInput { up() }
        waitForIdle()
        assertEquals("on lifting it stays where it was carried", GridDoor(DoorSide.N, 2), h.door.value)
        val settledX = eCentreInGrid(g).x / g.cell
        assertEquals("and settles into that cell's centre", 2.5f, settledX, 0.05f)
    }

    @Test
    fun `the E can be carried round the corner onto another wall`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.LIVING, 0, 0, 4, 3)), GridDoor(DoorSide.N, 1))
        setContent { Editor(h) }
        val g = gridGeometry()
        val start = eCentreInGrid(g)

        onNodeWithTag("editor.grid").performTouchInput {
            down(start)
            // Along the top towards the east…
            repeat(8) { moveBy(Offset(g.cell * 0.4f, 0f)) }
            // …and down the east side.
            repeat(6) { moveBy(Offset(0f, g.cell * 0.3f)) }
        }
        waitForIdle()
        // Still held: the E itself is already on the EAST wall — named so, and drawn in its column.
        onNodeWithContentDescription("Front door on the east wall").assertExists()
        assertEquals("drawn in the east wall's own cells", 3.5f, eCentreInGrid(g).x / g.cell, 0.1f)
        onNodeWithTag("editor.grid").performTouchInput { up() }
        waitForIdle()
        assertEquals("let go on the east wall, so the door is there", DoorSide.E, h.door.value?.side)
        onNodeWithContentDescription("Front door on the east wall").assertExists()
    }

    @Test
    fun `a tap on the E opens a note saying what it is, and another tap puts it away`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.LIVING, 0, 0, 4, 3)), GridDoor(DoorSide.N, 1))
        setContent { Editor(h) }
        val g = gridGeometry()
        onNodeWithTag(DOOR_NOTE_TAG).assertDoesNotExist()

        onNodeWithTag("editor.grid").performTouchInput { click(eCentreInGrid(g)) }
        waitForIdle()
        onNodeWithTag(DOOR_NOTE_TAG).assertExists()
        onNodeWithText("On the north wall. Drag the E to any wall to move it.").assertExists()
        assertEquals("a tap moves nothing", GridDoor(DoorSide.N, 1), h.door.value)

        onNodeWithTag(DOOR_NOTE_TAG).performClick()
        waitForIdle()
        onNodeWithTag(DOOR_NOTE_TAG).assertDoesNotExist()
    }

    @Test
    fun `in the door step the E comes to the wall under the finger on touch-down, before any lift`() = runComposeUiTest {
        val h = Harness(listOf(room("a", RoomType.LIVING, 0, 0, 4, 3)))
        setContent { Editor(h) }
        tapText("Next — set the front door")
        val g = gridGeometry()

        // Just inside the bottom wall, near the middle — and NOT lifted.
        onNodeWithTag("editor.grid").performTouchInput { down(Offset(g.cell * 1.5f, g.cell * 2.8f)) }
        waitForIdle()
        // ⭐ The E is there at once, on the wall under the finger.
        onNodeWithContentDescription("Front door on the south wall").assertExists()
        assertEquals("on the bottom wall's own row", 2.5f, eCentreInGrid(g).y / g.cell, 0.1f)
        onNodeWithTag("editor.grid").performTouchInput { moveBy(Offset(g.cell * 2f, 0f)) }
        waitForIdle()
        assertEquals("and it follows while held", 3.5f, eCentreInGrid(g).x / g.cell, 0.1f)
        // ⚠ The regression this pins: the first build SET the door on touch-down, which took the
        // "carry on without marking the door" line away under a scrolled page, moved the plan under
        // the held finger, and read this very drag as the EAST wall. Nothing may change until the lift.
        onNodeWithText("You can carry on without marking the door — we will say so on your score.").assertExists()
        assertEquals("nothing is set while held", null, h.door.value)
        onNodeWithTag("editor.grid").performTouchInput { up() }
        waitForIdle()
        assertEquals("set where it was let go", GridDoor(DoorSide.S, 3), h.door.value)
    }

    // ── helpers for the finger tests ─────────────────────────────────────────────────────────────

    /** The plan's box and its cell size, in the grid node's own pixels. */
    private class GridGeometry(val topLeft: Offset, val cell: Float)

    /**
     * ⚠ positionInRoot, NEVER boundsInRoot. This page scrolls, and boundsInRoot is CLIPPED to the
     * window: a grid whose top has scrolled out of view reports the window's edge as its top, and
     * every position measured against it comes out short. Found the hard way — the E measured 1.96
     * cells down when it was drawn at 2.5 — after a "Next" tap had scrolled the page.
     */
    private fun ComposeUiTest.gridGeometry(): GridGeometry {
        val n = onNodeWithTag("editor.grid").fetchSemanticsNode()
        // The Harness plot is 8 × 8 with square cells.
        return GridGeometry(topLeft = n.positionInRoot, cell = n.size.width / 8f)
    }

    /** Where the E's node is drawn, relative to the grid — i.e. where a finger lands on it. */
    private fun ComposeUiTest.eCentreInGrid(g: GridGeometry): Offset {
        val n = onNodeWithTag(DOOR_MARK_TAG).fetchSemanticsNode()
        return n.positionInRoot + Offset(n.size.width / 2f, n.size.height / 2f) - g.topLeft
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
