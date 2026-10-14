package com.vastufirst.app.ui.newplan

import com.vastufirst.shared.RoomType

/**
 * Change ONE room's kind, leaving every rectangle exactly where it was.
 *
 * ⭐ **Why this is a pure function and not two lines inside the Composable.** Changing a room's kind
 * changes the score — a master bedroom weighs 3.0 against a bedroom's 1.5, and four of the nineteen
 * kinds (CORRIDOR, BATHROOM, COURTYARD, UTILITY) carry no rule at all, so a room read as one of them
 * is NOT_SCORED and contributes nothing to either side of the weighted average. That is the real
 * severity of the lobby case: read as a corridor, the largest room in the flat did not merely score
 * low, it did not count. So this is a scoring operation wearing a label's clothes, and it flows
 * through the same
 * `updateRooms` → analyze → autosave path every drag does. The editor's geometry is fuzz-hardened
 * across 400 000 sequences precisely because arithmetic written inside a Composable is arithmetic
 * nothing can prove; a retype has no arithmetic, and that is exactly the claim worth pinning:
 *
 *  - every room keeps its `id`, `col`, `row`, `w` and `h` — a retype **cannot** move, resize, drop,
 *    reorder or duplicate anything, so no editor invariant can be broken by one;
 *  - the list order is preserved, so the reopen round-trip (which compares position by position)
 *    still matches;
 *  - the footprint is unchanged, so `clampDoorToRooms` is a no-op and the front door cannot move.
 *
 * Returns the **same list instance** when nothing changed (unknown id, or already that kind), so the
 * state write is equality-skipped and a no-op tap does not bump `updatedAt` on a saved home — the
 * same property `resolveGridResize` relies on.
 *
 * Only the FIRST room with [id] is changed; ids are unique by construction and a duplicate must not
 * silently retype two rooms.
 */
fun retypeRoom(rooms: List<GridRoom>, id: String, type: RoomType): List<GridRoom> {
    val i = rooms.indexOfFirst { it.id == id }
    if (i < 0 || rooms[i].type == type) return rooms
    return rooms.mapIndexed { index, room -> if (index == i) room.copy(type = type) else room }
}
