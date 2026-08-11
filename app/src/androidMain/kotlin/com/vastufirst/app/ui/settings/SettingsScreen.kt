package com.vastufirst.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import com.vastufirst.designsystem.components.VastuChip
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import com.vastufirst.app.ui.home.HomeViewModel
import com.vastufirst.app.ui.scan.PlanReadingConsent
import com.vastufirst.app.CrashLog
import com.vastufirst.app.ui.common.screenRoot

/**
 * Settings (§ design system screen 12). Preferences are shown (the school is fixed for now), plus
 * the honest data controls — nothing leaves the device, and the user can delete everything. School
 * switching lands in Phase 4. **There is no language preference and there never will be: the app is
 * English only, permanently (owner decision, 9 Aug 2026).**
 */
@Composable
fun SettingsScreen(
    onLegal: () -> Unit,
    onPrivacy: () -> Unit,
    onBack: () -> Unit,
    homeViewModel: HomeViewModel = koinViewModel(),
    consent: PlanReadingConsent = koinInject(),
    readerChoice: com.vastufirst.app.ui.scan.ReaderChoice = koinInject(),
    recipe: com.vastufirst.shared.scan.PlanReadRecipe = koinInject(),
) {
    // Thin wrapper: the ONLY thing that touches the ViewModel, so the screen renders headlessly
    // from fixture callbacks in the screenshot harness (UI-POLISH §6, stateless-content).
    var allowed by remember { mutableStateOf(consent.isGranted()) }
    val context = LocalContext.current
    // ⭐ A crash the app recorded last time. Read once, so the offer does not flicker in and out as
    // the screen recomposes, and cleared the moment the user acts either way — nobody should be
    // asked twice about the same crash.
    var crash by remember { mutableStateOf(CrashLog.lastCrash(context)) }
    var reader by remember { mutableStateOf(readerChoice.chosen()) }
    val readerOptions = remember(recipe) {
        listOfNotNull(recipe.config.model.ifBlank { null }, recipe.config.escalationModel)
    }
    SettingsContent(
        onLegal = onLegal,
        onPrivacy = onPrivacy,
        onBack = onBack,
        readerOptions = readerOptions,
        chosenReader = reader,
        onChooseReader = { m -> readerChoice.set(m); reader = m },
        onDeleteAll = homeViewModel::deleteAll,
        planReadingAllowed = allowed,
        onSetPlanReading = { granted -> consent.set(granted); allowed = granted },
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(
    onLegal: () -> Unit,
    onBack: () -> Unit,
    onDeleteAll: () -> Unit,
    planReadingAllowed: Boolean = false,
    onSetPlanReading: (Boolean) -> Unit = {},
    /** Where a scan is checked: on the photo itself (new, owner request) or on the guided grid. */
    onPrivacy: () -> Unit = {},
    /** True when the app recorded a crash last time it ran. */
    hasCrashReport: Boolean = false,
    onSendCrash: () -> Unit = {},
    onDismissCrash: () -> Unit = {},
    /**
     * ⭐ Which AI reads a plan — moved here out of the scan flow (owner, 10 Aug 2026: "Remove the
     * Luna and Gemini model selection and put them in settings for now"). Empty on a build with no
     * reading key, which hides the whole section rather than offering a choice that cannot work.
     */
    readerOptions: List<String> = emptyList(),
    chosenReader: String? = null,
    onChooseReader: (String?) -> Unit = {},
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
            // ⭐ No "Language — English" row, and this is now permanent. Dropped 4 Aug 2026 because
            // it could not be tapped and on a 200 % font phone it pushed "Delete all my data" below
            // the fold; made permanent 9 Aug 2026 when the six-language plan was cancelled. A
            // preference with one possible value is not a preference. Never reinstate it.
            // "School profile / Traditional 8-zone" was our own jargon on a screen a customer opens.
            // Same meaning, ordinary words, and shorter — which also stops the value squeezing the
            // label on a narrow phone at a large font.
            // ⚠ "8 zones" was simply wrong, and a reader could catch it. The engine reads NINE —
            // the eight directions plus the centre, the Brahmasthan — and the centre is on screen as
            // a zone in its own right: the North preview marks a middle staircase "C" and the report
            // scores it and calls it "Centre". Anybody counting the directions on their own report
            // got nine and found Settings saying eight. Spelled out rather than renumbered, because
            // "9 zones" invites the question this answers.
            RowItem("Vastu reading", trailing = "8 directions + centre")
            // ⭐ The "Check a scan: on the photo / on the grid" row is GONE (owner, 6 Aug 2026:
            // "we will completely remove anything to do with grid and modifiable boxes from scan
            // flow… user will only interact with actual floor plan"). It existed to compare the two
            // experiences on the same plan; that comparison is settled, and a switch that could send
            // a scan back into the editor contradicts the flow it would have been switching away
            // from. Scans are checked on the photo, and that is not a preference any more.
        }

        if (readerOptions.isNotEmpty()) {
            Spacer(Modifier.height(VastuTheme.spacing.s6))
            SectionLabel("Which AI reads a plan · testing")
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            // ⚠ FlowRow, per the standing rule that chips which can overflow wrap rather than
            // side-scroll — a model name is long and there is no bound on how long the next one is.
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
                verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
            ) {
                VastuChip("Auto", selected = chosenReader == null, onClick = { onChooseReader(null) })
                readerOptions.forEach { m ->
                    VastuChip(
                        com.vastufirst.app.ui.scan.shortModelName(m),
                        selected = chosenReader == m,
                        onClick = { onChooseReader(m) },
                    )
                }
            }
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(
                "Auto is the normal choice — it asks " +
                    com.vastufirst.app.ui.scan.shortModelName(readerOptions.first()) +
                    " and gets a second opinion when needed. A named pick reads with that one only, " +
                    "and now stays chosen if you try a plan again.",
                style = VastuTheme.type.bodySm, color = colors.textTertiary,
            )
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
                        // Copy cut (4 Aug 2026): 37 -> 26 words; read-first and nothing-personal
                        // claims kept whole.
                        "The app crashed last time. Emailing us what happened helps us fix it. " +
                            "You read it first, and it says nothing about you or your homes.",
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
