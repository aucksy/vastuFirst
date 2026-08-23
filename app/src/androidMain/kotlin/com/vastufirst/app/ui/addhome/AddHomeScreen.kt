package com.vastufirst.app.ui.addhome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.app.ui.common.screenRoot
import com.vastufirst.shared.PropertyType

/**
 * Add home — method choice (§6.2 · design system screen 2), and since 23 August 2026 the one
 * screen that asks whether this home is a HOUSE OR A FLAT.
 *
 * ⭐⭐ WHY THE QUESTION LIVES HERE, and not on the welcome screen where the other one does.
 *
 * "What brings you here?" is a fact about the PERSON — buying, building, or already living
 * somewhere — and it is asked once, on a fresh install. House-or-flat is a fact about the HOME. A
 * reader weighing a flat against a house would have to answer once and be wrong about one of them.
 *
 * This screen is the only one BOTH paths pass through before anything is read: the photograph path
 * and the drawing path both start on these three cards, and both end at a report. Anywhere later
 * and the compass dial or the room checklist would have to carry a question that has nothing to do
 * with what it is for.
 *
 * ⚠ IT STARTS ON "A HOUSE" DELIBERATELY. That is what every home in the product silently was until
 * this screen asked, so the default changes nothing for anybody — it only makes the assumption
 * visible and one tap to correct. A required choice would put a second tap in front of the sample
 * plan, whose whole promise is "the whole flow in ten seconds". The report carries a FLAT badge so
 * the answer is visible again at the other end.
 */
@Composable
fun AddHomeScreen(
    onDrawGrid: () -> Unit,
    onScan: () -> Unit,
    onSample: () -> Unit,
    propertyType: PropertyType = PropertyType.INDEPENDENT_HOUSE,
    onPropertyTypeChange: (PropertyType) -> Unit = {},
) {
    val colors = VastuTheme.colors
    Column(
        modifier = Modifier
            .screenRoot(colors.paper)
            .verticalScroll(rememberScrollState())
            .padding(VastuTheme.spacing.s6),
    ) {
        // ⛔ THE "STEP n OF 3" COUNTERS ARE GONE (11 Aug 2026) — do not put a number back unless it
        // can be a TRUE one. Three screens carried a counter and it was wrong on every path: this
        // screen and the upload screen BOTH said "Step 1 of 3", the compass said "Step 2 of 3", and
        // no screen anywhere said step 3. Counting what a person actually walks through, a scanned
        // home is five screens and a hand-drawn one is four — and the scanned total is not even
        // knowable here, because the front-door step only happens when the plan did not name its own
        // entrance. A progress counter that cannot be right is worse than none: it tells somebody
        // they are nearly finished and then hands them two more screens.
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VText("Add your home", style = VastuTheme.type.h2, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            // ⚠ REWRITTEN 23 Aug 2026, because inserting the house-or-flat question above the
            // method cards left the old line stranded: "Place your rooms on a simple grid — or try
            // a sample to see the whole flow first" described the three cards, and now sat two
            // sections above them, answering a question the reader had not been asked yet.
            //
            // It was also stale on its own terms. Upload leads these cards and has since the reader
            // shipped; a subtitle naming the grid first described the app as it was two releases
            // ago. The three cards each carry their own line, so nothing is lost by letting this one
            // say what the SCREEN does instead of what one card does.
            "Tell us what kind of home it is, then how you'd like to add it.",
            style = VastuTheme.type.body, color = colors.textSecondary,
        )

        // ⭐ ASKED BEFORE THE METHOD, not after, because it changes what the report is allowed to
        // tell them and they should see it before they invest any work in the home.
        Spacer(Modifier.height(VastuTheme.spacing.s6))
        SectionLabel("Is it a house or a flat?")
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            // ⚠ Says WHY, in one line. A question with no stated consequence gets tapped past, and
            // this one decides whether the reader is offered advice about a building they own a
            // floor of. It also has to be true of both answers, so it names the difference rather
            // than describing either one.
            "A flat cannot move the building's walls, tank or front door. We read those either way, "
                + "and only offer changes you could actually make.",
            style = VastuTheme.type.bodySm, color = colors.textSecondary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        Row(horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            PropertyChoice(
                label = "A house",
                subtitle = "The whole building is yours",
                isSelected = propertyType == PropertyType.INDEPENDENT_HOUSE,
                modifier = Modifier.weight(1f),
            ) { onPropertyTypeChange(PropertyType.INDEPENDENT_HOUSE) }
            PropertyChoice(
                label = "A flat",
                subtitle = "One home in a bigger building",
                isSelected = propertyType == PropertyType.FLAT,
                modifier = Modifier.weight(1f),
            ) { onPropertyTypeChange(PropertyType.FLAT) }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s6))
        SectionLabel("How would you like to add it?")
        Spacer(Modifier.height(VastuTheme.spacing.s3))

        Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            // Upload leads, because it is the shortcut. The subtitle was "we read the room names,
            // you place them" — written in the assisted-only era; since the §3q reader most scans
            // come back fully placed, so the honest constant across both outcomes is: we read, YOU
            // check, nothing is scored until you say it's right (the card below says the rest).
            MethodCard(
                icon = "⤒",
                title = "Upload a plan",
                subtitle = "Photo or PDF · we read it, you check every room",
                onClick = onScan,
            )
            MethodCard(
                icon = "▦",
                title = "Draw it on a grid",
                subtitle = "Place room blocks by hand · stays on your phone",
                onClick = onDrawGrid,
            )
            MethodCard(
                icon = "◉",
                title = "Try a sample plan",
                subtitle = "See the whole flow in ten seconds",
                onClick = onSample,
            )
        }

        Spacer(Modifier.height(VastuTheme.spacing.s6))
        Box(
            Modifier
                .clip(VastuTheme.shapes.md)
                .background(colors.surface)
                .padding(VastuTheme.spacing.s4),
        ) {
            VText(
                "You confirm every room yourself — nothing is scored until you say it's right.",
                style = VastuTheme.type.bodySm, color = colors.textTertiary,
            )
        }
    }
}

/**
 * One of the two house-or-flat answers.
 *
 * ⚠ TWO LINES, NOT A SEGMENTED PILL. A pill reading "House | Flat" fits, and at 200 % font scale on
 * a 320 dp screen it is two truncated words with no room for the subtitle that makes either of them
 * mean anything. This is a card, so it wraps instead of clipping — the failure this project has
 * photographed more than any other (docs/UI-POLISH.md).
 *
 * ⚠ `Role.RadioButton` and `selected` are both set, so a screen reader announces which one is
 * chosen. Colour alone would say it only to people who can see colour.
 */
@Composable
private fun PropertyChoice(
    label: String,
    subtitle: String,
    /**
     * ⚠ NOT NAMED `selected`, on purpose. `androidx.compose.ui.semantics.selected` is an extension
     * property on the semantics receiver, so inside the `semantics { }` lambda below a parameter of
     * that name and the property being assigned are the same bare word — `selected = selected`
     * either self-assigns an unset property or fails to resolve, depending on which scope wins.
     * Renaming the parameter removes the question rather than betting on the answer.
     */
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = VastuTheme.colors
    Column(
        modifier = modifier
            .clip(VastuTheme.shapes.md)
            .background(if (isSelected) colors.primary.copy(alpha = 0.10f) else colors.surfaceRaised)
            .border(
                if (isSelected) VastuTheme.borders.strong else VastuTheme.borders.regular,
                if (isSelected) colors.primary else colors.borderDefault,
                VastuTheme.shapes.md,
            )
            .clickableTap(role = Role.RadioButton, onClick = onClick)
            // ⚠ SEPARATE FROM THE ROLE, and it has to be: the app's shared tap modifier forwards
            // enabled, role and the click label and nothing else, so a RadioButton role on its own
            // announces "radio button" and never says which of the two is chosen. Colour alone
            // says it only to people who can see colour.
            .semantics { selected = isSelected }
            .padding(VastuTheme.spacing.s4),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1),
    ) {
        // ⚠ textPrimary WHETHER OR NOT IT IS CHOSEN, matching the welcome screen's intent cards —
        // which are the app's other two-of-a-kind choice and have always done it this way.
        //
        // Tinting the chosen label green cost one ATF error-level accessibility finding on this
        // screen, measured in CI: the app's green on its own 10 %-alpha wash is a low-contrast pair,
        // and it put the WORSE contrast on the option the reader had just picked. It was also
        // colour doing a job colour must not do alone. The fill and the heavier border already say
        // "chosen", and the control reports its state to a screen reader besides.
        VText(label, style = VastuTheme.type.h3, color = colors.textPrimary)
        VText(subtitle, style = VastuTheme.type.bodySm, color = colors.textSecondary)
    }
}

@Composable
private fun MethodCard(icon: String, title: String, subtitle: String, onClick: () -> Unit, soon: Boolean = false) {
    val colors = VastuTheme.colors
    Row(
        modifier = Modifier
            .clip(VastuTheme.shapes.md)
            .background(colors.surfaceRaised)
            .border(VastuTheme.borders.regular, colors.borderDefault, VastuTheme.shapes.md)
            .clickableTap(enabled = !soon, onClick = onClick)
            .padding(VastuTheme.spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s4),
    ) {
        Box(
            Modifier.size(VastuTheme.sizes.tile).clip(VastuTheme.shapes.sm).background(colors.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            VText(icon, style = VastuTheme.type.h3, color = colors.primary)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
                VText(title, style = VastuTheme.type.bodyLg, color = if (soon) colors.textTertiary else colors.textPrimary)
                if (soon) SoonTag()
            }
            VText(subtitle, style = VastuTheme.type.bodySm, color = colors.textTertiary)
        }
    }
}

@Composable
private fun SoonTag() {
    VText(
        "SOON",
        style = VastuTheme.type.caption,
        color = VastuTheme.colors.provenanceMod,
        modifier = Modifier
            .clip(VastuTheme.shapes.full)
            .background(VastuTheme.colors.provenanceMod.copy(alpha = 0.14f))
            .padding(horizontal = VastuTheme.spacing.s2, vertical = VastuTheme.spacing.s1),
    )
}
