package com.vastufirst.app.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.designsystem.components.BrandMark
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Intent
import com.vastufirst.app.ui.common.screenRoot

/**
 * Welcome (Product PRD §6.1 · design system screen 1). The one question that changes the whole
 * product — intent (§2). No sign-up, no phone number. Continue is disabled until an intent is
 * chosen.
 *
 * ⭐ NO LANGUAGE CONTROL, and this is permanent (owner decision, 9 Aug 2026). VastuFirst is
 * English only — not "English for now". The six-language plan is cancelled, so this screen carries
 * no picker, no "soon" pills and no note about language: a picker with one option is a dead control
 * (UI-POLISH §3F), and a line saying "this app is in English" on a visibly English screen is an
 * apology for something nobody asked about. Do not reinstate any of it.
 */
@Composable
fun WelcomeScreen(
    vm: NewPlanViewModel,
    onContinue: () -> Unit,
) {
    // Thin wrapper: the ONLY thing that touches the ViewModel, so the screen below renders
    // headlessly from fixture state in the screenshot harness (UI-POLISH §6, stateless-content).
    WelcomeContent(
        intent = vm.intent,
        onIntentChange = { vm.intent = it },
        onContinue = onContinue,
    )
}

/** Welcome as a pure function of its state — no ViewModel — so the render harness can draw it. */
@Composable
fun WelcomeContent(
    intent: Intent?,
    onIntentChange: (Intent) -> Unit,
    onContinue: () -> Unit,
) {
    val colors = VastuTheme.colors
    val chosen = intent

    Column(
        modifier = Modifier
            .screenRoot(colors.paper)
            .verticalScroll(rememberScrollState())
            .padding(VastuTheme.spacing.s6),
    ) {
        BrandMark()
        Spacer(Modifier.height(VastuTheme.spacing.s4))
        SectionLabel("No sign-up · No phone number")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        // ⚠ NEUTRAL, and it has to be (v0.6.6). This sits ABOVE the question that decides everything
        // else, so it cannot branch on the answer — and "fix it on paper before you build" was
        // written for the one person in three who is still building. Read by somebody choosing
        // between two flats, or living in a home they cannot rebuild, it promised something the
        // report deliberately no longer offers them.
        VText(
            text = "See your home's Vastu — and exactly what to do about it.",
            style = VastuTheme.type.h1,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VText(
            // Copy cut (4 Aug 2026): the buying/building/living clause repeated the intent cards
            // directly below, so it paid rent twice. 26 words → 14, no claim lost.
            text = "Upload or draw your plan. Mark North. See your score and what to do.",
            style = VastuTheme.type.body,
            color = colors.textSecondary,
        )

        Divider()

        SectionLabel("What brings you here?")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        // ⭐ BUYING leads. Most people who open this app are looking at a home somebody else has
        // already built — the plan is fixed and the question is "should I take it, and what will I
        // have to live with". Building your own from a plan that is still on paper is the rarer
        // case, so it sits second. The order is the owner's call (v0.6.6) and it also decides what
        // the report says: only BUILDING is offered layout changes at all.
        Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            IntentCard("I am buying a home", "Deciding between options", chosen == Intent.BUYING) { onIntentChange(Intent.BUYING) }
            IntentCard("I am building a home", "The plan is not final yet", chosen == Intent.BUILDING) { onIntentChange(Intent.BUILDING) }
            IntentCard("I already live here", "Looking for remedies", chosen == Intent.LIVING) { onIntentChange(Intent.LIVING) }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s6))
        VastuButton(text = "Continue", onClick = onContinue, enabled = chosen != null, modifier = Modifier.testTag("welcome.continue"))
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .padding(vertical = VastuTheme.spacing.s6)
            .fillMaxWidth()
            .height(VastuTheme.borders.regular)
            .background(VastuTheme.colors.borderDefault),
    )
}

@Composable
private fun IntentCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val colors = VastuTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.md)
            .background(if (selected) colors.primary.copy(alpha = 0.10f) else colors.surfaceRaised)
            .border(
                if (selected) VastuTheme.borders.strong else VastuTheme.borders.regular,
                if (selected) colors.primary else colors.borderDefault,
                VastuTheme.shapes.md,
            )
            .clickableTap(role = Role.Button, onClick = onClick)
            .padding(VastuTheme.spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            VText(text = title, style = VastuTheme.type.bodyLg, color = colors.textPrimary)
            VText(text = subtitle, style = VastuTheme.type.bodySm, color = colors.textTertiary)
        }
        if (selected) {
            Box(
                Modifier.size(VastuTheme.sizes.iconSm).clip(CircleShape).background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                VText("✓", style = VastuTheme.type.caption, color = colors.onPrimary)
            }
        }
    }
}
