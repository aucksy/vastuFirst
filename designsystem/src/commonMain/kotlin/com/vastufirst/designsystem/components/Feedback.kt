package com.vastufirst.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * Empty state (design system §06). Never a dead end — a title and a plain-language nudge.
 * Note: VastuFirst deliberately avoids scary error copy; where a plan can't be read we guide,
 * we don't stop (Product PRD; [[vastufirst-no-error-states]]).
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.md)
            .background(VastuTheme.colors.surface)
            .padding(VastuTheme.spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
    ) {
        if (icon != null) { icon(); Box(Modifier.height(VastuTheme.spacing.s2)) }
        VText(text = title, style = VastuTheme.type.h3, color = VastuTheme.colors.textPrimary, align = TextAlign.Center)
        VText(text = body, style = VastuTheme.type.body, color = VastuTheme.colors.textSecondary, align = TextAlign.Center)
    }
}

/** A gentle, non-technical "let's fix this together" surface (never "cannot analyze"). */
@Composable
fun GuidanceState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.md)
            .background(VastuTheme.colors.surface)
            .padding(VastuTheme.spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
    ) {
        VText(text = title, style = VastuTheme.type.h3, color = VastuTheme.colors.textPrimary, align = TextAlign.Center)
        VText(text = body, style = VastuTheme.type.body, color = VastuTheme.colors.textSecondary, align = TextAlign.Center)
        action?.invoke()
    }
}

/** A calm "working on it" surface while the engine computes — never a blank or a bare 0. */
@Composable
fun LoadingState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(VastuTheme.spacing.s6),
        contentAlignment = Alignment.Center,
    ) {
        VText(text = text, style = VastuTheme.type.body, color = VastuTheme.colors.textSecondary, align = TextAlign.Center)
    }
}

/** A dark toast strip ("Plan saved to your device"). */
@Composable
fun Toast(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(VastuTheme.shapes.md)
            .background(VastuTheme.colors.textPrimary)
            .padding(horizontal = VastuTheme.spacing.s4, vertical = VastuTheme.spacing.s3),
    ) {
        VText(text = text, style = VastuTheme.type.bodySm, color = VastuTheme.colors.paper)
    }
}
