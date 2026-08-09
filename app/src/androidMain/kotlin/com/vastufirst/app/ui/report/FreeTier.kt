package com.vastufirst.app.ui.report

import com.vastufirst.shared.Analysis
import com.vastufirst.shared.Defect
import com.vastufirst.shared.RoomResult
import com.vastufirst.shared.RoomType

/**
 * ⭐ WHAT THE FREE REPORT SHOWS — the owner's rule, 9 Aug 2026, and the ONLY place it is decided.
 *
 * The free report reads the **entrance, the kitchen and the toilets IN FULL** — the whole reason,
 * the zone's meaning, the layout change and every remedy. Everything else in the home is still
 * listed by name with its verdict, but its reasoning is behind the ₹699.
 *
 * **Why these three, so nobody "simplifies" the list later.** They are the three the tradition
 * weighs heaviest — the entrance moves the score more than any single room, the kitchen is fire
 * placed against its own quarter, and a toilet is the one placement every school agrees on. They
 * are also the three an Indian reader already has an opinion about before they open the app, which
 * means the free tier proves the product on exactly the rooms they came to check.
 *
 * **This is a gate on REASONING, never on the truth.** The score, the count of problems and every
 * room's verdict stay free. Product PRD §6.4 is binding here: *"The free tier must feel honest and
 * complete in itself. No hidden wall, no bait."* Hiding how many problems a home has in order to
 * sell the number would be exactly that bait, and we do not do it.
 *
 * Keep this file as the single source of truth. A second copy of this list somewhere in the UI is
 * how a paid room quietly becomes free, or a free one quietly becomes paid.
 */
val FREE_ROOM_TYPES: Set<RoomType> = setOf(
    RoomType.ENTRANCE,
    RoomType.KITCHEN,
    RoomType.TOILET,
)

/**
 * The front door is always free.
 *
 * It is the entrance — the same thing [RoomType.ENTRANCE] names — and it is the single
 * highest-weighted element in the whole reading, so paywalling it would hide the one finding most
 * likely to be worth acting on.
 */
const val DOOR_IS_FREE = true

/** True when this room's full reasoning is readable without paying. */
fun RoomResult.isFreeToRead(): Boolean = type in FREE_ROOM_TYPES

/**
 * True when this defect's full reasoning is readable without paying.
 *
 * A defect points at a room by id, so the room's type is what decides it. A defect with **no** room
 * behind it — the centre-of-the-home rules, a fixture, anything plan-wide — is paid: it is not one
 * of the three the owner named, and defaulting an unknown to "free" would quietly widen the free
 * tier every time the engine gains a new rule.
 */
fun Defect.isFreeToRead(rooms: List<RoomResult>): Boolean {
    val id = roomId ?: return false
    val type = rooms.firstOrNull { it.roomId == id }?.type ?: return false
    return type in FREE_ROOM_TYPES
}

/** How many findings in this analysis stay locked until the report is bought. */
fun Analysis.lockedCount(): Int =
    defects.count { !it.isFreeToRead(roomResults) } +
        roomResults.count { !it.isFreeToRead() }
