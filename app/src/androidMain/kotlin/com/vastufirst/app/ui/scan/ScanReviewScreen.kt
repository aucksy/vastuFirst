// ScanReviewScreen.kt — the ON-PHOTO review: the user's own plan stays on screen, and our reading
// of it is a LIST they check against it, room by room.
//
// ⭐ WHY THIS EXISTS (owner request, 4 Aug 2026). Every complaint ever filed against a scan —
// "balcony towering over the rooms", "everything clumped left", "a huge mostly-empty grid" — was
// about OUR redrawing, never about the photo. This screen never redraws the home: the photo is the
// picture of record, the extracted rooms are a list beside it, and tapping a room tints roughly
// where on the picture it was read, so "did it find my kitchen?" is answerable at a glance.
//
// ⭐⭐ REBUILT 10 AUG 2026, to three instructions from the owner:
//   1. *"Tapping a room on the floor plan should also highlight it the same way it highlights when
//      tapping the room on the list… Build a common UI/UX for this room highlight."* Both ends now
//      call one handler, and the drawing lives in one component shared with the report.
//   2. *"The floor plan on this screen should not scroll upwards, only the list of rooms should be
//      scrollable."* The plan is pinned; the list scrolls under it.
//   3. *"try to fit more rooms in visible below."* The rows lost a line each and the plan gained a
//      height cap, so more of the list is on screen at once.
//
// ⚠ HONESTY LIMITS, stated on screen rather than hidden:
//   · The tint is APPROXIMATE. Room boxes are fractions of the BUILDING's outer wall (prompt
//     contract). Since prompt v4 (4 Aug 2026) the reply also carries the building's own box on the
//     page, and ScanMapper.pageSource composes the two. Either way the subtitle keeps saying
//     "roughly".
//   · This screen VERIFIES; it does not redraw. Since 6 Aug 2026 the scan flow never opens the
//     editor at all, so the door and North are marked on this same photograph.
//   · NO DIRECTION AND NO VERDICT APPEAR HERE, and that is not an omission. Both are worked out
//     from North, which has not been marked yet at this point in the flow — a room's direction and
//     its one-word result are shown on the report, where they are known. Showing either here would
//     mean inventing one.
package com.vastufirst.app.ui.scan

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.vastufirst.app.ui.common.PlanRoom
import com.vastufirst.app.ui.common.PlanWithRooms
import com.vastufirst.app.ui.common.editorColor
import com.vastufirst.app.ui.common.label
import com.vastufirst.app.ui.common.roomDisplayNames
import com.vastufirst.app.ui.common.screenRoot
import com.vastufirst.app.ui.grid.microLabel
import com.vastufirst.app.ui.newplan.GridDoor
import com.vastufirst.designsystem.components.GuidanceState
import com.vastufirst.designsystem.components.IconTapButton
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonInline
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.VastuRoomRow
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.scan.ScannedRoom
import kotlinx.coroutines.launch

/** What the scan hands this screen: the picture it read, and the rooms it read off it. */
class ScanReviewData(
    val imageBytes: ByteArray?,
    val rooms: List<ScannedRoom>,
) {
    /**
     * The photo, decoded once per handover. Shared with the door screen so both draw the identical
     * bitmap — a second decode would be a second object, and the door marker is positioned against
     * the picture's own pixels.
     */
    fun decodeImage(): ImageBitmap? = imageBytes?.let { bytes ->
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
    }
}

/**
 * The handover slot between the scan screen and this one — a one-field singleton rather than a
 * navigation argument, because the payload is an image plus a room list and the nav graph passes
 * strings. Written right before navigating here; never persisted.
 */
class ScanReviewHandover {
    var data: ScanReviewData? = null
}

/**
 * ⭐ The id [toGridRooms] gives the scanned room at [index] — the ONE place this convention lives.
 *
 * ⚠ It is the only thread tying a SCORED room back to the rectangle it was read from, which is what
 * lets the report draw the same photograph and tint the same room. Spelling it out twice is how the
 * two halves silently stop agreeing.
 */
fun scanRoomId(index: Int): String = "scan-$index"

/** Every scanned room as the shared plan component needs it, in the scan's own order. */
fun planRoomsOf(rooms: List<ScannedRoom>): List<PlanRoom> {
    val names = roomDisplayNames(rooms.map { it.type })
    return rooms.mapIndexed { i, r ->
        PlanRoom(
            id = scanRoomId(i),
            type = r.type,
            name = r.label.ifBlank { names[i] },
            // The printed-size box when the plan stated one, else the reader's own rectangle. Only
            // the PICTURE uses the corrected box — the front-door frame keeps `source`, deliberately.
            box = r.printedBox ?: r.source,
        )
    }
}

@Composable
fun ScanReviewScreen(
    handover: ScanReviewHandover,
    door: GridDoor?,
    onContinue: () -> Unit,
    onChangeDoor: () -> Unit,
    onBack: () -> Unit,
) {
    val data = handover.data
    val image = remember(data) { data?.decodeImage() }
    ScanReviewContent(
        image = image,
        rooms = data?.rooms.orEmpty(),
        door = door,
        onContinue = onContinue,
        onChangeDoor = onChangeDoor,
        onBack = onBack,
    )
}

/**
 * What the plan printed for this room, for the line under its name.
 *
 * ⭐ A room fused from a run of sections has no single printed size — it has all of them (the owner's
 * balcony runs the width of his flat and his sheet dimensions it in three pieces). All of them are
 * printed here, because checking our reading against the paper is this screen's entire job, and a
 * fused row that showed "no size printed" would have quietly deleted three real measurements.
 */
private fun sizeNote(room: ScannedRoom): String = when {
    room.readInParts.size > 1 -> "one space: " + room.readInParts.joinToString(" + ")
    room.printedSize.isNotBlank() -> room.printedSize
    else -> "no size printed"
}

/** The screen as a pure function of its inputs — the seam the render harness draws. */
@Composable
fun ScanReviewContent(
    image: ImageBitmap?,
    rooms: List<ScannedRoom>,
    /**
     * ⭐ The front door, when the plan told us where it is — read from its own printed ENTRY/FOYER by
     * `frontDoorFromEntrance`, never guessed. Non-null means this screen STATES it rather than
     * sending the user off to mark it; null means the next step is the asking.
     */
    door: GridDoor? = null,
    onContinue: () -> Unit = {},
    onChangeDoor: () -> Unit = {},
    onBack: () -> Unit = {},
    /** For the harness: pre-select a room so the golden shows the tint. -1 = nothing selected. */
    startSelected: Int = -1,
) {
    val colors = VastuTheme.colors
    var selected by remember { mutableStateOf(if (startSelected >= 0) scanRoomId(startSelected) else null) }
    val planRooms = remember(rooms) { planRoomsOf(rooms) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    /**
     * ⭐⭐ THE ONE HANDLER BOTH ENDS CALL. Tapping a room on the picture and tapping it in the list
     * do the identical thing — select it, and bring its row into view — because they are the same
     * function. The owner asked for exactly this ("works the same way if user taps the room in list
     * or room on floor plan"), and two handlers is how that promise quietly breaks.
     */
    fun tapRoom(id: String) {
        selected = if (selected == id) null else id
        val index = planRooms.indexOfFirst { it.id == id }
        if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
    }

    // ⚠⚠ THE VIEWPORT DECIDES HOW BIG THE PINNED PICTURE MAY BE — found by the geometry gate,
    // 10 Aug 2026, on the first render of this layout. Pinning the plan at a FIXED cap left the
    // fixed parts of a landscape screen (480 dp tall) taller than the screen itself, so the last
    // child measured to 283 x 0 dp: "Put the front door somewhere else" was in the tree and NOT ON
    // THE PAGE. That is the zero-height-grid class of defect this project shipped once already, and
    // the gate is right that it must be fixed rather than ratcheted away.
    BoxWithConstraints(Modifier.screenRoot(colors.paper)) {
    val planCap = minOf(VastuTheme.sizes.planPane, maxHeight * PLAN_MAX_VIEWPORT_SHARE)
    Column(Modifier.padding(VastuTheme.spacing.s6)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
        ) {
            IconTapButton("‹", contentDescription = "Back", onClick = onBack)
            VText("Check what we read", style = VastuTheme.type.h2, color = colors.textPrimary)
        }
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            "Your plan, as you scanned it. Tap a room — here or below — to see roughly where we read it.",
            style = VastuTheme.type.bodySm,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s3))

        // ⭐ PINNED. Outside the scrolling list on purpose (owner: "The floor plan on this screen
        // should not scroll upwards, only the list of rooms should be scrollable") — the picture is
        // what the list is being checked against, so scrolling it away defeats the screen.
        if (image != null) {
            PlanWithRooms(
                image = image,
                rooms = planRooms,
                selectedId = selected,
                onTapRoom = ::tapRoom,
                maxHeight = planCap,
            )
        } else {
            Box(
                Modifier.fillMaxWidth().aspectRatio(com.vastufirst.app.ui.common.PLAN_DEFAULT_ASPECT),
                contentAlignment = Alignment.Center,
            ) {
                GuidanceState(
                    title = "The photo could not be shown",
                    body = "Every room we read is still listed below, and your score still works.",
                )
            }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s3))
        SectionLabel("${rooms.size} rooms read from your plan")
        Spacer(Modifier.height(VastuTheme.spacing.s2))

        // ⚠ A LazyColumn is legal HERE and was not before: this screen no longer scrolls as a whole,
        // so there is no outer scroll for a lazy list to be illegally nested inside. It is what lets
        // a tap on the picture scroll the matching row into view, which a plain Column cannot do.
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
        ) {
            items(planRooms, key = { it.id }) { pr ->
                val index = planRooms.indexOf(pr)
                val room = rooms[index]
                VastuRoomRow(
                    name = pr.name,
                    code = room.type.microLabel(),
                    codeColor = room.type.editorColor(),
                    // No direction and no verdict: North is not marked yet. See the file note.
                    note = room.type.label() + " · " + sizeNote(room),
                    selected = selected == pr.id,
                    onTap = { tapRoom(pr.id) },
                )
            }
            // ⭐ THE TAIL RIDES AT THE END OF THE LIST, not pinned beneath it.
            //
            // ⚠ Pinned, it could be crushed to nothing, and was: at 200 % font in landscape the fixed
            // parts of this screen are together taller than the screen before the picture is drawn at
            // all, so whatever is measured last gets zero height. Inside the scrolling region there
            // is no "last thing left over" — the reader scrolls to it, which is exactly how they
            // reached these buttons before the plan was pinned. The plan stays pinned, which is what
            // was actually asked for.
            item {
                Spacer(Modifier.height(VastuTheme.spacing.s3))
                // ⭐⭐ WHEN THE PLAN NAMED ITS OWN ENTRANCE, WE DO NOT ASK (owner, 6 Aug 2026: "cant
                // we do it ourselves when Entry is clearly marked? we ask only if its not"). But not
                // asking is not the same as not saying: the front door is the heaviest input the
                // engine weighs, so what we read is stated in one line, with the way to change it.
                if (door != null) {
                    VText(
                        "Front door: we read it from your plan's own entrance, on ${doorSideWords(door.side)}.",
                        style = VastuTheme.type.bodySm,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(VastuTheme.spacing.s2))
                }
                // The button never promises a screen other than the one it opens (audit B2).
                VastuButton(
                    if (door != null) "These are my rooms — which way is North?"
                    else "These are my rooms — set the front door",
                    onClick = onContinue,
                )
                if (door != null) {
                    Spacer(Modifier.height(VastuTheme.spacing.s2))
                    VastuButtonInline(
                        "Put the front door somewhere else",
                        onClick = onChangeDoor,
                        style = VastuButtonStyle.SECONDARY,
                    )
                }
            }
        }
    }
    }
}

/**
 * The most of the viewport's height the pinned plan may take. A landscape phone is 480 dp tall and
 * this screen's headings, list heading and buttons need the rest; a fixed cap that is right in
 * portrait is most of the screen in landscape.
 */
private const val PLAN_MAX_VIEWPORT_SHARE = 0.38f
