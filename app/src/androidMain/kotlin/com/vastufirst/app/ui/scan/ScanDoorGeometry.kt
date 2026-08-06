// ScanDoorGeometry.kt — turning a finger on a photograph into a front door, and back again.
//
// ⭐ WHY THIS IS ITS OWN FILE AND NOT A LAMBDA IN THE SCREEN. The front door is the most heavily
// weighted single input the engine scores, and until now the only way to set it was tapping the
// GRID — our redrawing of the home. The owner asked for it on the picture instead (6 Aug 2026:
// "marking the Door and north should happen only on this actual floor plan"). A screenshot cannot
// tap, so none of this maths would be covered by the render gate; kept pure and out here, it is
// covered by ordinary tests instead.
//
// THE TWO COORDINATE SPACES, and the one bridge between them:
//   · PAGE fractions — 0..1 across the scanned image, which is what [ScannedRoom.source] carries and
//     what a tap on the photo converts to.
//   · CELL space — the drawing grid the engine is handed, which is what [GridDoor] is expressed in.
// The bridge is the HOME FRAME: the union of the rooms' page boxes is the same rectangle as the
// union of their cell rectangles, because both are the same home read two ways. Everything below is
// that one proportion, applied in one direction or the other.
package com.vastufirst.app.ui.scan

import com.vastufirst.app.ui.newplan.GridDoor
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.DoorSide
import com.vastufirst.app.ui.newplan.doorForTap
import com.vastufirst.shared.scan.ScanBox
import com.vastufirst.shared.scan.ScannedRoom

/**
 * The part of the picture the HOME occupies — the union of every room's page box.
 *
 * Null-source rooms (older recorded replies, a model that omitted the building box) contribute
 * nothing, and when NOT ONE room carries a page box the whole picture is the frame. That fallback is
 * honest rather than clever: with no page geometry at all, "the plan fills the sheet" is the only
 * assumption available, and it degrades a tap's accuracy without ever making it meaningless.
 */
fun homeFrameOnPage(rooms: List<ScannedRoom>): ScanBox {
    val boxes = rooms.mapNotNull { it.source }.filter { it.w > 0.0 && it.h > 0.0 }
    if (boxes.isEmpty()) return ScanBox(x = 0.0, y = 0.0, w = 1.0, h = 1.0)
    val x = boxes.minOf { it.x }
    val y = boxes.minOf { it.y }
    val right = boxes.maxOf { it.x + it.w }
    val bottom = boxes.maxOf { it.y + it.h }
    return ScanBox(x = x, y = y, w = right - x, h = bottom - y)
}

/** The placed rooms as the editor's own type, so the proven [doorForTap] decides the side. */
private fun placedAsGrid(rooms: List<ScannedRoom>): List<GridRoom> =
    rooms.mapIndexedNotNull { i, r ->
        r.rect?.let { GridRoom(id = "scan-$i", type = r.type, col = it.col, row = it.row, w = it.w, h = it.h) }
    }

/**
 * Which front door a tap on the PHOTO means. [pageX]/[pageY] are fractions of the image, origin
 * top-left — the same convention every box in a reply uses.
 *
 * ⚠ The side is decided by [doorForTap] and nothing else. That function already answers "which wall
 * did this finger mean" correctly for thin houses, for taps out in the margin beyond a wall, and for
 * a plot drawn larger than the home — three defects paid for in earlier releases. Re-deriving any of
 * that here would be a second implementation of the most expensive decision in the app, so this
 * converts the tap into that function's units and hands it over.
 */
fun doorForPhotoTap(pageX: Float, pageY: Float, rooms: List<ScannedRoom>): GridDoor? {
    val grid = placedAsGrid(rooms)
    if (grid.isEmpty()) return null
    val frame = homeFrameOnPage(rooms)
    if (frame.w <= 0.0 || frame.h <= 0.0) return null
    val minC = grid.minOf { it.col }
    val maxC = grid.maxOf { it.col + it.w }
    val minR = grid.minOf { it.row }
    val maxR = grid.maxOf { it.row + it.h }
    val u = (pageX - frame.x) / frame.w
    val v = (pageY - frame.y) / frame.h
    return doorForTap(
        xCells = (minC + u * (maxC - minC)).toFloat(),
        yCells = (minR + v * (maxR - minR)).toFloat(),
        rooms = grid,
    )
}

/**
 * Where to DRAW a door that is already set: its point on the photo, in page fractions.
 *
 * The return trip of [doorForPhotoTap], and deliberately the same proportion read backwards — so a
 * door the user taps is drawn under their finger rather than a few per cent away from it, which is
 * the kind of small lie that makes someone tap again and again.
 */
fun doorMarkerOnPage(door: GridDoor, rooms: List<ScannedRoom>): Pair<Float, Float>? {
    val grid = placedAsGrid(rooms)
    if (grid.isEmpty()) return null
    val frame = homeFrameOnPage(rooms)
    val minC = grid.minOf { it.col }
    val maxC = grid.maxOf { it.col + it.w }
    val minR = grid.minOf { it.row }
    val maxR = grid.maxOf { it.row + it.h }
    val acrossCols = (maxC - minC).coerceAtLeast(1)
    val acrossRows = (maxR - minR).coerceAtLeast(1)
    // The middle of the door's own cell along its wall, and hard against that wall across it.
    val alongCols = ((door.cell + 0.5f) - minC) / acrossCols
    val alongRows = ((door.cell + 0.5f) - minR) / acrossRows
    val (u, v) = when (door.side) {
        DoorSide.N -> alongCols to 0f
        DoorSide.S -> alongCols to 1f
        DoorSide.W -> 0f to alongRows
        DoorSide.E -> 1f to alongRows
    }
    return (frame.x + u.coerceIn(0f, 1f) * frame.w).toFloat() to
        (frame.y + v.coerceIn(0f, 1f) * frame.h).toFloat()
}

/** "on the west wall" — the door in the words the rest of the app uses, for a one-line statement. */
fun doorSideWords(side: DoorSide): String = when (side) {
    DoorSide.N -> "the top wall of your plan"
    DoorSide.S -> "the bottom wall of your plan"
    DoorSide.W -> "the left wall of your plan"
    DoorSide.E -> "the right wall of your plan"
}
