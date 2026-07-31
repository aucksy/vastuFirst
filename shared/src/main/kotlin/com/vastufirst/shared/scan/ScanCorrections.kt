// ScanCorrections.kt — the user overruling what we read off their plan.
//
// §6.2b is explicit that the user "must confirm or correct each one before any score is computed",
// and until now the confirmation screen offered confirmation without correction: it printed
// "we read LOBBY as Corridor", put a CHECK against it, and gave no way to answer. Asking someone to
// check something they cannot change is the same as not asking.
//
// The correction is a pure function on the outcome rather than a pile of override state in the
// screen, so what travels on to the guided grid IS the corrected reading — there is no second copy
// to keep in step, and the handover in the navigation graph needs no knowledge of this at all.
package com.vastufirst.shared.scan

import com.vastufirst.shared.RoomType

/**
 * The user says room [index] is really a [type].
 *
 * ⭐ **The asking flags are cleared, the measuring ones are not.** [RoomFlag.LOOSE_LABEL_MATCH] and
 * [RoomFlag.LOW_MODEL_CONFIDENCE] both mean "we are not sure we read this right" — a question the
 * user has now answered, so the CHECK against that row goes away and the flag stops spending the
 * attention it exists to buy. [RoomFlag.OVERLAP_TRIMMED] and [RoomFlag.OUT_OF_BOUNDS_CLAMPED] are
 * about the room's *shape*, which knowing its kind tells us nothing about, so they stay.
 *
 * Returns **this same outcome** when there is nothing to do — a refusal carries no rooms, an index
 * outside the list is a caller bug that must not crash a paid flow, and re-picking the kind a room
 * already has is a no-op the screen should not treat as an edit.
 */
fun ScanOutcome.withRoomType(index: Int, type: RoomType): ScanOutcome = when (this) {
    // Spelled out rather than folded into `?.let { copy(...) }`: inside a lambda the `copy` would be
    // resolving against a smart-cast outer receiver, which is a needless thing to be clever about in
    // a module whose only compiler is a cloud runner five minutes away.
    is ScanOutcome.Placed -> {
        val fixed = rooms.corrected(index, type)
        if (fixed == null) this else copy(rooms = fixed)
    }
    is ScanOutcome.Assisted -> {
        val fixed = rooms.corrected(index, type)
        if (fixed == null) this else copy(rooms = fixed)
    }
    is ScanOutcome.Refused -> this
}

/** The corrected list, or null when nothing changed (so the caller can hand back the same instance). */
private fun List<ScannedRoom>.corrected(index: Int, type: RoomType): List<ScannedRoom>? {
    if (index !in indices) return null
    val room = this[index]
    if (room.type == type) return null
    return mapIndexed { i, r ->
        if (i == index) r.copy(type = type, flags = r.flags - ANSWERED_BY_THE_USER) else r
    }
}

/** Flags that mean "we are unsure we read this right" — answered the moment the user picks a kind. */
private val ANSWERED_BY_THE_USER = setOf(RoomFlag.LOOSE_LABEL_MATCH, RoomFlag.LOW_MODEL_CONFIDENCE)
