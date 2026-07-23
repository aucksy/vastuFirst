package com.vastufirst.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.vastufirst.designsystem.theme.VastuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VastuTheme {
                Phase0Screen()
            }
        }
    }
}

/**
 * Phase 0 proof screen: an empty app wrapped in `VastuTheme { }`, reading colours and the
 * type ramp only from the theme (no raw hex, no ad-hoc text style). Real screens arrive in
 * Phase 2 from the Saffron & Ivory design system.
 */
@Composable
private fun Phase0Screen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VastuTheme.colors.paper)
            .padding(VastuTheme.spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            text = "VastuFirst",
            style = VastuTheme.type.display.copy(
                color = VastuTheme.colors.primary,
                textAlign = TextAlign.Center,
            ),
        )
        BasicText(
            text = "Foundation ready",
            style = VastuTheme.type.body.copy(
                color = VastuTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.padding(top = VastuTheme.spacing.s3),
        )
    }
}
