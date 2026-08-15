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
// ⭐⭐ THE RESULT AND THE DIRECTION ARE ON EVERY ROW, SINCE 11 AUG 2026 (owner's original list).
// The row now carries the SAME one-word result and the SAME spelled-out direction the report uses —
// they come from the identical engine result through the identical mappers, so the two screens can
// never say different things about one room.
//
// ⚠ THIS IS WHY NORTH MOVED IN FRONT OF THIS SCREEN. A room has no direction until North is marked,
// so for as long as North came afterwards there was nothing true to print here. The flow is now
// scan → mark North → this screen → the front door (only if the plan did not name it) → report.
// If anything ever moves North back behind this screen, the pills go blank, not wrong: [readings]
// is empty and the row falls back to exactly what it showed before.
//
// ⚠ The room's VERDICT does not depend on the front door, which is why it is honest to show it here
// even on the plans whose door is asked for on the NEXT screen. The engine evaluates a room from its
// type and its shape (RoomEvaluator takes the room and its polygon and nothing else); the door has
// its own reading, in its own section of the report.
//
// ⚠ HONESTY LIMITS, stated on screen rather than hidden:
//   · The tint is APPROXIMATE. Room boxes are fractions of the BUILDING's outer wall (prompt
//     contract). Since prompt v4 (4 Aug 2026) the reply also carries the building's own box on the
//     page, and ScanMapper.pageSource composes the two. Either way the subtitle keeps saying
//     "roughly".
//   · This screen VERIFIES; it does not redraw. Since 6 Aug 2026 the scan flow never opens the
//     editor at all, so the door and North are marked on this same photograph.
//   · The printed SIZE stays on the row and is never traded away for the new pills. Checking our
//     reading against the paper in the reader's hand is this screen's whole job.
package com.vastufirst.app.ui.scan

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
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
import com.vastufirst.designsystem.components.VastuRoomStatus
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.app.ui.common.roomStatus
import com.vastufirst.app.ui.common.short
import com.vastufirst.shared.Analysis
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
            // ⭐ The reader's own rectangle, drawn as read. It is deliberately NOT re-shaped from
            // the size the caption prints: this box is drawn ON THE PHOTOGRAPH, and a builder's
            // sheet is not always drawn to its own captions. See the note on [ScannedRoom.source]
            // for the room-by-room measurement against both of the owner's plans.
            box = r.source,
        )
    }
}

/**
 * ⭐ ONE ROOM'S READING, in exactly the words the report uses for it: the one-word result and the
 * direction spelled out.
 *
 * ⚠ Both are taken from the engine's own result through the SAME two mappers the report calls
 * ([roomStatus], [short]). Re-deriving either here — "it's in the north half of the picture, call it
 * North" — is how one room comes to be called two different things two screens apart, which is the
 * defect the plan's printed name already had to be fixed for once.
 */
data class RoomReading(val status: VastuRoomStatus, val direction: String)

/**
 * Every scored room's reading, keyed by the id [toGridRooms] gave it — see [scanRoomId], the one
 * place that convention lives.
 *
 * Empty before North is marked (no analysis yet, or none of the ids match), and empty is a real
 * answer: the row simply shows what it always showed. It never guesses.
 */
fun roomReadings(analysis: Analysis?): Map<String, RoomReading> =
    analysis?.roomResults.orEmpty().associate { r ->
        r.roomId to RoomReading(
            status = r.verdict.roomStatus(),
            // ⚠ Capitalised HERE and nowhere deeper, exactly as the report does it: [short] also
            // feeds running prose where "the centre" has to stay lowercase. A pill is a label.
            direction = r.zone.short().replaceFirstChar { it.uppercase() },
        )
    }

@Composable
fun ScanReviewScreen(
    handover: ScanReviewHandover,
    door: GridDoor?,
    /** The caption the door's wall was read off, when it came from one — see [ScanReviewContent]. */
    doorFromCaption: String? = null,
    /**
     * ⭐ The live reading of the home, which by this point in the flow exists: North was marked on
     * the previous screen. Null only if the engine has not answered yet, and then the rows show no
     * result rather than a wrong one.
     */
    analysis: Analysis? = null,
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
        doorFromCaption = doorFromCaption,
        readings = remember(analysis) { roomReadings(analysis) },
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
    // ⚠ Trimmed the same way the scan-result screen trims it, which this screen was not doing.
    // Indian sheets usually print the SAME measurement twice — `3.72m X 4.50m ( 12'-2" x 14'-9" )` —
    // and the repeat is what pushed this caption onto a second line on every row of a fifteen-room
    // plan. [shortSize] drops the trailing repeat and nothing else, so the number the reader is
    // checking against their own paper is untouched; a compound size that OPENS with a bracket is a
    // real measurement rather than a repeat and is left whole.
    room.printedSize.isNotBlank() -> shortSize(room.printedSize)
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
    /**
     * ⭐ The caption the wall was read off — `PORCH`, `VERANDAH 9'5"X4'6"` — when the door came from
     * a printed way-in name rather than from a room typed as an entrance. Null for the typed case,
     * and then the line says "your plan's own entrance" as it always has.
     *
     * ⚠ It is shown because the two are not the same claim. A plan that prints ENTRY has told us
     * where the door is; a plan that prints PORCH has told us where the covered way in is and we
     * have inferred the rest. Saying which one happened is what makes "change it" an informed tap
     * rather than a leap of faith — and the front door is the heaviest input the engine weighs.
     */
    doorFromCaption: String? = null,
    /**
     * ⭐ The one-word result and the direction for each room, keyed by [scanRoomId] — see
     * [roomReadings]. Empty means North is not known yet, and then the rows carry the plan's own
     * name and printed size only, exactly as they did before 11 Aug 2026.
     */
    readings: Map<String, RoomReading> = emptyMap(),
    onContinue: () -> Unit = {},
    onChangeDoor: () -> Unit = {},
    onBack: () -> Unit = {},
    /** For the harness: pre-select a room so the golden shows the tint. -1 = nothing selected. */
    startSelected: Int = -1,
) {
    val colors = VastuTheme.colors
    var selected by remember { mutableStateOf(if (startSelected >= 0) scanRoomId(startSelected) else null) }
    val planRooms = remember(rooms) { planRoomsOf(rooms) }
    // ⚠ A PLAIN SCROLLING COLUMN, NOT A LAZY LIST — and the original note on this screen was right.
    // A lazy list only composes the rows you can see, so every room below the fold is not in the
    // semantics tree at all: the geometry gate cannot measure it, the accessibility checker cannot
    // read it, and a test cannot scroll to it. Six render tests failed for exactly that reason the
    // first time this went in. The mapper refuses a plan over twenty rooms, so the longest list this
    // can ever draw is twenty rows — laziness buys nothing here and costs the checks their eyes.
    val listScroll = rememberScrollState()
    // ⚠ Declared out here, not inside the constraints block below: [tapRoom] closes over it and is
    // defined above that block.
    val rowTopMarginPx = with(LocalDensity.current) { VastuTheme.spacing.s6.roundToPx() }
    val rowY = remember { mutableStateMapOf<String, Int>() }
    val scope = rememberCoroutineScope()

    /**
     * ⭐⭐ THE ONE HANDLER BOTH ENDS CALL. Tapping a room on the picture and tapping it in the list
     * do the identical thing — select it, and bring its row into view — because they are the same
     * function. The owner asked for exactly this ("works the same way if user taps the room in list
     * or room on floor plan"), and two handlers is how that promise quietly breaks.
     */
    fun tapRoom(id: String) {
        val opening = selected != id
        selected = if (opening) id else null
        // Row positions are held in root coordinates and combined with the live scroll offset, so
        // this works however deeply the row is nested — the same arithmetic the report uses.
        if (opening) rowY[id]?.let { y ->
            scope.launch { listScroll.animateScrollTo((listScroll.value + y - rowTopMarginPx).coerceAtLeast(0)) }
        }
    }

    // ⚠⚠ THE VIEWPORT DECIDES HOW BIG THE PINNED PICTURE MAY BE — found by the geometry gate,
    // 10 Aug 2026, on the first render of this layout. Pinning the plan at a FIXED cap left the
    // fixed parts of a landscape screen (480 dp tall) taller than the screen itself, so the last
    // child measured to 283 x 0 dp: "Put the front door somewhere else" was in the tree and NOT ON
    // THE PAGE. That is the zero-height-grid class of defect this project shipped once already, and
    // the gate is right that it must be fixed rather than ratcheted away.
    BoxWithConstraints(Modifier.screenRoot(colors.paper)) {
    val planCap = minOf(VastuTheme.sizes.planPane, maxHeight * PLAN_MAX_VIEWPORT_SHARE)
    // ⚠ The bottom padding belongs to the LIST, not to this column, and that is a correctness fix
    // rather than a tidy-up. Padding the column stops the scrolling list 24 dp short of the screen
    // edge, so whichever row straddles its lower edge is cut by a CONTAINER — which the geometry
    // gate rightly reports as clipped content, because from the outside it cannot be told apart from
    // a row whose box is genuinely too small. Running the list to the bottom of the window makes
    // that row cut by the FOLD, which is the edge of the photograph and not a defect. It also looks
    // better: a list should run off the bottom of the screen, not stop just above it.
    Column(Modifier.padding(horizontal = VastuTheme.spacing.s6).padding(top = VastuTheme.spacing.s6)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
        ) {
            IconTapButton("‹", contentDescription = "Back", onClick = onBack)
            VText("Check what we read", style = VastuTheme.type.h2, color = colors.textPrimary)
        }
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        // ⚠ Not an invitation to tap rooms when there are none. In the lost-reading state the card
        // below already explains what happened, so this line only has to not contradict it.
        if (rooms.isNotEmpty()) {
            VText(
                "Your plan, as you scanned it. Tap a room — here or below — to see roughly where we read it.",
                style = VastuTheme.type.bodySm,
                color = colors.textSecondary,
            )
        }
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
                maxPlanHeight = planCap,
            )
        } else {
            // ⚠⚠ CAPPED, NOT SHAPED — found by the geometry gate the moment this state was first
            // photographed (11 Aug 2026). An aspect ratio is a HEIGHT derived from the WIDTH, and a
            // landscape phone is wide: full width ÷ 1.2 came out taller than the whole 480 dp
            // screen, so everything below it was squeezed out and "0 ROOMS READ FROM YOUR PLAN"
            // measured 196 × 0 dp — in the tree, and not on the page. That is the zero-height class
            // this project shipped once already, arriving by a new road, in the one state nobody had
            // ever drawn. The pinned plan has been bounded by [planCap] since the same defect hit it
            // in landscape; its stand-in gets the same bound, and no fixed shape at all.
            Box(
                Modifier.fillMaxWidth().heightIn(max = planCap),
                contentAlignment = Alignment.Center,
            ) {
                GuidanceState(
                    title = if (rooms.isEmpty()) "Your reading was lost" else "The photo could not be shown",
                    // ⚠ The old body claimed "every room we read is still listed below, and your
                    // score still works" — which is true when only the picture failed, and a plain
                    // untruth when the whole reading has gone, because then nothing is listed below
                    // and there is no score. The two cases now say what is actually the case.
                    body = if (rooms.isEmpty()) {
                        "Your phone needed the memory back while we were working. " +
                            "Nothing was saved, so please read your plan again."
                    } else {
                        "Every room we read is still listed below, and your score still works."
                    },
                )
            }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s3))
        // "0 rooms read from your plan" is not a heading for a list, it is a heading for a hole —
        // the card above already explains it, so the list heading only appears when there is a list.
        if (rooms.isNotEmpty()) {
            SectionLabel("${rooms.size} rooms read from your plan")
            Spacer(Modifier.height(VastuTheme.spacing.s2))
        }

        // ⛔ A PLAIN COLUMN IN ITS OWN SCROLL — never a lazy list. A lazy list composes only the rows
        // you can see, so every room below the fold is absent from the semantics tree: the geometry
        // gate cannot measure it, the accessibility checker cannot read it, and no test can scroll to
        // it. Six render tests failed on exactly that the first time this went in. The mapper refuses
        // a plan over twenty rooms, so this list is at most twenty rows — laziness buys nothing and
        // costs the checks their eyes. Scrolling to a tapped row is done from recorded positions, the
        // same way the report does it.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(listScroll),
            verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
        ) {
            planRooms.forEachIndexed { index, pr ->
                val room = rooms[index]
                // ⭐ The report's own words for this room, when North is known. Null before that,
                // and then the row is exactly the row this screen has always drawn.
                val reading = readings[pr.id]
                VastuRoomRow(
                    name = pr.name,
                    code = room.type.microLabel(),
                    codeColor = room.type.editorColor(),
                    // ⭐ The one-word result and the direction, from the engine, identical to the
                    // report's — North was marked on the screen before this one.
                    status = reading?.status,
                    direction = reading?.direction,
                    // ⭐ AND THE PRINTED SIZE STAYS. It is what the reader is holding their own paper
                    // up against; the pills are additions to this row, never a replacement for it.
                    // Two pills and a caption is a lot for one row, which is why they sit in a
                    // FlowRow under the name — see VastuRoomRow, where a right-hand results column
                    // was tried, photographed, and found breaking words in half at 320 dp.
                    note = room.type.label() + " · " + sizeNote(room),
                    selected = selected == pr.id,
                    onTap = { tapRoom(pr.id) },
                    modifier = Modifier.onGloballyPositioned { rowY[pr.id] = it.positionInRoot().y.toInt() },
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
            run {
                Spacer(Modifier.height(VastuTheme.spacing.s3))
                // ⭐⭐ WHEN THE PLAN NAMED ITS OWN ENTRANCE, WE DO NOT ASK (owner, 6 Aug 2026: "cant
                // we do it ourselves when Entry is clearly marked? we ask only if its not"). But not
                // asking is not the same as not saying: the front door is the heaviest input the
                // engine weighs, so what we read is stated in one line, with the way to change it.
                if (door != null) {
                    VText(
                        if (doorFromCaption != null) {
                            "Front door: your plan prints \"$doorFromCaption\" on ${doorSideWords(door.side)}, so we put it there."
                        } else {
                            "Front door: we read it from your plan's own entrance, on ${doorSideWords(door.side)}."
                        },
                        style = VastuTheme.type.bodySm,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(VastuTheme.spacing.s2))
                }
                // The button never promises a screen other than the one it opens (audit B2).
                //
                // ⚠ It used to say "which way is North?" because North came next. North now comes
                // BEFORE this screen — that is what lets the rows above carry a direction at all —
                // so this is the LAST step whenever the plan named its own entrance, and it says so.
                //
                // ⭐⭐ AND IT NEVER OFFERS TO CONFIRM NOTHING. Everything this screen draws comes
                // from one in-memory slot that does not survive Android reclaiming the app. Come
                // back to a killed session and the reading is gone: no photograph, no rooms — and
                // the button still said "These are my rooms", inviting somebody to confirm a reading
                // of zero rooms and score an empty home. There is nothing to confirm, so the only
                // honest offer is to read the plan again.
                if (rooms.isEmpty()) {
                    VastuButton("Scan your plan again", onClick = onBack)
                } else {
                    VastuButton(
                        if (door != null) "These are my rooms — read my home"
                        else "These are my rooms — set the front door",
                        onClick = onContinue,
                    )
                }
                // Nothing to move a door on when the reading is gone — see the empty case above.
                if (door != null && rooms.isNotEmpty()) {
                    Spacer(Modifier.height(VastuTheme.spacing.s2))
                    VastuButtonInline(
                        "Put the front door somewhere else",
                        onClick = onChangeDoor,
                        style = VastuButtonStyle.SECONDARY,
                    )
                }
                Spacer(Modifier.height(VastuTheme.spacing.s6))
            }
        }
    }
    }
}

/**
 * The most of the viewport's height the pinned plan may take. A landscape phone is 480 dp tall and
 * this screen's headings, list heading and buttons need the rest; a fixed cap that is right in
 * portrait is most of the screen in landscape.
 *
 * ⚠ Raised 0.38 → 0.42 alongside the pane cap (11 Aug 2026), so a taller cap is not silently
 * cancelled by this on an ordinary portrait phone. It is still the guard that stops the pinned
 * picture eating a landscape screen — that is what it is for, and it stays well under half.
 */
private const val PLAN_MAX_VIEWPORT_SHARE = 0.42f
