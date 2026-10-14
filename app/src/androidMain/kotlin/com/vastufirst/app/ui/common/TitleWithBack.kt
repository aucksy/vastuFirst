package com.vastufirst.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.vastufirst.designsystem.components.IconTapButton
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * A screen's title with the ‹ back arrow in front of it — the ONE way a screen shows its way back.
 *
 * ⭐ WHY (14 Oct 2026, iPhone plan): an iPhone has no Back key. Four screens had no on-screen way back
 * and relied on Android's: Welcome (when opened from the saved homes), Add a home, the grid editor and
 * Unlock. They now carry the same arrow the Legal, Privacy, Settings and door screens already had — on
 * both phones, so the two apps stay one design (the plan's recommended option).
 *
 * With [onBack] null there is nothing to go back to (Welcome on a first launch), so only the title
 * shows — never an arrow that does nothing.
 */
@Composable
fun TitleWithBack(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    style: TextStyle = VastuTheme.type.h2,
) {
    val colors = VastuTheme.colors
    if (onBack == null) {
        VText(title, style = style, color = colors.textPrimary, modifier = modifier)
        return
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
    ) {
        IconTapButton("‹", contentDescription = "Back", onClick = onBack)
        VText(title, style = style, color = colors.textPrimary)
    }
}
