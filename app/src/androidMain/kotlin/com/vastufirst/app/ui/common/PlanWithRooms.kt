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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
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
 * The door mark's drawn size, as a share of the smaller side of the picture.
 *
 * ⚠ The DRAWN circle and the TOUCH target are two different sizes on purpose. The target is the
 * accessibility floor and never shrinks; the mark itself is proportional, because a fixed 48 dp disc
 * on a plan drawn 92 dp wide in landscape covers a third of the home. Bounded at both ends so it is
 * never a speck on a big sheet nor a blot on a small one.
 *
 * ⚠ Cut a fifth on 17 Aug 2026 (owner: *"Make it 20% smaller"*) — 0.09 → 0.072. The touch target it
 * sits inside is unchanged, so nothing got harder to hit.
 */
const val PLAN_DOOR_MARK_SHARE = 0.072f

/**
 * The largest the door mark may be drawn, as a share of the touch target it sits inside.
 *
 * ⚠ THIS IS THE HALF OF "20 % SMALLER" THAT ACTUALLY DID THE WORK. On an ordinary near-square sheet
 * the proportional size runs past this cap, so the cap — not the share — is what the reader sees.
 * Cutting only the share left the mark five per cent smaller and looking unchanged, which the
 * rendered picture showed and no measurement could.
 */
const val PLAN_DOOR_MARK_CAP = 0.40f

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
 *   · down ON the door marker  → this gesture belongs to the door. Drag moves it; a tap names it.
 *   · a second finger arrives  → pinch to zoom (only when [zoomable]).
 *   · drag while zoomed in     → pan the sheet.
 *   · up without passing slop  → a tap: the door, or the room under the finger.
 *
 * ⚠ NOTE WHAT IS **NOT** CONSUMED. At zoom 1 with one finger and no door under it, this reader takes
 * nothing and behaves exactly as the old `detectTapGestures` did — which is why no existing golden
 * or test moves. A picture that grabbed single-finger drags at rest would steal the report's own
 * scroll, because there the plan sits inside a scrolling column.
 */
private suspend fun PointerInputScope.planGestures(
    zoomable: Boolean,
    zoom: () -> Float,
    doorDragAt: () -> Offset?,
    doorTapAt: () -> Offset?,
    doorTouchPx: Float,
    onZoomPan: (centroid: Offset, zoomChange: Float, panChange: Offset) -> Unit,
    onTap: (Offset) -> Unit,
    onDoorTap: () -> Unit,
    onDoorDrag: (Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val near = { at: Offset? -> at != null && (down.position - at).getDistance() <= doorTouchPx }
        // ⚠ DRAGGING THE DOOR IS ONLY OFFERED ONCE THE DOOR IS SELECTED, and that is a safety rule
        // rather than a flourish. Any drag beginning within a 48 dp circle would otherwise move the
        // heaviest single input in the whole score — and once the plan can be panned, a finger
        // starting on the mark and meaning to shift the picture is an ordinary thing to do. Tapping
        // the mark first says out loud what it is, and only then does it follow the finger. The
        // sentence the tap prints is the same sentence that invites the drag, so the two agree.
        val dragsDoor = near(doorDragAt())
        val tapsDoor = near(doorTapAt())
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
            }
            if (!dragging) continue

            if (dragsDoor) {
                onDoorDrag(moving.position)
                moving.consume()
            } else if (zoomable && zoom() > 1f) {
                onZoomPan(moving.position, 1f, moving.positionChange())
                moving.consume()
            }
        }

        if (dragging || pinching) return@awaitEachGesture
        // A tap. The door owns it if the finger came down on the mark.
        if (tapsDoor) onDoorTap() else onTap(down.position)
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
    /** True while the door is the thing the user has selected — it is then drawn to be found. */
    doorSelected: Boolean = false,
    /** The door was tapped. The screen says what it is. */
    onTapDoor: () -> Unit = {},
    /** The door was dragged to this point on the picture, in page fractions. */
    onMoveDoorToPage: (Float, Float) -> Unit = { _, _ -> },
) {
    val colors = VastuTheme.colors
    val strokeDp = VastuTheme.spacing.s1
    val doorTouch = VastuTheme.sizes.minTouch
    // One string is ever measured here ("E"), so the default cache is ample.
    val measurer = rememberTextMeasurer()
    val doorLetterStyle = VastuTheme.type.caption.copy(color = colors.paper)
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
    val doorFill = colors.primary
    val doorHalo = colors.paper

    var zoom by remember(image) { mutableFloatStateOf(1f) }
    var pan by remember(image) { mutableStateOf(Offset.Zero) }
    // ⚠ Read LIVE, never used as a pointerInput key. Keying the gesture reader on the door would
    // cancel and restart it the instant a drag moved the door — the finger would stop working after
    // the first pixel. The editor screen carries the same note for the same reason.
    val liveDoor by rememberUpdatedState(doorAtPage)
    val liveDoorSelected by rememberUpdatedState(doorSelected)
    val liveRooms by rememberUpdatedState(rooms)

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
        run {
            Canvas(
                modifier = Modifier
                    .width(drawnWidth)
                    .height(drawnHeight)
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
                                if (f.w > 0f) Offset(f.ox + dx * f.w, f.oy + dy * f.h) else null
                            }
                        }
                        planGestures(
                            zoomable = zoomable,
                            zoom = { zoom },
                            // Only a SELECTED door may be dragged — see the note in planGestures.
                            doorDragAt = { if (liveDoorSelected) markerAt() else null },
                            doorTapAt = markerAt,
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
                                pageOf(at)?.let { (fx, fy) ->
                                    roomAtPoint(liveRooms, fx, fy)?.let { onTapRoom(it.id) }
                                }
                            },
                            onDoorTap = onTapDoor,
                            onDoorDrag = { at ->
                                pageOf(at)?.let { (fx, fy) ->
                                    onMoveDoorToPage(fx.coerceIn(0f, 1f), fy.coerceIn(0f, 1f))
                                }
                            },
                        )
                    }
                    .semantics {
                        contentDescription = buildPlanDescription(selected?.name, doorAtPage != null, zoomable)
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

                // ⭐⭐ THE FRONT DOOR, LAST, so nothing is drawn over it.
                //
                // ⚠ Sized off the a11y touch floor rather than off the line weight. The door marker on
                // the other screen is built from the stroke width and comes out about 12 dp across —
                // which the owner could not find on his own plan at arm's length. This one is drawn
                // to the size of the target it actually is, so what he sees is what he can hit.
                doorAtPage?.let { (dx, dy) ->
                    // ⚠ The letter has to FIT, so the floor is measured from the glyph rather than
                    // guessed from the line weight — the same rule the compass "N" marker follows.
                    // Without it a small plan draws a disc narrower than its own letter.
                    val glyph = measurer.measure("E", doorLetterStyle)
                    val letterFloor = maxOf(glyph.size.width, glyph.size.height) / 2f + strokeDp.toPx() / 2f
                    // Proportional, floored and capped — see [PLAN_DOOR_MARK_SHARE].
                    //
                    // ⚠ THE CAP CAME DOWN WITH THE SHARE, and it had to: on an ordinary square sheet
                    // the OLD mark was already sitting ON the cap, so cutting the share alone made
                    // it five per cent smaller instead of twenty. Found by looking at the rendered
                    // picture, which is the only thing that could have said so — every number in the
                    // geometry gate was green both before and after.
                    //
                    // Half the touch target, not all of it: the mark is what you SEE and the target
                    // is what you HIT, and they were never meant to be the same size.
                    val r = (minOf(drawn.width, drawn.height) * PLAN_DOOR_MARK_SHARE)
                        .coerceIn(maxOf(strokeDp.toPx() * 2f, letterFloor), doorTouch.toPx() * PLAN_DOOR_MARK_CAP)
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
                    val raw = Offset(origin.x + dx * drawn.width, origin.y + dy * drawn.height)
                    val at = if (zoom > 1f) raw else Offset(
                        x = raw.x.coerceIn(r, (size.width - r).coerceAtLeast(r)),
                        y = raw.y.coerceIn(r, (size.height - r).coerceAtLeast(r)),
                    )
                    // A paper collar WIDER than the disc, so the mark lifts off a busy sheet; then
                    // the filled disc; then the letter. "E" for entrance — a coloured dot on its own
                    // said only "something is here", which is what the owner could not read.
                    //
                    // ⚠ The collar has to be bigger than the disc or it is drawn and then completely
                    // painted over, which is exactly nothing.
                    drawCircle(color = doorHalo, radius = r + strokeDp.toPx() / 2f, center = at)
                    drawCircle(
                        color = doorFill.copy(alpha = if (doorSelected) 1f else 0.9f),
                        radius = r,
                        center = at,
                    )
                    drawText(
                        glyph,
                        topLeft = Offset(at.x - glyph.size.width / 2f, at.y - glyph.size.height / 2f),
                    )
                    if (doorSelected) {
                        drawCircle(
                            color = doorFill,
                            radius = r + strokeDp.toPx(),
                            center = at,
                            style = Stroke(width = strokeDp.toPx() / 2f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * What a screen reader is told about the picture.
 *
 * Kept out of the composable so the sentence can be read and changed without going near the drawing,
 * and so every branch of it is exercised by a plain unit test rather than only by a screenshot.
 */
internal fun buildPlanDescription(selectedName: String?, hasDoor: Boolean, zoomable: Boolean): String {
    val head = selectedName
        ?.let { "Your plan, showing roughly where $it was read" }
        ?: "Your scanned plan. Tap a room to see roughly where we read it."
    // ⚠ The door sentence STATES a fact and does not issue an instruction. Someone using a screen
    // reader cannot pinch and cannot drag a mark they navigate to by swiping, so telling them to do
    // either is telling them to do something they cannot. The button lower down the screen sets the
    // door with a single tap and is the route that works for everybody — it is named here instead.
    // ⚠ It names the LETTER now, because the mark carries one since 17 Aug 2026. A sighted reader
    // sees an E on the wall; a screen-reader user hearing only "a mark" cannot ask anybody about it.
    val door = if (hasDoor) " Your front door is marked E on it. To move it, use \"Put the front door somewhere else\" below." else ""
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
