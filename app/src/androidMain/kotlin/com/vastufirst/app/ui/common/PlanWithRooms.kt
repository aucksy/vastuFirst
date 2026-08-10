// PlanWithRooms.kt — the user's own scanned plan, with every room we read drawn on it and tappable.
//
// ⭐⭐ ONE COMPONENT, TWO SCREENS, ONE BEHAVIOUR (owner, 10 Aug 2026): *"Tapping a room on the floor
// plan should also highlight it the same way it highlights when tapping the room on the list…  Build
// a common UI/UX for this room highlight in the list which works the same way if user taps the room
// in list or room on floor plan"*.
//
// The list half of that behaviour is `VastuRoomRow`; this is the plan half. Both call the SAME
// handler with the SAME room, so "tap it here" and "tap it there" cannot drift apart — which is the
// whole of what he asked for, and the thing two separate implementations would quietly lose.
package com.vastufirst.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
) {
    val colors = VastuTheme.colors
    val strokeDp = VastuTheme.spacing.s1
    val aspect = if (image.width > 0 && image.height > 0) {
        (image.width.toFloat() / image.height).coerceIn(PLAN_MIN_ASPECT, PLAN_MAX_ASPECT)
    } else {
        PLAN_DEFAULT_ASPECT
    }
    val selected = rooms.firstOrNull { it.id == selectedId }
    val quiet = colors.borderStrong
    // ⚠ Resolved HERE, in composable scope. `editorColor()` reads the theme, and a draw lambda is not
    // a composable scope — asking for it inside the Canvas does not compile.
    val selectedTint = selected?.type?.editorColor() ?: colors.primary

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
                    .pointerInput(rooms, image) {
                        detectTapGestures { tap ->
                            // The picture is letterboxed inside this box, so a tap has to be
                            // converted through the DRAWN rectangle, never the box's own size.
                            val scale = minOf(size.width.toFloat() / image.width, size.height.toFloat() / image.height)
                            val drawnW = image.width * scale
                            val drawnH = image.height * scale
                            val originX = (size.width - drawnW) / 2f
                            val originY = (size.height - drawnH) / 2f
                            if (drawnW > 0f && drawnH > 0f) {
                                val fx = (tap.x - originX) / drawnW
                                val fy = (tap.y - originY) / drawnH
                                roomAtPoint(rooms, fx, fy)?.let { onTapRoom(it.id) }
                            }
                        }
                    }
                    .semantics {
                        contentDescription = selected
                            ?.let { "Your plan, showing roughly where ${it.name} was read" }
                            ?: "Your scanned plan. Tap a room to see roughly where we read it."
                    },
            ) {
                val scale = minOf(size.width / image.width, size.height / image.height)
                val drawn = Size(image.width * scale, image.height * scale)
                val origin = Offset((size.width - drawn.width) / 2f, (size.height - drawn.height) / 2f)
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
            }
        }
    }
}

/**
 * The picture's shape, bounded. Carried over from the review screen unchanged: the bounds stop a
 * freakishly tall or wide sheet from taking the whole screen or collapsing into a slot.
 */
const val PLAN_MIN_ASPECT = 0.7f
const val PLAN_MAX_ASPECT = 1.8f
const val PLAN_DEFAULT_ASPECT = 1.2f
