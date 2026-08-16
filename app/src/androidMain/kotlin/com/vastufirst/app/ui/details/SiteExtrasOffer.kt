// SiteExtrasOffer.kt — the one offer to answer the questions a plan cannot answer.
//
// ⭐⭐ WHY THIS EXISTS (owner, 18 Aug 2026): *"the question about 4 more questions seems after
// thought now. no one will ever choose it. optimize it"*.
//
// He is right, and the reason is in what it used to be: one grey sentence in the smallest type on
// the page, followed by a small outline pill reading "Check 4 more things", sitting last on a
// screen whose big green button is the way out. Nothing on it said what answering would GET the
// reader, nothing gave it any weight, and it was the very last thing on the page. It looked like a
// footnote because it was built like one.
//
// What it says now, in the order a person actually asks:
//   · how much is at stake  — "4 more things could change your score", counted, in a heading;
//   · why we are asking     — a plan is a drawing; it cannot show a tank, a tree or a road;
//   · what it costs         — one tap each;
//   · the way in            — a full-width button, level with every other button on the page.
//
// ⚠ ONE COMPONENT, TWO SCREENS. The offer is made at the end of "Check what we read" and again on
// the report for anyone who walked past it there (and for every hand-drawn home, which never sees
// that screen at all). Those were two separate lumps of copy, and they had already drifted — one a
// sentence plus a hugging pill, the other a sentence plus a full-width button. One component means
// improving the offer improves it in both places, which is the whole point.
package com.vastufirst.app.ui.details

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.VastuCard
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * How many of the optional questions are still open. Zero means the offer has nothing left to sell
 * and must not be drawn at all — an invitation into a screen with nothing on it is worse than no
 * invitation.
 */
fun siteItemsLeft(answers: SiteAnswers): Int = SiteItem.entries.size - answers.answeredCount

/**
 * The heading — a count and a consequence, which is the whole of why anyone would tap.
 *
 * ⚠ "could change your score" is a claim, and it is a true one: every item here feeds a rule the
 * engine already runs, and a home with its water tank in the worst corner scores exactly the same
 * as one with it in the best until somebody says where it is. Saying so is not salesmanship, it is
 * the same honesty the report's own coverage line carries.
 */
fun extrasHeadline(answers: SiteAnswers): String {
    val left = siteItemsLeft(answers)
    return if (left == 1) "1 more thing could change your score"
    else "$left more things could change your score"
}

/**
 * The reason, naming ONLY the items still open — so a reader who has already answered two is not
 * told we cannot see the two they just told us about.
 */
fun extrasReason(answers: SiteAnswers): String {
    val names = SiteItem.entries.filterNot { answers.isAnswered(it) }.map { it.inSentence }
    if (names.isEmpty()) return ""
    val listed = if (names.size == 1) names.first()
    else names.dropLast(1).joinToString(", ") + " or " + names.last()
    return if (names.size == 1) {
        "Your plan cannot show $listed. Tell us where it is and we will read it too — one tap."
    } else {
        "Your plan cannot show $listed. Tell us where they are and we will read them too — one tap each."
    }
}

/**
 * ⭐ THE ONE LABEL FOR THE OPTIONAL EXTRAS — written once, shown in two places.
 *
 * ⚠ IT HAS BEEN REWRITTEN TWICE, and both times for the same reason. "Answer a few more and check
 * more" asked for something without saying what of. "Check 4 more things" counted honestly but read
 * as a chore — a fourth item on a list, below the button that ends the screen. This one finishes
 * the sentence the card's own body starts ("tell us where they are"), so the reader is not asked to
 * work out what the button does from the button alone.
 *
 * ⚠ AND THE RULE DATA MUST NEVER QUOTE IT. Three rules once printed the old words verbatim, so
 * renaming this turned their instruction into a hunt for a control that is not on screen. The rule
 * sentences describe the offer instead of naming a control, and they must stay that way.
 */
fun addDetailsLabel(answers: SiteAnswers): String {
    val left = siteItemsLeft(answers)
    return if (left == 1) "Tell us about the last one" else "Tell us about these $left"
}

/**
 * The offer itself. Draws nothing once every question has been answered.
 *
 * ⚠ A CARD, NOT A LOOSE SENTENCE. On both screens this sits below a full-width primary button, and
 * anything below one of those is competing with the way out of the page. A bordered card with its
 * own heading is the cheapest way to say "this is a thing, not a leftover"; the accent stripe is
 * the same one the app uses everywhere it wants an eye to stop.
 */
@Composable
fun SiteExtrasOffer(
    answers: SiteAnswers,
    onAddDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (siteItemsLeft(answers) <= 0) return
    val colors = VastuTheme.colors
    VastuCard(modifier = modifier, accent = colors.primary) {
        VText(extrasHeadline(answers), style = VastuTheme.type.h3, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(extrasReason(answers), style = VastuTheme.type.bodySm, color = colors.textSecondary)
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        // Full width inside the card, level with every other button in the column — see the rule on
        // VastuButtonStyle. `large = false` because this is an offer, not the page's own call to
        // action, and the height is what carries that difference rather than the width.
        VastuButton(
            addDetailsLabel(answers),
            onClick = onAddDetails,
            style = VastuButtonStyle.SECONDARY,
            large = false,
        )
    }
}
