package com.vastufirst.app.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.designsystem.components.BrandMark
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuChip
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Intent
import com.vastufirst.app.ui.common.screenRoot

/**
 * Welcome (Product PRD §6.1 · design system screen 1). Language + the one question that changes
 * the whole product — intent (§2). No sign-up, no phone number. Continue is disabled until an
 * intent is chosen. Phase 2 ships English; the other five scripts arrive in Phase 4.
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
@OptIn(ExperimentalLayoutApi::class)
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
        VText(
            text = "Fix your home's Vastu on paper — before you build.",
            style = VastuTheme.type.h1,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VText(
            text = "Draw your plan. Mark which way North is. See exactly what to change, while changing it is still free.",
            style = VastuTheme.type.body,
            color = colors.textSecondary,
        )

        Divider()

        SectionLabel("Language")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2), verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
            VastuChip(text = "English", selected = true, onClick = {})
            listOf("हिन्दी", "தமிழ்", "తెలుగు", "मराठी", "বাংলা").forEach { SoonPill(it) }
        }
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText("More languages are coming soon — the app is in English for now.", style = VastuTheme.type.bodySm, color = colors.textTertiary)

        Divider()

        SectionLabel("What brings you here?")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            IntentCard("I am building a home", "The plan is not final yet", chosen == Intent.BUILDING) { onIntentChange(Intent.BUILDING) }
            IntentCard("I am buying a home", "Deciding between options", chosen == Intent.BUYING) { onIntentChange(Intent.BUYING) }
            IntentCard("I already live here", "Looking for remedies", chosen == Intent.LIVING) { onIntentChange(Intent.LIVING) }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s6))
        VastuButton(text = "Continue", onClick = onContinue, enabled = chosen != null, modifier = Modifier.testTag("welcome.continue"))
        Spacer(Modifier.height(VastuTheme.spacing.s4))
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
private fun SoonPill(text: String) {
    val colors = VastuTheme.colors
    Box(
        modifier = Modifier
            .clip(VastuTheme.shapes.full)
            .border(VastuTheme.borders.regular, colors.borderDefault, VastuTheme.shapes.full)
            .padding(horizontal = VastuTheme.spacing.s4, vertical = VastuTheme.spacing.s3),
    ) {
        VText(text = text, style = VastuTheme.type.bodySm, color = colors.textTertiary)
    }
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
            .clickableTap(onClick = onClick)
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
