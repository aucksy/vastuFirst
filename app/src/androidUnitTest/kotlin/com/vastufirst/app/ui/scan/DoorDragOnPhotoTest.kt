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
 * on the photograph screens: *"its not moving real-time when holding and dragging it.. currently
 * it follows after you have dragged it.. and I am also not able to move it to other walls.. its stuck
 * on same wall where you put it.. it should not be locked behind a button... user can just tap and
 * drag it... on first tap and pop up can tell them what it is"*.
 *
 * Every drag test here FAILED on the build before the fix: "Where is your front door?" understood no
 * drag at all. (The same fix reached "Check what we read"; that screen and its two tests went on
 * 30 Sep 2026, and the front-door screen is where the E is dragged now — from the flow, or from the
 * report's "Change front door".)
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
    fun `on the front-door screen the drag hint appears only once there is an E to drag`() = runComposeUiTest {
        val door = mutableStateOf<GridDoor?>(null)
        val image = photo()
        setContent {
            VastuTheme { ScanDoorContent(image = image, rooms = rooms, door = door.value, onDoor = { door.value = it }) }
        }
        // No door yet, so no E on the plan: the line must not send anybody looking for one.
        onNodeWithText("Tap the wall you walk in through.").assertExists()
        onNodeWithText("drag the E", substring = true).assertDoesNotExist()

        val plan = picture("Your plan.")
        pictureNode("Your plan.").performTouchInput { click(Offset(plan.width * 0.4f, plan.height * 0.73f)) }
        waitForIdle()
        onNodeWithText("Tap the wall you walk in through, or drag the E.").assertExists()
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
