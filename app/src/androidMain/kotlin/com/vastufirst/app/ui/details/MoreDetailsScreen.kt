package com.vastufirst.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.vastufirst.app.ui.common.screenRoot
import com.vastufirst.app.ui.common.short
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonInline
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.VastuChip
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Zone

/**
 * "A few more things" — the optional step that lets the rest of the engine's rules run
 * (docs/SCORE-ACCURACY-CAVEATS.md #4).
 *
 * ⚠ DELIBERATELY OPTIONAL, and reached from the score rather than forced before it. The free score
 * is meant to be quick; putting four more questions in front of it would cost every user time to
 * catch the minority who have something to report. Instead the score screen SAYS what it did and did
 * not look at, and this is the way to close the gap for anyone who wants to.
 *
 * Every answer is a direction, because a direction is exactly what the rules ask for — see
 * SiteDetails.kt for why that is the whole design rather than a simplification.
 */
@Composable
fun MoreDetailsScreen(vm: NewPlanViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    MoreDetailsContent(
        answers = vm.siteAnswers,
        onAnswer = vm::setSiteAnswer,
        onDecline = vm::declineSiteItem,
        onDone = onDone,
        onBack = onBack,
    )
}

@Composable
fun MoreDetailsContent(
    answers: SiteAnswers,
    onAnswer: (SiteItem, Zone) -> Unit,
    onDecline: (SiteItem) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = VastuTheme.colors
    Column(
        modifier = Modifier.screenRoot(colors.paper).verticalScroll(rememberScrollState()).padding(VastuTheme.spacing.s6),
    ) {
        SectionLabel("Optional")
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText("A few more things", style = VastuTheme.type.h2, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            "Your score so far comes from your rooms, your front door and your home's shape. " +
                "Answer any of these and we can check more. Skip anything you don't know — " +
                "we'll say it wasn't checked rather than guess.",
            style = VastuTheme.type.body, color = colors.textSecondary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s6))

        SiteItem.entries.forEach { item ->
            SiteQuestion(
                item = item,
                chosen = answers.answers[item],
                declined = item in answers.declined,
                onAnswer = { onAnswer(item, it) },
                onDecline = { onDecline(item) },
            )
            Spacer(Modifier.height(VastuTheme.spacing.s4))
        }

        Spacer(Modifier.height(VastuTheme.spacing.s2))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            VastuButtonInline("Back", onClick = onBack, style = VastuButtonStyle.SECONDARY)
            VastuButton("Save and see my score", onClick = onDone, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(VastuTheme.spacing.s4))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SiteQuestion(
    item: SiteItem,
    chosen: Zone?,
    declined: Boolean,
    onAnswer: (Zone) -> Unit,
    onDecline: () -> Unit,
) {
    val colors = VastuTheme.colors
    QuestionCard(answered = chosen != null || declined) {
        VText(item.question, style = VastuTheme.type.h3, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s1))
        VText(item.help, style = VastuTheme.type.bodySm, color = colors.textSecondary)
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VText(
            when {
                chosen != null -> "It's in the ${chosen.short().lowercase()}."
                declined -> "There isn't one."
                else -> "Which direction is it in?"
            },
            style = VastuTheme.type.bodySm, color = colors.textTertiary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        // FlowRow: eight direction chips plus "there isn't one" cannot fit one line on a 320 dp phone
        // at any font size, and must wrap whole chips rather than break a direction name in half.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
            verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
        ) {
            ANSWERABLE_ZONES.forEach { zone ->
                VastuChip(
                    text = zone.short(),
                    selected = chosen == zone,
                    onClick = { onAnswer(zone) },
                )
            }
            VastuChip(
                text = "There isn't one",
                selected = declined,
                onClick = onDecline,
            )
        }
    }
}

/**
 * ⚠ NOT VastuCard, and the reason is worth writing down because it will catch somebody again.
 *
 * `VastuCard` measures itself at `IntrinsicSize.Min` height so its accent stripe can fill the card.
 * A `FlowRow` cannot be measured that way — asked for an intrinsic height it wraps against a width
 * it does not really have, and the items that fall off the end come out **zero by zero**. The
 * geometry gate caught it before it shipped: at 320 dp, "North-West" and "There isn't one" measured
 * 0 × 0 dp, i.e. two answers the user could see nowhere and tap nowhere.
 *
 * It is specific to a row of NINE chips; the two-item FlowRow inside the score screen's defect cards
 * is unaffected and stays as it is. Here the card is a plain Column, so the flow row is measured
 * once, normally, against a real width — and "answered" is carried by a stronger border rather than
 * a stripe that needed the intrinsic height in the first place.
 */
@Composable
private fun QuestionCard(answered: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val colors = VastuTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.md)
            .background(colors.surface)
            .border(
                if (answered) VastuTheme.borders.focus else VastuTheme.borders.regular,
                if (answered) colors.primary else colors.borderDefault,
                VastuTheme.shapes.md,
            )
            .padding(VastuTheme.spacing.s4),
        content = content,
    )
}

/** The one-line summary of what has and hasn't been answered, for the score screen. */
fun coverageLine(answers: SiteAnswers): String {
    val total = SiteItem.entries.size
    val done = answers.answeredCount
    return when (done) {
        0 -> "Your rooms, your front door and your home's shape. We haven't looked at your water tank, trees or the road outside."
        total -> "Your rooms, your front door, your home's shape — and the $total extra things you told us about."
        else -> "Your rooms, your front door, your home's shape, and $done of $total extra things you told us about."
    }
}
