package com.vastufirst.app.ui.scan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vastufirst.app.ui.common.TitleWithBack
import com.vastufirst.app.ui.common.screenRoot
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * ⚠ THE iPHONE'S CAMERA AND PICKERS ARE NOT BUILT YET (iPhone plan, stage 4). Until they are, this
 * screen SAYS so — never a button that leads nowhere (never ship a lookalike) — and offers the one
 * way in that works on the iPhone today: drawing the home on the grid, scored by exactly the same rules.
 */
@Composable
actual fun ScanRoute(
    vm: ScanViewModel,
    onUseRooms: (com.vastufirst.shared.scan.ScanOutcome) -> Unit,
    onDrawInstead: () -> Unit,
    onBack: () -> Unit,
    startWith: ScanStart?,
    north: NorthOnResult,
) {
    val colors = VastuTheme.colors
    Column(Modifier.screenRoot(colors.paper).padding(VastuTheme.spacing.s6)) {
        TitleWithBack("Read your plan", onBack = onBack)
        Spacer(Modifier.height(VastuTheme.spacing.s4))
        VText(
            "Reading a plan from a photo or a PDF isn't ready on iPhone yet. You can draw your home on " +
                "a grid instead — it is scored by exactly the same rules.",
            style = VastuTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s6))
        VastuButton("Draw it on a grid instead", onClick = onDrawInstead, modifier = Modifier.fillMaxWidth())
    }
}
