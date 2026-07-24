// GridEditing.kt — the arithmetic behind the direct-manipulation floor-plan editor.
//
// WHY THIS LIVES IN `shared` AND NOT IN THE SCREEN:
// this project builds in the cloud only and has no screenshot/instrumentation harness yet, so a
// gesture written inside a Composable is a gesture nothing can prove. Every rule that decides
// WHERE a room ends up is therefore a pure function here, tested by `:shared:test` on every push
// (docs/EDITOR-REWORK-PLAN.md §4.2, §5 Build A item 1). The Composable is left holding only the
// pointer plumbing and the drawing.
//
// Zero Android, zero Compose — deliberately. `scripts/check-boundaries.sh` enforces it.
package com.vastufirst.shared.editor

import com.vastufirst.shared.Zone
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which corner of a room a drag has hold of. Handles exist only on the selected room. */
enum class Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * A room's footprint on the guided grid, in whole cells. Mirrors the `col/row/w/h` quartet of the
 * app's `GridRoom` — the engine's data contract is untouched by the editor rework, so this type
 * deliberately carries no id and no room type.
 *
 * [col]/[row] are the top-left cell; **row 0 is the NORTH edge** (rows grow southward), matching
 * both the drawn grid and the engine's pada grid.
 */
data class CellRect(val col: Int, val row: Int, val w: Int, val h: Int) {
    /** Exclusive right edge, in cell coordinates. */
    val right: Int get() = col + w

    /** Exclusive bottom edge, in cell coordinates. */
    val bottom: Int get() = row + h
}

/** Two rooms overlap only when they share AREA — sharing a wall (a touching edge) does not. */
fun CellRect.overlaps(other: CellRect): Boolean =
    col < other.right && other.col < right && row < other.bottom && other.row < bottom

/** True when [candidate] would sit on top of any of [others]. Callers exclude the room being moved. */
fun anyOverlap(candidate: CellRect, others: List<CellRect>): Boolean =
    others.any { candidate.overlaps(it) }

/** [r] clamped to sit fully inside a [cols]×[rows] grid (size capped, then position). */
private fun clampToGrid(r: CellRect, cols: Int, rows: Int): CellRect {
    val w = r.w.coerceIn(1, cols)
    val h = r.h.coerceIn(1, rows)
    return CellRect(r.col.coerceIn(0, cols - w), r.row.coerceIn(0, rows - h), w, h)
}

/** The first free top-left cell (scanning row-major) where a [w]×[h] rect fits clear of [placed]. */
private fun firstFreeSlot(w: Int, h: Int, cols: Int, rows: Int, placed: List<CellRect>): Pair<Int, Int>? {
    if (w > cols || h > rows) return null
    for (row in 0..rows - h) {
        for (col in 0..cols - w) {
            if (!anyOverlap(CellRect(col, row, w, h), placed)) return col to row
        }
    }
    return null
}

/**
 * Re-pack [rects] into a [cols]×[rows] grid so that **none overlap**, preserving each rectangle's
 * order and size where possible.
 *
 * ⚠ This is the guard for the plot-resize bug: shrinking the plot and clamping every room to the new
 * bounds *independently* can push two rooms onto the SAME cells. The editor never otherwise allows an
 * overlap, and an overlap is score-corrupting — the engine scores the buried room a second time. So
 * after any resize the rooms are run through here: an earlier rectangle keeps its clamped spot; a
 * later one that would land on top is relocated to the nearest free slot (top-left → bottom-right);
 * if the grid is too full for its size it is shrunk toward 1×1. Rooms are **never dropped**, and the
 * result is 1:1 with the input in the same order (so callers can re-attach id/type by index).
 */
fun fitWithoutOverlap(rects: List<CellRect>, cols: Int, rows: Int): List<CellRect> {
    val placed = ArrayList<CellRect>(rects.size)
    for (r in rects) {
        val clamped = clampToGrid(r, cols, rows)
        if (!anyOverlap(clamped, placed)) { placed.add(clamped); continue }
        val slot = firstFreeSlot(clamped.w, clamped.h, cols, rows, placed)
        if (slot != null) { placed.add(CellRect(slot.first, slot.second, clamped.w, clamped.h)); continue }
        // Grid too full at this size: shrink toward 1×1 until a slot opens up.
        var w = clamped.w
        var h = clamped.h
        var seated: CellRect? = null
        while (seated == null && (w > 1 || h > 1)) {
            if (w > 1) w--
            if (h > 1) h--
            firstFreeSlot(w, h, cols, rows, placed)?.let { seated = CellRect(it.first, it.second, w, h) }
        }
        // Last resort (grid genuinely can't hold even a 1×1 — needs >cols×rows rooms): keep the
        // clamped rect rather than drop the room. Unreachable for any realistic plan.
        placed.add(seated ?: firstFreeSlot(1, 1, cols, rows, placed)?.let { CellRect(it.first, it.second, 1, 1) } ?: clamped)
    }
    return placed
}

/**
 * The grips shown on the selected room.
 *
 * A cell is **34–45 dp** on the phones we support (EDITOR-REWORK-PLAN §3), so four 48 dp targets on
 * a one-cell room would bury the room and each other. A 1×1 room therefore gets a SINGLE grip, at
 * the bottom-right, and is still fully movable by dragging its body.
 */
fun handlesFor(rect: CellRect): List<Handle> =
    if (rect.w == 1 && rect.h == 1) listOf(Handle.BOTTOM_RIGHT)
    else listOf(Handle.TOP_LEFT, Handle.TOP_RIGHT, Handle.BOTTOM_LEFT, Handle.BOTTOM_RIGHT)

/** The cell-coordinate corner a handle sits on, before it is clamped into the grid for drawing. */
fun handleAnchor(rect: CellRect, handle: Handle): Pair<Int, Int> = when (handle) {
    Handle.TOP_LEFT -> rect.col to rect.row
    Handle.TOP_RIGHT -> rect.right to rect.row
    Handle.BOTTOM_LEFT -> rect.col to rect.bottom
    Handle.BOTTOM_RIGHT -> rect.right to rect.bottom
}

/**
 * Move [start] by a whole number of cells, kept inside the [grid].
 *
 * ⚠ [start] is the rectangle FROZEN at finger-down and [dCol]/[dRow] are derived from the raw
 * pointer delta — never from the previous preview. Feeding a clamped result back in is what makes a
 * dragged shape fall permanently behind the finger once it has touched a wall
 * (EDITOR-REWORK-PLAN §4.2, invariants 1 and 2). `moveBy` is pure so that property is testable.
 */
fun moveBy(start: CellRect, dCol: Int, dRow: Int, cols: Int, rows: Int = cols): CellRect = start.copy(
    col = (start.col + dCol).coerceIn(0, cols - start.w),
    row = (start.row + dRow).coerceIn(0, rows - start.h),
)

/**
 * Resize [start] by dragging one corner, keeping the OPPOSITE corner pinned.
 *
 * The clamp for a top/left edge is expressed against that opposite edge
 * (`right - minCells`, `bottom - minCells`). Clamping it to the grid instead lets a top-left drag
 * cross the bottom-right corner and invert the rectangle into a negative size — which renders as
 * nothing at all, and is the defect this signature exists to prevent.
 */
fun resizeBy(
    start: CellRect,
    handle: Handle,
    dCol: Int,
    dRow: Int,
    cols: Int,
    minCells: Int = 1,
    rows: Int = cols,
): CellRect {
    val pullsRight = handle == Handle.TOP_RIGHT || handle == Handle.BOTTOM_RIGHT
    val pullsDown = handle == Handle.BOTTOM_LEFT || handle == Handle.BOTTOM_RIGHT

    val col: Int
    val w: Int
    if (pullsRight) {
        col = start.col
        w = (start.w + dCol).coerceIn(minCells, cols - start.col)
    } else {
        col = (start.col + dCol).coerceIn(0, start.right - minCells)
        w = start.right - col
    }

    val row: Int
    val h: Int
    if (pullsDown) {
        row = start.row
        h = (start.h + dRow).coerceIn(minCells, rows - start.row)
    } else {
        row = (start.row + dRow).coerceIn(0, start.bottom - minCells)
        h = start.bottom - row
    }

    return CellRect(col, row, w, h)
}

/**
 * Snap a fractional cell delta to a whole one, with a sticky band around the current value.
 *
 * A plain `roundToInt` flickers between two cells whenever the finger rests near a .5 boundary —
 * the shape shivers and the haptic tick machine-guns. [stick] widens the exit threshold so leaving
 * the current cell takes a clear commitment, while re-entering is immediate.
 */
fun snapWithHysteresis(exactCells: Float, current: Int, stick: Float = 0.15f): Int {
    val target = exactCells.roundToInt()
    if (target == current) return current
    return if (abs(exactCells - current) > 0.5f + stick) target else current
}

/** Which grid cell a pointer coordinate falls in, clamped to the grid. */
fun cellIndex(coordPx: Float, cellPx: Float, grid: Int): Int {
    if (cellPx <= 0f) return 0
    return (coordPx / cellPx).toInt().coerceIn(0, grid - 1)
}

/**
 * The compass zone a rectangle's CENTRE sits in, on the drawn grid: thirds each way, row 0 = North,
 * column 0 = West — the same 3×3 map the engine's pada grid uses, so the chip speaks the words the
 * report will use ("North-East", "centre").
 *
 * ⚠ Honest limit: the engine scores zones on the *footprint* (the bounding box of the placed rooms),
 * not on the whole 8×8 drawing grid, and it credits the largest overlap rather than the centre. The
 * two agree once the drawing fills the grid, and can differ while it does not — recorded in
 * docs/SCORE-ACCURACY-CAVEATS.md. The chip is a placement aid, not the score.
 */
fun zoneOfRect(rect: CellRect, cols: Int, rows: Int = cols): Zone {
    fun band(centre: Float, n: Int): Int {
        val f = centre / n
        return when {
            f < 1f / 3f -> 0
            f < 2f / 3f -> 1
            else -> 2
        }
    }
    val rowBand = band(rect.row + rect.h / 2f, rows)
    val colBand = band(rect.col + rect.w / 2f, cols)
    return ZONE_MAP[rowBand][colBand]
}

/**
 * The grid lines where one third-band ends and the next begins, so the editor can draw them
 * stronger. Derived from the same cell-centre rule [zoneOfRect] uses, rather than hard-coded, so the
 * lines the user sees can never disagree with the zone the chip names. On an 8-cell grid: 3 and 5.
 */
fun bandBoundaries(grid: Int): List<Int> {
    fun bandOfCell(i: Int): Int {
        val f = (i + 0.5f) / grid
        return when {
            f < 1f / 3f -> 0
            f < 2f / 3f -> 1
            else -> 2
        }
    }
    return (1 until grid).filter { bandOfCell(it - 1) != bandOfCell(it) }
}

/** row 0 = North, col 0 = West. Identical to the engine's PadaGrid.ZONE_MAP. */
private val ZONE_MAP = arrayOf(
    arrayOf(Zone.NW, Zone.N, Zone.NE),
    arrayOf(Zone.W, Zone.BRAHMASTHAN, Zone.E),
    arrayOf(Zone.SW, Zone.S, Zone.SE),
)
