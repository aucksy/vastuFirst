package com.vastufirst.app.ui.unlock

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.app.ui.common.screenRoot

/**
 * Unlock (§6.5 · design system screen 8) — no dark patterns; the price is shown before you pay.
 * Phase 2 has no payments (Razorpay is Phase 5), so this UNLOCKS LOCALLY and says so honestly —
 * the client sees the paywall UX without a real charge.
 */
@Composable
fun UnlockScreen(onUnlocked: () -> Unit) {
    val colors = VastuTheme.colors
    Column(
        modifier = Modifier.screenRoot(colors.paper).padding(VastuTheme.spacing.s6),
    ) {
        VText("Unlock the full report", style = VastuTheme.type.h2, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s4))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            VText("₹699", style = VastuTheme.type.scoreDisplay, color = colors.textPrimary)
            VText("one-time · this home, forever", style = VastuTheme.type.body, color = colors.textTertiary, modifier = Modifier.padding(bottom = VastuTheme.spacing.s3))
        }
        Spacer(Modifier.height(VastuTheme.spacing.s6))

        SectionLabel("What you get")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            listOf(
                "Every issue ranked, not just the top three",
                "Layout change and remedy for each",
                "Source & provenance on every rule",
                "Where the schools disagree, shown honestly",
            ).forEach { Feature(it) }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s8))
        VastuButton("Unlock on this device", onClick = onUnlocked)
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VText(
            "Preview build: no payment is taken yet — the report unlocks on this device. Real checkout arrives in a later update.",
            style = VastuTheme.type.caption, color = colors.textTertiary,
        )
    }
}

@Composable
private fun Feature(text: String) {
    val colors = VastuTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
        VText("✓", style = VastuTheme.type.body, color = colors.verdictIdeal)
        VText(text, style = VastuTheme.type.body, color = colors.textPrimary)
    }
}
