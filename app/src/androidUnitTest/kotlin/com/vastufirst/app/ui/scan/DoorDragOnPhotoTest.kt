package com.vastufirst.app.ui.scan

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.toSize
import com.vastufirst.app.ui.common.DOOR_MARK_TAG
import com.vastufirst.app.ui.common.DOOR_NOTE_TAG
import com.vastufirst.app.ui.newplan.DoorSide
import com.vastufirst.app.ui.newplan.GridDoor
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.editor.CellRect
import com.vastufirst.shared.scan.ScanBox
import com.vastufirst.shared.scan.ScannedRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐⭐ THE FRONT DOOR ON THE PHOTOGRAPH, DRIVEN AS A REAL FINGER — the owner's report of 27 Sep 2026,
 * on the two photograph screens: *"its not moving real-time when holding and dragging it.. currently
 * it follows after you have dragged it.. and I am also not able to move it to other walls.. its stuck
 * on same wall where you put it.. it should not be locked behind a button... user can just tap and
 * drag it... on first tap and pop up can tell them what it is"*.
 *
 * Every drag test here FAILED on the build before the fix: "Check what we read" refused to move an E
 * that had not been tapped first, and "Where is your front door?" understood no drag at all.
 *
 * The home: two rooms side by side, 8 cells wide and 4 deep, drawn on the photograph from x 0.2 to 0.8
 * and y 0.25 to 0.75. The photograph is square, so a fraction of the picture is a fraction of its box.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class DoorDragOnPhotoTest {

    private val rooms = listOf(
        ScannedRoom(
            type = RoomType.LIVING, label = "LIVING", rect = CellRect(0, 0, 4, 4),
            source = ScanBox(x = 0.2, y = 0.25, w = 0.3, h = 0.5),
        ),
        ScannedRoom(
            type = RoomType.BEDROOM, label = "BED", rect = CellRect(4, 0, 4, 4),
            source = ScanBox(x = 0.5, y = 0.25, w = 0.3, h = 0.5),
        ),
    )

    private fun photo() =
        android.graphics.Bitmap.createBitmap(1000, 1000, android.graphics.Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.rgb(0xEF, 0xE9, 0xDA)) }
            .asImageBitmap()

    // ── "Check what we read" ─────────────────────────────────────────────────────────────────────

    @Test
    fun `on Check what we read the E drags at once and follows the finger while held`() = runComposeUiTest {
        val door = mutableStateOf<GridDoor?>(GridDoor(DoorSide.N, 1))
        val image = photo()
        setContent {
            VastuTheme {
                ScanReviewContent(image = image, rooms = rooms, door = door.value, onDoorChange = { door.value = it })
            }
        }
        val plan = picture("Your scanned plan")
        val start = eCentre() - plan.topLeft

        // No tap first. A finger on the E, and along the top wall to about two-thirds of the way.
        pictureNode("Your scanned plan").performTouchInput {
            down(start)
            repeat(7) { moveBy(Offset(plan.width * 0.05f, 0f)) }
        }
        waitForIdle()
        // Page x 0.3125 + 0.35 = 0.6625. ⭐ Drawn under the finger — NOT at the centre of the cell it
        // will settle in (0.6875), which is exactly the lag the owner saw.
        val heldX = (eCentre().x - plan.left) / plan.width
        assertEquals("the E is drawn where the finger has it", 0.6625f, heldX, 0.01f)
        assertEquals("the door itself is set on the lift", GridDoor(DoorSide.N, 1), door.value)

        // Round the corner: out past the east wall, and down it.
        pictureNode("Your scanned plan").performTouchInput {
            moveBy(Offset(plan.width * 0.2f, 0f))
            repeat(5) { moveBy(Offset(0f, plan.height * 0.05f)) }
        }
        waitForIdle()
        val cornerX = (eCentre().x - plan.left) / plan.width
        val cornerY = (eCentre().y - plan.top) / plan.height
        assertEquals("still held, the E is ON the east wall", 0.8f, cornerX, 0.01f)
        assertEquals("and has come down it with the finger", 0.5f, cornerY, 0.01f)

        pictureNode("Your scanned plan").performTouchInput { up() }
        waitForIdle()
        assertEquals("let go on the east wall, so the door is there", DoorSide.E, door.value?.side)
    }

    @Test
    fun `on Check what we read a tap on the E opens its note and the old door text is gone`() = runComposeUiTest {
        val image = photo()
        setContent {
            VastuTheme { ScanReviewContent(image = image, rooms = rooms, door = GridDoor(DoorSide.N, 1)) }
        }
        // The card and the two lines that used to fill the screen about the door.
        onNodeWithText("We found your main entrance", substring = true).assertDoesNotExist()
        onNodeWithText("Front door:", substring = true).assertDoesNotExist()
        onNodeWithTag(DOOR_NOTE_TAG).assertDoesNotExist()

        val plan = picture("Your scanned plan")
        pictureNode("Your scanned plan").performTouchInput { click(eCentre() - plan.topLeft) }
        waitForIdle()
        onNodeWithTag(DOOR_NOTE_TAG).assertExists()
        onNodeWithText("Your front door").assertExists()
        onNodeWithText(
            "We read it from your plan's own entrance, on the top wall of your plan. Drag the E to move it.",
        ).assertExists()
    }

    // ── "Where is your front door?" ──────────────────────────────────────────────────────────────

    @Test
    fun `on the front-door screen the E drags along a wall and round a corner`() = runComposeUiTest {
        val door = mutableStateOf<GridDoor?>(GridDoor(DoorSide.N, 1))
        val image = photo()
        setContent {
            VastuTheme { ScanDoorContent(image = image, rooms = rooms, door = door.value, onDoor = { door.value = it }) }
        }
        val plan = picture("Your plan.")
        val start = eCentre() - plan.topLeft

        pictureNode("Your plan.").performTouchInput {
            down(start)
            repeat(7) { moveBy(Offset(plan.width * 0.05f, 0f)) }
        }
        waitForIdle()
        val heldX = (eCentre().x - plan.left) / plan.width
        assertEquals("drawn under the finger, not snapped", 0.6625f, heldX, 0.01f)
        assertEquals("the door itself is set on the lift", GridDoor(DoorSide.N, 1), door.value)

        pictureNode("Your plan.").performTouchInput {
            moveBy(Offset(plan.width * 0.2f, 0f))
            repeat(5) { moveBy(Offset(0f, plan.height * 0.05f)) }
        }
        waitForIdle()
        assertEquals("still held, the E is ON the east wall", 0.8f, (eCentre().x - plan.left) / plan.width, 0.01f)
        pictureNode("Your plan.").performTouchInput { up() }
        waitForIdle()
        assertEquals("let go on the east wall, so the door is there", DoorSide.E, door.value?.side)
    }

    @Test
    fun `on the front-door screen a tap on another wall still places the door there`() = runComposeUiTest {
        val door = mutableStateOf<GridDoor?>(null)
        val image = photo()
        setContent {
            VastuTheme { ScanDoorContent(image = image, rooms = rooms, door = door.value, onDoor = { door.value = it }) }
        }
        val plan = picture("Your plan.")
        // Just inside the bottom wall of the home.
        pictureNode("Your plan.").performTouchInput { click(Offset(plan.width * 0.4f, plan.height * 0.73f)) }
        waitForIdle()
        assertEquals(DoorSide.S, door.value?.side)
    }

    @Test
    fun `on the front-door screen a tap on the E opens its note and moves nothing`() = runComposeUiTest {
        val door = mutableStateOf<GridDoor?>(GridDoor(DoorSide.W, 2))
        val image = photo()
        setContent {
            VastuTheme { ScanDoorContent(image = image, rooms = rooms, door = door.value, onDoor = { door.value = it }) }
        }
        val plan = picture("Your plan.")
        pictureNode("Your plan.").performTouchInput { click(eCentre() - plan.topLeft) }
        waitForIdle()
        onNodeWithTag(DOOR_NOTE_TAG).assertExists()
        assertEquals("a tap is a question, not a move", GridDoor(DoorSide.W, 2), door.value)
        // The two lines that used to sit under the plan are gone from the page.
        onNodeWithText("Marked on", substring = true).assertExists()   // …inside the note now
        onNodeWithText("YOUR FRONT DOOR").assertDoesNotExist()
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    private fun ComposeUiTest.pictureNode(descStart: String) =
        onNodeWithContentDescription(descStart, substring = true)

    /**
     * ⚠ positionInRoot and size, never boundsInRoot — which is clipped to the window, so a picture
     * partly scrolled out of view would report an edge that is not its own and every position
     * measured against it would come out short (the editor's finger test found that one).
     */
    private fun ComposeUiTest.picture(descStart: String): Rect {
        val n = pictureNode(descStart).fetchSemanticsNode()
        return Rect(n.positionInRoot, n.size.toSize())
    }

    /** The E's own node — where a finger lands on it, in root coordinates. */
    private fun ComposeUiTest.eCentre(): Offset {
        val n = onNodeWithTag(DOOR_MARK_TAG).fetchSemanticsNode()
        return (n.positionInRoot + Offset(n.size.width / 2f, n.size.height / 2f)).also {
            assertTrue("the E must be on screen to be touched", it.x > 0f && it.y > 0f)
        }
    }
}
