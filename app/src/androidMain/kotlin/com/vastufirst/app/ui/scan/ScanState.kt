package com.vastufirst.app.ui.scan

import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScanOutcome
import com.vastufirst.shared.scan.ScanResult
import com.vastufirst.shared.scan.ScannedRoom
import com.vastufirst.shared.editor.CellRect

/**
 * What the scan screen is showing. A plain sealed type rather than a bag of booleans, so an
 * impossible combination (reading AND showing a result) cannot be represented.
 */
sealed interface ScanUiState {
    /** Nothing chosen yet — the ask, and the offline alternative next to it. */
    data object Idle : ScanUiState

    /** The image is on its way. */
    data object Reading : ScanUiState

    /**
     * We have an answer about the plan. [ScanOutcome] says which of the three it is. [readBy] is the
     * model id that produced it, shown on the results screen so the owner's model-comparison rescans
     * are attributable (4 Aug 2026 request); null in fixtures that predate the field.
     */
    data class Done(val outcome: ScanOutcome, val readBy: String? = null) : ScanUiState

    /** ⭐ Rate-limited. A wait, not an error — see [ScanResult.Busy]. */
    data class Busy(val retryAfterSeconds: Int?) : ScanUiState

    /** Couldn't reach the reader at all. The guided grid is offered, and it is fully offline. */
    data object Unavailable : ScanUiState

    /** The picked file could not be turned into an image (a corrupt or password-locked PDF). */
    data object BadImage : ScanUiState

    /**
     * ⭐ This build was made without the plan-reading key, so it cannot read a plan at all — and it
     * says so, out loud, on the screen.
     *
     * It exists because of a specific failure: v0.3.14 and v0.3.15 shipped with a stand-in reader
     * that replayed four recorded readings on a loop. Three of the four were the same test plan, so
     * every upload produced the same room list and the owner reasonably concluded the feature was
     * broken. The screen looked exactly like a working one. **A build that cannot do the thing must
     * never be indistinguishable from a build that can**, so "no key" is a visible state rather than
     * a silent substitution.
     */
    data object NotConfigured : ScanUiState
}

/**
 * Scanned rooms → the editor's [GridRoom]s.
 *
 * ⚠ **Assisted rooms carry no geometry**, because on most real plans the model does not know where
 * the rooms are (measured three ways — docs/SCAN-PLAN-READING-PLAN.md §3h). They are laid out here in
 * a plain left-to-right strip in reading order: uniform, obviously provisional, and *visibly not a
 * floor plan*, so nobody mistakes it for one. The screen before this says so in as many words. The
 * saving is real and it is exactly the two slowest steps — working out the room list, and hunting
 * each type out of the palette — while placement, the part the model cannot do, stays with the
 * person who knows the answer.
 *
 * The strip is packed the same way whatever the plan, so it can never be read as "the AI thinks your
 * kitchen is here".
 */
fun toGridRooms(rooms: List<ScannedRoom>, cols: Int, rows: Int): List<GridRoom> {
    if (rooms.isNotEmpty() && rooms.all { it.rect != null }) {
        return rooms.mapIndexed { i, r -> r.toGridRoom(i, r.rect!!) }
    }
    // Unplaced: a provisional row of equal tiles, packed across the grid and wrapping down.
    //
    // ⭐⭐ TWO CELLS EACH WAY, and each dimension was bought with a separate defect that only the
    // rendered picture showed. Single cells were the first version — the smallest thing the editor
    // supports, unmistakably not a floor plan, unable to overlap by construction — and it failed
    // twice over:
    //
    //  · **TOO NARROW TO NAME.** Sixteen rooms arrived as sixteen blank coloured squares under an
    //    instruction to "drag each one to where it really is". The user could not tell which one was
    //    the kitchen. Two cells across fits every name the palette offers.
    //  · **TOO SMALL TO TAP.** On a 412 dp screen one cell of a ten-wide plan is about 36 dp against
    //    a 48 dp touch floor, so every tile was an undersized target — the person this app is built
    //    for failing to grab the very thing they are being told to drag.
    //
    // ⚠ The geometry gate COUNTED the second one: 203 findings across the configuration matrix,
    // nearly all of them the touch floor. It then ADOPTED that number as this screen's baseline
    // without complaint, because the ratchet only fails a count that RISES — so a defect present
    // from a screen's very first render is blessed as normal. Widening alone did not move it; the
    // HEIGHT was what the floor was failing.
    //
    // Two by two still reads as a uniform holding block rather than a plan — equal tiles, and the
    // heading above says outright that they are not placed — still cannot overlap, and on the 10 × 10
    // grid an unplaced scan always draws on it holds twenty-five rooms, more than any real plan.
    val w = if (cols >= 2) 2 else 1
    val h = if (rows >= 2) 2 else 1
    var col = 0
    var row = 0
    return rooms.mapIndexedNotNull { i, r ->
        if (row + h > rows) return@mapIndexedNotNull null   // out of canvas; the rest stay unplaced
        val cell = CellRect(col, row, w, h)
        col += w
        if (col + w > cols) { col = 0; row += h }
        r.toGridRoom(i, cell)
    }
}

private fun ScannedRoom.toGridRoom(index: Int, rect: CellRect) = GridRoom(
    id = "scan-$index",
    type = type,
    col = rect.col,
    row = rect.row,
    w = rect.w,
    h = rect.h,
)

/** The grid a scan's rooms should be drawn on. Placed reads carry their own; unplaced use the default. */
fun gridForOutcome(outcome: ScanOutcome): Pair<Int, Int> = when (outcome) {
    is ScanOutcome.Placed -> outcome.cols to outcome.rows
    else -> ScanMapper.DEFAULT_GRID to ScanMapper.DEFAULT_GRID
}

/** The rooms an outcome carries, if any. A refusal carries none — there was nothing to read. */
fun ScanOutcome.scannedRooms(): List<ScannedRoom> = when (this) {
    is ScanOutcome.Placed -> rooms
    is ScanOutcome.Assisted -> rooms
    is ScanOutcome.Refused -> emptyList()
}
