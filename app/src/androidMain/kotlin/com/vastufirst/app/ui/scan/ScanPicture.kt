// ScanPicture.kt — the photograph a scan read, and the rooms read off it, handed from screen to screen.
//
// ⭐ "CHECK WHAT WE READ" IS GONE (owner, 30 Sep 2026: *"I specially think the 'Check what we read'
// screen is not needed. We show the list of rooms detected on this screen anyways"*). This file is
// what that screen shared with the rest of the scan path and still does: the hand-over slot, the id
// that ties a scored room back to its rectangle, and the rooms in the shape the plan picture draws.
//
// Where each of that screen's own jobs went:
//   · correcting a room's kind      → the scan result, where every room is listed (folded shut);
//   · each room's printed size      → the scan result's rows, including all the parts of a room the
//                                     plan measures in several pieces;
//   · each room's direction         → the report's photograph and its room list;
//   · dragging the front-door E     → the front-door screen, one tap from the report
//                                     ("Change front door"), or the flow's own door step;
//   · where the door was read from  → the report, behind the i on "Your front door";
//   · the four optional questions   → the report, which always offered them too.
package com.vastufirst.app.ui.scan

import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.vastufirst.app.ui.common.PlanRoom
import com.vastufirst.app.ui.common.roomDisplayNames
import com.vastufirst.shared.scan.ScannedRoom

/** What a scan hands on: the picture it read, and the rooms it read off it. */
class ScanPicture(
    val imageBytes: ByteArray?,
    val rooms: List<ScannedRoom>,
) {
    /**
     * The photo, decoded once per hand-over. Shared by every screen that draws it, so the door marker
     * and the room boxes are positioned against the same picture's own pixels.
     */
    fun decodeImage(): ImageBitmap? = imageBytes?.let { bytes ->
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
    }
}

/**
 * The hand-over slot between the scan and the screens after it — a one-field singleton rather than a
 * navigation argument, because the payload is an image plus a room list and the nav graph passes
 * strings.
 *
 * ⭐ OBSERVABLE (since 16 Aug 2026). Resuming an unfinished home fills it from DISK, a read that lands a
 * few frames after the screen is already showing; a plain field would never recompose.
 *
 * ⚠ Not persisted. What is on disk is the JPEG beside the draft row (see `PlanPhotoStore`). Only a
 * route that knows which home the picture belongs to ever writes this, and every door into a
 * DIFFERENT home empties it — so whatever is in it belongs to the home on screen.
 */
class ScanPictureSlot {
    var data: ScanPicture? by mutableStateOf(null)
}

/**
 * ⭐ The id [toGridRooms] gives the scanned room at [index] — the ONE place this convention lives.
 *
 * ⚠ It is the only thread tying a SCORED room back to the rectangle it was read from, which is what
 * lets the report draw the photograph and mark the same room. Spelling it out twice is how the two
 * halves silently stop agreeing.
 */
fun scanRoomId(index: Int): String = "scan-$index"

/** Every scanned room as the plan picture needs it, in the scan's own order. */
fun planRoomsOf(rooms: List<ScannedRoom>): List<PlanRoom> {
    val names = roomDisplayNames(rooms.map { it.type })
    return rooms.mapIndexed { i, r ->
        PlanRoom(
            id = scanRoomId(i),
            type = r.type,
            name = r.label.ifBlank { names[i] },
            // ⭐ The reader's own rectangle, drawn as read. It is deliberately NOT re-shaped from the
            // size the caption prints: this box is drawn ON THE PHOTOGRAPH, and a builder's sheet is
            // not always drawn to its own captions. See [ScannedRoom.source].
            box = r.source,
        )
    }
}
