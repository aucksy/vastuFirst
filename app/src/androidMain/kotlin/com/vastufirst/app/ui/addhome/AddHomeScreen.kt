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
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * Add home — method choice (§6.2 · design system screen 2). Phase 2 wires the guided grid and
 * bundled samples; AI plan-reading (upload) is Phase 4, shown here as "coming soon" rather than
 * hidden, so the full shape of the product is visible.
 */
@Composable
fun AddHomeScreen(
    onDrawGrid: () -> Unit,
    onSample: () -> Unit,
) {
    val colors = VastuTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.paper)
            .verticalScroll(rememberScrollState())
            .padding(VastuTheme.spacing.s6),
    ) {
        SectionLabel("Step 1 of 3")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VText("Add your home", style = VastuTheme.type.h2, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            "Place your rooms on a simple grid — or try a sample to see the whole flow first.",
            style = VastuTheme.type.body, color = colors.textSecondary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s6))

        Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            MethodCard(
                icon = "▦",
                title = "Draw it on a grid",
                subtitle = "Place room blocks by hand · one-handed",
                onClick = onDrawGrid,
            )
            MethodCard(
                icon = "◉",
                title = "Try a sample plan",
                subtitle = "See the whole flow in ten seconds",
                onClick = onSample,
            )
            MethodCard(
                icon = "⤒",
                title = "Upload a plan",
                subtitle = "Photo or PDF · AI reads the rooms",
                onClick = {},
                soon = true,
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
