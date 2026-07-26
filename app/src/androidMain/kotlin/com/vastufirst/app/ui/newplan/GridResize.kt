package com.vastufirst.app.ui.newplan

import com.vastufirst.shared.editor.CellRect
import com.vastufirst.shared.editor.fitWithoutOverlap

/**
 * The plot-resize decision, and the reopen grid-shape derivation, as PURE functions — so the
 * behaviour the owner is judging (shrink re-packs, an infeasible shrink is refused, a door on a
 * removed wall is cleared, a reopened home keeps its proportions) is unit-tested rather than left
 * inside a ViewModel that no test can reach. [NewPlanViewModel.updateGrid] / [NewPlanViewModel.load]
 * are now thin bindings of these.
 */

/** The outcome of a requested plot resize. [changed] is true only when a room moved or the door was
 *  cleared — a pure grow that shifts nothing leaves the score identical, so the caller skips the
 *  recompute (docs/PHASE-2-PROGRESS review F2). A `null` return means REFUSE (rooms can't fit). */
data class GridResizeResult(
    val cols: Int,
    val rows: Int,
    val rooms: List<GridRoom>,
    val door: GridDoor?,
    val changed: Boolean,
    /**
     * False when the plot could **not** become what was asked for because the request was clamped to
     * the [MIN_GRID]/[MAX_GRID] bound and nothing moved — i.e. the key the user pressed cannot act.
     *
     * ⚠ This exists so the plot-size keys stop failing SILENTLY. Every other refusal in this editor
     * says "no" (a move or resize that would overlap fires `haptics.reject()`), but the plot keys did
     * nothing at all — same light tap whether they worked or not — so a key at its limit, or one
     * refused because the rooms can't fit, reads as a broken button. The screen turns `false` (and a
     * `null` return) into the same "no" buzz the arrows already give.
     */
    val honoured: Boolean,
)

/**
 * Resolve a requested plot size [reqCols]×[reqRows] against the current [rooms]/[door].
 *
 * - The request is clamped to [MIN_GRID]..[MAX_GRID].
 * - Rooms are re-packed with [fitWithoutOverlap] so none overlap (shrinking + independently clamping
 *   would stack two rooms → the engine would score the buried one twice). If they cannot all fit,
 *   the whole resize is **refused** (`null`) rather than forcing an overlap.
 * - A door on a wall/position the new size no longer has is cleared.
 *
 * Returns `null` to mean "don't change anything" (an infeasible shrink). A same-size request returns
 * a `changed = false` result (a no-op the caller can apply harmlessly).
 */
fun resolveGridResize(
    rooms: List<GridRoom>,
    door: GridDoor?,
    curCols: Int,
    curRows: Int,
    reqCols: Int,
    reqRows: Int,
): GridResizeResult? {
    val c = reqCols.coerceIn(MIN_GRID, MAX_GRID)
    val r = reqRows.coerceIn(MIN_GRID, MAX_GRID)
    // Nothing to do. `honoured` is false when that is because the request was CLAMPED to a bound
    // (pressing "narrower" at MIN_GRID) rather than because it asked for the size already showing.
    if (c == curCols && r == curRows) {
        return GridResizeResult(
            curCols, curRows, rooms, door,
            changed = false, honoured = reqCols == c && reqRows == r,
        )
    }

    val fitted = fitWithoutOverlap(rooms.map { CellRect(it.col, it.row, it.w, it.h) }, c, r) ?: return null
    var changed = false
    val repacked = rooms.mapIndexed { i, room ->
        val f = fitted[i]
        room.copy(col = f.col, row = f.row, w = f.w, h = f.h)
    }
    val newRooms = if (repacked != rooms) { changed = true; repacked } else rooms

    var newDoor = door
    door?.let { d ->
        val fits = when (d.side) {
            DoorSide.N, DoorSide.S -> d.cell in 0 until c
            DoorSide.E, DoorSide.W -> d.cell in 0 until r
        }
        if (!fits) { newDoor = null; changed = true }
    }
    // Re-clamp the surviving door onto the REPACKED footprint (the bbox the engine scores). A resize
    // that repacks rooms can shrink/shift the footprint past the door — without this it would display
    // in one place but be scored/reloaded in another (the F4 class of bug, on the plot-resize path
    // that F4 missed). Same clamp updateRooms applies on a room edit. Found by the fuzz harness.
    newDoor = clampDoorToRooms(newDoor, newRooms)
    if (newDoor != door) changed = true
    // The plot did move to (a clamped version of) what was asked for, so the key acted — even if the
    // request overshot the range, the user sees the plot change and needs no "no".
    return GridResizeResult(c, r, newRooms, newDoor, changed, honoured = true)
}

/**
 * The plot shape to show when a saved home is reopened. The plot proportions are NOT stored on the
 * engine [com.vastufirst.shared.Plan] (the engine scores only the rooms' footprint), so on reopen we
 * re-derive the **tightest** grid that encloses the reopened rooms, clamped to [MIN_GRID]..[MAX_GRID].
 *
 * ⚠ KNOWN LIMITATION (UAT S2/I5): any empty margin the user drew *beyond* the rooms is not restored —
 * a 10-wide plot whose rooms only reach column 6 reopens ~6 wide. The rooms and the score are exact;
 * only the surrounding canvas margin is lost. Pinned by [GridResizeTest] so it is a deliberate,
 * documented choice, and a candidate for the "persist plot shape" fix if the owner wants the margin back.
 */
fun gridSizeForRooms(rooms: List<GridRoom>): Pair<Int, Int> {
    val cols = (rooms.maxOfOrNull { it.col + it.w } ?: GRID).coerceIn(MIN_GRID, MAX_GRID)
    val rows = (rooms.maxOfOrNull { it.row + it.h } ?: GRID).coerceIn(MIN_GRID, MAX_GRID)
    return cols to rows
}

/**
 * Keep an existing door on the current rooms' FOOTPRINT (the bounding box the engine actually scores),
 * or clear it when there are no rooms. UAT F4: removing or moving a room can shrink the footprint past
 * where the door sits; without this the door would keep its displayed position but be SCORED (and
 * reloaded) at the clamped position — it appears to jump when the home is reopened. This is the same
 * clamp [placeDoor] applies at placement and [doorGeometry] applies when the plan is built, so
 * displayed == scored == reloaded after any room edit too (the C15 guarantee, extended to edits).
 */
fun clampDoorToRooms(door: GridDoor?, rooms: List<GridRoom>): GridDoor? {
    val d = door ?: return null
    if (rooms.isEmpty()) return null
    val minC = rooms.minOf { it.col }; val maxC = rooms.maxOf { it.col + it.w }
    val minR = rooms.minOf { it.row }; val maxR = rooms.maxOf { it.row + it.h }
    return when (d.side) {
        DoorSide.N, DoorSide.S -> d.copy(cell = d.cell.coerceIn(minC, maxC - 1))
        DoorSide.E, DoorSide.W -> d.copy(cell = d.cell.coerceIn(minR, maxR - 1))
    }
}
