package com.vastufirst.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import com.vastufirst.designsystem.components.IconTapButton
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import com.vastufirst.app.ui.home.HomeViewModel
import com.vastufirst.app.ui.scan.PlanReadingConsent
import com.vastufirst.app.ui.common.screenRoot

/**
 * Settings (§ design system screen 12). Phase 2: preferences are shown (language + school are
 * fixed this phase), plus the honest data controls — nothing leaves the device, and the user can
 * delete everything. Full localisation + school switching land in Phase 4.
 */
@Composable
fun SettingsScreen(
    onLegal: () -> Unit,
    onBack: () -> Unit,
    homeViewModel: HomeViewModel = koinViewModel(),
    consent: PlanReadingConsent = koinInject(),
) {
    // Thin wrapper: the ONLY thing that touches the ViewModel, so the screen renders headlessly
    // from fixture callbacks in the screenshot harness (UI-POLISH §6, stateless-content).
    var allowed by remember { mutableStateOf(consent.isGranted()) }
    SettingsContent(
        onLegal = onLegal,
        onBack = onBack,
        onDeleteAll = homeViewModel::deleteAll,
        planReadingAllowed = allowed,
        onSetPlanReading = { granted -> consent.set(granted); allowed = granted },
    )
}

/** Settings as a pure function of its callbacks — no ViewModel — so the render harness can draw it. */
@Composable
fun SettingsContent(
    onLegal: () -> Unit,
    onBack: () -> Unit,
    onDeleteAll: () -> Unit,
    planReadingAllowed: Boolean = false,
    onSetPlanReading: (Boolean) -> Unit = {},
) {
    val colors = VastuTheme.colors
    var showConfirm by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.screenRoot(colors.paper).verticalScroll(rememberScrollState()).padding(VastuTheme.spacing.s6),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            IconTapButton("‹", contentDescription = "Back", onClick = onBack, modifier = Modifier.testTag("settings.back"))
            VText("Settings", style = VastuTheme.type.h2, color = colors.textPrimary)
        }
        Spacer(Modifier.height(VastuTheme.spacing.s6))

        SectionLabel("Preferences")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        Group {
            RowItem("Language", trailing = "English")
            RowItem("School profile", trailing = "Traditional 8-zone")
        }

        Spacer(Modifier.height(VastuTheme.spacing.s6))
        SectionLabel("Data & privacy")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        Group {
            RowItem("Your plans stay on this device", trailing = "On", trailingColor = colors.verdictIdeal)
            // ⭐ Consent has to be withdrawable to be consent at all (DPDP, NFR §10). Tapping the row
            // flips it, and turning it off puts the upload behind the explanation screen again.
            RowItem(
                "Reading uploaded plans online",
                trailing = if (planReadingAllowed) "Allowed" else "Off",
                trailingColor = if (planReadingAllowed) colors.verdictIdeal else colors.textTertiary,
                onClick = { onSetPlanReading(!planReadingAllowed) },
            )
            RowItem("Honesty & sources", trailing = "›", onClick = onLegal)
            RowItem("Delete all my data", trailing = "›", labelColor = colors.error, trailingColor = colors.error, onClick = { showConfirm = true })
        }
        Spacer(Modifier.height(VastuTheme.spacing.s4))
        // ⚠ Kept to one short sentence on purpose. Spelling the exception out in full took this to
        // three lines, which pushed the last row of the screen below the fold at 320 dp and at 200 %
        // font — the L1 ratchet caught it. The detail belongs on the consent screen, which is where
        // the user is actually deciding; this line only has to be true and readable.
        VText(
            "No account. No phone number. Nothing leaves your phone except a plan you upload to be read.",
            style = VastuTheme.type.bodySm,
            color = colors.textTertiary,
        )
    }

    if (showConfirm) {
        Dialog(onDismissRequest = { showConfirm = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(VastuTheme.shapes.lg)
                    .background(colors.paper)
                    .padding(VastuTheme.spacing.s6),
                verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s4),
            ) {
                VText("Delete all your data?", style = VastuTheme.type.h3, color = colors.textPrimary)
                VText(
                    "This permanently removes every saved home from this device. It can't be undone.",
                    style = VastuTheme.type.body, color = colors.textSecondary,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                    VastuButton(
                        "Keep my data",
                        onClick = { showConfirm = false },
                        large = false,
                        modifier = Modifier.weight(1f),
                    )
                    VastuButton(
                        "Delete everything",
                        onClick = { onDeleteAll(); showConfirm = false },
                        style = VastuButtonStyle.SECONDARY,
                        large = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.md)
            .background(VastuTheme.colors.surfaceRaised)
            .border(VastuTheme.borders.regular, VastuTheme.colors.borderDefault, VastuTheme.shapes.md),
    ) { content() }
}

@Composable
private fun RowItem(
    label: String,
    trailing: String,
    onClick: (() -> Unit)? = null,
    labelColor: Color = VastuTheme.colors.textPrimary,
    trailingColor: Color = VastuTheme.colors.textTertiary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickableTap(onClick = onClick) else Modifier)
            .padding(VastuTheme.spacing.s4),
        horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Both weighted, so neither can crush the other to zero width at font scale 2.0. A long
        // label ("Your plans stay on this device") was squeezing the trailing "On" to 0 dp, and a
        // long value ("Traditional 8-zone") would squeeze the label the other way. Two weighted
        // children always keep a non-zero share (UI-POLISH §3.D — the row-shatter class).
        VText(label, style = VastuTheme.type.body, color = labelColor, modifier = Modifier.weight(1f))
        VText(trailing, style = VastuTheme.type.body, color = trailingColor, align = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
