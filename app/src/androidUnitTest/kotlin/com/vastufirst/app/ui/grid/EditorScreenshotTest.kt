package com.vastufirst.app.ui.grid

import android.app.Application
import androidx.compose.runtime.Composable
import com.vastufirst.app.render.captureAcrossMatrix
import com.vastufirst.app.render.writeManifestAcrossMatrix
import com.vastufirst.app.ui.newplan.DoorSide
import com.vastufirst.app.ui.newplan.GridDoor
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.SamplePlans
import com.vastufirst.app.ui.scan.gridForOutcome
import com.vastufirst.app.ui.scan.scannedRooms
import com.vastufirst.app.ui.scan.toGridRooms
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.editor.Cell
import com.vastufirst.shared.scan.PlanImageType
import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.ScanBox
import com.vastufirst.shared.scan.ScanDraft
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScanOutcome
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

    /** The north-east 3×3 corner of an 8×8 home — the cells the L-shaped fixture below cuts away. */
    private val NE_CORNER: Set<Cell> =
        (5..7).flatMap { c -> (0..2).map { r -> Cell(c, r) } }.toSet()

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
     * The step tells the user "your home is outlined below, tap the wall where your front door is",
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

    /**
     * ⭐⭐ The owner's own flat, scanned — the grid he would actually land on.
     *
     * Green Court, Sector 90, Gurgaon. The captions are what his sheet prints and the rectangles are
     * what the shipped app drew from them; this renders the result through the REAL mapper, so the
     * picture moves whenever the mapper's behaviour moves.
     *
     * ⚠ It exists because none of the recorded scan fixtures print room dimensions — they are
     * synthetic or they route to Assisted — so shaping rooms to their printed sizes changed no golden
     * at all and would have shipped with nothing rendering it. His plan is the only one that
     * exercises it, and "never hand over a screen you have not looked at" applies most to the change
     * whose whole point is what the screen looks like.
     */
    @Test
    fun editor_scanned_real_plan() {
        captureAcrossMatrix("editor-scanned") { ScannedGurgaonFlat() }
        writeManifestAcrossMatrix("editor-scanned") { ScannedGurgaonFlat() }
    }

    @Composable
    private fun ScannedGurgaonFlat() {
        val draft = ScanDraft(
            planType = PlanImageType.TWO_D_PLAN,
            hasRoomLabels = true,
            planConfidence = 0.95,
            rooms = listOf(
                ScanBox("BALCONY 6'-0\" WIDE", x = 0.0, y = 0.0, w = 1.0, h = 0.3, confidence = 0.9),
                ScanBox("W.C 5'-0\"X2'-11\"", x = 0.0, y = 0.3, w = 0.4, h = 0.1, confidence = 0.9),
                ScanBox("BATH 5'-0\"X3'-8½\"", x = 0.0, y = 0.4, w = 0.4, h = 0.2, confidence = 0.9),
                ScanBox("KITCHEN 6'-11\"X9'-7\"", x = 0.0, y = 0.6, w = 0.4, h = 0.1, confidence = 0.9),
                ScanBox("BED ROOM 10'-0\"X10'-6\"", x = 0.4, y = 0.3, w = 0.6, h = 0.4, confidence = 0.9),
                ScanBox("LOBBY 10'-0\"X12'-9\"", x = 0.4, y = 0.7, w = 0.6, h = 0.2, confidence = 0.9),
                ScanBox("PASSAGE 2'-3\"X9'-6\"", x = 0.7, y = 0.9, w = 0.3, h = 0.1, confidence = 0.9),
            ),
        )
        val outcome = ScanMapper.map(draft, imageAspect = 1.0)
        val (cols, rows) = gridForOutcome(outcome)
        GuidedGridContent(
            rooms = toGridRooms(outcome.scannedRooms(), cols, rows),
            door = null,
            onRoomsChange = {},
            onDoorChange = {},
            onNext = {},
            cols = cols,
            rows = rows,
        )
    }

    /**
     * ⭐⭐ THE OWNER'S OWN FLAT, from the reply the real API gave for it.
     *
     * ⚠ This is the picture of the thing he complained about, and there was no golden of it because
     * his plan never reached the editor: fifteen named spaces against a gate of twelve meant every
     * rectangle was thrown away and the rooms were parked in a row. What must be visible here is a
     * HOME — bedrooms and a balcony along the top, the living/dining running WIDE across the middle
     * the way his sheet prints it, the master bedroom at the bottom.
     *
     * It is driven from the bundled recorded reply rather than a re-typing of it, so the picture and
     * the pinned test are looking at the same fifteen rooms.
     */
    @Test
    fun editor_scanned_owner_flat() {
        captureAcrossMatrix("editor-scanned-owner") { ScannedOwnerFlat() }
        writeManifestAcrossMatrix("editor-scanned-owner") { ScannedOwnerFlat() }
    }

    @Composable
    private fun ScannedOwnerFlat() {
        val outcome = ScanMapper.map(
            RecordedScans.load(RecordedScans.OWNER_FLAT)!!.reply,
            imageAspect = 646.0 / 1400.0,
        )
        val (cols, rows) = gridForOutcome(outcome)
        GuidedGridContent(
            rooms = toGridRooms(outcome.scannedRooms(), cols, rows),
            door = null,
            onRoomsChange = {},
            onDoorChange = {},
            onNext = {},
            cols = cols,
            rows = rows,
            roomsUnplaced = outcome !is ScanOutcome.Placed,
        )
    }

    /**
     * ⭐⭐ THE PARKING ROW — the screen the owner was actually handed, and the one nothing had ever
     * photographed.
     *
     * When a scan cannot work out WHERE the rooms go it parks them as equal single squares in a row.
     * That row used to sit under the heading "Place your rooms — touch a room and slide to move it",
     * which reads as a finished plan that has gone wrong; and because the home's shape is measured
     * against the box around whatever is placed, the app then offered to cut away the leftovers of
     * the row itself and called them "the south" while they sat directly under the word NORTH.
     *
     * What this picture has to show: a heading that says these rooms are NOT placed, an instruction
     * to drag them, and **no shape question at all**. Rendered from the real 24-space floor plate,
     * which is a genuine Assisted case.
     */
    @Test
    fun editor_scanned_unplaced() {
        captureAcrossMatrix("editor-scanned-unplaced") { ScannedButUnplaced() }
        writeManifestAcrossMatrix("editor-scanned-unplaced") { ScannedButUnplaced() }
    }

    @Composable
    private fun ScannedButUnplaced() {
        // ⚠ real-unsized, not real-dense: real-dense PLACES since the lift rule went, so an
        // "unplaced" golden was being driven from a plan that is never unplaced.
        val outcome = ScanMapper.map(RecordedScans.load(RecordedScans.UNSIZED)!!.reply)
        val (cols, rows) = gridForOutcome(outcome)
        GuidedGridContent(
            rooms = toGridRooms(outcome.scannedRooms(), cols, rows),
            door = null,
            onRoomsChange = {},
            onDoorChange = {},
            onNext = {},
            cols = cols,
            rows = rows,
            roomsUnplaced = true,
        )
    }

    /**
     * ⭐⭐ THE HOME'S SHAPE — the state the biggest accuracy fix in the product lives in.
     *
     * An L-shaped home used to be handed to the engine as a filled rectangle, so its missing corner
     * was scored as though it were there. The app now asks about every empty patch instead of
     * assuming. Two goldens, because they are two different screens:
     *
     *  - `editor-shape-ask` — the question, with the corner in question highlighted on the plan. This
     *    is the one that has to survive 200 % font on a 320 dp phone: it is a heading, three lines of
     *    body and two full-width buttons appearing between the plan and the room palette.
     *  - `editor-shape-cut` — the answer taken. The corner is struck through, the home's real outline
     *    is stroked as an L, and the card names the direction. If the outline ever stops being drawn
     *    as an L, this picture says so.
     */
    @Test
    fun editor_shape_question() {
        captureAcrossMatrix("editor-shape-ask") { LShapedHome() }
        writeManifestAcrossMatrix("editor-shape-ask") { LShapedHome() }
    }

    @Test
    fun editor_shape_cut() {
        captureAcrossMatrix("editor-shape-cut") { LShapedHome(cut = true) }
        writeManifestAcrossMatrix("editor-shape-cut") { LShapedHome(cut = true) }
    }

    /** An 8×8 home whose whole north-east 3×3 corner has no room on it — the textbook L. */
    @Composable
    private fun LShapedHome(cut: Boolean = false) {
        GuidedGridContent(
            rooms = listOf(
                GridRoom("L1", RoomType.LIVING, 0, 0, 5, 4),
                GridRoom("L2", RoomType.MASTER_BEDROOM, 0, 4, 5, 4),
                GridRoom("L3", RoomType.KITCHEN, 5, 3, 3, 5),
            ),
            door = GridDoor(DoorSide.S, 2),
            onRoomsChange = {},
            onDoorChange = {},
            onNext = {},
            cutOutCells = if (cut) NE_CORNER else emptySet(),
        )
    }

    /**
     * ⭐ An unfinished home the user chose to carry on with, from the saved-homes screen (v0.6.6).
     *
     * ⚠ The card's WORDS changed with the behaviour. It used to apologise for the phone having closed
     * the app, because the app restored this work by itself; nothing comes back unless it is asked
     * for now, so the card confirms a choice instead of explaining an accident. The way out — "start
     * this home again" — is unchanged and still one tap, because tapping the wrong row must not
     * trap anybody. It is a card at the very top of the editor, pushing the plan down, which is
     * exactly the sort of change that shatters a layout at 200 % font on a 320 dp phone.
     */
    @Test
    fun editor_restored_draft() {
        captureAcrossMatrix("editor-restored") { RestoredDraft() }
        writeManifestAcrossMatrix("editor-restored") { RestoredDraft() }
    }

    @Composable
    private fun RestoredDraft() {
        GuidedGridContent(
            rooms = sample.rooms,
            door = sample.door,
            onRoomsChange = {},
            onDoorChange = {},
            onNext = {},
            restoredFromDraft = true,
        )
    }

    /**
     * ⭐⭐ THE OWNER'S OWN CASE (v0.6.6): a SCAN he never finished placing, picked up again from the
     * saved-homes list. Two states that had never been drawn together — the "carrying on with your
     * unfinished home" card AND the parking row — and it is the tallest thing this editor puts above
     * the plan, on a screen whose heading, instruction, card, button and parked squares all have to
     * survive 200 % font on a 320 dp phone.
     *
     * ⚠ It also pins the defect this release nearly shipped. "These rooms are still parked" was not
     * stored with the unfinished home, so a scan resumed from the list came back titled "Place your
     * rooms" over a row of identical squares and then asked whether the gaps between them were part
     * of the home — the exact screen he was handed for his own flat. If this picture ever shows the
     * ordinary "touch a room and slide to move it" heading, that regression is back.
     */
    @Test
    fun editor_restored_unplaced_scan() {
        captureAcrossMatrix("editor-restored-unplaced") { RestoredUnplacedScan() }
        writeManifestAcrossMatrix("editor-restored-unplaced") { RestoredUnplacedScan() }
    }

    @Composable
    private fun RestoredUnplacedScan() {
        // ⚠ real-unsized, not real-dense: real-dense PLACES since the lift rule went, so an
        // "unplaced" golden was being driven from a plan that is never unplaced.
        val outcome = ScanMapper.map(RecordedScans.load(RecordedScans.UNSIZED)!!.reply)
        val (cols, rows) = gridForOutcome(outcome)
        GuidedGridContent(
            rooms = toGridRooms(outcome.scannedRooms(), cols, rows),
            door = null,
            onRoomsChange = {},
            onDoorChange = {},
            onNext = {},
            cols = cols,
            rows = rows,
            restoredFromDraft = true,
            roomsUnplaced = true,
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
