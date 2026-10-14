// TapToPlace.kt — "show us on your plan": the rooms a scan read but could not place, put on the grid
// by tapping the user's own photograph where each one is.
//
// ⭐⭐ WHY (owner, 14 Oct 2026, approved as the first version of the stage 4 item "reading a plan without
// ever falling back to a grid"). About one plan in six comes back with every room NAMED and none of them
// PLACED: the reader's rectangles were a template, or there were too many rooms to trust them. Those
// rooms used to arrive as a row of equal squares with one instruction — drag each one to its real
// place — on a grid that looks nothing like the sheet in the user's hand. The user already knows where
// their kitchen is, and their plan is right there: one tap on it says more than a drag across a grid.
//
// HOW IT STAYS HONEST:
//  · The user's tap decides WHERE, and nothing of ours. A centre the reader guessed is exactly what
//    failed; this never reads one.
//  · No room the reader found is dropped, retyped or renamed. Every room is tapped before anything is
//    placed, so nothing lands in a corner the user did not choose (a room parked at the top-left would
//    be scored north-west).
//  · Nothing is decided for good. The rooms land on the ordinary grid, where every drag, grip and key
//    still works, and the parked state ends exactly as it does when the user moves a room by hand.
//
// The maths ([placeFromTaps]) is pure and lives here, outside the composable, so ordinary tests cover
// it — a screenshot cannot tap.
package com.vastufirst.app.ui.grid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.vastufirst.app.ui.common.PLAN_MAX_ASPECT
import com.vastufirst.app.ui.common.PLAN_MIN_ASPECT
import com.vastufirst.app.ui.common.label
import com.vastufirst.app.ui.common.planFit
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.VastuCard
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.editor.CellRect
import com.vastufirst.shared.editor.anyOverlap
import kotlin.math.abs
import kotlin.math.roundToInt

/** A point on the user's plan photograph, in fractions of the picture (origin top-left). */
data class PlanPoint(val x: Float, val y: Float)

/**
 * Below this spread (a fraction of the picture) the taps are treated as one line along that axis, and
 * the rooms go to the middle of it rather than being stretched across the whole grid by a few pixels
 * of finger wobble.
 */
internal const val TAP_SPREAD_MIN = 0.03f

/**
 * ⭐⭐ THE ROOMS, PUT WHERE THE USER TAPPED THEM.
 *
 * The taps' own outline is the home: the left-most tapped room goes against the west wall, the top-most
 * against the north wall, and everything between keeps its place in proportion, along each axis on its
 * own. That is the right proportion for this app because the engine cuts a home into thirds along each
 * axis separately — so a room tapped in the north-east third of the tapped home is scored north-east.
 *
 * Each room keeps its size. A room whose spot is already taken (two rooms tapped close together on a
 * crowded grid) goes to the NEAREST free spot of the same size, then to a narrower one, then to a single
 * cell — never on top of another room (the engine would score the buried room twice) and never dropped.
 * Rooms are placed in the order they were tapped, so the first rooms tapped keep their exact spots.
 *
 * Returns [rooms] unchanged until EVERY room has a tap: a room with no tap has no honest place to go.
 */
fun placeFromTaps(
    rooms: List<GridRoom>,
    taps: Map<String, PlanPoint>,
    cols: Int,
    rows: Int,
): List<GridRoom> {
    if (rooms.isEmpty() || rooms.any { it.id !in taps }) return rooms
    val points = rooms.map { taps.getValue(it.id) }
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    fun along(v: Float, lo: Float, hi: Float): Float =
        if (hi - lo < TAP_SPREAD_MIN) 0.5f else ((v - lo) / (hi - lo)).coerceIn(0f, 1f)

    val byId = rooms.associateBy { it.id }
    val tapOrder = taps.keys.filter { it in byId }
    val placed = LinkedHashMap<String, CellRect>()
    for (id in tapOrder) {
        val room = byId.getValue(id)
        val p = taps.getValue(id)
        val w = room.w.coerceIn(1, cols)
        val h = room.h.coerceIn(1, rows)
        // The room's CENTRE goes to the tapped proportion of the space its centre can occupy.
        val centreX = w / 2f + along(p.x, minX, maxX) * (cols - w)
        val centreY = h / 2f + along(p.y, minY, maxY) * (rows - h)
        placed[id] = nearestFree(centreX, centreY, w, h, cols, rows, placed.values.toList())
    }
    return rooms.map { r ->
        val c = placed.getValue(r.id)
        r.copy(col = c.col, row = c.row, w = c.w, h = c.h)
    }
}

/**
 * The free rectangle whose centre is nearest ([cx], [cy]), trying the room's own size first, then each
 * side cut to one cell, then a single cell. A single cell always exists while the grid has room for one
 * more room; if it truly has none, the room goes to its own spot and the overlap guard in the editor
 * refuses the next move, which is the same outcome as a plot made too small by hand.
 */
private fun nearestFree(
    cx: Float, cy: Float, w: Int, h: Int, cols: Int, rows: Int, taken: List<CellRect>,
): CellRect {
    val sizes = listOf(w to h, w to 1, 1 to h, 1 to 1).distinct()
    for ((sw, sh) in sizes) {
        var best: CellRect? = null
        var bestD = Float.MAX_VALUE
        for (row in 0..rows - sh) for (col in 0..cols - sw) {
            val c = CellRect(col, row, sw, sh)
            if (anyOverlap(c, taken)) continue
            val d = abs(col + sw / 2f - cx) + abs(row + sh / 2f - cy)
            if (d < bestD) { bestD = d; best = c }
        }
        if (best != null) return best
    }
    val col = (cx - w / 2f).roundToInt().coerceIn(0, cols - w)
    val row = (cy - h / 2f).roundToInt().coerceIn(0, rows - h)
    return CellRect(col, row, w, h)
}

/** What the plan prints for this room — what the user looks for on their sheet — or its kind. */
fun tapName(room: GridRoom): String =
    room.label.trim().ifBlank { room.type.label() }

/** The room the next tap is for: the first, in the editor's order, that has no tap yet and was not put off. */
internal fun nextToTap(rooms: List<GridRoom>, taps: Map<String, PlanPoint>, putOff: List<String>): GridRoom? {
    val waiting = rooms.filter { it.id !in taps }
    waiting.firstOrNull { it.id !in putOff }?.let { return it }
    // Everything left was skipped: they come round again, in the order they were skipped.
    val firstPutOff = putOff.firstOrNull { id -> waiting.any { it.id == id } }
    return waiting.firstOrNull { it.id == firstPutOff } ?: waiting.firstOrNull()
}

// Saved through rotation as plain strings: "id|x|y" per tap, in tap order.
private fun encode(taps: Map<String, PlanPoint>): List<String> = taps.map { (id, p) -> "$id|${p.x}|${p.y}" }
private fun decode(saved: List<String>): Map<String, PlanPoint> = LinkedHashMap<String, PlanPoint>().apply {
    for (s in saved) {
        val parts = s.split('|')
        if (parts.size == 3) put(parts[0], PlanPoint(parts[1].toFloatOrNull() ?: continue, parts[2].toFloatOrNull() ?: continue))
    }
}

/**
 * ⭐ The card: the user's plan, the room to find next, and the button that puts them all on the grid.
 *
 * [startTaps] exists for the screenshot harness, which cannot tap (the same reason the editor has its
 * other `start…` switches).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TapToPlaceCard(
    image: ImageBitmap,
    rooms: List<GridRoom>,
    onPlace: (Map<String, PlanPoint>) -> Unit,
    maxPlanHeight: Dp,
    startTaps: Map<String, PlanPoint> = emptyMap(),
) {
    val colors = VastuTheme.colors
    var saved by rememberSaveable { mutableStateOf(encode(startTaps)) }
    var putOff by rememberSaveable { mutableStateOf(listOf<String>()) }
    val taps = decode(saved).filterKeys { id -> rooms.any { it.id == id } }
    val current = nextToTap(rooms, taps, putOff)
    val done = taps.size
    val total = rooms.size

    VastuCard(accent = colors.primary) {
        VText("Show us on your plan", style = VastuTheme.type.h3, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            if (current != null) "Tap your plan where it says “${tapName(current)}”."
            else "Every room has its place. Put them on the grid, then adjust any of them there.",
            style = VastuTheme.type.body, color = colors.textPrimary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s1))
        VText(
            "$done of $total rooms shown",
            style = VastuTheme.type.caption, color = colors.textSecondary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        TapPlanPicture(
            image = image,
            points = taps.values.toList(),
            maxPlanHeight = maxPlanHeight,
            description = if (current != null) "Your plan. Tap where it says ${tapName(current)}."
            else "Your plan, with every room shown on it.",
            onTap = { p ->
                if (current != null) {
                    saved = encode(LinkedHashMap(taps).apply { put(current.id, p) })
                    putOff = putOff - current.id
                }
            },
        )
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
            verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
        ) {
            if (current != null && total - done > 1) {
                VastuButton(
                    "Skip for now",
                    onClick = { putOff = (putOff - current.id) + current.id },
                    style = VastuButtonStyle.SECONDARY, large = false,
                )
            }
            if (done > 0) {
                VastuButton(
                    "Undo last tap",
                    onClick = { saved = encode(LinkedHashMap(taps).apply { remove(taps.keys.last()) }) },
                    style = VastuButtonStyle.SECONDARY, large = false,
                )
            }
        }
        if (current == null) {
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            VastuButton("Put them on the grid", onClick = { onPlace(taps) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * The photograph, letterboxed exactly as the report's plan is ([planFit]), with a dot on every place
 * already tapped. Only a tap counts: a finger that travels is the page scrolling, and is left to it.
 */
@Composable
private fun TapPlanPicture(
    image: ImageBitmap,
    points: List<PlanPoint>,
    maxPlanHeight: Dp,
    description: String,
    onTap: (PlanPoint) -> Unit,
) {
    val colors = VastuTheme.colors
    val dotFill = colors.primary
    val dotRing = colors.primaryDark
    val dotRadius = VastuTheme.spacing.s2
    val ringWidth = VastuTheme.borders.strong
    val liveOnTap by rememberUpdatedState(onTap)
    val aspect = (if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height else 1f)
        .coerceIn(PLAN_MIN_ASPECT, PLAN_MAX_ASPECT)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val drawnHeight = minOf(this.maxWidth / aspect, maxPlanHeight)
        val drawnWidth = drawnHeight * aspect
        Box(Modifier.width(drawnWidth).height(drawnHeight)) {
            Canvas(
                Modifier
                    .matchParentSize()
                    .clipToBounds()
                    .testTag(TAP_PLAN_TAG)
                    .semantics { contentDescription = description }
                    .pointerInput(image) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var travelled = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) travelled = true
                            }
                            if (travelled) return@awaitEachGesture
                            val f = planFit(size.width.toFloat(), size.height.toFloat(), image.width, image.height)
                            if (f.w <= 0f || f.h <= 0f) return@awaitEachGesture
                            val fx = (down.position.x - f.ox) / f.w
                            val fy = (down.position.y - f.oy) / f.h
                            // A tap in the margin around the picture is not a place on the plan.
                            if (fx in 0f..1f && fy in 0f..1f) liveOnTap(PlanPoint(fx, fy))
                        }
                    },
            ) {
                val fit = planFit(size.width, size.height, image.width, image.height)
                drawImage(
                    image = image,
                    dstOffset = IntOffset(fit.ox.toInt(), fit.oy.toInt()),
                    dstSize = IntSize(fit.w.toInt(), fit.h.toInt()),
                )
                // ⚠ SEE-THROUGH, found by looking at the first render: a solid dot sits exactly on the
                // room's printed name — the tap goes where the words are — and hid the very caption the
                // user had just found. A ring over a faint wash marks the spot and leaves the words legible.
                val r = dotRadius.toPx()
                for (p in points) {
                    val at = Offset(fit.ox + p.x * fit.w, fit.oy + p.y * fit.h)
                    drawCircle(dotFill.copy(alpha = DOT_WASH_ALPHA), radius = r, center = at)
                    drawCircle(dotRing, radius = r, center = at, style = Stroke(width = ringWidth.toPx()))
                }
            }
        }
    }
}

/** How much of the tap mark's colour fills its middle: enough to see, little enough to read through. */
private const val DOT_WASH_ALPHA = 0.25f

/** The photograph's test tag — the tests find the picture by it. */
const val TAP_PLAN_TAG = "editor.tapPlan"
