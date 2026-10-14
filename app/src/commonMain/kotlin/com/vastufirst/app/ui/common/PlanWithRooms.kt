// PlanWithRooms.kt — the user's own scanned plan, with every room we read on it and tappable.
//
// ⭐⭐ ONE BEHAVIOUR FOR THE PLAN AND THE LIST (owner, 10 Aug 2026): *"Tapping a room on the floor
// plan should also highlight it the same way it highlights when tapping the room on the list…"*. The
// list half is `VastuRoomRow`; this is the plan half. Both SELECT the same room the same way.
//
// ⚠ ONE SCREEN DRAWS IT NOW: the report (since 30 Sep 2026, when "Check what we read" was removed).
// That screen was the only one that pinned this picture outside a scrolling page, so it was the only
// one that could let a finger pinch it bigger or drag the front-door E across it. Both went with it:
// on the report the picture sits inside the page's scroll, where a picture that claimed drags would
// eat the scroll, and the E is moved on the front-door screen ("Change front door").
package com.vastufirst.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
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
 * ⭐⭐ WHERE THE PICTURE ACTUALLY LANDS INSIDE ITS BOX — ONE function, and the whole safety of this
 * file.
 *
 * The photo is letterboxed: it is fitted into the box and centred, so it almost never fills it. Room
 * highlights, the front-door marker, the tapped room's label and every tap all have to agree about
 * that rectangle to within a pixel, or the picture says one thing and the finger does another. They
 * all call this.
 */
internal data class PlanFit(val ox: Float, val oy: Float, val w: Float, val h: Float)

internal fun planFit(
    boxW: Float,
    boxH: Float,
    imageW: Int,
    imageH: Int,
): PlanFit {
    if (imageW <= 0 || imageH <= 0 || boxW <= 0f || boxH <= 0f) return PlanFit(0f, 0f, 0f, 0f)
    val scale = minOf(boxW / imageW, boxH / imageH)
    val w = imageW * scale
    val h = imageH * scale
    return PlanFit(ox = (boxW - w) / 2f, oy = (boxH - h) / 2f, w = w, h = h)
}

/**
 * ⚠ THE DOOR MARK ITSELF NO LONGER LIVES HERE. Its size and its drawing moved to [DoorMark.kt] on
 * 18 August 2026 so that this picture, the "Where is your front door?" screen and the hand-drawn
 * editor all show the reader the SAME mark.
 */

/**
 * ⭐ ONE GESTURE READER FOR THE PICTURE — a tap, on the front door or on a room.
 *
 * ⚠ It takes NOTHING that moves. The picture sits inside the report's scrolling page, so a drag that
 * starts on it — anywhere, the E included — is left to the page's own scroll. Only a finger that goes
 * down and comes up without travelling past touch slop is this picture's: the E's own reading if it
 * landed on the E, otherwise the room under it.
 */
private suspend fun PointerInputScope.planTaps(
    doorAt: () -> Offset?,
    doorTouchPx: Float,
    onTap: (Offset) -> Unit,
    onDoorTap: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val markerNow = doorAt()
        val onDoor = markerNow != null && (down.position - markerNow).getDistance() <= doorTouchPx
        var travelled = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop || change.isConsumed) {
                travelled = true
            }
        }
        if (travelled) return@awaitEachGesture
        if (onDoor) onDoorTap() else onTap(down.position)
    }
}

/**
 * The plan, and a tap that selects a room on it.
 *
 * Nothing is drawn over a room at rest — no outline, no box. The selected room shows its short
 * direction on its middle ([TappedRoomDirection]), and its card says the rest.
 */
@Composable
fun PlanWithRooms(
    image: ImageBitmap,
    rooms: List<PlanRoom>,
    selectedId: String?,
    onTapRoom: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Caps the picture's height.
     *
     * ⚠ NOT called `maxHeight`. Inside a BoxWithConstraints the scope has its own `maxHeight`, and it
     * is the CLOSER name — so a bare `maxHeight` in the body would silently read the box's constraint
     * instead of this cap, and the cap would never apply.
     */
    maxPlanHeight: Dp = Dp.Unspecified,
    /**
     * ⭐⭐ THE FRONT DOOR, DRAWN ON THE PLAN — where on the picture it sits, in page fractions.
     *
     * Null when no door is known. The caller works this out with `doorMarkerOnPage`, the exact inverse
     * of the front-door screen's tap conversion, so the mark lands where the reader put it.
     */
    doorAtPage: Pair<Float, Float>? = null,
    /** The door was tapped. On the report this brings its reading into view. */
    onTapDoor: () -> Unit = {},
    /**
     * ⭐⭐ THE ENGINE'S OWN DIRECTION FOR EACH ROOM, by room id — the SAME zone and words its card
     * prints (owner, 29 Sep 2026; redrawn by him 30 Sep). At rest nothing is shown; the tapped room shows
     * its SHORT direction ("SW") on the middle of the room, and its full name stays in its card. A room
     * missing from this map shows nothing: this picture never works a direction out for itself. See
     * RoomDirection.kt.
     */
    directions: Map<String, RoomDirection> = emptyMap(),
) {
    val colors = VastuTheme.colors
    val strokeDp = VastuTheme.spacing.s1
    val doorTouch = VastuTheme.sizes.minTouch
    // One string is ever measured here (the door mark's letter), so the default cache is ample.
    val measurer = rememberTextMeasurer()
    // ⚠ The SIZE on this style is ignored — [drawDoorMark] replaces it with one derived from the
    // disc, so the letter cannot outgrow its own circle at a 200 % font. Only the family lands.
    val doorLetterStyle = VastuTheme.type.caption
    // The clamp stops a freakishly tall or wide sheet taking the whole page or collapsing to a slot.
    val realAspect = if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height else PLAN_DEFAULT_ASPECT
    val aspect = realAspect.coerceIn(PLAN_MIN_ASPECT, PLAN_MAX_ASPECT)
    val selected = rooms.firstOrNull { it.id == selectedId }

    // ⚠ Read LIVE, never used as a pointerInput key: the gesture reader is keyed on the picture, and
    // a new door or room list must change what the NEXT tap means without restarting it.
    val liveDoor by rememberUpdatedState(doorAtPage)
    val liveRooms by rememberUpdatedState(rooms)
    val liveOnTapDoor by rememberUpdatedState(onTapDoor)
    val liveOnTapRoom by rememberUpdatedState(onTapRoom)

    // ⚠⚠ THE HEIGHT CAP MUST BE APPLIED TO THE PICTURE, NOT AROUND IT — found by looking at the
    // render, 10 Aug 2026. Capping a parent box and giving the child an aspect ratio does NOT bound
    // the child: it takes the full width, works out its own height from the ratio, and draws straight
    // out of the bottom of its parent, which does not clip. Sizing the picture explicitly from the
    // available width and the cap leaves nothing to overflow.
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val drawnHeight = if (maxPlanHeight == Dp.Unspecified) this.maxWidth / aspect
        else minOf(this.maxWidth / aspect, maxPlanHeight)
        val drawnWidth = drawnHeight * aspect
        // ⚠ Sized directly rather than filling its parent. The fill-the-whole-parent modifier is also
        // the string the window-inset gate greps for when it looks for a screen root, and a picture
        // inside a screen is not one. (Do not spell that modifier's name anywhere in this file,
        // comments included: the gate reads the file as text, exactly like the CI skip markers do.)
        Box(Modifier.width(drawnWidth).height(drawnHeight)) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    // ⚠⚠ A COMPOSE CANVAS DOES NOT CLIP ITS OWN INK. `drawImage` is not bounded by
                    // the node's size, and the geometry gate measures layout boxes, not pixels.
                    .clipToBounds()
                    .pointerInput(image) {
                        val fitNow = {
                            planFit(size.width.toFloat(), size.height.toFloat(), image.width, image.height)
                        }
                        val markerAt = {
                            liveDoor?.let { (dx, dy) ->
                                val f = fitNow()
                                if (f.w > 0f) {
                                    val r = doorMarkRadiusPx(f.w, f.h, doorTouch.toPx())
                                    doorCentrePx(f, dx, dy, r, size.width.toFloat(), size.height.toFloat())
                                } else {
                                    null
                                }
                            }
                        }
                        planTaps(
                            doorAt = markerAt,
                            doorTouchPx = doorTouch.toPx() / 2f,
                            onTap = { at ->
                                val f = fitNow()
                                if (f.w > 0f && f.h > 0f) {
                                    val fx = (at.x - f.ox) / f.w
                                    val fy = (at.y - f.oy) / f.h
                                    // ⭐ The finger aims at the room itself, its printed name — see
                                    // [roomNearestCentre]. ⭐ ONE SELECTION, ONE RULE: a room chosen here
                                    // is the same selection as a tap on its card.
                                    roomNearestCentre(liveRooms, fx, fy, f.w, f.h, doorTouch.toPx())
                                        ?.let { liveOnTapRoom(it.id) }
                                }
                            },
                            onDoorTap = { liveOnTapDoor() },
                        )
                    }
                    .semantics {
                        contentDescription = buildPlanDescription(
                            selected?.name,
                            doorAtPage != null,
                            selectedDirection = selected?.let { directions[it.id]?.words },
                        )
                    },
            ) {
                val fit = planFit(size.width, size.height, image.width, image.height)
                val drawn = Size(fit.w, fit.h)
                val origin = Offset(fit.ox, fit.oy)
                drawImage(
                    image = image,
                    dstOffset = IntOffset(origin.x.toInt(), origin.y.toInt()),
                    dstSize = IntSize(drawn.width.toInt(), drawn.height.toInt()),
                )
                // ⭐⭐ NOTHING IS DRAWN ON THE PHOTOGRAPH FOR A ROOM — no box, no dot, no ring. The tapped
                // room's short direction is one small label laid over the picture by
                // [TappedRoomDirection], on the room's middle; at rest there is none.

                // ⭐⭐ THE FRONT DOOR, LAST, so nothing is drawn over it.
                doorAtPage?.let { (dx, dy) ->
                    // Proportional between two fixed bounds — see [doorMarkRadiusPx], the one place the
                    // entrance mark's size is decided for the whole app.
                    val r = doorMarkRadiusPx(drawn.width, drawn.height, doorTouch.toPx())
                    // ⭐⭐ IT SITS ON THE WALL, HALF IN AND HALF OUT (owner, 17 Aug 2026: *"make it sit
                    // on the border of the map so its half out and half in"*). See [doorCentrePx] for
                    // the one case it is nudged.
                    val at = doorCentrePx(fit, dx, dy, r, size.width, size.height)
                    // ⭐ ONE DRAWING, SHARED with the front-door screen and the hand-drawn editor —
                    // see [drawDoorMark].
                    drawDoorMark(
                        center = at,
                        radius = r,
                        selected = false,
                        colors = colors,
                        strokePx = strokeDp.toPx(),
                        measurer = measurer,
                        letterStyle = doorLetterStyle,
                    )
                }
            }

            // ⭐⭐ THE TAPPED room's short direction, the engine's own, laid over the user's own photograph
            // on the middle of the room (owner, 30 Sep 2026). Nothing at rest, and never the full name:
            // that is in the room's card.
            val tappedDirection = selected?.let { directions[it.id] }
            val tappedMiddle = selected?.centreOrNull()
            if (selected != null && tappedDirection != null && tappedMiddle != null) {
                val density = LocalDensity.current
                val boxW = with(density) { drawnWidth.toPx() }
                val boxH = with(density) { drawnHeight.toPx() }
                val fit = planFit(boxW, boxH, image.width, image.height)
                val at = Offset(fit.ox + tappedMiddle.first * fit.w, fit.oy + tappedMiddle.second * fit.h)
                if (at.x in 0f..boxW && at.y in 0f..boxH) {
                    TappedRoomDirection(PlanLabel(selected.id, tappedDirection, at), boxW, boxH)
                }
            }
        }
    }
}

/**
 * Where the E's centre is drawn, in the picture box's pixels.
 *
 * ⭐ ONE FUNCTION FOR THE DRAWING AND THE TOUCH TEST, so the two can never disagree about where the E
 * is. The centre is nudged just inside the box when it would otherwise sit on the picture's own edge
 * (the no-page-box fallback, where the home frame IS the sheet) — the only case where this moves it.
 */
internal fun doorCentrePx(
    fit: PlanFit,
    dx: Float,
    dy: Float,
    radius: Float,
    boxW: Float,
    boxH: Float,
): Offset {
    val raw = Offset(fit.ox + dx * fit.w, fit.oy + dy * fit.h)
    return Offset(
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
    /** The tapped room's direction in its card's words — what a screen reader is told about it. */
    selectedDirection: String? = null,
): String {
    val head = when {
        // ⚠ At rest nothing is written on the plan (owner, 30 Sep 2026), so this says what a tap
        // gives rather than "with each room's direction written on it".
        selectedName == null -> "Your scanned plan. Tap a room to hear its direction."
        // The same words the room's card prints, in full although the plan shows only the short
        // form — with the card's middle dot said as words.
        selectedDirection != null ->
            "Your plan. $selectedName is in the ${spokenDirection(selectedDirection)}."
        else -> "Your plan, showing where $selectedName was read."
    }
    // ⚠ The door sentence STATES a fact and does not issue an instruction, and it names the LETTER the
    // mark carries, so a screen-reader user can ask somebody about it.
    val door = if (hasDoor) " Your front door is marked E on it." else ""
    return head + door
}

/**
 * The picture's shape, bounded: the bounds stop a freakishly tall or wide sheet from taking the whole
 * page or collapsing into a slot.
 */
const val PLAN_MIN_ASPECT = 0.7f
const val PLAN_MAX_ASPECT = 1.8f
const val PLAN_DEFAULT_ASPECT = 1.2f
