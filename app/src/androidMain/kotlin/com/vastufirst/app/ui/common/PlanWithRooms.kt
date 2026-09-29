// PlanWithRooms.kt — the user's own scanned plan, with every room we read drawn on it and tappable.
//
// ⭐⭐ ONE COMPONENT, TWO SCREENS, ONE BEHAVIOUR (owner, 10 Aug 2026): *"Tapping a room on the floor
// plan should also highlight it the same way it highlights when tapping the room on the list…  Build
// a common UI/UX for this room highlight in the list which works the same way if user taps the room
// in list or room on floor plan"*.
//
// The list half of that behaviour is `VastuRoomRow`; this is the plan half. Both SELECT the same
// room the same way, so "tap it here" and "tap it there" cannot drift apart — which is the whole of
// what he asked for, and the thing two separate implementations would quietly lose.
//
// ⚠ AMENDED 16 AUG 2026. The review screen gives this component a handler that ALSO scrolls its
// list, while the row gets one that only selects. That is not drift — it is the difference between
// the two ends. This plan is pinned above the list, so it can select a room whose row is off the
// screen and something has to reveal it; a row the user tapped is already under their finger.
// Scrolling that was the owner's *"tapping a room in the list makes the list jump"*.
//
// ⭐⭐ AND ON 16 AUG 2026 IT LEARNED TO ZOOM, AND TO SHOW THE FRONT DOOR. Both are owner requests and
// both are described where they are implemented: [planFit] for the one piece of arithmetic every
// feature here shares, and [planGestures] for the single gesture reader that serves all of them.
package com.vastufirst.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.scan.ScanBox

/** One room as this component needs it: where it sits on the picture, and what to call it. */
data class PlanRoom(
    val id: String,
    val type: RoomType,
    val name: String,
    /** Fractions of the picture. Null when the reader gave us no rectangle for this room. */
    val box: ScanBox?,
)

/**
 * Which room a tap at [point] (in fractions of the drawn picture) landed on, or null for none.
 *
 * ⚠ SMALLEST-FIRST, and that is the whole of the arithmetic. Rooms overlap on a real plan — an
 * attached toilet sits inside the bedroom's rectangle, a passage runs under three of them — so the
 * first box that contains the tap is very often the largest one. Sorting by area and taking the
 * smallest match means a tap on the toilet selects the toilet, which is what the finger meant.
 *
 * Pure, and separated from the drawing on purpose, so every case can be tested without rendering
 * anything: overlap, no match, and a room the reader gave no rectangle for.
 */
fun roomAtPoint(rooms: List<PlanRoom>, x: Float, y: Float): PlanRoom? =
    rooms.asSequence()
        .filter { r ->
            val b = r.box ?: return@filter false
            x >= b.x && x <= b.x + b.w && y >= b.y && y <= b.y + b.h
        }
        .minByOrNull { (it.box!!.w * it.box.h) }

/**
 * ⭐⭐ WHERE THE PICTURE ACTUALLY LANDS INSIDE ITS BOX — ONE function, and the whole safety of this
 * file.
 *
 * The photo is letterboxed: it is fitted into the box and centred, so it almost never fills it. Room
 * highlights, the front-door marker and every tap all have to agree about that rectangle to within a
 * pixel, or the picture says one thing and the finger does another.
 *
 * ⚠ This used to be written out TWICE — once in the draw pass and once inside the tap handler — and
 * it worked only because both copies were identical. The moment zoom arrived, two copies would have
 * meant the highlights zooming and the taps not. Both now call this.
 */
internal data class PlanFit(val ox: Float, val oy: Float, val w: Float, val h: Float)

internal fun planFit(
    boxW: Float,
    boxH: Float,
    imageW: Int,
    imageH: Int,
    zoom: Float,
    pan: Offset,
): PlanFit {
    if (imageW <= 0 || imageH <= 0 || boxW <= 0f || boxH <= 0f) return PlanFit(0f, 0f, 0f, 0f)
    val base = minOf(boxW / imageW, boxH / imageH) * zoom
    val w = imageW * base
    val h = imageH * base
    val clamped = clampPlanPan(pan, w, h, boxW, boxH)
    return PlanFit(ox = (boxW - w) / 2f + clamped.x, oy = (boxH - h) / 2f + clamped.y, w = w, h = h)
}

/**
 * Keep the sheet inside its own box.
 *
 * Along an axis where the picture is SMALLER than the box there is nothing to pan — it is centred
 * and stays centred, so the offset is pinned to zero. Where it is bigger, the offset may run to half
 * the overflow in each direction, which puts either edge of the sheet exactly on the edge of the box
 * and no further. Without this the plan can be flung off screen and there is no way back.
 */
internal fun clampPlanPan(pan: Offset, drawnW: Float, drawnH: Float, boxW: Float, boxH: Float): Offset {
    val maxX = ((drawnW - boxW) / 2f).coerceAtLeast(0f)
    val maxY = ((drawnH - boxH) / 2f).coerceAtLeast(0f)
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

/** How far the user may magnify. Past this the stored picture is only being upsampled. */
const val PLAN_MAX_ZOOM = 5f

/**
 * ⚠ THE DOOR MARK ITSELF NO LONGER LIVES HERE. Its size and its drawing moved to [DoorMark.kt] on
 * 18 August 2026 so that this picture, the "Where is your front door?" screen, the report and the
 * hand-drawn editor all show the reader the SAME mark. See that file for what four different marks
 * cost, and for the crash the old font-driven sizing was four tenths of a dp away from.
 */

/**
 * ⭐⭐ ONE GESTURE READER FOR THE WHOLE PICTURE — taps, pinch, pan and the front door.
 *
 * ⚠ EXACTLY ONE `pointerInput` ON THIS CANVAS, and it must stay that way. Two detectors on one node
 * each run their own touch-slop bookkeeping, so a tap with four pixels of finger roll is eaten by
 * the drag — the editor screen carries the same warning for the same reason. Here the cost would be
 * a lost room selection AND a silently moved front door, which is the heaviest single input in the
 * score. So every gesture this picture understands is decided in one place, at the moment the finger
 * goes down:
 *
 *   · down ON the E, where it can move → this gesture belongs to the door. A drag carries it, from
 *                                         the first movement past touch slop; a tap opens its note.
 *   · a second finger arrives           → pinch to zoom (only when [zoomable]).
 *   · drag while zoomed in              → pan the sheet.
 *   · up without passing slop           → a tap: the door, or the room under the finger.
 *
 * ⭐⭐ THE E NO LONGER HAS TO BE TAPPED BEFORE IT WILL MOVE (owner, 27 Sep 2026: *"it should not be
 * locked behind a button... user can just tap and drag it"*). It used to be offered for dragging
 * only once selected, so a reader who simply put a finger on it and pulled saw nothing happen at
 * all. A finger that lands on the E and moves now means exactly that. Panning is still a drag that
 * starts anywhere ELSE on the sheet, which is where a finger meaning to shift the picture lands.
 *
 * ⚠ NOTE WHAT IS **NOT** CONSUMED. At zoom 1 with one finger and no door under it, this reader takes
 * nothing and behaves exactly as the old `detectTapGestures` did — which is why no existing golden
 * or test moves. A picture that grabbed single-finger drags at rest would steal the report's own
 * scroll, because there the plan sits inside a scrolling column — and there [doorMovable] is false,
 * so even a drag that starts on the E is left to the page.
 */
private suspend fun PointerInputScope.planGestures(
    zoomable: Boolean,
    zoom: () -> Float,
    doorAt: () -> Offset?,
    doorMovable: () -> Boolean,
    doorTouchPx: Float,
    onZoomPan: (centroid: Offset, zoomChange: Float, panChange: Offset) -> Unit,
    onTap: (Offset) -> Unit,
    onDoorTap: () -> Unit,
    onDoorDragStart: () -> Unit,
    onDoorDrag: (Offset) -> Unit,
    onDoorDragEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val markerNow = doorAt()
        val onDoor = markerNow != null && (down.position - markerNow).getDistance() <= doorTouchPx
        val dragsDoor = onDoor && doorMovable()
        // ⭐ THE E KEEPS ITS PLACE UNDER THE FINGER. A finger rarely lands on the exact centre of the
        // mark; carrying that small offset through the drag means the E does not leap to the
        // fingertip on the first movement — it simply starts moving with the hand that holds it.
        val grab = if (dragsDoor) markerNow?.minus(down.position) ?: Offset.Zero else Offset.Zero
        var dragging = false
        var pinching = false

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break

            if (zoomable && pressed.size > 1) {
                pinching = true
                val z = event.calculateZoom()
                val p = event.calculatePan()
                if (z != 1f || p != Offset.Zero) {
                    onZoomPan(event.calculateCentroid(useCurrent = false), z, p)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
                continue
            }
            // ⚠ ONCE A PINCH, ALWAYS A PINCH — for the rest of this gesture. Lifting one finger of a
            // two-finger pinch used to leave the loop hunting for the ORIGINAL pointer, which may be
            // the one that went; the picture then ignored the finger still on it until every finger
            // was lifted. Treating the remainder as part of the pinch ends it cleanly instead.
            if (pinching) continue

            val moving = event.changes.firstOrNull { it.id == down.id } ?: continue
            if (!dragging && (moving.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                dragging = true
                if (dragsDoor) onDoorDragStart()
            }
            if (!dragging) continue

            if (dragsDoor) {
                // EVERY movement, not only the ones that would change the scored door — this is what
                // makes the E follow the finger rather than catch up with it.
                onDoorDrag(moving.position + grab)
                moving.consume()
            } else if (zoomable && zoom() > 1f) {
                onZoomPan(moving.position, 1f, moving.positionChange())
                moving.consume()
            }
        }

        if (dragging && dragsDoor) {
            onDoorDragEnd()
            return@awaitEachGesture
        }
        if (dragging || pinching) return@awaitEachGesture
        // A tap. The door owns it if the finger came down on the mark.
        if (onDoor) onDoorTap() else onTap(down.position)
    }
}

/**
 * The plan, the rooms drawn over it, and a tap that selects one.
 *
 * ⚠ EVERY room is outlined, not only the selected one. A picture where nothing is drawn until you
 * tap the right spot is a feature with no affordance — you cannot tap what you cannot see. The
 * selected room is filled and thickened; the rest are quiet outlines that say "these are tappable".
 */
@Composable
fun PlanWithRooms(
    image: ImageBitmap,
    rooms: List<PlanRoom>,
    selectedId: String?,
    onTapRoom: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Caps the picture's height so the list under it is not pushed off the screen.
     *
     * ⚠ NOT called `maxHeight`. Inside a BoxWithConstraints the scope has its own `maxHeight`, and it
     * is the CLOSER name — so a bare `maxHeight` in the body would silently read the box's constraint
     * instead of this cap, and the cap would never apply. It compiles, it runs, and the picture
     * overflows exactly as it did before the fix.
     */
    maxPlanHeight: Dp = Dp.Unspecified,
    /**
     * ⭐⭐ PINCH TO ZOOM AND DRAG TO PAN — the owner's fix for a vertical plan, 16 Aug 2026.
     *
     * **What he saw:** *"vertical plans come out too small on Check what we read. A tall plan is
     * shrunk to fit a wide box and I cannot see anything."* He was right, and the arithmetic says how
     * badly. The picture's height is capped (320 dp on this screen), so a LANDSCAPE sheet runs out of
     * WIDTH first and fills the column, while a PORTRAIT sheet hits the height cap first and is then
     * given whatever width its own shape implies. A real portrait sheet in this repo — 1256 × 2760 —
     * is drawn about 146 dp wide in a 364 dp column: forty per cent of the space, and nothing on the
     * screen could magnify it.
     *
     * ⚠ ROTATING IT WAS THE OTHER CANDIDATE AND IT IS WRONG, not merely worse. North is set on the
     * screen BEFORE this one, against this same picture unrotated. Every row here prints a spelled
     * out direction. The front-door line names walls by where they are in the picture — "the top wall
     * of your plan". The report draws the same photo unrotated two taps later. And there is no
     * compass on this screen, so a turned picture gives no clue it has been turned: a silent
     * ninety-degree lie about direction, in an app about direction.
     *
     * ⚠ OFF BY DEFAULT, deliberately. Only the review screen's plan is pinned outside a scrolling
     * column. On the report the same component sits INSIDE one, and a picture that claimed
     * single-finger drags there would eat the page scroll.
     *
     * ⚠ HONEST LIMIT, worth knowing before raising [PLAN_MAX_ZOOM]: the app never holds the user's
     * full-resolution sheet. The picked file is shrunk to 1400 px on its longest edge before anything
     * sees it, because that is the size the reader's accuracy was measured at. So on a tall sheet the
     * magnification stops being real detail at roughly 2×; past that it is a bigger, blurrier
     * picture. That is still the difference between unreadable and readable, but it is not unlimited,
     * and the fix for the rest is a second display-resolution copy rather than a bigger number here.
     */
    zoomable: Boolean = false,
    /**
     * ⭐⭐ THE FRONT DOOR, DRAWN ON THE PLAN — where on the picture it sits, in page fractions.
     *
     * Null when no door is known. The caller works this out with `doorMarkerOnPage`, which is the
     * exact inverse of the tap conversion, so the mark lands under the finger that placed it.
     */
    doorAtPage: Pair<Float, Float>? = null,
    /** The door was tapped. On the report this reveals its line; where there is a note, it opens. */
    onTapDoor: () -> Unit = {},
    /**
     * The E was dragged and LET GO at this point ON THE HOME'S OUTLINE, in page fractions — already
     * slid onto the nearest wall, so the caller only has to turn it into the scored door. Called once
     * per drag, when the finger lifts.
     */
    onMoveDoorToPage: (Float, Float) -> Unit = { _, _ -> },
    /**
     * ⭐ The home's outline on the picture, in page fractions — the line a dragged E slides along.
     * Null where the door cannot be moved: the report passes none, and there a drag that starts on
     * the E is left to the page's own scroll.
     */
    doorOutline: ScanBox? = null,
    /** What the note on the E says. Null draws no note — the report has its own line for the door. */
    doorNote: DoorNoteText? = null,
    /** For the harness: open the E's note on first draw, so a golden can photograph it. */
    startDoorNoteOpen: Boolean = false,
    /** One tap per wall for anyone who cannot drag — see [DoorMarkTarget]. Null where it cannot move. */
    onMoveDoorToSide: ((com.vastufirst.app.ui.newplan.DoorSide) -> Unit)? = null,
    /** How this screen names a wall aloud, for those actions. */
    doorWallWords: (com.vastufirst.app.ui.newplan.DoorSide) -> String = { it.name },
    /** What a screen reader hears on the E itself: a fact, never an instruction to drag. */
    doorDescription: String = "Your front door",
    /**
     * ⭐⭐ THE DIRECTION TRIAL (owner, 29 Sep 2026) — what a tapped room shows on this picture.
     *
     * [RoomMarker.BOX], the default, is this component exactly as it was before the trial: quiet
     * outlines, and the tapped room tinted. [RoomMarker.DIRECTION] draws a small ring per room
     * instead, and the tapped room gets a larger ring and its direction in words. See RoomMarker.kt.
     */
    marker: RoomMarker = RoomMarker.BOX,
    /**
     * The engine's own direction words for each room, by room id — the SAME words its row prints.
     * Only read under [RoomMarker.DIRECTION]. A room missing from it gets its ring and no words: this
     * picture never works a direction out for itself.
     */
    directions: Map<String, String> = emptyMap(),
) {
    val colors = VastuTheme.colors
    val strokeDp = VastuTheme.spacing.s1
    // The direction trial's rings — see the drawing below. Read here: a draw lambda is not a
    // composable scope and cannot read the theme.
    val dotDp = VastuTheme.sizes.dot
    val ringWidthDp = VastuTheme.borders.strong
    val pinWidthDp = VastuTheme.borders.focus
    val labelClearanceDp = VastuTheme.sizes.dot * 2 + VastuTheme.spacing.s1
    val doorTouch = VastuTheme.sizes.minTouch
    // One string is ever measured here (the door mark's letter), so the default cache is ample.
    val measurer = rememberTextMeasurer()
    // ⚠ The SIZE on this style is ignored — [drawDoorMark] replaces it with one derived from the
    // disc, so the letter cannot outgrow its own circle at a 200 % font. Only the family lands.
    val doorLetterStyle = VastuTheme.type.caption
    // ⚠ Real shape when it can zoom. The clamp below exists to stop a freakish sheet taking the whole
    // screen, and it does that by making the BOX a different shape from the picture — which only ever
    // added dead margin either side of a tall plan. Once the user can magnify, the honest thing is to
    // give the box the picture's own shape and let them do the rest.
    val realAspect = if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height else PLAN_DEFAULT_ASPECT
    val aspect = if (zoomable) realAspect else realAspect.coerceIn(PLAN_MIN_ASPECT, PLAN_MAX_ASPECT)
    val selected = rooms.firstOrNull { it.id == selectedId }
    val quiet = colors.borderStrong
    // ⚠ Resolved HERE, in composable scope. `editorColor()` reads the theme, and a draw lambda is not
    // a composable scope — asking for it inside the Canvas does not compile.
    val selectedTint = selected?.type?.editorColor() ?: colors.primary

    var zoom by remember(image) { mutableFloatStateOf(1f) }
    var pan by remember(image) { mutableStateOf(Offset.Zero) }
    /**
     * ⭐⭐ WHERE THE E IS WHILE A FINGER CARRIES IT — on the outline, under the finger, every frame.
     *
     * Null at rest, and then the E is drawn where the SCORED door is ([doorAtPage]). The two differ
     * only while the finger is down: the scored door moves in whole steps along a wall, the finger
     * does not, and drawing the scored door was exactly why the E used to lag and jump behind the
     * hand. On lifting, the door is set where the E was let go, this goes back to null, and the E
     * settles onto the spot that is scored.
     *
     * ⚠ THE DOOR ITSELF IS SET ON LIFTING, NOT WHILE HELD — found by the finger test in the editor on
     * the first build of this. Setting the door changes words elsewhere on a screen (a line about
     * skipping the door goes away, a wall is named), a screen that is scrolled then shifts, and the
     * picture moves under a finger that has not moved — so the E jumped to another wall mid-drag.
     * While the finger is down, only the E moves.
     */
    var draggedDoor by remember(image) { mutableStateOf<Pair<Float, Float>?>(null) }
    /** The E's note — the owner's "pop up", opened by a tap on the E and put away by any other tap. */
    var noteOpen by remember(image) { mutableStateOf(startDoorNoteOpen) }
    val shownDoor = draggedDoor ?: doorAtPage
    // ⚠ Read LIVE, never used as a pointerInput key. Keying the gesture reader on the door would
    // cancel and restart it the instant a drag moved the door — the finger would stop working after
    // the first pixel. The editor screen carries the same note for the same reason.
    val liveDoor by rememberUpdatedState(shownDoor)
    val liveOutline by rememberUpdatedState(doorOutline)
    val liveRooms by rememberUpdatedState(rooms)
    // ⚠ Read live for the same reason as the door: the gesture reader is keyed on the picture, and
    // flipping the trial's chooser must change what the NEXT tap means without restarting it.
    val liveMarker by rememberUpdatedState(marker)
    val liveOnMoveDoor by rememberUpdatedState(onMoveDoorToPage)
    val liveOnTapDoor by rememberUpdatedState(onTapDoor)
    val liveHasNote by rememberUpdatedState(doorNote != null)

    // ⚠⚠ THE HEIGHT CAP MUST BE APPLIED TO THE PICTURE, NOT AROUND IT — found by looking at the
    // render, 10 Aug 2026. Capping a parent box and giving the child an aspect ratio does NOT bound
    // the child: it takes the full width, works out its own height from the ratio, and draws
    // straight out of the bottom of its parent, which does not clip. On a square builder's sheet
    // that put the plan a couple of hundred pixels past its limit and the "15 rooms read from your
    // plan" heading was printed on top of the drawing. Sizing the picture explicitly from the
    // available width and the cap is the fix; there is nothing left to overflow.
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val drawnHeight = if (maxPlanHeight == Dp.Unspecified) this.maxWidth / aspect
        else minOf(this.maxWidth / aspect, maxPlanHeight)
        val drawnWidth = drawnHeight * aspect
        // ⚠ Sized directly rather than filling its parent. The fill-the-whole-parent modifier is
        // also the string the window-inset gate greps for when it looks for a screen root, and a
        // picture inside a screen is not one — an explicit size says what is meant and keeps that
        // check honest. (Do not spell that modifier's name anywhere in this file, comments included:
        // the gate reads the file as text, exactly like the CI skip markers do.)
        // ⭐ A BOX AROUND THE PICTURE, so the E's own node and its note can sit over it. The picture
        // still takes the whole box; the two extras draw nothing unless there is a door to name.
        Box(Modifier.width(drawnWidth).height(drawnHeight)) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    // ⚠⚠ A COMPOSE CANVAS DOES NOT CLIP ITS OWN INK. Without this the magnified
                    // sheet is painted straight over whatever else is on the screen — the room list
                    // below it, the title above it — because `drawImage` is not bounded by the
                    // node's size. Nothing catches it either: the geometry gate measures LAYOUT
                    // boxes, and the layout box is still the right size; only the pixels escape. It
                    // is invisible until you look at the picture, which is exactly this project's
                    // most expensive class of defect.
                    .clipToBounds()
                    .pointerInput(image, zoomable) {
                        val fitNow = {
                            planFit(size.width.toFloat(), size.height.toFloat(), image.width, image.height, zoom, pan)
                        }
                        val pageOf: (Offset) -> Pair<Float, Float>? = { at ->
                            val f = fitNow()
                            if (f.w > 0f && f.h > 0f) ((at.x - f.ox) / f.w) to ((at.y - f.oy) / f.h) else null
                        }
                        val markerAt = {
                            liveDoor?.let { (dx, dy) ->
                                val f = fitNow()
                                if (f.w > 0f) {
                                    val r = doorMarkRadiusPx(f.w, f.h, doorTouch.toPx())
                                    doorCentrePx(f, dx, dy, r, size.width.toFloat(), size.height.toFloat(), zoom)
                                } else {
                                    null
                                }
                            }
                        }
                        planGestures(
                            zoomable = zoomable,
                            zoom = { zoom },
                            doorAt = markerAt,
                            // Only where there is an outline to slide along — never on the report.
                            doorMovable = { liveOutline != null },
                            doorTouchPx = doorTouch.toPx() / 2f,
                            onZoomPan = { centroid, dZoom, dPan ->
                                val boxW = size.width.toFloat()
                                val boxH = size.height.toFloat()
                                val next = (zoom * dZoom).coerceIn(1f, PLAN_MAX_ZOOM)
                                // ⚠ ZOOM ABOUT THE FINGERS, NOT THE MIDDLE OF THE BOX. Anchoring at
                                // the centre makes the thing being pinched slide away from under the
                                // hand, which on a plan means the room you are trying to read leaves
                                // the screen as you enlarge it. The page point under the centroid is
                                // held still across the change of scale.
                                val before = planFit(boxW, boxH, image.width, image.height, zoom, pan)
                                val fx = if (before.w > 0f) (centroid.x - before.ox) / before.w else 0.5f
                                val fy = if (before.h > 0f) (centroid.y - before.oy) / before.h else 0.5f
                                val after = planFit(boxW, boxH, image.width, image.height, next, Offset.Zero)
                                val wanted = Offset(
                                    x = (centroid.x + dPan.x) - fx * after.w - (boxW - after.w) / 2f,
                                    y = (centroid.y + dPan.y) - fy * after.h - (boxH - after.h) / 2f,
                                )
                                zoom = next
                                pan = clampPlanPan(wanted, after.w, after.h, boxW, boxH)
                            },
                            onTap = { at ->
                                // Any other tap puts the note away — one thing is explained at a time.
                                noteOpen = false
                                pageOf(at)?.let { (fx, fy) ->
                                    // ⭐ The box way aims at outlines it can see; the direction way aims
                                    // at dots — see [roomNearestCentre] for why the two need different
                                    // arithmetic. The box way's is untouched.
                                    val hit = if (liveMarker == RoomMarker.DIRECTION) {
                                        val f = fitNow()
                                        roomNearestCentre(liveRooms, fx, fy, f.w, f.h, doorTouch.toPx())
                                    } else {
                                        roomAtPoint(liveRooms, fx, fy)
                                    }
                                    hit?.let { onTapRoom(it.id) }
                                }
                            },
                            onDoorTap = {
                                if (liveHasNote) noteOpen = !noteOpen
                                liveOnTapDoor()
                            },
                            // A drag is not a question about what the E is, so its note goes away.
                            onDoorDragStart = { noteOpen = false },
                            onDoorDrag = { at ->
                                val outline = liveOutline
                                pageOf(at)?.let { (fx, fy) ->
                                    if (outline != null) {
                                        val p = nearestOnOutline(
                                            fx, fy,
                                            outline.x.toFloat(), outline.y.toFloat(),
                                            (outline.x + outline.w).toFloat(), (outline.y + outline.h).toFloat(),
                                        )
                                        draggedDoor = p.x to p.y
                                    }
                                }
                            },
                            // ⭐ LIFTED: the door is set where the E was let go, ONCE, and the E settles
                            // onto the spot that is scored. See the note on [draggedDoor] for why the
                            // door is not set while the finger is still down.
                            onDoorDragEnd = {
                                draggedDoor?.let { (x, y) -> liveOnMoveDoor(x, y) }
                                draggedDoor = null
                            },
                        )
                    }
                    .semantics {
                        contentDescription = buildPlanDescription(
                            selected?.name,
                            doorAtPage != null,
                            zoomable,
                            marker = marker,
                            selectedDirection = selected?.let { directions[it.id] },
                        )
                    },
            ) {
                val fit = planFit(size.width, size.height, image.width, image.height, zoom, pan)
                val drawn = Size(fit.w, fit.h)
                val origin = Offset(fit.ox, fit.oy)
                drawImage(
                    image = image,
                    dstOffset = IntOffset(origin.x.toInt(), origin.y.toInt()),
                    dstSize = IntSize(drawn.width.toInt(), drawn.height.toInt()),
                )
                fun rectOf(b: ScanBox): Pair<Offset, Size> =
                    Offset(origin.x + b.x.toFloat() * drawn.width, origin.y + b.y.toFloat() * drawn.height) to
                        Size(b.w.toFloat() * drawn.width, b.h.toFloat() * drawn.height)

                if (marker == RoomMarker.BOX) {
                    // The quiet ones first, so the selected room's outline is never drawn under another's.
                    rooms.forEach { r ->
                        if (r.id == selectedId) return@forEach
                        val b = r.box ?: return@forEach
                        val (tl, area) = rectOf(b)
                        drawRect(color = quiet, topLeft = tl, size = area, style = Stroke(width = strokeDp.toPx() / 4f))
                    }
                    selected?.box?.let { b ->
                        val (tl, area) = rectOf(b)
                        drawRect(color = selectedTint.copy(alpha = 0.28f), topLeft = tl, size = area)
                        drawRect(color = selectedTint, topLeft = tl, size = area, style = Stroke(width = strokeDp.toPx() / 2f))
                    }
                } else {
                    // ⭐⭐ THE DIRECTION TRIAL: A RING PER ROOM, NO BOX AT ALL. The outlines were the
                    // affordance ("these are tappable"); a quiet ring at the middle of each room keeps
                    // that without drawing a rectangle we know to be a guess. The tapped room's ring
                    // is larger, in its own colour, lightly filled, and its direction in words is laid
                    // over it by [RoomDirectionLabel].
                    //
                    // ⚠ RINGS, NOT DOTS — found by looking at the first render. The middle of a room's
                    // box is, on most sheets, exactly where the plan PRINTS the room's name (the reader
                    // finds rooms by their captions), so solid dots sat on the names and ate letters:
                    // "LIVING ROOM" came out "ING ROOM", on the one screen whose whole job is checking
                    // those names. A ring's middle is empty and the name reads straight through it.
                    // Each ring rides on a paper-coloured halo so it shows on a dark render too.
                    val halo = strokeDp.toPx() / 2f
                    val quietR = dotDp.toPx()
                    val quietW = ringWidthDp.toPx()
                    val pinR = dotDp.toPx() * 2f
                    val pinW = pinWidthDp.toPx()
                    fun pointOf(c: Pair<Float, Float>) = Offset(origin.x + c.first * drawn.width, origin.y + c.second * drawn.height)
                    rooms.forEach { r ->
                        if (r.id == selectedId) return@forEach
                        val at = r.centreOrNull()?.let(::pointOf) ?: return@forEach
                        drawCircle(color = colors.paper, radius = quietR, center = at, style = Stroke(width = quietW + 2f * halo))
                        drawCircle(color = colors.primaryDark, radius = quietR, center = at, style = Stroke(width = quietW))
                    }
                    selected?.centreOrNull()?.let(::pointOf)?.let { at ->
                        drawCircle(color = selectedTint.copy(alpha = 0.25f), radius = pinR, center = at)
                        drawCircle(color = colors.paper, radius = pinR, center = at, style = Stroke(width = pinW + 2f * halo))
                        drawCircle(color = selectedTint, radius = pinR, center = at, style = Stroke(width = pinW))
                    }
                }

                // ⭐⭐ THE FRONT DOOR, LAST, so nothing is drawn over it — and drawn where the finger
                // has it while it is being carried, not where the scored door last settled.
                shownDoor?.let { (dx, dy) ->
                    // Proportional between two fixed bounds — see [doorMarkRadiusPx], which is the
                    // one place the entrance mark's size is decided for the whole app.
                    val r = doorMarkRadiusPx(drawn.width, drawn.height, doorTouch.toPx())
                    // ⭐⭐ IT SITS ON THE WALL NOW, HALF IN AND HALF OUT (owner, 17 Aug 2026: *"make
                    // it sit on the border of the map so its half out and half in"*).
                    //
                    // ⚠ It used to be pulled INWARD by its own radius, which is what made it float
                    // clear of the wall it is naming — on his own plan it hung in the sheet margin
                    // beside the home rather than on it. The point is already on the home's outline
                    // (doorMarkerOnPage puts it hard against that wall), and the outline sits inside
                    // the photograph with the sheet's margin around it, so straddling it is drawn in
                    // full without leaving the picture.
                    //
                    // ⚠ The one exception is the fallback where NO room carried a page box and the
                    // home frame becomes the whole sheet. Then the point IS the picture's own edge,
                    // and a canvas clips its ink — so at rest the centre is nudged just inside the
                    // canvas, which is the only case where this does anything at all. While zoomed
                    // the mark is deliberately left alone: pinning it to the screen edge would be
                    // the picture claiming a door is somewhere it is not.
                    val at = doorCentrePx(fit, dx, dy, r, size.width, size.height, zoom)
                    // ⭐ ONE DRAWING, SHARED. Collar, disc, letter and the selected ring all come
                    // from [drawDoorMark] so this picture cannot drift away from the front-door
                    // screen, the report or the hand-drawn editor — which is exactly what happened
                    // when each of the four drew its own. The ring shows while the E is held or its
                    // note is open: the moments the reader is looking at it on purpose.
                    drawDoorMark(
                        center = at,
                        radius = r,
                        selected = noteOpen || draggedDoor != null,
                        colors = colors,
                        strokePx = strokeDp.toPx(),
                        measurer = measurer,
                        letterStyle = doorLetterStyle,
                    )
                }
            }

            // ⭐⭐ THE DIRECTION TRIAL — the tapped room's direction, in the engine's own words, laid
            // over the room. Drawn BEFORE the E's node and note, so the door's note is never hidden
            // under a label. A magnified sheet can carry the room out of view; its label goes with
            // it rather than floating somewhere the room is not.
            if (marker == RoomMarker.DIRECTION) {
                val words = selected?.let { directions[it.id] }
                val centre = selected?.centreOrNull()
                if (words != null && centre != null) {
                    val density = LocalDensity.current
                    val boxW = with(density) { drawnWidth.toPx() }
                    val boxH = with(density) { drawnHeight.toPx() }
                    val fit = planFit(boxW, boxH, image.width, image.height, zoom, pan)
                    val pin = Offset(fit.ox + centre.first * fit.w, fit.oy + centre.second * fit.h)
                    if (pin.x in 0f..boxW && pin.y in 0f..boxH) {
                        RoomDirectionLabel(
                            text = words,
                            pinPx = pin,
                            boxW = boxW,
                            boxH = boxH,
                            // Clear of the tapped room's ring, so the words never sit on the name
                            // the ring is drawn round.
                            clearancePx = with(density) { labelClearanceDp.toPx() },
                        )
                    }
                }
            }

            // ⭐ THE E'S OWN NODE AND ITS NOTE — only where the door can be moved or explained, so the
            // report's picture (which passes neither) is exactly what it was.
            val door = shownDoor
            if (door != null && (doorOutline != null || doorNote != null)) {
                val density = LocalDensity.current
                val boxW = with(density) { drawnWidth.toPx() }
                val boxH = with(density) { drawnHeight.toPx() }
                val fit = planFit(boxW, boxH, image.width, image.height, zoom, pan)
                val r = doorMarkRadiusPx(fit.w, fit.h, with(density) { doorTouch.toPx() })
                val centre = doorCentrePx(fit, door.first, door.second, r, boxW, boxH, zoom)
                // A magnified sheet can carry the E out of view; its node goes with it rather than
                // floating over the screen somewhere the door is not.
                if (centre.x in 0f..boxW && centre.y in 0f..boxH) {
                    DoorMarkTarget(
                        centrePx = centre,
                        description = doorDescription,
                        onOpen = {
                            if (doorNote != null) noteOpen = true
                            onTapDoor()
                        },
                        moveTo = onMoveDoorToSide,
                        wallWords = doorWallWords,
                    )
                }
                if (noteOpen && doorNote != null) {
                    DoorNoteOverlay(
                        note = doorNote,
                        markerInTopHalf = centre.y < boxH / 2f,
                        onClose = { noteOpen = false },
                    )
                }
            }
        }
    }
}

/**
 * Where the E's centre is drawn, in the picture box's pixels.
 *
 * ⭐ ONE FUNCTION FOR THE DRAWING, THE TOUCH TEST AND THE SCREEN-READER NODE, so the three can never
 * disagree about where the E is. At rest the centre is nudged just inside the box when it would
 * otherwise sit on the picture's own edge (the no-page-box fallback, where the home frame IS the
 * sheet); while zoomed it is left exactly where the door is, even off screen.
 */
internal fun doorCentrePx(
    fit: PlanFit,
    dx: Float,
    dy: Float,
    radius: Float,
    boxW: Float,
    boxH: Float,
    zoom: Float,
): Offset {
    val raw = Offset(fit.ox + dx * fit.w, fit.oy + dy * fit.h)
    return if (zoom > 1f) raw else Offset(
        x = raw.x.coerceIn(radius, (boxW - radius).coerceAtLeast(radius)),
        y = raw.y.coerceIn(radius, (boxH - radius).coerceAtLeast(radius)),
    )
}

/**
 * What a screen reader is told about the picture.
 *
 * Kept out of the composable so the sentence can be read and changed without going near the drawing,
 * and so every branch of it is exercised by a plain unit test rather than only by a screenshot.
 */
internal fun buildPlanDescription(
    selectedName: String?,
    hasDoor: Boolean,
    zoomable: Boolean,
    /** The direction trial says the room's direction, where the box way said where it was read. */
    marker: RoomMarker = RoomMarker.BOX,
    selectedDirection: String? = null,
): String {
    val head = if (marker == RoomMarker.DIRECTION) {
        when {
            selectedName == null -> "Your scanned plan. Tap a room to see which direction it is in."
            // The same words the room's row prints, so a screen-reader user hears what everyone sees.
            selectedDirection != null -> "Your plan. $selectedName is in the $selectedDirection."
            else -> "Your plan, with a pin where $selectedName was read."
        }
    } else {
        selectedName
            ?.let { "Your plan, showing roughly where $it was read" }
            ?: "Your scanned plan. Tap a room to see roughly where we read it."
    }
    // ⚠ The door sentence STATES a fact and does not issue an instruction. Someone using a screen
    // reader cannot pinch and cannot drag a mark they navigate to by swiping, so telling them to do
    // either is telling them to do something they cannot.
    // ⚠ It names the LETTER, because the mark carries one since 17 Aug 2026. A sighted reader sees an
    // E on the wall; a screen-reader user hearing only "a mark" cannot ask anybody about it.
    // ⚠ It no longer names a button (27 Sep 2026). The E is its own node now, with one action per
    // wall where the door can be moved, and this picture is also the report's — where the button it
    // used to name does not exist.
    val door = if (hasDoor) " Your front door is marked E on it." else ""
    val zoomWords = if (zoomable) " Pinch with two fingers to make the plan bigger." else ""
    return head + door + zoomWords
}

/**
 * The picture's shape, bounded. Carried over from the review screen unchanged: the bounds stop a
 * freakishly tall or wide sheet from taking the whole screen or collapsing into a slot.
 *
 * ⚠ Not applied when the picture can be zoomed — see `zoomable`. On a tall sheet the clamp only ever
 * bought dead margin down each side, and magnifying is the better answer to "I cannot see it".
 */
const val PLAN_MIN_ASPECT = 0.7f
const val PLAN_MAX_ASPECT = 1.8f
const val PLAN_DEFAULT_ASPECT = 1.2f
