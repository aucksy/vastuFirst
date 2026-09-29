// RoomMarker.kt — what a tapped room shows on the user's own plan: the box the reader drew, or the
// room's DIRECTION. The direction is a trial, built beside the box and switched by one value of data.
//
// ⭐⭐ WHY THIS FILE EXISTS (owner, 29 Sep 2026): *"On 'Check what we read', tapping a room draws a box
// over where the reader thinks the room is. Those boxes have never been accurate, because every floor
// plan is drawn differently. But Vastu only needs each room's DIRECTION. So, as a trial: when I tap a
// room, show its direction ON the room, on the plan, instead of drawing a box."*
//
// ⚠ IT IS A TRIAL, AND THE BOX WAY IS STILL HERE, WHOLE. Nothing that draws, taps or tests the box was
// deleted or rewritten. `directionTrial` in `scan/reader-config.json` picks the default; with it off
// the app is byte-for-byte the box way and offers no chooser at all. With it on, a small chooser under
// the plan flips between the two on the SAME plan, so they can be compared without reinstalling.
//
// ⚠⚠ THE WORDS ON THE ROOM ARE NEVER WORKED OUT HERE. They are the engine's own, handed in by the
// screen from the same result its rows print — see `roomReadings` on "Check what we read" and the
// report's room list. Deriving a direction from where a dot sits on the photograph would be a second
// calculation, and one room called two things two inches apart is exactly the defect the rows were
// built to prevent.
package com.vastufirst.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuChip
import com.vastufirst.designsystem.theme.VastuTheme
import kotlin.math.roundToInt

/** What a tapped room shows on the plan. */
enum class RoomMarker {
    /** The rectangle the reader drew — everything before 29 Sep 2026. */
    BOX,

    /** The room's direction, in the engine's own words, on the room — the trial. */
    DIRECTION,
}

/**
 * ⭐ THE ONE PLACE THE TRIAL'S STATE LIVES — shared by "Check what we read" and the report, so both
 * pictures always show the same kind of marker.
 *
 * [offered] is the switch from the data: false = the old app exactly, box only and no chooser. When it
 * is on, [current] starts on the direction (the trial is what he installs to feel) and the chooser can
 * flip it for comparison. Held for the life of the app and NOT saved: every launch starts on the trial
 * again, so a comparison made yesterday cannot quietly leave the trial switched off today.
 */
class RoomMarkerChoice(trialOn: Boolean) {
    val offered: Boolean = trialOn
    var current: RoomMarker by mutableStateOf(if (trialOn) RoomMarker.DIRECTION else RoomMarker.BOX)
}

/** The test tag on the direction label a tapped room shows on the plan. */
const val ROOM_DIRECTION_TAG = "plan.room.direction"

/** The test tag on the trial's chooser under the plan. */
const val ROOM_MARKER_CHOICE_TAG = "plan.marker.choice"

/** Where a room is taken to BE, as one point: the middle of the rectangle it was read from. */
internal fun PlanRoom.centreOrNull(): Pair<Float, Float>? =
    box?.let { (it.x + it.w / 2.0).toFloat() to (it.y + it.h / 2.0).toFloat() }

/**
 * ⭐ WHICH ROOM A TAP MEANT, WHEN WHAT IS DRAWN IS A DOT PER ROOM rather than a box per room.
 *
 * With the box way the finger aims at an outline it can see, so "the smallest box that contains the
 * tap" ([roomAtPoint]) is right. With the direction way no box is drawn — the finger aims at a room's
 * ring, or at its name printed on the photograph — and smallest-first then picks wrongly exactly where
 * the reader's boxes are worst: a toilet's box spilling over the middle of the living room would take
 * the tap aimed at the LIVING room's own ring. So here the nearest ring wins among the rooms whose box
 * contains the tap; a tap outside every box takes the nearest ring within [maxPx]. Distances are
 * measured in drawn pixels ([pxPerX], [pxPerY]), so a tall sheet and a wide one behave the same.
 *
 * Pure, so every case is pinned by a plain test (RoomMarkerTest) rather than by a picture.
 */
fun roomNearestCentre(
    rooms: List<PlanRoom>,
    x: Float,
    y: Float,
    pxPerX: Float,
    pxPerY: Float,
    maxPx: Float,
): PlanRoom? {
    fun distPx(r: PlanRoom): Float? = r.centreOrNull()?.let { (cx, cy) ->
        val dx = (x - cx) * pxPerX
        val dy = (y - cy) * pxPerY
        kotlin.math.sqrt(dx * dx + dy * dy)
    }
    val containing = rooms.filter { r ->
        val b = r.box ?: return@filter false
        x >= b.x && x <= b.x + b.w && y >= b.y && y <= b.y + b.h
    }
    containing.minByOrNull { distPx(it) ?: Float.MAX_VALUE }?.let { return it }
    return rooms.mapNotNull { r -> distPx(r)?.let { r to it } }
        .filter { it.second <= maxPx }
        .minByOrNull { it.second }
        ?.first
}

/**
 * Where the direction label's top-left corner goes, in the picture box's pixels: centred over the
 * room's dot and just above it; below it when there is no room above; and always wholly inside the
 * box, so the words are never cut off by the picture's own edge.
 *
 * Pure, so the edge cases — a room hard against the top wall, a label wider than the room is from the
 * side — are pinned by a plain test rather than found in a photograph.
 */
internal fun directionLabelTopLeft(
    pinX: Float,
    pinY: Float,
    labelW: Int,
    labelH: Int,
    boxW: Float,
    boxH: Float,
    gapPx: Float,
): IntOffset {
    val above = pinY - gapPx - labelH
    val y = if (above >= 0f) above else pinY + gapPx
    val x = pinX - labelW / 2f
    return IntOffset(
        x.coerceIn(0f, (boxW - labelW).coerceAtLeast(0f)).roundToInt(),
        y.coerceIn(0f, (boxH - labelH).coerceAtLeast(0f)).roundToInt(),
    )
}

/**
 * ⭐ THE ROOM'S DIRECTION, ON THE ROOM — the trial itself.
 *
 * The row's own direction pill, made solid: the same small type as the pill on the room's row, so the
 * two read as one thing said twice, in ink with paper letters, because it sits on somebody's
 * photograph and has to read on any of them — a white CAD sheet, a coloured brochure, a grey render.
 * The ring under it is drawn by the picture's canvas (see [PlanWithRooms]); this is only the words,
 * placed by [directionLabelTopLeft] [clearancePx] away from the ring's middle.
 *
 * ⚠ The row pill's SMALL type, not a heading's. The first render used the label style and, at a 200 %
 * font, the words were big enough to cover the neighbouring room's printed name.
 *
 * ⚠ NO POINTER INPUT, on purpose. It lies over the picture, and the picture's one gesture reader must
 * still receive every touch — a tap on the label is a tap on that room, which it already is.
 */
@Composable
internal fun RoomDirectionLabel(text: String, pinPx: Offset, boxW: Float, boxH: Float, clearancePx: Float) {
    val colors = VastuTheme.colors
    Layout(
        content = {
            VText(
                text = text,
                style = VastuTheme.type.caption,
                color = colors.paper,
                modifier = Modifier
                    .testTag(ROOM_DIRECTION_TAG)
                    .clip(VastuTheme.shapes.full)
                    .background(colors.textPrimary)
                    .padding(horizontal = VastuTheme.spacing.s2, vertical = VastuTheme.spacing.s1),
            )
        },
    ) { measurables, constraints ->
        // Never wider than the picture: at a 200 % font on a 320 dp phone the words wrap before they
        // would run off the side.
        val label = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth.coerceAtLeast(0)))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val at = directionLabelTopLeft(pinPx.x, pinPx.y, label.width, label.height, boxW, boxH, clearancePx)
            label.place(at.x, at.y)
        }
    }
}

/**
 * ⭐ THE TRIAL'S CHOOSER — the box or the direction, on the same plan, without reinstalling (owner:
 * *"If it is cheap to do, let me compare both views on the same plan without reinstalling."*).
 *
 * It says "Trial" in its own words because it is one: a reader handed this build should know the
 * choice is being tried, not wonder why a report has a setting in it. Shown only while the switch in
 * the data is on — with it off, nothing here is drawn anywhere.
 *
 * ⚠ Two chips in a FlowRow, not a segmented bar. A segment gets half the row and "Its direction" at a
 * 200 % font is wider than half of a 320 dp phone; a chip hugs its words and a FlowRow puts the second
 * one on its own line when the first does not leave room — see the note on `VastuSegmented`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomMarkerChooser(current: RoomMarker, onChange: (RoomMarker) -> Unit, modifier: Modifier = Modifier) {
    val colors = VastuTheme.colors
    Column(modifier.testTag(ROOM_MARKER_CHOICE_TAG)) {
        VText("Trial — a tapped room shows", style = VastuTheme.type.caption, color = colors.textSecondary)
        Spacer(Modifier.height(VastuTheme.spacing.s1))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
            verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
        ) {
            VastuChip(
                text = "Its direction",
                selected = current == RoomMarker.DIRECTION,
                onClick = { onChange(RoomMarker.DIRECTION) },
            )
            VastuChip(
                text = "Its box",
                selected = current == RoomMarker.BOX,
                onClick = { onChange(RoomMarker.BOX) },
            )
        }
    }
}
