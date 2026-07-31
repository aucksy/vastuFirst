package com.vastufirst.app.ui.grid

import android.app.Application
import androidx.compose.runtime.Composable
import com.vastufirst.app.render.captureAcrossMatrix
import com.vastufirst.app.render.writeManifestAcrossMatrix
import com.vastufirst.app.ui.newplan.DoorSide
import com.vastufirst.app.ui.newplan.GridDoor
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.SamplePlans
import com.vastufirst.shared.RoomType
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The floor-plan editor (GuidedGridScreen / GuidedGridContent) rendered across the whole §6.4
 * matrix — the FIRST time any machine in this project has drawn this screen (EDITOR-REWORK-PLAN.md
 * Build B item 10). It renders two states that matter most:
 *
 *  - `editor`       — the sample home placed, so rooms, door, chip, selected-room tools and the
 *                     direction labels are all on screen at once.
 *  - `editor-empty` — the empty grid. This is the state the user lands in every time, and the one
 *                     that shipped INVISIBLE in v0.2.1 because the grid measured to zero height. If
 *                     this render shows a square grid with a caption, the original defect is dead.
 *
 * NATIVE graphics mode is mandatory — LEGACY renders a blank canvas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Render from a PLAIN Application, not the app's VastuApp — the editor here is stateless fixture
// content and needs no DI. Booting VastuApp would call startKoin() once per test method in the
// shared JVM and throw KoinApplicationAlreadyStartedException on the second (UI-POLISH §6 harness).
@Config(application = Application::class)
class EditorScreenshotTest {

    private val sample = SamplePlans.all.first()

    @Test
    fun editor_withRooms() {
        captureAcrossMatrix("editor") {
            GuidedGridContent(
                rooms = sample.rooms,
                door = sample.door,
                onRoomsChange = {},
                onDoorChange = {},
                onNext = {},
            )
        }
    }

    @Test
    fun editor_empty() {
        captureAcrossMatrix("editor-empty") {
            GuidedGridContent(
                rooms = emptyList(),
                door = null,
                onRoomsChange = {},
                onDoorChange = {},
                onNext = {},
            )
        }
    }

    // A rectangular plot (8 wide × 5 deep) — proves the grid draws at the true aspect ratio with
    // square cells and per-axis third-bands, not a forced square (editor Build C, rectangular plots).
    @Test
    fun editor_wide() {
        captureAcrossMatrix("editor-wide") {
            GuidedGridContent(
                rooms = emptyList(),
                door = null,
                onRoomsChange = {},
                onDoorChange = {},
                onNext = {},
                cols = 8,
                rows = 5,
            )
        }
        writeManifestAcrossMatrix("editor-wide") {
            GuidedGridContent(emptyList(), null, {}, {}, {}, cols = 8, rows = 5)
        }
    }

    /**
     * ⭐ A house drawn SMALLER than the plot, with its front door on the house's north wall.
     *
     * This is the state the v0.3.9 door-marker fix exists for, and until now **no golden rendered
     * it**: the sample home fills the 8×8 grid, so its door already sat on the footprint edge and the
     * screenshots could not tell the fixed and broken versions apart. The bug it pins is visible —
     * the "D" floating in the empty margin above the rooms instead of sitting on the house's own
     * outer wall — so a regression here is caught by looking, which is how it was found.
     */
    @Test
    fun editor_margin() {
        captureAcrossMatrix("editor-margin") { MarginHouse() }
        writeManifestAcrossMatrix("editor-margin") { MarginHouse() }
    }

    @Composable
    private fun MarginHouse(doorStep: Boolean = false) {
        GuidedGridContent(
            rooms = listOf(
                GridRoom("m1", RoomType.LIVING, 3, 3, 3, 2),
                GridRoom("m2", RoomType.KITCHEN, 6, 3, 2, 2),
            ),
            door = GridDoor(DoorSide.N, 4),
            onRoomsChange = {},
            onDoorChange = {},
            onNext = {},
            cols = 10,
            rows = 10,
            startInDoorMode = doorStep,
        )
    }

    /**
     * ⭐ The DOOR STEP, with the house drawn smaller than the plot — never rendered before this build.
     *
     * The step tells the user "your home is outlined below, tap the wall where your main entrance is",
     * and the outline it refers to is drawn only in this mode. Rendering the door step is exactly how
     * UAT S8 was found (the outline did not exist, so "the outer wall" had to be guessed), so it gets
     * a golden: if the outline ever stops being drawn, or stops hugging the rooms, it shows up here.
     */
    @Test
    fun editor_door() {
        captureAcrossMatrix("editor-door") { MarginHouse(doorStep = true) }
        writeManifestAcrossMatrix("editor-door") { MarginHouse(doorStep = true) }
    }

    /**
     * ⭐⭐ A room SELECTED — and this is the first time anything in this project has drawn it.
     *
     * The selected-room panel appears only once the user taps a room, and no golden could tap. So the
     * remove/done buttons, the move arrows and the size steppers have shipped through every build
     * unseen, contrary to CLAUDE.md §2b, and the corner grips are missing from every existing golden
     * for the same reason. It is also the panel this build adds the room-type control to, which is
     * exactly the wrong moment to keep guessing: the panel is the tallest in the editor and 200 % font
     * at 320 dp is where a new row shatters a layout.
     */
    @Test
    fun editor_selected() {
        captureAcrossMatrix("editor-selected") { SelectedHouse() }
        writeManifestAcrossMatrix("editor-selected") { SelectedHouse() }
    }

    /**
     * ⭐ The room-type list OPEN — nineteen chips wrapped, with the room's present kind marked.
     *
     * This is the state that carries the real layout risk: a wrapping row of nineteen chips at 200 %
     * font on a 320 dp screen. It is also where a contrast defect would hide, the way the CHECK pill's
     * did — an accent on a tint made from the same accent measured 3.71 : 1 against a required 4.5 and
     * had been failing since the day it was written, unnoticed because nothing had rendered it.
     */
    @Test
    fun editor_retype() {
        captureAcrossMatrix("editor-retype") { SelectedHouse(typeListOpen = true) }
        writeManifestAcrossMatrix("editor-retype") { SelectedHouse(typeListOpen = true) }
    }

    @Composable
    private fun SelectedHouse(typeListOpen: Boolean = false) {
        GuidedGridContent(
            rooms = sample.rooms,
            door = sample.door,
            onRoomsChange = {},
            onDoorChange = {},
            onNext = {},
            startSelectedId = sample.rooms.first().id,
            startTypeListOpen = typeListOpen,
        )
    }

    // L1 measurement manifests (semantics geometry) — the gate reads these to catch zero-size
    // nodes, clipped text, tiny touch targets and unreachable CTAs (UI-POLISH §6.5).
    @Test
    fun editor_manifest() {
        writeManifestAcrossMatrix("editor") {
            GuidedGridContent(sample.rooms, sample.door, {}, {}, {})
        }
    }

    @Test
    fun editor_empty_manifest() {
        writeManifestAcrossMatrix("editor-empty") {
            GuidedGridContent(emptyList(), null, {}, {}, {})
        }
    }
}
