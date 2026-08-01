package com.vastufirst.shared.editor

import com.vastufirst.shared.Intent
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.RoomType
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
