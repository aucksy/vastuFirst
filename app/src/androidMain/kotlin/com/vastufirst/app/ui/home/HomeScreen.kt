package com.vastufirst.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vastufirst.data.SavedPlan
import com.vastufirst.designsystem.components.BrandMark
import com.vastufirst.designsystem.components.EmptyState
import com.vastufirst.designsystem.components.IconTapButton
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.VastuCard
import com.vastufirst.designsystem.components.VastuListRow
import com.vastufirst.designsystem.components.VastuTextField
import com.vastufirst.designsystem.components.LocalDecimalMark
import com.vastufirst.designsystem.components.scoreBandColor
import com.vastufirst.designsystem.components.scoreOutOfTen
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.app.ui.common.relativeUpdated
import com.vastufirst.app.ui.common.screenRoot
import org.koin.androidx.compose.koinViewModel

/**
 * Saved-plans home (§6 · design system screen 11). Add a home, or reopen a saved one (which
 * re-runs the engine from the stored plan). Two plans sit side by side so a BUYING user can
 * compare — the promised comparison, honestly (Impl PRD §8.4). Each home shows its own name (auto
 * "Home N", renamable) and real "last updated" time, so the compare is between distinct homes.
 */
@Composable
fun HomeScreen(
    onAddHome: () -> Unit,
    onOpenPlan: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    // Thin wrapper: the ONLY thing that touches the ViewModel, so the list below renders headlessly
    // from a fixture in the screenshot harness — including the empty state (UI-POLISH §6).
    val saved by viewModel.plans.collectAsStateWithLifecycle()
    val scoreChanges by viewModel.scoreChanges.collectAsStateWithLifecycle()
    HomeContent(
        plans = saved.plans,
        unreadable = saved.unreadable,
        onAddHome = onAddHome,
        onOpenPlan = onOpenPlan,
        onSettings = onSettings,
        onRename = viewModel::rename,
        scoreChanges = scoreChanges,
        onAcknowledgeScoreChanges = viewModel::acknowledgeScoreChanges,
    )
}

/** Saved-plans home as a pure function of its state — no ViewModel — so the render harness can draw it.
 *  `now` is injectable so the "updated N days ago" line is deterministic in the harness. */
@Composable
fun HomeContent(
    plans: List<SavedPlan>,
    onAddHome: () -> Unit,
    onOpenPlan: (String) -> Unit,
    onSettings: () -> Unit,
    onRename: (String, String) -> Unit = { _, _ -> },
    now: Long = System.currentTimeMillis(),
    /** Saved rows this build could not read. Shown rather than hidden — see the note below. */
    unreadable: Int = 0,
    /** Homes scored under an older set of Vastu rules, re-run under today's. Null = nothing changed. */
    scoreChanges: ScoreChangeNotice? = null,
    onAcknowledgeScoreChanges: (ScoreChangeNotice) -> Unit = {},
) {
    val colors = VastuTheme.colors
    // The home currently being renamed (null = no dialog). Held here so the dialog overlays the whole
    // screen rather than living inside a scrolling row.
    var renaming by remember { mutableStateOf<SavedPlan?>(null) }

    Column(
        modifier = Modifier.screenRoot(colors.paper).padding(VastuTheme.spacing.s6),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                BrandMark(size = VastuTheme.sizes.tile)
                VText("Your plans", style = VastuTheme.type.h2, color = colors.textPrimary)
            }
            Box(
                Modifier.size(VastuTheme.sizes.control).clip(CircleShape).background(colors.surface)
                    .clickableTap(role = Role.Button, onClick = onSettings)
                    .semantics(mergeDescendants = true) { contentDescription = "Settings" },
                contentAlignment = Alignment.Center,
            ) { VText("⚙", style = VastuTheme.type.h3, color = colors.textSecondary) }
        }
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        // ⭐ A home that could not be read is SAID, never silently skipped. One unreadable row used to
        // throw inside this list's flow and empty the whole screen; now the rest load — but a home
        // quietly missing from the list looks exactly like a home the app deleted on its own, which
        // is the worst thing an app holding your data can appear to do. The row is still on disk.
        if (unreadable > 0) {
            VText(
                if (unreadable == 1) "1 home couldn't be opened by this version. It's still saved — an update should bring it back."
                else "$unreadable homes couldn't be opened by this version. They're still saved — an update should bring them back.",
                style = VastuTheme.type.bodySm,
                color = colors.verdictSuboptimal,
            )
            Spacer(Modifier.height(VastuTheme.spacing.s3))
        }

        if (plans.isEmpty()) {
            EmptyState(
                title = "No plans yet",
                body = "Add your first floor plan to see its Vastu score.",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                // ⭐ WE CHANGED A RULE — said out loud, above the numbers it moved. Inside the list
                // rather than above it so the "Add a home" button can never be pushed off the bottom
                // of a 320 dp screen by a long explanation, which is a defect this app has shipped
                // before. It is the first thing on screen on arrival either way.
                scoreChanges?.let { notice ->
                    item(key = "score-change") {
                        ScoreChangeCard(notice = notice, onAcknowledge = { onAcknowledgeScoreChanges(notice) })
                    }
                }
                items(plans, key = { it.id }) { plan ->
                    PlanRow(plan = plan, now = now, onOpen = onOpenPlan, onRename = { renaming = plan })
                }
            }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s4))
        VastuButton("Add a home", onClick = onAddHome, modifier = Modifier.testTag("home.add"))
    }

    renaming?.let { plan ->
        Dialog(onDismissRequest = { renaming = null }) {
            RenameDialogContent(
                currentName = plan.name,
                onCancel = { renaming = null },
                onSave = { newName -> onRename(plan.id, newName); renaming = null },
            )
        }
    }
}

/**
 * ⭐ "We changed a rule, here is what it did to your number."
 *
 * ⚠ The alternative — re-scoring saved homes quietly — is the single most trust-destroying thing a
 * paid app of this kind can do. Someone who wrote 3.1 on a piece of paper last week and opens the
 * app to find 3.3, with no word about it, has no way to tell a considered ruling from a bug. The
 * reason comes from the rule data itself, so a rule change can never ship without its explanation.
 *
 * Written for an older reader in bright daylight: a heading that says what happened, the reason in
 * ordinary words, one line per home with both numbers, and a single button.
 */
@Composable
private fun ScoreChangeCard(notice: ScoreChangeNotice, onAcknowledge: () -> Unit) {
    val colors = VastuTheme.colors
    val mark = LocalDecimalMark.current
    VastuCard(accent = colors.info) {
        VText(scoreChangeTitle(notice), style = VastuTheme.type.h3, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(notice.reason, style = VastuTheme.type.body, color = colors.textSecondary)
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        notice.changes.forEach { change ->
            VText(
                scoreChangeLine(change) { scoreOutOfTen(it, mark) },
                style = VastuTheme.type.label,
                color = colors.textPrimary,
            )
        }
        Spacer(Modifier.height(VastuTheme.spacing.s4))
        VastuButton(
            "Got it",
            onClick = onAcknowledge,
            large = false,
            modifier = Modifier.testTag("home.scorechange.ack"),
        )
    }
}

@Composable
private fun PlanRow(plan: SavedPlan, now: Long, onOpen: (String) -> Unit, onRename: () -> Unit) {
    val colors = VastuTheme.colors
    val intentLabel = plan.intent.name.lowercase().replaceFirstChar { it.uppercase() }
    VastuListRow(
        title = plan.name,
        subtitle = "$intentLabel · ${relativeUpdated(plan.updatedAt, now)}",
        modifier = Modifier.clickableTap(role = Role.Button, onClick = { onOpen(plan.id) }),
        trailing = {
            // The pencil is a child tap target: it consumes its own tap, so tapping it renames while
            // tapping the rest of the row still opens the home.
            IconTapButton(glyph = "✎", contentDescription = "Rename ${plan.name}", onClick = onRename)
            val mark = LocalDecimalMark.current
            // ⚠ No contentDescription here, on purpose. The whole row is clickable, so it is already
            // ONE merged node to a screen reader; a description added inside would be appended to
            // the row's text rather than replacing it, and the row would read the score twice.
            Column(horizontalAlignment = Alignment.End) {
                VText(scoreOutOfTen(plan.score, mark), style = VastuTheme.type.h2, color = scoreBandColor(plan.score))
                VText("/10", style = VastuTheme.type.caption, color = colors.textTertiary)
            }
        },
    )
}

/**
 * The rename box (its own composable so the screenshot harness can render it without a live Dialog
 * window / Activity). Pre-filled with the current name; Save is disabled until the name is non-blank.
 */
@Composable
fun RenameDialogContent(
    currentName: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    val colors = VastuTheme.colors
    var text by remember(currentName) { mutableStateOf(currentName) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.lg)
            .background(colors.paper)
            .padding(VastuTheme.spacing.s6),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s4),
    ) {
        VText("Rename this home", style = VastuTheme.type.h3, color = colors.textPrimary)
        VastuTextField(
            value = text,
            onValueChange = { text = it },
            label = "Home name",
            placeholder = "e.g. Dwarka flat",
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            VastuButton(
                "Cancel",
                onClick = onCancel,
                style = VastuButtonStyle.SECONDARY,
                large = false,
                modifier = Modifier.weight(1f),
            )
            VastuButton(
                "Save",
                onClick = { onSave(text) },
                enabled = text.isNotBlank(),
                large = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
