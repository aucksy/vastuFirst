// RoomMarker.kt — what the user's own plan photograph shows about each room: the box the reader
// drew, or the room's DIRECTION. The direction is a trial, built beside the box, switched by data.
//
// ⭐⭐ WHY THIS FILE EXISTS (owner, 29 Sep 2026): *"On 'Check what we read', tapping a room draws a box
// over where the reader thinks the room is. Those boxes have never been accurate, because every floor
// plan is drawn differently. But Vastu only needs each room's DIRECTION."*
//
// ⭐⭐ AND WHAT IT SHOWS, IN HIS WORDS — redrawn by him on 30 Sep 2026, after living with the first one:
// *"it should not show any short or long form direction until tapped on a room bellow in the list..
// and it should only show the short form on the floor plan and full direction name on the card below
// only .. and try to improve the placement of direction short form to be in center of the room and 20%
// smaller"*. So: at rest the plan carries NO direction; the tapped room — by its card, or on the plan
// itself, one selection under one rule — shows its SHORT direction ("SW") on the middle of the room, on
// THE USER'S OWN PHOTOGRAPH; the full name ("South-West") is printed only in that room's card. Nothing
// is redrawn — the photo is the picture of record, exactly as it has been since 4 Aug 2026.
//
// (The first design, 29 Sep 2026, put every room's short direction on the plan all the time and spelled
// the tapped one out on the plan. It is gone because he asked for this one, not because it broke.)
//
// ⚠ IT IS A TRIAL, AND THE BOX WAY IS STILL HERE, WHOLE. Nothing that draws, taps or tests the box was
// deleted or rewritten. `directionTrial` in `scan/reader-config.json` picks the default; with it off
// the PLAN is the box way exactly — box, tint, tap and screen-reader words — and no chooser is offered.
// With it on, a small chooser under the plan flips between the two on the SAME plan, so they can be
// compared without reinstalling.
//
// ⚠ What the switch does NOT turn back: the words in the rooms' cards. A room crossing into a zone it
// may not occupy reads where most of it is ("North-West · crosses the centre") with the switch either
// way, because that was the owner's separate decision about the words (29 Sep 2026), made for every
// place a room's direction is printed — not a part of the trial. Found by the 30 Sep audit; the
// comparison of the box-way pictures before and after that day shows only those words moving.
//
// ⚠⚠ THE WORDS ON THE ROOMS ARE NEVER WORKED OUT HERE. They are the engine's own, handed in by the
// report from the same result its room list prints. Deriving a direction from where a label sits on the photograph would be a second
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuChip
import com.vastufirst.designsystem.components.pillShapeFor
import com.vastufirst.designsystem.components.wrappedLineHeight
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Zone
import kotlin.math.roundToInt

/** What the plan shows about each room. */
enum class RoomMarker {
    /** The rectangle the reader drew, for a tapped room — everything before 29 Sep 2026. */
    BOX,

    /** The tapped room's short direction, from the engine, on the middle of the room — the trial. */
    DIRECTION,
}

/**
 * ⭐ THE ONE PLACE THE TRIAL'S STATE LIVES — read by the report's photograph (the only picture that
 * shows it since "Check what we read" was removed on 30 Sep 2026).
 *
 * [offered] is the switch from the data: false = the box way on the plan, and no chooser. When it
 * is on, [current] starts on the direction (the trial is what he installs to feel) and the chooser can
 * flip it for comparison. Held for the life of the app and NOT saved: every launch starts on the trial
 * again, so a comparison made yesterday cannot quietly leave the trial switched off today.
 */
class RoomMarkerChoice(trialOn: Boolean) {
    val offered: Boolean = trialOn
    var current: RoomMarker by mutableStateOf(if (trialOn) RoomMarker.DIRECTION else RoomMarker.BOX)
}

/**
 * A room's direction as the plan shows it: the engine's zone, and the words its card prints.
 * [code] — "SW", "N", "C" — is what the plan shows for the tapped room; [words] — "South-West",
 * "North-West · crosses the centre" — are its card's, and what a screen reader hears. Both come from
 * ONE engine zone, so the short and the long can never disagree.
 */
data class RoomDirection(val zone: Zone, val words: String) {
    val code: String get() = zone.code()
}

/**
 * A room's direction as a screen reader should SAY it: its card's words, with the card's middle dot
 * said as words — "North-West, and crosses the centre" — rather than read out, or skipped, as a symbol.
 */
internal fun spokenDirection(words: String): String = words.replace(" · ", ", and ")

/**
 * The test tag on the one direction on the plan: the tapped room's short form ("SW"). At rest there is
 * none, and there is never more than one.
 */
const val ROOM_CODE_TAG = "plan.room.code"

/** The test tag on the trial's chooser under the plan. */
const val ROOM_MARKER_CHOICE_TAG = "plan.marker.choice"

/** Where a room is taken to BE, as one point: the middle of the rectangle it was read from. */
internal fun PlanRoom.centreOrNull(): Pair<Float, Float>? =
    box?.let { (it.x + it.w / 2.0).toFloat() to (it.y + it.h / 2.0).toFloat() }

/**
 * ⭐ WHICH ROOM A TAP MEANT, WHEN NO BOX IS DRAWN to aim at.
 *
 * With the box way the finger aims at an outline it can see, so "the smallest box that contains the
 * tap" ([roomAtPoint]) is right. With the direction way no box is drawn — the finger aims at the room's
 * name printed on the photograph, which is its middle, or at its label once tapped — and smallest-first
 * then picks wrongly exactly where the reader's boxes are worst: a toilet's box spilling over the middle
 * of the living room would take the tap aimed at the LIVING room. So here the nearest room middle wins among the rooms
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
 * Where the direction label's top-left corner goes, in the picture box's pixels: its CENTRE on the
 * room's middle ([pinX], [pinY]), and always wholly inside the picture with [insetPx] to spare — the
 * label's glow — so neither the letters nor the glow are ever cut off by the picture's own edge.
 *
 * ⭐ ON THE MIDDLE, by the owner's choice (30 Sep 2026: *"improve the placement of direction short form
 * to be in center of the room"*). Until then it sat just ABOVE the middle, and that was right while
 * every room carried a label: the middle is where most sheets PRINT the room's name, and eight labels
 * there hid eight names — the first render turned "LIVING ROOM" into "ING ROOM". Now only the tapped
 * room carries one and its card below names the room, so the middle is his call to make. Where a
 * centred label covers a printed name, the screenshots show it rather than an argument.
 *
 * Pure, so the edge cases — a room hard against a wall, a label wider than the picture — are pinned by a
 * plain test rather than found in a photograph.
 */
internal fun directionLabelTopLeft(
    pinX: Float,
    pinY: Float,
    labelW: Int,
    labelH: Int,
    boxW: Float,
    boxH: Float,
    insetPx: Float,
): IntOffset {
    // Never below the inset, even when the label is wider than the picture — a lower bound above the
    // upper one would throw, and a label that cannot fit should at least start inside the picture.
    val maxX = (boxW - labelW - insetPx).coerceAtLeast(insetPx)
    val maxY = (boxH - labelH - insetPx).coerceAtLeast(insetPx)
    return IntOffset(
        (pinX - labelW / 2f).coerceIn(insetPx, maxX).roundToInt(),
        (pinY - labelH / 2f).coerceIn(insetPx, maxY).roundToInt(),
    )
}

/**
 * ⭐ HOW MUCH SMALLER THE LABEL ON THE PLAN IS THAN THE ONE IT REPLACED (owner, 30 Sep 2026: *"20%
 * smaller"*). Its text, its padding and its glow are each their own theme token times this, so the
 * label still follows the theme and no raw size lives outside the theme package.
 */
internal const val PLAN_LABEL_SCALE = 0.8f

/** The colour a zone wears everywhere else in the app — the zone map's own. */
@Composable
internal fun Zone.planColor(): Color = with(VastuTheme.colors) {
    when (this@planColor) {
        Zone.N -> zoneN; Zone.NE -> zoneNE; Zone.E -> zoneE; Zone.SE -> zoneSE
        Zone.S -> zoneS; Zone.SW -> zoneSW; Zone.W -> zoneW; Zone.NW -> zoneNW
        Zone.BRAHMASTHAN -> zoneCentre
    }
}

/** The tapped room's label on the plan: where its middle is drawn, in the picture box's pixels, and its direction. */
internal class PlanLabel(val id: String, val direction: RoomDirection, val at: Offset)

/**
 * ⭐⭐ THE TAPPED ROOM'S DIRECTION, ON THE USER'S OWN PLAN — the trial itself, as the owner redrew it on
 * 30 Sep 2026.
 *
 * ONE label, for the tapped room only, and only its SHORT form — "SW", "N", "C". The full name is in the
 * room's own card below and nowhere on the plan. It sits on the middle of the room
 * ([directionLabelTopLeft]) and keeps the tapped look it had: a dark pill with paper letters, edged and
 * glowing in its zone's colour — the zone map's palette, so the plan and the map speak the same colours.
 *
 * ⚠ 20 % SMALLER THAN THE LABEL IT REPLACED — the text, its padding and the glow, each its own theme
 * token times [PLAN_LABEL_SCALE]. The border keeps the focus width: it carries the zone's colour, and a
 * thinner line stops reading as a colour at all.
 *
 * ⚠ A SCREEN READER HEARS THE WORDS, NOT THE LETTERS. "SW" read aloud is two letters to decode, in an
 * app made of directions — the one thing the room card was built never to do (see the card's own note).
 * So the label is described in its card's words, said as words; its drawn text stays the short form.
 *
 * ⚠ NO POINTER INPUT, on purpose. The label lies over the picture, and the picture's one gesture reader
 * must still receive every touch — a tap on the label is a tap on its room.
 */
@Composable
internal fun TappedRoomDirection(label: PlanLabel, boxW: Float, boxH: Float) {
    val colors = VastuTheme.colors
    val caption = VastuTheme.type.caption
    val style = caption.copy(
        fontSize = caption.fontSize * PLAN_LABEL_SCALE,
        lineHeight = caption.lineHeight * PLAN_LABEL_SCALE,
    )
    // ⚠ The same side padding as before and twice the top and bottom, both scaled alike: with less at
    // the sides a one-letter direction ("C", "S") came out as a tall oval in the first render. This way
    // one letter sits in a circle and two in a pill.
    val padH = VastuTheme.spacing.s2 * PLAN_LABEL_SCALE
    val padV = VastuTheme.spacing.s1 * PLAN_LABEL_SCALE
    val glow = VastuTheme.spacing.s1 * PLAN_LABEL_SCALE
    val glowPx = with(LocalDensity.current) { glow.toPx() }
    val zoneColor = label.direction.zone.planColor()
    // One line keeps its round ends; wrapped words get rounded corners (see pillShapeFor). Two letters
    // do not wrap on any phone in the matrix, but the pill is safe if a narrower one ever makes them.
    var wrappedLine by remember(label.id) { mutableStateOf<Float?>(null) }
    val shape = pillShapeFor(wrappedLine, padV)
    val spoken = spokenDirection(label.direction.words)
    Layout(
        content = {
            VText(
                text = label.direction.code,
                style = style,
                color = colors.paper,
                onTextLayout = { wrappedLine = wrappedLineHeight(it) },
                modifier = Modifier
                    .testTag(ROOM_CODE_TAG)
                    .semantics { contentDescription = spoken }
                    // The glow: a soft wash of the zone's colour just outside the pill, with the pill's
                    // own corners grown by the glow.
                    .drawBehind {
                        val g = glow.toPx()
                        val r = wrappedLine?.let { it / 2f + padV.toPx() } ?: (size.height / 2f)
                        drawRoundRect(
                            color = zoneColor.copy(alpha = 0.35f),
                            topLeft = Offset(-g, -g),
                            size = Size(size.width + 2 * g, size.height + 2 * g),
                            cornerRadius = CornerRadius(r + g),
                        )
                    }
                    .clip(shape)
                    .background(colors.textPrimary)
                    .border(VastuTheme.borders.focus, zoneColor, shape)
                    .padding(horizontal = padH, vertical = padV),
            )
        },
    ) { measurables, constraints ->
        // Never wider than the picture.
        val p = measurables.single().measure(Constraints(maxWidth = constraints.maxWidth.coerceAtLeast(0)))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val at = directionLabelTopLeft(label.at.x, label.at.y, p.width, p.height, boxW, boxH, glowPx)
            p.place(at.x, at.y)
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
