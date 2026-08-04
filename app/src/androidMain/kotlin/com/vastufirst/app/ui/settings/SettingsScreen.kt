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
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.vastufirst.app.ui.scan.ScanReviewStyle
import com.vastufirst.app.CrashLog
import com.vastufirst.app.ui.common.screenRoot

/**
 * Settings (§ design system screen 12). Phase 2: preferences are shown (language + school are
 * fixed this phase), plus the honest data controls — nothing leaves the device, and the user can
 * delete everything. Full localisation + school switching land in Phase 4.
 */
@Composable
fun SettingsScreen(
    onLegal: () -> Unit,
    onPrivacy: () -> Unit,
    onBack: () -> Unit,
    homeViewModel: HomeViewModel = koinViewModel(),
    consent: PlanReadingConsent = koinInject(),
    reviewStyle: ScanReviewStyle = koinInject(),
) {
    // Thin wrapper: the ONLY thing that touches the ViewModel, so the screen renders headlessly
    // from fixture callbacks in the screenshot harness (UI-POLISH §6, stateless-content).
    var allowed by remember { mutableStateOf(consent.isGranted()) }
    var photoReview by remember { mutableStateOf(reviewStyle.onPhoto()) }
    val context = LocalContext.current
    // ⭐ A crash the app recorded last time. Read once, so the offer does not flicker in and out as
    // the screen recomposes, and cleared the moment the user acts either way — nobody should be
    // asked twice about the same crash.
    var crash by remember { mutableStateOf(CrashLog.lastCrash(context)) }
    SettingsContent(
        onLegal = onLegal,
        onPrivacy = onPrivacy,
        onBack = onBack,
        onDeleteAll = homeViewModel::deleteAll,
        planReadingAllowed = allowed,
        onSetPlanReading = { granted -> consent.set(granted); allowed = granted },
        photoReview = photoReview,
        onSetPhotoReview = { onPhoto -> reviewStyle.set(onPhoto); photoReview = onPhoto },
        hasCrashReport = crash != null,
        onSendCrash = {
            crash?.let { text -> sendCrashEmail(context, text) }
            CrashLog.clear(context)
            crash = null
        },
        onDismissCrash = {
            CrashLog.clear(context)
            crash = null
        },
    )
}

/**
 * Hand the crash text to the user's own mail app, with everything visible before they send.
 *
 * ⚠ ACTION_SENDTO with a mailto: URI, never ACTION_SEND. SENDTO can only be answered by something
 * that actually handles email, so the chooser cannot offer to post the crash to a social app — which
 * is a thing ACTION_SEND does, and a thing nobody wants to discover after tapping.
 */
/** A blank line in the email body, built from a character code so no editing pass can eat an escape. */
private val LINE_BREAK: String = 10.toChar().toString()

private fun sendCrashEmail(context: Context, body: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:contact@vastufirst.com")
        putExtra(Intent.EXTRA_SUBJECT, "VastuFirst — something went wrong")
        putExtra(
            Intent.EXTRA_TEXT,
            "Anything you were doing when it happened (optional):" + LINE_BREAK + LINE_BREAK +
                LINE_BREAK + "--- technical detail, nothing personal ---" + LINE_BREAK + body,
        )
    }
    // No mail app on the device is a real case on a cheap phone; failing silently is better than
    // crashing the settings screen while reporting a crash.
    runCatching { context.startActivity(intent) }
}

/** Settings as a pure function of its callbacks — no ViewModel — so the render harness can draw it. */
@Composable
fun SettingsContent(
    onLegal: () -> Unit,
    onBack: () -> Unit,
    onDeleteAll: () -> Unit,
    planReadingAllowed: Boolean = false,
    onSetPlanReading: (Boolean) -> Unit = {},
    /** Where a scan is checked: on the photo itself (new, owner request) or on the guided grid. */
    photoReview: Boolean = false,
    onSetPhotoReview: (Boolean) -> Unit = {},
    onPrivacy: () -> Unit = {},
    /** True when the app recorded a crash last time it ran. */
    hasCrashReport: Boolean = false,
    onSendCrash: () -> Unit = {},
    onDismissCrash: () -> Unit = {},
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
            // "School profile / Traditional 8-zone" was our own jargon on a screen a customer opens.
            // Same meaning, ordinary words, and shorter — which also stops the value squeezing the
            // label on a narrow phone at a large font.
            RowItem("Vastu reading", trailing = "8 zones")
            // ⭐ The two scan-checking experiences, side by side by design (owner request, 4 Aug
            // 2026): "On the photo" keeps the scanned picture on screen with the rooms as a
            // checklist; "On the grid" is the classic editor. Tap to switch, take a scan, compare.
            RowItem(
                "Check a scan",
                trailing = if (photoReview) "On the photo" else "On the grid",
                onClick = { onSetPhotoReview(!photoReview) },
            )
        }
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        // ⚠ Without this line the toggle is a silent no-op for every scan that can't place rooms —
        // flip it, scan, land on the grid anyway, and it reads as broken (4 Aug 2026 audit; the
        // project rule is that nothing may do nothing silently). One line, one sentence, on purpose:
        // a longer note pushed the last row below the fold at 320 dp (see the note below this Group).
        VText(
            "If a scan can't be checked on the photo, it opens on the grid.",
            style = VastuTheme.type.bodySm, color = colors.textTertiary,
        )

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
            RowItem("Privacy", trailing = "›", onClick = onPrivacy)
            RowItem("Honesty & sources", trailing = "›", onClick = onLegal)
            RowItem("Delete all my data", trailing = "›", labelColor = colors.error, trailingColor = colors.error, onClick = { showConfirm = true })
        }
        Spacer(Modifier.height(VastuTheme.spacing.s4))
        // ⚠ Kept to one short sentence on purpose. Spelling the exception out in full took this to
        // three lines, which pushed the last row of the screen below the fold at 320 dp and at 200 %
        // font — the L1 ratchet caught it. The detail belongs on the consent screen, which is where
        // the user is actually deciding; this line only has to be true and readable.
        // ⚠ Shortened AGAIN, and for the same measured reason it was shortened before: adding the
        // Privacy row above cost a line, and the geometry gate immediately reported one more element
        // pushed below the fold. The full explanation now has a screen of its own one tap away, so
        // this only has to be true and short — it is a reassurance, not the policy.
        VText(
            "No account, no phone number. Only a plan you upload ever leaves your phone.",
            style = VastuTheme.type.bodySm,
            color = colors.textTertiary,
        )

        // ⭐ The app closed unexpectedly last time. Offered here rather than as a pop-up on launch:
        // interrupting somebody the moment they reopen an app that just crashed on them is adding
        // insult to injury. Nothing is sent until they press send, and they can read it all first.
        if (hasCrashReport) {
            Spacer(Modifier.height(VastuTheme.spacing.s6))
            SectionLabel("Something went wrong")
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            Group {
                Column(Modifier.padding(VastuTheme.spacing.s4)) {
                    VText(
                        "The app closed unexpectedly last time you used it. Sending us what happened " +
                            "helps us fix it. It opens your email app so you can read it first, and it " +
                            "contains nothing about you or your homes.",
                        style = VastuTheme.type.bodySm, color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(VastuTheme.spacing.s3))
                    VastuButton("Send what went wrong", onClick = onSendCrash, large = false)
                    Spacer(Modifier.height(VastuTheme.spacing.s2))
                    VastuButton(
                        "No thanks", onClick = onDismissCrash,
                        style = VastuButtonStyle.SECONDARY, large = false,
                    )
                }
            }
        }
        Spacer(Modifier.height(VastuTheme.spacing.s4))
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
