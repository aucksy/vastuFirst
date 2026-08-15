package com.vastufirst.app.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.vastufirst.designsystem.components.IconTapButton
import com.vastufirst.designsystem.components.ProvenanceTag
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuProvenance
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.app.ui.common.screenRoot

/**
 * Honesty & sources (§ design system screen 13). The visible disclaimer (§0.1 / §11) and the
 * provenance vocabulary that is the product's core differentiator — every rule is tagged, and a
 * MOD tag is honesty about age, never a warning.
 */
@Composable
fun LegalScreen(onBack: () -> Unit) {
    val colors = VastuTheme.colors
    Column(
        modifier = Modifier.screenRoot(colors.paper).verticalScroll(rememberScrollState()).padding(VastuTheme.spacing.s6),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            IconTapButton("‹", contentDescription = "Back", onClick = onBack)
            VText("Honesty & sources", style = VastuTheme.type.h2, color = colors.textPrimary)
        }
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        Box(
            Modifier.fillMaxWidth().clip(VastuTheme.shapes.md).background(colors.surface).padding(VastuTheme.spacing.s4),
        ) {
            VText(
                "Vastu is a traditional practice. This is guidance for your own decisions — not a guaranteed outcome.",
                style = VastuTheme.type.bodyLg, color = colors.textPrimary,
            )
        }

        Spacer(Modifier.height(VastuTheme.spacing.s6))
        SectionLabel("How we tag every rule")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            // ⚠ Said "with citation" until 15 Aug 2026. No rule in the rule data carries one, so the
            // promise had nothing behind it. Restore the citation wording only once citations exist.
            TagRow(VastuProvenance.TEXT, "Traceable to a classical text.")
            TagRow(VastuProvenance.DERIV, "Reasoned from the mandala; widely taught.")
            TagRow(VastuProvenance.MOD, "20th-century. Honesty about age, not a warning.")
            TagRow(VastuProvenance.DISP, "Two readings shown, neither chosen for you.")
        }

        Spacer(Modifier.height(VastuTheme.spacing.s6))
        VText(
            "We never use fear, urgency, or promises of wealth, health or fortune. Where the tradition contradicts itself, we show you both sides.",
            style = VastuTheme.type.body, color = colors.textSecondary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s4))
    }
}

@Composable
private fun TagRow(provenance: VastuProvenance, description: String) {
    // Pill above text, never beside it: at font scale 2.0 the pill's intrinsic width squeezed a
    // weighted sibling to a letters-per-line sliver (4 Aug audit C4).
    Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1)) {
        ProvenanceTag(provenance)
        VText(description, style = VastuTheme.type.bodySm, color = VastuTheme.colors.textSecondary)
    }
}
