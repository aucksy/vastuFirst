// DoorMark.kt — ONE entrance mark, drawn identically wherever a home is pictured.
//
// ⭐⭐ WHY THIS FILE EXISTS (owner, 18 Aug 2026): *"The entrance circle is not consistent across all
// possible screens. if its not auto-detected then it falls back to older circle and after selecting
// the position, going to next screen and then coming back, shows circle with E which seems just too
// big… keep the entrance door experience consistent and intuitive and clean"*.
//
// He was right on every count. Before this file the SAME front door was drawn four different ways,
// and one of the four was "not at all":
//
//   · "Where is your front door?"  — a 12 dp dot inside a 20 dp ring, no letter at all.
//   · "Check what we read"          — a 38–46 dp filled disc with a white collar and an "E".
//   · The report                    — NOTHING. The picture had no door parameter, so the heaviest
//                                     single input in the whole score was invisible on the one
//                                     screen the reader pays for.
//   · The hand-drawn editor         — a 20 dp GOLD badge lettered "D".
//
// So a reader who marked their door on the photo saw a speck, tapped on, and met a disc four times
// the size on the very next screen — then no mark whatever on the report, and a differently
// coloured, differently lettered one if they had drawn their home by hand. Every one of those was
// written where it was needed, and each was individually defensible; together they were four
// answers to one question. There is now one answer, and it lives here.
//
// ⚠⚠ AND THE OLD SIZING COULD CRASH THE SCREEN. It read
//     radius = (share of the plan).coerceIn(letterFloorFromTheFont, capFromTheTouchTarget)
// where the FLOOR grew with the font scale and the CAP did not. `coerceIn` throws when the minimum
// exceeds the maximum, and at a 200 % font the floor measured ~18.8 dp against a ~19.2 dp cap — four
// tenths of a dp from an IllegalArgumentException on "Check what we read", on the phones of exactly
// the readers this app is built for. Pinning the letter to the CIRCLE instead of to the font removes
// the floor entirely, so the two bounds are now constants and the range can never be empty.
package com.vastufirst.app.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.TextUnit
import com.vastufirst.designsystem.theme.VastuColors

/**
 * The letter inside the mark. "E" for entrance, everywhere.
 *
 * ⚠ The hand-drawn editor used to print "D" for door. Two letters for one thing is one letter too
 * many, and this is the one the app already explains out loud — the review screen's card says "we
 * found your main entrance and marked it E on your plan", and it said that while the editor was
 * drawing a D.
 */
const val DOOR_MARK_LETTER = "E"

/**
 * The mark's drawn size, as a share of the SMALLER side of the picture.
 *
 * A fixed size cannot work: the same disc that reads well on a plan drawn 320 dp tall covers a
 * third of one drawn 92 dp wide in landscape. So it is proportional between the two bounds below,
 * and pinned to them outside that band.
 */
const val PLAN_DOOR_MARK_SHARE = 0.072f

/**
 * The biggest the drawn mark may be, as a share of the 48 dp touch target it sits inside — so 12 dp
 * of radius, a 24 dp disc, about 28 dp including its collar.
 *
 * ⚠ CUT FROM 0.40 (a ~38 dp disc) ON 18 AUG 2026, and the cap is the number that does the work.
 * On any ordinary near-square sheet the proportional size runs past it, so what the reader sees is
 * this constant and not the share above — a lesson this project paid for once already, when cutting
 * only the share made the mark five per cent smaller and looked unchanged.
 *
 * ⚠ The DRAWN mark and the TOUCH target are two different sizes on purpose, and always were. The
 * target stays the full 48 dp accessibility floor; this is only what the eye sees.
 */
const val PLAN_DOOR_MARK_MAX_OF_TOUCH = 0.25f

/**
 * The smallest the drawn mark may be, as a share of the same touch target — 8 dp of radius, a 16 dp
 * disc. The old front-door screen drew 12 dp across and the owner could not find it on his own plan
 * at arm's length, so this floor is set above that and never goes lower.
 */
const val PLAN_DOOR_MARK_MIN_OF_TOUCH = 0.16f

/**
 * How big to draw the mark on a picture [planW] × [planH] px, given the touch floor in px.
 *
 * Pure and separate from the drawing so the bounds can be tested without rendering anything — in
 * particular that the range is never empty, whatever the font scale, which is the failure described
 * at the top of this file.
 */
fun doorMarkRadiusPx(planW: Float, planH: Float, minTouchPx: Float): Float {
    val low = minTouchPx * PLAN_DOOR_MARK_MIN_OF_TOUCH
    val high = minTouchPx * PLAN_DOOR_MARK_MAX_OF_TOUCH
    return (minOf(planW, planH) * PLAN_DOOR_MARK_SHARE).coerceIn(low, high)
}

/**
 * ⭐⭐ THE ENTRANCE MARK. Collar, disc, letter — and a ring when the reader has it selected.
 *
 * ⚠ THE LETTER IS PINNED TO THE CIRCLE, NOT TO THE FONT, and that is the same rule the editor's own
 * badge has followed since the v0.2.1 audit. A map symbol whose meaning is spoken in full by the
 * picture's `contentDescription` does not need to grow with the reader's text size; letting it grow
 * either shears the glyph out of the disc or — as it did here — drags the disc's own floor above its
 * ceiling and throws. Sized from the radius, the "E" fits by construction at every font scale.
 *
 * ⚠ THE COLLAR MUST BE WIDER THAN THE DISC or it is drawn and then painted over completely, which
 * is exactly nothing. It is what lifts the mark off a busy builder's sheet.
 */
fun DrawScope.drawDoorMark(
    center: Offset,
    radius: Float,
    selected: Boolean,
    colors: VastuColors,
    strokePx: Float,
    measurer: TextMeasurer,
    letterStyle: TextStyle,
) {
    // ⚠ THE LETTER IS `onPrimary`, NOT `paper`, AND THAT IS A LEGIBILITY FIX (18 Aug 2026). A near
    // white "E" on the sage disc measures about 2.7 : 1 — under every text floor there is — while the
    // palette's own partner for this fill measures about 5 : 1. The hand-drawn editor's badge had
    // been using the right one all along; the photograph screens had not. No gate could report it:
    // this letter is painted onto a canvas, and the contrast checker reads the semantics tree.
    val glyph = measurer.measure(
        DOOR_MARK_LETTER,
        letterStyle.copy(
            color = colors.onPrimary,
            fontSize = radius.toDp().toSp(),
            // Unspecified, so the line box is the font's own and not 1.4× it — a tall line box
            // centres the letter by its padding rather than by its ink.
            lineHeight = TextUnit.Unspecified,
        ),
    )
    drawCircle(color = colors.paper, radius = radius + strokePx / 2f, center = center)
    drawCircle(
        color = colors.primary.copy(alpha = if (selected) 1f else 0.92f),
        radius = radius,
        center = center,
    )
    drawText(
        glyph,
        topLeft = Offset(center.x - glyph.size.width / 2f, center.y - glyph.size.height / 2f),
    )
    if (selected) {
        drawCircle(
            color = colors.primary,
            radius = radius + strokePx,
            center = center,
            style = Stroke(width = strokePx / 2f),
        )
    }
}
