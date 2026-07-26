package com.vastufirst.app.ui.grid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import com.vastufirst.app.ui.common.GRID_ROOM_TYPES
import com.vastufirst.app.ui.common.editorColor
import com.vastufirst.app.ui.common.label
import com.vastufirst.app.ui.common.screenRoot
import com.vastufirst.app.ui.common.short
import com.vastufirst.app.ui.common.spoken
import com.vastufirst.app.ui.newplan.DoorSide
import com.vastufirst.app.ui.newplan.GRID
import com.vastufirst.app.ui.newplan.doorMarkerCell
import com.vastufirst.app.ui.newplan.GridDoor
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.VastuChip
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.editor.CellRect
import com.vastufirst.shared.editor.Handle
import com.vastufirst.shared.editor.anyOverlap
import com.vastufirst.shared.editor.bandBoundaries
import com.vastufirst.shared.editor.cellIndex
import com.vastufirst.shared.editor.handleAnchor
import com.vastufirst.shared.editor.handlesFor
import com.vastufirst.shared.editor.moveBy
import com.vastufirst.shared.editor.resizeBy
import com.vastufirst.shared.editor.snapWithHysteresis
import com.vastufirst.shared.editor.zoneOfRect
import kotlin.math.min
import kotlin.math.roundToInt

/** The dragged room lifts very slightly off the plan — enough to read as "held", not as a zoom. */
private const val LIFT_SCALE = 1.02f

private fun GridRoom.rect() = CellRect(col, row, w, h)
private fun GridRoom.withRect(r: CellRect) = copy(col = r.col, row = r.row, w = r.w, h = r.h)

/**
 * A gesture in flight (EDITOR-REWORK-PLAN.md §4.2).
 *
 * [startRect] is FROZEN at finger-down and never mutated; [rect] and [attempted] are always derived
 * from it plus the snapped [steps], never from the previous preview — that is the single thing
 * standing between this and a shape that falls permanently behind the finger after touching a wall.
 *
 * [rect] is the last position that did NOT overlap another room (and so is what a lift commits);
 * [attempted] is where the finger actually is. They differ only while [blocked].
 */
@Immutable
private data class ActiveDrag(
    val roomId: String?,        // null = placing a brand-new room
    val type: RoomType,
    val handle: Handle?,        // null = moving the whole room
    val startRect: CellRect,
    val steps: IntOffset,
    val rect: CellRect,
    val attempted: CellRect,
    val blocked: Boolean,
)

private data class Hit(val roomId: String, val rect: CellRect, val handle: Handle?)

/**
 * Where a grip is DRAWN, and therefore where it is hit-tested — one function so the two can never
 * disagree. Centres are nudged [clampPx] inside the plan — just enough that the small drawn dot is
 * not sliced by the grid's rounded-rect clip — and NOT the old ~24 dp inward shove, which pulled the
 * dots visibly off the corners of any room hugging a wall and let the resize zones swallow the whole
 * room so a move-tap resized instead (owner report #4/#7). A wall-hugging room's grip sits on the
 * grid edge, which is fully on-screen and reachable.
 */
private fun handleCentre(
    rect: CellRect, handle: Handle, cellPx: Float, gridWpx: Float, gridHpx: Float, clampPx: Float,
): Offset {
    val (c, r) = handleAnchor(rect, handle)
    fun clamp(v: Float, extent: Float): Float {
        val lo = min(clampPx, extent / 2f)             // a very narrow plan must not invert the range
        val hi = maxOf(extent - clampPx, extent / 2f)
        return v.coerceIn(lo, hi)
    }
    return Offset(clamp(c * cellPx, gridWpx), clamp(r * cellPx, gridHpx))
}

/**
 * Hit-test priority (§4.1): the selected room's grips → room bodies, topmost first → empty space.
 * Grips are tested as CIRCLES; two square targets on a small room would both claim the same point.
 */
private fun hitTest(
    pos: Offset,
    rooms: List<GridRoom>,
    selectedId: String?,
    cellPx: Float,
    gridWpx: Float,
    gridHpx: Float,
    touchPx: Float,
    gripClampPx: Float,
): Hit? {
    // The grab-zone is never bigger than half a cell, so the four corner zones can't meet in the
    // middle of a small room and steal the move-tap — the room's centre always stays a move zone
    // (owner report #7). On roomy cells the full 24 dp target still applies.
    val radius = min(touchPx / 2f, cellPx * 0.5f)
    rooms.firstOrNull { it.id == selectedId }?.let { sel ->
        val r = sel.rect()
        // A grip only wins when the finger is CLOSER to that corner than to the room's CENTRE — so
        // the middle of a room always moves, at any room size or grid density. On a tiny (1×1) room
        // the edge-clamp otherwise pulls the single grip far enough inward to swallow the room's own
        // centre, and a move-tap resizes (owner #7). Confirmed by the fuzz harness (tools/…/sim.mjs).
        val ccx = (r.col + r.w / 2f) * cellPx
        val ccy = (r.row + r.h / 2f) * cellPx
        val dToCentre = (pos.x - ccx) * (pos.x - ccx) + (pos.y - ccy) * (pos.y - ccy)
        for (h in handlesFor(r)) {
            val centre = handleCentre(r, h, cellPx, gridWpx, gridHpx, gripClampPx)
            val dx = pos.x - centre.x
            val dy = pos.y - centre.y
            val dToGrip = dx * dx + dy * dy
            if (dToGrip <= radius * radius && dToGrip < dToCentre) return Hit(sel.id, r, h)
        }
    }
    for (room in rooms.asReversed()) {
        val r = room.rect()
        if (pos.x >= r.col * cellPx && pos.x < r.right * cellPx &&
            pos.y >= r.row * cellPx && pos.y < r.bottom * cellPx
        ) return Hit(room.id, r, null)
    }
    return null
}

/** A new room's ghost under the finger: 2×2 where it fits, trimmed at the south and east edges. */
private fun placementAt(
    pos: Offset, type: RoomType, cellPx: Float, rooms: List<GridRoom>, cols: Int, rows: Int,
): ActiveDrag {
    val col = cellIndex(pos.x, cellPx, cols)
    val row = cellIndex(pos.y, cellPx, rows)
    val rect = CellRect(col, row, min(2, cols - col), min(2, rows - row))
    val blocked = anyOverlap(rect, rooms.map { it.rect() })
    return ActiveDrag(null, type, null, rect, IntOffset.Zero, rect, rect, blocked)
}

/** A unique id that survives deleting a room and adding another in the same cell. */
private fun newRoomId(existing: List<GridRoom>): String {
    var n = existing.size
    while (existing.any { it.id == "room-$n" }) n++
    return "room-$n"
}

private fun describe(type: RoomType, rect: CellRect, cols: Int, rows: Int): String =
    "${type.label()} · ${rect.w}×${rect.h} · ${zoneOfRect(rect, cols, rows).short()}"

/**
 * Guided grid editor (§6.2a · design system screen 3) — the primary, always-available, offline
 * path, reworked for direct manipulation (docs/EDITOR-REWORK-PLAN.md Build A).
 *
 * Touch a room and keep sliding: it selects on the way down and moves in the same gesture. Pull a
 * corner grip to resize. To add a room, arm a type from the palette and press the plan — a ghost
 * follows the finger and **the lift commits it**, because a mis-tap here changes the score the
 * client is paying for (Potter/Weldon/Shneiderman, CHI '88: ~65 % fewer wrong-target errors than
 * commit-on-touch). Overlapping is refused rather than silently accepted.
 *
 * A second mode places the front door on an outer wall — the highest-weighted element the engine
 * scores. Unchanged by this rework.
 */
@Composable
fun GuidedGridScreen(
    vm: NewPlanViewModel,
    onNext: () -> Unit,
) {
    // Thin wrapper: it is the ONLY thing that touches the ViewModel, so the whole editor below can
    // be rendered headlessly from fixture state by the screenshot harness (GuidedGridContent).
    GuidedGridContent(
        rooms = vm.rooms,
        door = vm.door,
        cols = vm.gridCols,
        rows = vm.gridRows,
        onRoomsChange = vm::updateRooms,
        onDoorChange = vm::updateDoor,
        onGridChange = vm::updateGrid,
        onNext = onNext,
    )
}

/**
 * The editor as a pure function of its state — no ViewModel, no DI, no coroutine dispatcher — so a
 * JVM screenshot test can draw it from a fixture list (UI-POLISH §6). The gesture/measurement logic
 * is unchanged from Build A; only the data source (was `vm.rooms`/`vm.door`) and the two sinks (were
 * `vm.updateRooms`/`vm.updateDoor`) are now parameters.
 */
@OptIn(ExperimentalLayoutApi::class)   // FlowRow: plot-size + size steppers on one wrapping line
@Composable
fun GuidedGridContent(
    rooms: List<GridRoom>,
    door: GridDoor?,
    onRoomsChange: (List<GridRoom>) -> Unit,
    onDoorChange: (GridDoor?) -> Unit,
    onNext: () -> Unit,
    cols: Int = GRID,
    rows: Int = GRID,
    /** Returns false when the plot could not become the requested size (rooms won't fit, or the
     *  MIN_GRID/MAX_GRID limit) — the plot keys turn that into a "no" buzz instead of doing nothing. */
    onGridChange: (Int, Int) -> Boolean = { _, _ -> true },
) {
    val colors = VastuTheme.colors
    val haptics = rememberEditorHaptics()

    // Captured for the DrawScopes below, which cannot read a @Composable theme value. A bare `1f`
    // inside a draw scope is ONE PHYSICAL PIXEL — 0.33dp on a 3x phone, i.e. invisible (§5).
    val lineColor = colors.borderDefault
    val bandColor = colors.borderStrong
    val ghostColor = colors.primary
    val badColor = colors.error
    val gripFill = colors.surfaceRaised
    val gripStroke = colors.primaryDark
    val density = LocalDensity.current
    val linePx = with(density) { VastuTheme.borders.regular.toPx() }
    val bandPx = with(density) { VastuTheme.borders.strong.toPx() }
    val strokePx = with(density) { VastuTheme.borders.focus.toPx() }
    val gripRadiusPx = with(density) { VastuTheme.sizes.handleGrip.toPx() } / 2f
    val touchPx = with(density) { VastuTheme.sizes.handleTouch.toPx() }
    // shapes.sm is an 8dp corner; s2 is the same 8dp token, and a DrawScope needs it as a number.
    val cornerPx = with(density) { VastuTheme.spacing.s2.toPx() }
    val dashOnPx = with(density) { VastuTheme.spacing.s2.toPx() }
    val dashOffPx = with(density) { VastuTheme.spacing.s1.toPx() }
    // Third-band boundaries per axis (a rectangular plot has different thirds each way).
    val bandLinesX = remember(cols) { bandBoundaries(cols).toSet() }
    val bandLinesY = remember(rows) { bandBoundaries(rows).toSet() }

    // rememberSaveable so a rotation (or the OS briefly reclaiming the app) doesn't drop what the user
    // had selected / armed / the door step they were on (C15). RoomType is an enum → Serializable, so
    // the default saver handles it.
    var armedType by rememberSaveable { mutableStateOf<RoomType?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var doorMode by rememberSaveable { mutableStateOf(false) }
    // Replaced only when the SNAPPED cell changes, never per pointer event — so a drag recomposes
    // a handful of times, not sixty times a second (§4.7).
    var activeDrag by remember { mutableStateOf<ActiveDrag?>(null) }
    // Hoisted above the `when` below: created inside it, the palette's scroll position was torn
    // down and reset to the far left every time a room was placed (UI audit item 16).
    val paletteScroll = rememberScrollState()

    // Read fresh inside the gesture arbiter, which is keyed on Unit and captures this composable's
    // lambda from the first composition (see the pointerInput block). rememberUpdatedState keeps a
    // stable State whose .value is always the latest room list — the same "read live" guarantee the
    // old `vm.rooms` read gave, without the ViewModel.
    val roomsState = rememberUpdatedState(rooms)
    val selected = rooms.firstOrNull { it.id == selectedId }

    fun placeDoor(col: Int, row: Int) {
        // Read the LIVE room list, not the one captured when this screen first composed — otherwise a
        // door placed after adding rooms would clamp to a stale footprint.
        val currentRooms = roomsState.value
        if (currentRooms.isEmpty()) return
        // Nearest outer wall to the tapped cell decides the side; the parallel coord is the position.
        val distN = row; val distS = rows - 1 - row; val distW = col; val distE = cols - 1 - col
        // Clamp the position onto the ROOM FOOTPRINT (the house outline the engine actually scores is
        // the rooms' bounding box). A door tapped past the rooms is snapped to the footprint when the
        // plan is built, so without this it would appear to "jump" when the home is reopened (C15).
        val fMinC = currentRooms.minOf { it.col }; val fMaxC = currentRooms.maxOf { it.col + it.w }
        val fMinR = currentRooms.minOf { it.row }; val fMaxR = currentRooms.maxOf { it.row + it.h }
        val cCol = col.coerceIn(fMinC, fMaxC - 1)
        val cRow = row.coerceIn(fMinR, fMaxR - 1)
        val d = when (minOf(distN, distS, distW, distE)) {
            distN -> GridDoor(DoorSide.N, cCol)
            distS -> GridDoor(DoorSide.S, cCol)
            distW -> GridDoor(DoorSide.W, cRow)
            else -> GridDoor(DoorSide.E, cRow)
        }
        onDoorChange(d)
    }

    /**
     * A plot-size key press. It BUZZES when the plot could not become the requested size.
     *
     * ⚠ A plot key can decline for two invisible reasons — the rooms don't fit at that size
     * (`resolveGridResize` refuses rather than overlap them, which would make the engine score the
     * buried room twice), or the plot is already at its 4/10 limit — and it used to do nothing at all
     * in both cases, giving the same light tap as a key that worked. Every other refusal on this
     * screen already says "no" (an overlapping move or resize fires `haptics.reject()` below), so
     * these were the one control here that failed silently and read as a broken button.
     */
    fun stepPlot(c: Int, r: Int) { if (!onGridChange(c, r)) haptics.reject() }

    /** The button path for every drag action (WCAG 2.2 SC 2.5.7) — refused the same way a drag is. */
    fun applyToSelected(next: CellRect) {
        val sel = selected ?: return
        if (anyOverlap(next, rooms.filterNot { it.id == sel.id }.map { it.rect() })) {
            haptics.reject()
            return
        }
        if (next == sel.rect()) return
        onRoomsChange(rooms.map { if (it.id == sel.id) it.withRect(next) else it })
    }

    Column(
        modifier = Modifier
            .screenRoot(colors.paper)
            .verticalScroll(rememberScrollState())
            .padding(VastuTheme.spacing.s6),
    ) {
        VText(
            if (doorMode) "Mark your front door" else "Place your rooms",
            style = VastuTheme.type.h2, color = colors.textPrimary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            when {
                doorMode -> "Tap the outer wall where your main entrance is."
                selected != null -> "Drag the room to move it, or pull a corner to resize."
                armedType != null -> "Press the plan where this room goes. Slide to adjust, lift to place."
                rooms.isEmpty() -> "Pick a room below, then press the plan to place it."
                else -> "Touch a room and slide to move it, or add another below."
            },
            style = VastuTheme.type.body, color = colors.textSecondary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        // The live size/zone readout. Drawn as an overlay pinned to the TOP of the plan (below), a
        // fixed spot the hand never covers while dragging a room (§4.4) — not floating near the shape.
        // (Never name a local `drag` in this function: it would shadow the gestures `drag()` below.)
        val dragNow = activeDrag
        val chipText = when {
            dragNow != null && dragNow.blocked -> "Rooms can’t overlap"
            dragNow != null -> describe(dragNow.type, dragNow.rect, cols, rows)
            selected != null -> describe(selected.type, selected.rect(), cols, rows)
            else -> null
        }
        // The size/zone chip is NOT a reserved row here any more — an always-on 48 dp band left a big
        // blank strip above the grid at rest (owner report #1), and collapsing it made the grid lurch
        // down the instant a move began. It is now drawn as an overlay pinned to the TOP of the plan
        // (see `chipText` use inside the BoxWithConstraints below): zero layout cost, no idle blank, no
        // shift, and still pinned where the hand never covers it.

        // ⭐ The compass is LOCKED to left-to-right, no matter the app's locale. North/East/South/
        // West are cardinal directions, not text — under an RTL locale (e.g. a future Urdu build) the
        // SpaceBetween label row would mirror WEST↔EAST and the grid would flip, silently reversing
        // every Vastu direction the engine scored. The render harness caught this on the ar-XB pass.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        // NORTH above the plan, WEST · SOUTH · EAST below it. Users of a Vastu app think in
        // directions, and this is the vocabulary the report uses (§2). The labels sit outside the
        // grid box so they cost the plan no width — a cell is only 34 dp on a 320 dp phone.
        VText(
            "NORTH", style = VastuTheme.type.caption, color = colors.textTertiary,
            align = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(VastuTheme.spacing.s1))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                // Tag matches the design's data-fid so the L1 gate can find this element and prove
                // it has a non-zero size — the exact v0.2.1 defect this whole harness exists for.
                .testTag("editor.grid")
                // MANDATORY. The children are placed with Modifier.offset, which does NOT contribute
                // to the parent's measured size — so without an explicit height this Box measures to
                // the tallest child, and to ZERO when no room has been placed yet. That is the state
                // the user lands in every time: an invisible, untappable grid (UI-POLISH §3.C).
                // Square cells at the plot's true proportions (cols × rows) — a rectangular home is
                // drawn true-to-life, no forced square, no empty strip (docs/RECT-PLOT-RESEARCH.md).
                .aspectRatio(cols.toFloat() / rows.toFloat())
                .clip(VastuTheme.shapes.md)
                .background(colors.surface)
                .border(VastuTheme.borders.regular, colors.borderDefault, VastuTheme.shapes.md)
                .drawBehind {
                    val stepX = size.width / cols
                    val stepY = size.height / rows
                    // The two band boundaries per axis are drawn stronger: they are the thirds the
                    // zones are cut on, so the user can see North-East before the chip names it.
                    for (i in 1 until cols) {
                        val band = i in bandLinesX
                        drawLine(
                            if (band) bandColor else lineColor,
                            Offset(stepX * i, 0f), Offset(stepX * i, size.height),
                            strokeWidth = if (band) bandPx else linePx,
                        )
                    }
                    for (i in 1 until rows) {
                        val band = i in bandLinesY
                        drawLine(
                            if (band) bandColor else lineColor,
                            Offset(0f, stepY * i), Offset(size.width, stepY * i),
                            strokeWidth = if (band) bandPx else linePx,
                        )
                    }
                }
                // ⚠ Keyed on the PLOT SIZE (cols, rows), and NEVER on the room list. The grid size
                // can only change via the plot-size steppers — never while a finger is on the plan —
                // so re-arming the gesture detector when it changes is safe, and it is REQUIRED: the
                // block below closes over cols/rows, and pointerInput(Unit) froze them at the first
                // composition. After a plot resize the finger maths then used the OLD grid — rooms
                // placed off the visible grid, taps landing beside the room, a room unable to move
                // past the former bottom edge until the screen was left and re-entered (owner report
                // #6/#8/#9). Re-keying on (cols, rows) is what keeps the finger and the drawing in the
                // same coordinate space. The room list is still read LIVE via roomsState (below), so a
                // room edit never cancels its own in-flight drag (§4.1; UI audit item 25).
                // ⚠ Exactly ONE pointerInput on this node: two (one tap, one drag) each run their own
                // touch-slop bookkeeping, and a tap with 4 px of finger roll gets eaten by the drag.
                .pointerInput(cols, rows) {
                    awaitEachGesture {
                        // ⚠ NOTHING may return from this block before the first down. awaitEachGesture
                        // re-runs the block as soon as it returns and only suspends while a pointer is
                        // held, so an early return here is a tight infinite loop, i.e. an ANR.
                        val down = awaitFirstDown()

                        val gridWpx = size.width.toFloat()
                        val gridHpx = size.height.toFloat()
                        val cellPx = gridWpx / cols          // square cells: width/cols == height/rows
                        if (cellPx <= 0f) return@awaitEachGesture

                        // ⚠ Read the list HERE, after the down. The `rooms` PARAM is captured BY VALUE,
                        // and pointerInput is keyed on (cols, rows) — NOT the room list — so this lambda
                        // survives room edits; using the captured `rooms` would hit-test a stale plan.
                        // roomsState.value is always current (see rememberUpdatedState above).
                        val current = roomsState.value

                        // --- front door: unchanged, still a tap (rework §7 out of scope) ---
                        if (doorMode) {
                            down.consume()
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                up.consume()
                                placeDoor(
                                    cellIndex(up.position.x, cellPx, cols),
                                    cellIndex(up.position.y, cellPx, rows),
                                )
                                haptics.confirm()
                            }
                            return@awaitEachGesture
                        }

                        // --- take-off placement: the ghost follows the finger, the LIFT commits ---
                        val arming = armedType
                        if (arming != null) {
                            down.consume()
                            haptics.grab()
                            var ghost = placementAt(down.position, arming, cellPx, current, cols, rows)
                            activeDrag = ghost
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                // Recomputed from the ABSOLUTE position, so this must read the change
                                // before consuming it — a consumed change reports no movement at all.
                                val next = placementAt(change.position, arming, cellPx, current, cols, rows)
                                change.consume()
                                if (next.rect != ghost.rect) {
                                    ghost = next
                                    activeDrag = next
                                    haptics.tick()
                                }
                            }
                            activeDrag = null
                            if (ghost.blocked) {
                                haptics.reject()
                            } else {
                                val id = newRoomId(current)
                                val r = ghost.rect
                                onRoomsChange(current + GridRoom(id, arming, r.col, r.row, r.w, r.h))
                                // It lands ALREADY SELECTED: that is how the user discovers moving
                                // and resizing without ever being told "tap to select" (§4.4).
                                selectedId = id
                                armedType = null
                                haptics.confirm()
                            }
                            return@awaitEachGesture
                        }

                        // --- move / resize ---
                        val hit = hitTest(down.position, current, selectedId, cellPx, gridWpx, gridHpx, touchPx, gripRadiusPx)
                        if (hit == null) {
                            // Empty space. Deselect, and DON'T consume — a finger that starts here
                            // must still be able to scroll the page.
                            selectedId = null
                            return@awaitEachGesture
                        }
                        if (hit.roomId != selectedId) selectedId = hit.roomId
                        haptics.grab()   // on finger-DOWN: zero perceived latency, never on release

                        var overSlop = Offset.Zero
                        val slop = awaitTouchSlopOrCancellation(down.id) { change, over ->
                            // Claims the gesture from the page scroller this screen sits in, so a
                            // vertical drag inside the plan moves the room instead of scrolling.
                            change.consume()
                            overSlop = over
                        } ?: return@awaitEachGesture   // lifted before slop: it was a tap, and the
                                                       // selection above was its whole effect

                        val others = current.filterNot { it.id == hit.roomId }.map { it.rect() }
                        val type = current.first { it.id == hit.roomId }.type
                        val handle = hit.handle          // a local val, so the null-check smart-casts
                        val start = hit.rect             // FROZEN — never reassigned, never derived from
                        var state = ActiveDrag(
                            roomId = hit.roomId, type = type, handle = handle,
                            startRect = start, steps = IntOffset.Zero,
                            rect = start, attempted = start, blocked = false,
                        )
                        activeDrag = state

                        // Snapped, hysteretic, and ALWAYS derived from the frozen start rect.
                        fun advance(raw: Offset) {
                            val stepCol = snapWithHysteresis(raw.x / cellPx, state.steps.x)
                            val stepRow = snapWithHysteresis(raw.y / cellPx, state.steps.y)
                            if (stepCol == state.steps.x && stepRow == state.steps.y) return
                            val attempt =
                                if (handle == null) moveBy(start, stepCol, stepRow, cols, rows)
                                else resizeBy(start, handle, stepCol, stepRow, cols, rows = rows)
                            val bad = anyOverlap(attempt, others)
                            state = state.copy(
                                steps = IntOffset(stepCol, stepRow),
                                attempted = attempt,
                                rect = if (bad) state.rect else attempt,
                                blocked = bad,
                            )
                            activeDrag = state
                            haptics.tick()
                        }

                        var raw = overSlop      // RAW accumulator: nothing snapped is ever fed back
                        advance(raw)
                        val completed = drag(slop.id) { change ->
                            raw += change.positionChange()
                            advance(raw)
                            change.consume()
                        }

                        val landed = state
                        activeDrag = null
                        if (completed) {
                            if (landed.rect != start) {
                                onRoomsChange(
                                    current.map { if (it.id == hit.roomId) it.withRect(landed.rect) else it },
                                )
                                haptics.confirm()
                            } else if (landed.blocked) {
                                haptics.reject()
                            }
                        }
                    }
                },
        ) {
            val cell = maxWidth / cols            // square cell size in dp (width/cols == height/rows)
            val cellPx = with(density) { cell.toPx() }
            val live = activeDrag

            rooms.forEach { room ->
                key(room.id) {
                    RoomTile(
                        room = room,
                        rect = if (live?.roomId == room.id) live.rect else room.rect(),
                        cell = cell,
                        cellPx = cellPx,
                        cols = cols,
                        rows = rows,
                        selected = room.id == selectedId,
                        lifted = live?.roomId == room.id,
                        onSelect = { selectedId = room.id },
                    )
                }
            }
            door?.let { DoorMarker(it, rooms, cell, cols, rows) }

            // Ghost + grips are DRAWN, not composed: they are pointer-only affordances (so they
            // carry no semantics — TalkBack reaches every one of these actions through the buttons
            // below instead) and drawing keeps them out of the layout pass entirely.
            Canvas(Modifier.matchParentSize()) {
                val cp = size.width / cols
                val d = activeDrag
                if (d != null && (d.roomId == null || d.blocked)) {
                    val r = d.attempted
                    val topLeft = Offset(r.col * cp, r.row * cp)
                    val boxSize = Size(r.w * cp, r.h * cp)
                    val tint = if (d.blocked) badColor else ghostColor
                    drawRoundRect(
                        color = tint.copy(alpha = 0.16f), topLeft = topLeft, size = boxSize,
                        cornerRadius = CornerRadius(cornerPx),
                    )
                    drawRoundRect(
                        color = tint, topLeft = topLeft, size = boxSize,
                        cornerRadius = CornerRadius(cornerPx),
                        style = Stroke(
                            width = strokePx,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashOnPx, dashOffPx)),
                        ),
                    )
                }
                val sel = rooms.firstOrNull { it.id == selectedId } ?: return@Canvas
                val shown = if (d?.roomId == sel.id) d.rect else sel.rect()
                for (h in handlesFor(shown)) {
                    // SAME clamp the hit-test uses, so the dot is drawn exactly where a finger hits it.
                    val centre = handleCentre(shown, h, cp, size.width, size.height, gripRadiusPx)
                    // A small SOLID dot with a thin cream halo — reads as a deliberate grip on any
                    // room tint. The old large hollow ring floated over the corner and looked clunky
                    // (owner feedback, v0.2.8). Halo first (slightly larger), solid core on top.
                    drawCircle(color = gripFill, radius = gripRadiusPx, center = centre)
                    drawCircle(color = gripStroke, radius = gripRadiusPx - strokePx, center = centre)
                }
            }

            // Size/zone readout, pinned to the TOP of the plan as an overlay (owner report #1: no
            // reserved band above the grid, no layout shift when it appears). It is not interactive, so
            // it never steals a touch from the gesture arbiter on the parent; it sits at top-centre,
            // the one place the hand is never resting while dragging a room (§4.4).
            if (chipText != null) {
                VText(
                    text = chipText,
                    style = VastuTheme.type.bodySm,
                    color = colors.paper,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = VastuTheme.spacing.s2)
                        .clip(VastuTheme.shapes.full)
                        .background(if (dragNow?.blocked == true) badColor else colors.textPrimary)
                        .padding(horizontal = VastuTheme.spacing.s3, vertical = VastuTheme.spacing.s2),
                )
            }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s1))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            VText("WEST", style = VastuTheme.type.caption, color = colors.textTertiary)
            VText("SOUTH", style = VastuTheme.type.caption, color = colors.textTertiary)
            VText("EAST", style = VastuTheme.type.caption, color = colors.textTertiary)
        }
        } // end LTR lock (compass never mirrors)

        Spacer(Modifier.height(VastuTheme.spacing.s4))

        // Context toolbar. `armed` is a local val so the branch below smart-casts it.
        val armed = armedType
        when {
            selected != null -> SelectedRoomTools(
                room = selected,
                cols = cols,
                rows = rows,
                onNudge = { dc, dr -> applyToSelected(moveBy(selected.rect(), dc, dr, cols, rows)) },
                onResize = { dw, dh ->
                    val r = selected.rect()
                    applyToSelected(
                        CellRect(
                            r.col, r.row,
                            (r.w + dw).coerceIn(1, cols - r.col),
                            (r.h + dh).coerceIn(1, rows - r.row),
                        ),
                    )
                },
                onDelete = { onRoomsChange(rooms.filterNot { it.id == selected.id }); selectedId = null },
                onDone = { selectedId = null },
            )

            doorMode -> VastuButton("Done placing door", onClick = { doorMode = false }, large = false)

            armed != null -> PlacingBar(type = armed, onCancel = { armedType = null })

            else -> Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                // Plot size — draw your real proportions (a rectangular plot, square cells). The score
                // is unaffected (the engine scores the rooms' footprint), so this only shapes the
                // canvas. ONE wrapping row: wide + deep sit side by side where there's room (owner
                // report #2) and drop to two lines only on a very narrow / large-font screen — a
                // FlowRow wraps between the two groups, never inside one (§3.D preserved).
                SectionLabel("Plot size")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s4),
                    verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                        EditorKey("−", "Narrower plot") { stepPlot(cols - 1, rows) }
                        VText(
                            "$cols wide", style = VastuTheme.type.bodySm, color = colors.textSecondary,
                            maxLines = 1, align = TextAlign.Center, modifier = Modifier.widthIn(min = VastuTheme.spacing.s10),
                        )
                        EditorKey("+", "Wider plot") { stepPlot(cols + 1, rows) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                        EditorKey("−", "Shallower plot") { stepPlot(cols, rows - 1) }
                        VText(
                            "$rows deep", style = VastuTheme.type.bodySm, color = colors.textSecondary,
                            maxLines = 1, align = TextAlign.Center, modifier = Modifier.widthIn(min = VastuTheme.spacing.s10),
                        )
                        EditorKey("+", "Deeper plot") { stepPlot(cols, rows + 1) }
                    }
                }

                SectionLabel("Add a room")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(paletteScroll),
                    horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
                ) {
                    GRID_ROOM_TYPES.forEach { t ->
                        VastuChip(
                            text = t.label(),
                            selected = false,
                            onClick = { armedType = t; selectedId = null },
                        )
                    }
                }
                // The door is placed by tapping a wall, and placeDoor does nothing until a room
                // exists — so offering "Set the front door" on the empty grid is a dead end (UAT S4).
                // Show it only once there's something to attach a wall to. HIGHLIGHTED (primary) — the
                // front door is the highest-weighted thing the engine scores, and as a low-contrast
                // secondary button it read as absent (owner report #3). Once a door is set it steps
                // back to secondary, since "move it" is a lesser action than "you still need one".
                if (rooms.isNotEmpty()) {
                    VastuButton(
                        text = if (door == null) "Set the front door" else "Move the front door",
                        onClick = { doorMode = true; selectedId = null; armedType = null },
                        style = if (door == null) VastuButtonStyle.PRIMARY else VastuButtonStyle.SECONDARY,
                        large = false,
                    )
                }
            }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s4))
        VastuButton(
            "Next — mark North", onClick = onNext, enabled = rooms.isNotEmpty(),
            modifier = Modifier.testTag("editor.next"),
        )
    }
}

/**
 * One placed room. It carries the semantics for the whole tile but NOT a `clickable` — a clickable
 * child would win the hit-test against the parent's gesture arbiter and swallow every drag. The
 * `onClick` here is a semantics action only: it gives TalkBack a way in without touching pointers.
 */
@Composable
private fun BoxScope.RoomTile(
    room: GridRoom,
    rect: CellRect,
    cell: androidx.compose.ui.unit.Dp,
    cellPx: Float,
    cols: Int,
    rows: Int,
    selected: Boolean,
    lifted: Boolean,
    onSelect: () -> Unit,
) {
    val colors = VastuTheme.colors
    val tint = room.type.editorColor()
    val liftPx = with(LocalDensity.current) { VastuTheme.elevation.overlay.toPx() }
    // NOT named `shape`: inside graphicsLayer{} that would resolve to the scope's own property.
    val tileShape = VastuTheme.shapes.sm
    val zone = zoneOfRect(rect, cols, rows).short()
    val description = "${room.type.label()}, ${rect.w} by ${rect.h} cells, $zone"

    Box(
        modifier = Modifier
            // The LAMBDA offset overload: the non-lambda one reads its state during composition and
            // recomposes the tile for every pointer event (§4.7).
            .offset { IntOffset((rect.col * cellPx).roundToInt(), (rect.row * cellPx).roundToInt()) }
            .size(width = cell * rect.w, height = cell * rect.h)
            .padding(VastuTheme.spacing.s1)
            // AFTER the padding, so the lift's shadow traces the room the user can see rather than
            // a box 4dp larger on every side, and the scale grows about that same rectangle.
            .then(
                if (lifted) Modifier.graphicsLayer {
                    scaleX = LIFT_SCALE
                    scaleY = LIFT_SCALE
                    shadowElevation = liftPx
                    shape = tileShape
                } else Modifier,
            )
            .clip(tileShape)
            .background(tint.copy(alpha = if (lifted) 0.28f else 0.18f))
            .border(
                if (selected) VastuTheme.borders.focus else VastuTheme.borders.regular,
                tint, tileShape,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
                stateDescription = if (selected) "Selected" else "Not selected"
                onClick { onSelect(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        // A single cell has no room for a word at any font scale; the chip and TalkBack name it.
        if (rect.w * rect.h >= 2) {
            VText(
                room.type.label(), style = VastuTheme.type.bodySm,
                color = colors.textPrimary, maxLines = 1, align = TextAlign.Center,
            )
        }
    }
}

/** The armed-mode bar: a persistent, redundant signifier for a mode, with a one-tap way out. */
@Composable
private fun PlacingBar(type: RoomType, onCancel: () -> Unit) {
    val colors = VastuTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = VastuTheme.sizes.control)
            .clip(VastuTheme.shapes.full)
            .background(colors.textPrimary)
            .padding(start = VastuTheme.spacing.s4, end = VastuTheme.spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        VText(
            "Placing: ${type.label()}",
            style = VastuTheme.type.body, color = colors.paper, maxLines = 1,
            modifier = Modifier.padding(vertical = VastuTheme.spacing.s2),
        )
        Box(
            modifier = Modifier
                .size(VastuTheme.sizes.minTouch)
                .clip(CircleShape)
                .clickableTap(role = Role.Button, onClick = onCancel)
                .semantics { contentDescription = "Stop placing ${type.label()}" },
            contentAlignment = Alignment.Center,
        ) {
            VText("✕", style = VastuTheme.type.body, color = colors.paper)
        }
    }
}

@Composable
private fun BoxScope.DoorMarker(door: GridDoor, rooms: List<GridRoom>, cell: androidx.compose.ui.unit.Dp, cols: Int, rows: Int) {
    val colors = VastuTheme.colors
    // The door sits on the HOUSE'S outer wall — the rooms' footprint (bounding box) — NOT the plot's
    // outer edge. That is where the engine scores it (buildEnginePlan/doorGeometry use the footprint
    // edges), where placeDoor clamps it, and where it lands on reopen (the plot collapses to the
    // footprint). Drawing it on the plot edge instead left the door floating in the empty margin above/
    // beside the rooms whenever the plot was drawn larger than the house — displayed ≠ scored ≠ reloaded.
    // Found by rendering tools/grid-prototype/harness.html and looking (thin + default plans).
    val (col, row) = doorMarkerCell(door, rooms, cols, rows)
    Box(
        modifier = Modifier
            .offset(x = cell * col, y = cell * row)
            .size(cell)
            .padding(VastuTheme.spacing.s1)
            .semantics { contentDescription = "Front door on the ${door.side.spoken()} wall" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(VastuTheme.sizes.iconSm).clip(CircleShape).background(colors.secondary),
            contentAlignment = Alignment.Center,
        ) {
            VText("D", style = VastuTheme.type.caption, color = colors.onPrimary)
        }
    }
}

/**
 * The selected-room panel. The arrows and steppers are NOT a fallback: WCAG 2.2 SC 2.5.7 requires a
 * single-pointer, non-drag path for every drag action, and a position on an 8×8 grid is a pair of
 * integers — so dragging is not "essential" and the exemption does not apply. They are also the
 * path for the older and less phone-literate users who are a large part of this audience.
 */
@OptIn(ExperimentalLayoutApi::class)   // FlowRow: W + H size steppers on one wrapping line
@Composable
private fun SelectedRoomTools(
    room: GridRoom,
    cols: Int,
    rows: Int,
    onNudge: (Int, Int) -> Unit,
    onResize: (Int, Int) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = VastuTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.md)
            .background(colors.surface)
            .border(VastuTheme.borders.regular, colors.borderDefault, VastuTheme.shapes.md)
            .padding(VastuTheme.spacing.s4),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
    ) {
        VText(room.type.label(), style = VastuTheme.type.h3, color = colors.textPrimary)
        VText(
            "${room.w} × ${room.h} · ${zoneOfRect(room.rect(), cols, rows).short()}",
            style = VastuTheme.type.bodySm, color = colors.textTertiary,
        )

        // Remove / Done come FIRST, right under the room's name — the two decisions a user reaches
        // for most sit closest to the top, and the fiddly nudge/size controls follow (owner report #5).
        Row(horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            VastuButton(
                "Remove", onClick = onDelete, style = VastuButtonStyle.SECONDARY,
                large = false, modifier = Modifier.weight(1f),
            )
            VastuButton("Done", onClick = onDone, large = false, modifier = Modifier.weight(1f))
        }

        SectionLabel("Move")
        Row(horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
            EditorKey("◀", "Move left") { onNudge(-1, 0) }
            EditorKey("▲", "Move up") { onNudge(0, -1) }
            EditorKey("▼", "Move down") { onNudge(0, 1) }
            EditorKey("▶", "Move right") { onNudge(1, 0) }
        }

        // Both size controls on ONE line (owner report #5). A FlowRow wraps to two lines only on a
        // very narrow / large-font screen, and only between the W and H groups — never mid-group, so
        // a control is never clipped (the UI-POLISH §3.D guarantee, kept without forcing two rows).
        SectionLabel("Size")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s4),
            verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorKey("−", "Narrower") { onResize(-1, 0) }
                VText(
                    "W ${room.w}", style = VastuTheme.type.bodySm, color = colors.textSecondary,
                    maxLines = 1, align = TextAlign.Center, modifier = Modifier.widthIn(min = VastuTheme.spacing.s8),
                )
                EditorKey("+", "Wider") { onResize(1, 0) }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorKey("−", "Shorter") { onResize(0, -1) }
                VText(
                    "H ${room.h}", style = VastuTheme.type.bodySm, color = colors.textSecondary,
                    maxLines = 1, align = TextAlign.Center, modifier = Modifier.widthIn(min = VastuTheme.spacing.s8),
                )
                EditorKey("+", "Taller") { onResize(0, 1) }
            }
        }
    }
}

/** A square 48 dp editor key. Its glyph is decorative; the spoken label is the [description]. */
@Composable
private fun EditorKey(glyph: String, description: String, onClick: () -> Unit) {
    val colors = VastuTheme.colors
    Box(
        modifier = Modifier
            .size(VastuTheme.sizes.minTouch)
            .clip(VastuTheme.shapes.full)
            .background(colors.primary.copy(alpha = 0.14f))
            .clickableTap(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        VText(glyph, style = VastuTheme.type.body, color = colors.primaryDark, maxLines = 1)
    }
}
