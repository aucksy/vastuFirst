// RoomMarker.kt — what the user's own plan photograph shows about each room: the box the reader
// drew, or the room's DIRECTION. The direction is a trial, built beside the box, switched by data.
//
// ⭐⭐ WHY THIS FILE EXISTS (owner, 29 Sep 2026): *"On 'Check what we read', tapping a room draws a box
// over where the reader thinks the room is. Those boxes have never been accurate, because every floor
// plan is drawn differently. But Vastu only needs each room's DIRECTION."*
//
// ⭐⭐ AND WHAT IT SHOWS, IN HIS WORDS (same day, after the first build): *"the directions will just show
// on the floor plan... by default, you're showing SW or N or E on the floor plan on that screen. But
// when you tap on the actual room in the list [or on the floor plan], then it just converts to
// southwest or north or east... don't generate a new image. Just use the actual floor plan just like we
// were doing before."* So: every room carries its short direction ON THE USER'S OWN PHOTOGRAPH, all the
// time; the tapped room's lights up and spells itself out. Nothing is redrawn — the photo is the
// picture of record, exactly as it has been since 4 Aug 2026.
//
// ⚠ IT IS A TRIAL, AND THE BOX WAY IS STILL HERE, WHOLE. Nothing that draws, taps or tests the box was
// deleted or rewritten. `directionTrial` in `scan/reader-config.json` picks the default; with it off
// the app is byte-for-byte the box way and offers no chooser at all. With it on, a small chooser under
// the plan flips between the two on the SAME plan, so they can be compared without reinstalling.
//
// ⚠⚠ THE WORDS ON THE ROOMS ARE NEVER WORKED OUT HERE. They are the engine's own, handed in by the
// screen from the same result its rows print — see `roomReadings` on "Check what we read" and the
// report's room list. Deriving a direction from where a label sits on the photograph would be a second
// calculation, and one room called two things two inches apart is exactly the defect the rows were
// built to prevent.
package com.vastufirst.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuChip
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Zone
import kotlin.math.roundToInt

/** What the plan shows about each room. */
enum class RoomMarker {
    /** The rectangle the reader drew, for a tapped room — everything before 29 Sep 2026. */
    BOX,

    /** Every room's direction, in the engine's own words, on the room — the trial. */
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

/**
 * A room's direction as the plan shows it: the engine's zone, and the words its row prints.
 * [code] — "SW", "N", "C" — is what every room carries; [words] — "South-West" — is what the tapped
 * room spells out. Both come from ONE engine zone, so the short and the long can never disagree.
 */
data class RoomDirection(val zone: Zone, val words: String) {
    val code: String get() = zone.code()
}

/** The test tag on a room's short direction on the plan ("SW"). One per untapped room. */
const val ROOM_CODE_TAG = "plan.room.code"

/** The test tag on the tapped room's direction, spelled out ("South-West"). */
const val ROOM_DIRECTION_TAG = "plan.room.direction"

/** The test tag on the trial's chooser under the plan. */
const val ROOM_MARKER_CHOICE_TAG = "plan.marker.choice"

/** Where a room is taken to BE, as one point: the middle of the rectangle it was read from. */
internal fun PlanRoom.centreOrNull(): Pair<Float, Float>? =
    box?.let { (it.x + it.w / 2.0).toFloat() to (it.y + it.h / 2.0).toFloat() }

/**
 * ⭐ WHICH ROOM A TAP MEANT, WHEN WHAT IS DRAWN IS A LABEL PER ROOM rather than a box per room.
 *
 * With the box way the finger aims at an outline it can see, so "the smallest box that contains the
 * tap" ([roomAtPoint]) is right. With the direction way no box is drawn — the finger aims at a room's
 * label, or at its name printed on the photograph — and smallest-first then picks wrongly exactly where
 * the reader's boxes are worst: a toilet's box spilling over the middle of the living room would take
 * the tap aimed at the LIVING room's own label. So here the nearest room middle wins among the rooms
 * whose box contains the tap; a tap outside every box takes the nearest middle within [maxPx].
 * Distances are measured in drawn pixels ([pxPerX], [pxPerY]), so a tall sheet and a wide one behave
 * the same.
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
 * Where a room's direction label's top-left corner goes, in the picture box's pixels: centred over the
 * room's middle and just ABOVE it; below it when there is no room above; and always wholly inside the
 * box, so the words are never cut off by the picture's own edge.
 *
 * ⚠ ABOVE the middle, never on it. The middle of a room's box is, on most sheets, exactly where the
 * plan PRINTS the room's name (the reader finds rooms by their captions); a label laid on the middle
 * hides the very name this screen asks the reader to check. The first two renders proved it: dots and
 * rings there turned "LIVING ROOM" into "ING ROOM" and "G ROOM".
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

/** The colour a zone wears everywhere else in the app — the zone map's own. */
@Composable
internal fun Zone.planColor(): Color = with(VastuTheme.colors) {
    when (this@planColor) {
        Zone.N -> zoneN; Zone.NE -> zoneNE; Zone.E -> zoneE; Zone.SE -> zoneSE
        Zone.S -> zoneS; Zone.SW -> zoneSW; Zone.W -> zoneW; Zone.NW -> zoneNW
        Zone.BRAHMASTHAN -> zoneCentre
    }
}

/** One room's label on the plan: where its middle is drawn, in the picture box's pixels, and its direction. */
internal class PlanLabel(val id: String, val direction: RoomDirection, val at: Offset)

/**
 * ⭐⭐ EVERY ROOM'S DIRECTION, ON THE USER'S OWN PLAN — the trial itself.
 *
 * Each room carries its short direction — "SW", "N", "C" — on a small paper pill edged in its zone's own
 * colour (the zone map's palette, so the plan and the map speak the same colours). The TAPPED room's
 * label lights up: it spells the direction out in the very words its row prints ("South-West"), in ink
 * with paper letters, and glows in its zone's colour. Drawn last, so it is never under a neighbour's.
 *
 * Small type — the row pill's — because this sits on somebody's photograph: at a 200 % font a heading's
 * size covered the neighbouring room's printed name.
 *
 * ⚠ NO POINTER INPUT, on purpose. The labels lie over the picture, and the picture's one gesture
 * reader must still receive every touch — a tap on a room's label is a tap on that room.
 */
@Composable
internal fun RoomDirectionLabels(labels: List<PlanLabel>, selectedId: String?, boxW: Float, boxH: Float, gapPx: Float) {
    val colors = VastuTheme.colors
    val borders = VastuTheme.borders
    val shapes = VastuTheme.shapes
    val glowDp = VastuTheme.spacing.s1
    // The tapped room LAST, so it is drawn over any neighbour its longer words reach.
    val ordered = labels.sortedBy { it.id == selectedId }
    Layout(
        content = {
            ordered.forEach { label ->
                val tapped = label.id == selectedId
                val zoneColor = label.direction.zone.planColor()
                VText(
                    text = if (tapped) label.direction.words else label.direction.code,
                    style = VastuTheme.type.caption,
                    color = if (tapped) colors.paper else colors.textPrimary,
                    modifier = Modifier
                        .testTag(if (tapped) ROOM_DIRECTION_TAG else ROOM_CODE_TAG)
                        .then(
                            if (tapped) {
                                // The glow: a soft wash of the zone's colour just outside the pill.
                                Modifier.drawBehind {
                                    val g = glowDp.toPx()
                                    drawRoundRect(
                                        color = zoneColor.copy(alpha = 0.35f),
                                        topLeft = Offset(-g, -g),
                                        size = Size(size.width + 2 * g, size.height + 2 * g),
                                        cornerRadius = CornerRadius((size.height + 2 * g) / 2f),
                                    )
                                }
                            } else {
                                Modifier
                            },
                        )
                        .clip(shapes.full)
                        .background(if (tapped) colors.textPrimary else colors.paper.copy(alpha = 0.9f))
                        .border(if (tapped) borders.focus else borders.strong, zoneColor, shapes.full)
                        // ⚠ The same side padding for both, and twice the top and bottom: with less at
                        // the sides a one-letter direction ("C", "S") came out as a tall oval in the
                        // first render. This way one letter sits in a circle and two in a pill.
                        .padding(horizontal = VastuTheme.spacing.s2, vertical = VastuTheme.spacing.s1),
                )
            }
        },
    ) { measurables, constraints ->
        // Never wider than the picture: at a 200 % font on a 320 dp phone a long direction wraps before
        // it would run off the side.
        val loose = Constraints(maxWidth = constraints.maxWidth.coerceAtLeast(0))
        val placeables = measurables.map { it.measure(loose) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p ->
                val c = ordered[i].at
                val at = directionLabelTopLeft(c.x, c.y, p.width, p.height, boxW, boxH, gapPx)
                p.place(at.x, at.y)
            }
        }
    }
}

/**
 * ⭐ THE TRIAL'S CHOOSER — the directions or the box, on the same plan, without reinstalling (owner:
 * *"If it is cheap to do, let me compare both views on the same plan without reinstalling."*).
 *
 * It says "Trial" in its own words because it is one: a reader handed this build should know the
 * choice is being tried, not wonder why a report has a setting in it. Shown only while the switch in
 * the data is on — with it off, nothing here is drawn anywhere.
 *
 * ⚠ Two chips in a FlowRow, not a segmented bar. A segment gets half the row and a long word at a
 * 200 % font is wider than half of a 320 dp phone; a chip hugs its words and a FlowRow puts the second
 * one on its own line when the first does not leave room — see the note on `VastuSegmented`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomMarkerChooser(current: RoomMarker, onChange: (RoomMarker) -> Unit, modifier: Modifier = Modifier) {
    val colors = VastuTheme.colors
    Column(modifier.testTag(ROOM_MARKER_CHOICE_TAG)) {
        VText("Trial — your plan shows", style = VastuTheme.type.caption, color = colors.textSecondary)
        Spacer(Modifier.height(VastuTheme.spacing.s1))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
            verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
        ) {
            VastuChip(
                text = "Directions",
                selected = current == RoomMarker.DIRECTION,
                onClick = { onChange(RoomMarker.DIRECTION) },
            )
            VastuChip(
                text = "Boxes",
                selected = current == RoomMarker.BOX,
                onClick = { onChange(RoomMarker.BOX) },
            )
        }
    }
}
