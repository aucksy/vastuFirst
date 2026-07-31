// RoomTileLabel.kt — what a room tile prints when the room is too small for its own name.
//
// ⭐ WHY THIS EXISTS. The owner scanned a flat whose second toilet is 4'-11" wide in a home about
// 21 ft across — roughly a seventh of it. On a ten-step grid that is one step, and one step is
// 26–34 dp on a real phone, so the tile printed "To…". His words: *"Toilet is not built correctly to
// show the word Toilet."*
//
// Shrinking the text was rejected before it was written: the app is for older users reading
// directions, and a floor plan full of 8 pt labels is worse than one with none. Abbreviating
// everything was rejected too — "Corr", "Util", "Base" are not words anyone reads at a glance.
//
// What actually solves it is what architects have always done on a narrow room: TURN THE NAME ON ITS
// SIDE. A one-cell-wide toilet that is three cells deep has ninety points of length going spare and
// none across. The name is unabbreviated, it is the convention of the drawing this app is imitating,
// and it costs nothing in accessibility because the screen reader reads the tile's description, not
// its glyphs.
//
// So the rungs, in order, each one measured rather than guessed:
//
//     1. the full name, across          "Toilet"
//     2. the full name, turned          "Toilet" running down a narrow room
//     3. the short name, across         "WC"
//     4. the short name, turned
//     5. nothing — the tile still carries its colour, and selecting it names it in full below
//
// Rung 5 is unchanged behaviour for a single 1×1 cell, which genuinely has room for nothing.
package com.vastufirst.app.ui.grid

import com.vastufirst.app.ui.common.label
import com.vastufirst.shared.RoomType

/** A room name as it will actually be drawn: which words, and which way up. */
data class TileLabel(val text: String, val rotated: Boolean)

/**
 * The shorter word for a room kind, used only when the full one will not fit.
 *
 * ⚠ Deliberately tiny, and every entry is a word a person says out loud. Where no natural short form
 * exists the full name is returned and the rung simply does nothing — an invented abbreviation would
 * be a worse answer than turning the label sideways or leaving it out.
 */
fun RoomType.shortLabel(): String = when (this) {
    // "W.C" is how Indian plans print it, so this is the word the user's own drawing already uses.
    RoomType.TOILET -> "WC"
    RoomType.BATHROOM -> "Bath"
    RoomType.BEDROOM -> "Bed"
    RoomType.ENTRANCE -> "Entry"
    RoomType.COURTYARD -> "Court"
    else -> label()
}

/**
 * Choose the label for a tile [widthPx] × [heightPx], given a way to [measure] a string's drawn size
 * as (width, height) in pixels at the tile's text style.
 *
 * Pure and injectable so the decision can be unit-tested at every font scale without rendering
 * anything — the same reason the editor's arithmetic lives outside its Composable.
 *
 * Rotation is only ever offered when the tile is genuinely taller than it is wide. A turned label in
 * a square or wide room reads as a mistake, not as a drawing convention.
 */
fun chooseTileLabel(
    full: String,
    short: String,
    widthPx: Float,
    heightPx: Float,
    measure: (String) -> Pair<Float, Float>,
): TileLabel? {
    if (widthPx <= 0f || heightPx <= 0f) return null
    val tall = heightPx > widthPx
    for (text in if (short == full) listOf(full) else listOf(full, short)) {
        val (w, h) = measure(text)
        // Across: the word must fit the width AND one line must fit the height.
        if (w <= widthPx && h <= heightPx) return TileLabel(text, rotated = false)
        // Turned: the two axes swap, so the word runs down the length and the line's own height has
        // to fit ACROSS the tile. Checking that second condition is what stops a turned label
        // spilling out of a one-cell room at 200 % font, where a line is taller than a cell is wide.
        if (tall && w <= heightPx && h <= widthPx) return TileLabel(text, rotated = true)
    }
    return null
}
