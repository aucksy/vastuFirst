package com.vastufirst.app.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * Placeholder for the Welcome screen (Product PRD §6.1). The real language + intent pickers
 * are built from the Sage & Gold design system in Block C; this proves navigation reaches it.
 */
@Composable
fun WelcomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VastuTheme.colors.paper)
            .padding(VastuTheme.spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            text = "Fix your home's Vastu on paper — before you build.",
            style = VastuTheme.type.h2.copy(color = VastuTheme.colors.textPrimary, textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        BasicText(
            text = "Welcome flow — coming next.",
            style = VastuTheme.type.body.copy(color = VastuTheme.colors.textSecondary, textAlign = TextAlign.Center),
        )
    }
}
