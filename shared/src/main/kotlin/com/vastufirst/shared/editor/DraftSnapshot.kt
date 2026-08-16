package com.vastufirst.shared.editor

import com.vastufirst.shared.Intent
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.Zone
import kotlinx.serialization.Serializable

/**
 * A half-drawn home, as it is written to disk.
 *
 * WHY THIS EXISTS: until now an unfinished home lived only in a ViewModel. Android reclaims a
 * backgrounded app whenever it wants to — and on a cheap phone with 3 GB of RAM, answering a phone
 * call is enough — so a user who spent ten minutes placing rooms could come back to an empty grid
 * with no explanation and nothing to recover. A saved home was safe; the one being made was not.
 *
 * ⚠ WHY IT IS A SEPARATE TYPE and not the editor's own GridRoom. This is a FILE FORMAT: once a
 * build has written it, some phone somewhere is holding one. Renaming a field in the editor's UI
 * types would silently stop old drafts loading, and the symptom — "it lost my home again" — is
 * indistinguishable from the bug this exists to fix. Keeping the stored shape separate means that
 * breakage has to be a deliberate edit to this file. Every field carries a default for the same
 * reason: a draft written by an older build must still load into a newer one.
 */
@Serializable
data class DraftSnapshot(
    val name: String? = null,
    val intent: Intent? = null,
    val propertyType: PropertyType = PropertyType.INDEPENDENT_HOUSE,
    val north: Int = 0,
    val gridCols: Int = 8,
    val gridRows: Int = 8,
    val rooms: List<DraftRoom> = emptyList(),
    val door: DraftDoor? = null,
    /** Cells the user said are NOT part of the home — the missing corner of an L. */
    val cutOut: List<Cell> = emptyList(),
    /** Cells the user confirmed ARE part of the home, so the app does not ask about them again. */
    val kept: List<Cell> = emptyList(),
    /**
     * The optional extras — water tank, tree, road — keyed by NAME rather than by the enum itself,
     * because that enum lives in the Android module. A key this build does not recognise is ignored
     * on load rather than making the whole draft unreadable.
     */
    val siteAnswers: Map<String, Zone> = emptyMap(),
    /** Extras the user said there isn't one of. A real answer, and not the same as never asked. */
    val siteDeclined: List<String> = emptyList(),
    /**
     * ⭐ True when these rooms are still PARKED in the row a scan left them in, because the reading
     * could not work out where they go — nobody has moved one yet.
     *
     * ⚠ It was deliberately NOT stored until v0.6.6, on the grounds that a home brought back after a
     * background kill is whatever the user last left, and re-parking it would be the app forgetting
     * their work. That reasoning breaks the moment resuming becomes an ordinary thing the user does
     * on purpose — which is exactly what listing unfinished homes made it. Left out, an unplaced scan
     * picked up from the list comes back titled "Place your rooms" over a row of identical squares
     * and then asks whether the leftovers of that row are part of the home: the precise screen the
     * owner was handed for his own flat.
     *
     * It costs nothing to store, because it can only ever be true when the user has moved NOTHING —
     * any edit at all clears it before this snapshot is taken.
     *
     * A field appended LAST, with a default, so a draft written by an older build still loads.
     */
    val roomsUnplaced: Boolean = false,
    /**
     * ⭐⭐ True when these rooms were READ OFF A PHOTOGRAPH that the reader placed successfully —
     * i.e. this half-finished home came through the scan, not the grid (owner, 17 Aug 2026:
     * *"'Carry On' option from Home Screen is taking user to manual grid"*).
     *
     * ⚠ It exists to decide which SCREEN "Carry on" reopens, and nothing else. It never reaches the
     * engine, and it is not the same question as [roomsUnplaced]: a scan whose rooms could NOT be
     * placed is genuinely finished by hand on the grid, and that is the one path where the grid is
     * the right answer. Both flags stored, both meaning what they say.
     *
     * ⚠ The photograph itself is deliberately NOT stored — it never has been, and a home's plan
     * image can be several megabytes. So a resumed scan cannot show "Check what we read"; it picks
     * up at the North dial with every room, kind and door intact, which is all the score is made of.
     *
     * A field appended LAST, with a default, so a draft written by an older build still loads.
     */
    val fromScan: Boolean = false,
) {
    /** Nothing drawn yet ⇒ nothing worth restoring, and nothing worth telling the user about. */
    val isEmpty: Boolean get() = rooms.isEmpty()
}

@Serializable
data class DraftRoom(
    val id: String,
    val type: RoomType,
    val col: Int,
    val row: Int,
    val w: Int,
    val h: Int,
)

/** [side] is the wall's enum NAME rather than the enum itself: the enum lives in the Android module. */
@Serializable
data class DraftDoor(val side: String, val cell: Int)
