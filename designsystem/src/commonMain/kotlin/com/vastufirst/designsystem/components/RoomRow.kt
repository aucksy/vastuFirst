package com.vastufirst.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * ⭐ THE ONE-WORD RESULT (owner, 10 Aug 2026, with a picture).
 *
 * The report's four verdicts — Ideal, Fine, Not ideal, Defect — are the engine's vocabulary, and the
 * owner asked the ROW to carry one word, not four: *"the list rooms will also show the 1 word result
 * of the room along with its direction"*. So the row says **Aligned** or **Review**, and the precise
 * verdict is inside, where the reasoning is.
 *
 * ⚠ THE COLLAPSE IS ON THE LABEL ONLY, NEVER ON THE FINDING. "Not ideal" and "Defect" both read
 * "Review" on the row, and both still open onto their own verdict, reason, provenance and remedies,
 * exactly as before. A room's finding is never removed, softened, or merged with another's — that is
 * the standing rule for every scoring and reporting change (Product PRD §4.5.2b).
 *
 * ⚠ [NOT_RATED] is a THIRD word and it has to exist, however much two is tidier. About one room in
 * eight comes back as something the tradition does not place — the engine scores it `NOT_SCORED` and
 * the report says "the tradition doesn't mention it". Calling that "Aligned" would be an approval we
 * never gave, and calling it "Review" would invent a problem. It gets its own grey word.
 */
/**
 * ⭐⭐ FOUR WORDS, AND THEY ARE THE METER'S OWN WORDS. There used to be three, and the middle one
 * did two jobs at once: a merely middling room and a genuine defect were BOTH stamped "Review" in
 * the defect red. Two things went wrong on screen because of it.
 *
 * - The meter at the top of the report counts rooms as *already right · not ideal · need fixing*,
 *   and then the list underneath used none of those three words. A reader could not match the
 *   numbers to the rows.
 * - A home with **no defects at all** was made to look as though it had one. On the app's own good
 *   sample the page reads "Reads well throughout — nothing the tradition counts as a defect" and
 *   the "need fixing" count is zero, while the one middling kitchen right below it carried a red
 *   warning pill. Amber never appeared on a room row anywhere in the product.
 *
 * So the middling verdict gets its own word and its own amber, and every word here is exactly the
 * word the meter above uses. No finding is removed or softened — a defect is still red and still
 * says so; it is the room that is NOT a defect that stops being dressed as one.
 */
enum class VastuRoomStatus { ALIGNED, NOT_IDEAL, NEEDS_FIXING, NOT_RATED }

@Composable
fun VastuRoomStatus.color(): Color = when (this) {
    VastuRoomStatus.ALIGNED -> VastuTheme.colors.verdictIdeal
    VastuRoomStatus.NOT_IDEAL -> VastuTheme.colors.verdictSuboptimal
    VastuRoomStatus.NEEDS_FIXING -> VastuTheme.colors.verdictDefect
    VastuRoomStatus.NOT_RATED -> VastuTheme.colors.verdictNotAssessed
}

private fun VastuRoomStatus.label(): String = when (this) {
    VastuRoomStatus.ALIGNED -> "Already right"
    VastuRoomStatus.NOT_IDEAL -> "Not ideal"
    VastuRoomStatus.NEEDS_FIXING -> "Needs fixing"
    VastuRoomStatus.NOT_RATED -> "Not rated"
}

/**
 * ⚠ Drawn from the glyphs this app has already PHOTOGRAPHED rendering. The obvious characters for
 * these three (a circled slash, a filled triangle) are not in the bundled DM Sans, and a missing
 * glyph draws as an empty box in the goldens — which is a thing only the picture can tell you.
 * These three are already on screen elsewhere in the report and are known to draw.
 */
private fun VastuRoomStatus.glyph(): String = when (this) {
    VastuRoomStatus.ALIGNED -> "✓"
    // ⚠ Both middling and defect keep the triangle. It is one of the three characters this project
    // has PHOTOGRAPHED rendering in the bundled DM Sans; reaching for a crossed circle or a filled
    // triangle to separate them would draw an empty box on a real phone, which is a thing only the
    // picture can tell you. The colour and the word carry the difference, and the design contract
    // requires exactly that — never colour alone.
    VastuRoomStatus.NOT_IDEAL -> "△"
    VastuRoomStatus.NEEDS_FIXING -> "△"
    VastuRoomStatus.NOT_RATED -> "–"
}

/** The status word as a pill — colour PLUS word PLUS glyph, so colour is never the only carrier. */
@Composable
fun RoomStatusPill(status: VastuRoomStatus, modifier: Modifier = Modifier) =
    TagPill(text = status.label(), color = status.color(), glyph = status.glyph(), modifier = modifier)

/**
 * ⭐⭐ ONE ROW, ONE TAP, AND THE SAME BEHAVIOUR FROM BOTH ENDS (owner, 10 Aug 2026).
 *
 * The owner asked for a shared behaviour: *"Build a common UI/UX for this room highlight in the list
 * which works the same way if user taps the room in list or room on floor plan"*. This row is the
 * list half of it. Tapping it does BOTH things at once — it marks the room on the plan above and it
 * opens the room's reasoning — so there is exactly one target, one outcome, and nothing to learn.
 * Tapping the same room on the plan calls the identical handler, so the two ends cannot drift apart.
 *
 * ⚠ ONE tap target, deliberately, not two. A separate chevron control would be a second ≥48 dp
 * target inside a row that is already carrying a circle, a name, two pills and an arrow — at 320 dp
 * and 200 % font that is the row-shatter defect (UI-POLISH §3.D) built in on purpose. The arrow is
 * an affordance, not a button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VastuRoomRow(
    name: String,
    /** The room's short code — "K", "MB", "T" — inside its tinted circle. */
    code: String,
    codeColor: Color,
    modifier: Modifier = Modifier,
    /**
     * "South-East", "centre" — the direction SPELLED OUT, and omitted entirely before North is
     * known rather than shown blank or guessed.
     *
     * ⚠ Not the "SE" of the owner's sketch, and the reason is already written into this codebase:
     * the front-door marker announced "on the N wall" and that was logged as a defect, because a
     * bare letter is the one thing an app made entirely of directions must never make its reader
     * decode. Everything else here spells them out; this matches.
     */
    direction: String? = null,
    /** Omitted before North is known: there is no verdict to give yet, and a blank pill implies one. */
    status: VastuRoomStatus? = null,
    /** The plan's own printed size, on the screen whose job is checking what we read. */
    note: String? = null,
    selected: Boolean = false,
    expanded: Boolean = false,
    onTap: (() -> Unit)? = null,
    body: (@Composable () -> Unit)? = null,
) {
    val colors = VastuTheme.colors
    // ⚠ The open row is marked in a NEUTRAL colour, never the room's own. Tinting it by room type
    // put an orange wash behind an open kitchen and a red one behind an open toilet — sitting
    // directly beside a red "Review" pill, which reads as alarm when all it means is "this is the
    // one you are looking at". Seen in the first render. Selection is state, not a verdict.
    val accent = if (selected) colors.primary else colors.borderDefault

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.md)
            .background(colors.surfaceRaised)
            .border(
                if (selected) VastuTheme.borders.focus else VastuTheme.borders.regular,
                accent,
                VastuTheme.shapes.md,
            )
            .then(
                if (onTap == null) Modifier else Modifier.clickableTap(
                    role = Role.Button,
                    onClickLabel = if (expanded) "close this room" else "show this room on the plan and read why",
                    onClick = onTap,
                )
            )
            .padding(VastuTheme.spacing.s4),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // ⚠ THE CIRCLE CARRIES THE COLOUR; THE LETTER CARRIES THE MEANING, so the letter is
            // ordinary dark ink and not the room's own colour. Drawn in its own colour it was a pale
            // tint on a paler tint of itself, and Google's accessibility checker failed it for
            // contrast on five rooms at once — a two-letter code nobody can read is the whole of what
            // that circle is for. The tint still tells kitchens from bedrooms at a glance, and it was
            // never the only thing doing so.
            Box(
                Modifier
                    .size(VastuTheme.sizes.tileSm)
                    .clip(CircleShape)
                    .background(codeColor.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                VText(code, style = VastuTheme.type.caption, color = colors.textPrimary)
            }
            Spacer(Modifier.width(VastuTheme.spacing.s3))
            Column(Modifier.weight(1f)) {
                VText(name, style = VastuTheme.type.h3, color = colors.textPrimary, maxLines = 2)
                if (direction != null || status != null || note != null) {
                    // ⭐⭐ THE PILLS SIT UNDER THE NAME, NOT BESIDE IT — and this is not a style choice.
                    //
                    // ⚠ FOUND BY LOOKING, 10 Aug 2026, on the first render of this row. The owner's
                    // sketch puts the result pill on the right of the row, and built that way it
                    // measures FIRST and takes the width it wants, leaving the weighted name column
                    // whatever is left. At 200 % font that broke a room's name across two lines in
                    // the middle of the word — "Bedroo / m" — and at 320 dp it broke a direction the
                    // same way, "South-Wes / t". Every geometry number was green for both: the boxes
                    // are the size they claim to be, it is the words inside them that are wrong,
                    // which is exactly the class UI-POLISH §6.7b says only a picture catches.
                    //
                    // In a FlowRow under the name, the name has the full column and each pill either
                    // fits beside its neighbour or takes its own line whole. Nothing competes for the
                    // name's width, so no word can be cut in half at any size in the matrix.
                    //
                    // The cost is the sketch's right-hand column of results, which was genuinely
                    // easier to run an eye down. Correct at every size wins over tidy at one.
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
                        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1),
                    ) {
                        if (status != null) RoomStatusPill(status)
                        if (direction != null) {
                            TagPill(text = direction, color = colors.textSecondary)
                        }
                        if (note != null) {
                            VText(note, style = VastuTheme.type.bodySm, color = colors.textSecondary, maxLines = 2)
                        }
                    }
                }
            }
            if (body != null) {
                Spacer(Modifier.width(VastuTheme.spacing.s2))
                VText(
                    if (expanded) "⌃" else "⌄",
                    style = VastuTheme.type.body,
                    color = colors.textTertiary,
                )
            }
        }
        if (expanded && body != null) {
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            Box(Modifier.fillMaxWidth().height(VastuTheme.borders.regular).background(colors.borderDefault))
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            body()
        }
    }
}
