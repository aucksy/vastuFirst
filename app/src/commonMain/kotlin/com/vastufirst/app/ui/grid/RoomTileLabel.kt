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
//     5. the plan letter-code           "K", "BR", "WC" — how architects letter a cramped plan
//     6. nothing — only for a sliver too small for even one letter
//
// ⭐ Rung 5 was added by the 4 Aug 2026 audit (C1). The ladder used to end at "nothing", and at
// 200 % font that left up to 9 of 19 tray tiles as blank coloured squares on the very screen that
// says "drag each room to where it really is" — a blocker, not a nicety. A letter code is not a
// word anyone says out loud, but it IS the convention of the drawing this app imitates, and an
// ambiguous letter beats an unlabelled square every time: selecting the tile still names it in
// full. Rung 6 survives only for the genuinely impossible sliver, because drawing a glyph wider
// than its tile is the §6.7b ink-overflow defect — the one class worse than blank.
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
 * The last-resort plan letter-code — one or two capitals, the way architects letter a plan too
 * cramped for words. Used only when neither the full nor the short name fits either way up.
 * Every code is unique across the room kinds (pinned in RoomTileLabelTest), so even at the
 * smallest sizes no two DIFFERENT kinds share a label.
 */
fun RoomType.microLabel(): String = when (this) {
    RoomType.ENTRANCE -> "EN"
    RoomType.KITCHEN -> "K"
    RoomType.MASTER_BEDROOM -> "MB"
    RoomType.BEDROOM -> "BR"
    RoomType.GUEST_BEDROOM -> "GB"
    RoomType.POOJA -> "PJ"
    // "T", not "WC": the owner's own flat has a one-step-wide toilet, and at 200 % font a one-step
    // tile is narrower than a two-letter code — the exact tile this rung exists to save.
    RoomType.TOILET -> "T"
    RoomType.BATHROOM -> "B"
    RoomType.LIVING -> "L"
    RoomType.DINING -> "D"
    RoomType.STAIRCASE -> "ST"
    RoomType.STUDY -> "SD"
    RoomType.STORE -> "SR"
    RoomType.GARAGE -> "G"
    RoomType.BALCONY -> "BL"
    RoomType.BASEMENT -> "BM"
    RoomType.COURTYARD -> "CY"
    RoomType.UTILITY -> "U"
    RoomType.CORRIDOR -> "C"
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
    micro: String,
    widthPx: Float,
    heightPx: Float,
    measure: (String) -> Pair<Float, Float>,
): TileLabel? {
    if (widthPx <= 0f || heightPx <= 0f) return null
    val tall = heightPx > widthPx
    for (text in listOf(full, short, micro).distinct()) {
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
