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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vastufirst.data.SavedDraft
import com.vastufirst.data.SavedPlan
import com.vastufirst.data.UnreadableHome
import com.vastufirst.data.homeNameAlreadyTaken
import com.vastufirst.designsystem.components.BrandMark
import com.vastufirst.designsystem.components.EmptyState
import com.vastufirst.designsystem.components.IconTapButton
import com.vastufirst.designsystem.components.SectionLabel
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
import com.vastufirst.app.ui.common.rememberDayClock
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
    onOpenDraft: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    // Thin wrapper: the ONLY thing that touches the ViewModel, so the list below renders headlessly
    // from a fixture in the screenshot harness — including the empty state (UI-POLISH §6).
    val saved by viewModel.plans.collectAsStateWithLifecycle()
    val scoreChanges by viewModel.scoreChanges.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    // ⭐ THE DATES FOLLOW THE CALENDAR, NOT THE MOMENT THE SCREEN OPENED. Every row's "today" used to
    // be measured against a clock read once, when this screen was first drawn — so an app left open
    // across midnight went on calling yesterday "today" until something rebuilt the screen.
    //
    // ⚠ It is supplied HERE and never inside [HomeContent], whose own default stays a plain one-shot
    // reading. HomeContent is what the screenshot harness draws, and a clock that waits forever
    // would stop that harness ever seeing the screen as finished. See [rememberDayClock].
    HomeContent(
        plans = saved.plans,
        now = rememberDayClock(),
        unreadable = saved.unreadable,
        onAddHome = onAddHome,
        onOpenPlan = onOpenPlan,
        onSettings = onSettings,
        onRename = viewModel::rename,
        scoreChanges = scoreChanges,
        onAcknowledgeScoreChanges = viewModel::acknowledgeScoreChanges,
        drafts = drafts,
        onOpenDraft = onOpenDraft,
        onDiscardDraft = viewModel::deleteDraft,
        onRemoveUnreadable = viewModel::removeUnreadable,
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
    /** Saved rows this build could not read. Shown BY NAME rather than hidden — see the note below. */
    unreadable: List<UnreadableHome> = emptyList(),
    /** Remove one unreadable row for good. Reached only through the are-you-sure dialog. */
    onRemoveUnreadable: (String) -> Unit = {},
    /** Homes scored under an older set of Vastu rules, re-run under today's. Null = nothing changed. */
    scoreChanges: ScoreChangeNotice? = null,
    onAcknowledgeScoreChanges: (ScoreChangeNotice) -> Unit = {},
    /**
     * ⭐ Homes that were started and never finished (v0.6.6). They sit ABOVE the finished ones,
     * because an unfinished home is the one thing on this screen with something still to do — and
     * because the app no longer brings them back by itself, this list is the only way in. Before
     * this release, entering the drawing flow for ANY reason silently reloaded the leftover work.
     */
    drafts: List<SavedDraft> = emptyList(),
    onOpenDraft: (String) -> Unit = {},
    onDiscardDraft: (String) -> Unit = {},
) {
    val colors = VastuTheme.colors
    // The home currently being renamed (null = no dialog). Held here so the dialog overlays the whole
    // screen rather than living inside a scrolling row.
    var renaming by remember { mutableStateOf<SavedPlan?>(null) }
    // The unfinished home the user has asked to throw away, waiting on the confirmation. Throwing one
    // away cannot be undone, so it is never a single tap.
    var discarding by remember { mutableStateOf<SavedDraft?>(null) }
    // The unreadable home the user has asked to remove — same rule: destructive, so never one tap.
    var removing by remember { mutableStateOf<UnreadableHome?>(null) }

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
        //
        // A real CARD, like its sibling notice (the score-change card), not a bare line of accent
        // text — it was the weakest-contrast thing on the screen carrying its heaviest news (audit
        // C11). Each home is named — the name column still reads when the contents don't — and each
        // gets its own remove (audit B7: this card was a permanent dead end; the only delete in the
        // app took ALL data with it). Removing is behind an are-you-sure that states the real
        // price, because the row was being kept precisely so a later build could rescue it.
        if (unreadable.isNotEmpty()) {
            val one = unreadable.size == 1
            VastuCard(accent = colors.warning) {
                VText(
                    if (one) "1 home can't be opened" else "${unreadable.size} homes can't be opened",
                    style = VastuTheme.type.h3, color = colors.textPrimary,
                )
                Spacer(Modifier.height(VastuTheme.spacing.s2))
                VText(
                    (if (one) "It's still on your phone — nothing has been deleted. " else "They're still on your phone — nothing has been deleted. ") +
                        "An app update may open ${if (one) "it" else "them"} again.",
                    style = VastuTheme.type.body, color = colors.textSecondary,
                )
                unreadable.forEach { home ->
                    Spacer(Modifier.height(VastuTheme.spacing.s2))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        VText(
                            "${home.name} · ${relativeUpdated(home.updatedAt, now)}",
                            style = VastuTheme.type.label, color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        IconTapButton(
                            glyph = "✕",
                            contentDescription = "Remove ${home.name}",
                            onClick = { removing = home },
                        )
                        // textSecondary, not the accent, for the same measured contrast reason as
                        // the draft row's "Carry on" (3.64 : 1 on this surface, 4.5 required).
                        VText("Remove", style = VastuTheme.type.label, color = colors.textSecondary)
                    }
                }
            }
            Spacer(Modifier.height(VastuTheme.spacing.s3))
        }

        if (plans.isEmpty() && drafts.isEmpty()) {
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
                // The unfinished homes, first and labelled. A heading rather than a different colour:
                // "still to finish" is a fact about the row, and a word says it to everybody —
                // including a screen reader and somebody reading in daylight.
                if (drafts.isNotEmpty()) {
                    item(key = "drafts-header") { SectionLabel("Still to finish") }
                    items(drafts, key = { "draft:${it.id}" }) { draft ->
                        DraftRow(
                            draft = draft, now = now,
                            onOpen = { onOpenDraft(draft.id) },
                            onDiscard = { discarding = draft },
                        )
                    }
                    if (plans.isNotEmpty()) item(key = "plans-header") { SectionLabel("Finished") }
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
                // ⭐ EVERY name on this screen, not only the finished homes. A home this build cannot
                // open is listed by name in the card above, and an unfinished one is listed by name
                // in "Still to finish" — so a name matching either of those makes two rows on THIS
                // screen read identically, which is the whole defect.
                otherNames = plans.filter { it.id != plan.id }.map { it.name } +
                    unreadable.map { it.name } +
                    drafts.mapNotNull { it.draft.name },
            )
        }
    }

    discarding?.let { draft ->
        Dialog(onDismissRequest = { discarding = null }) {
            DiscardDraftDialogContent(
                roomCount = draft.roomCount,
                onCancel = { discarding = null },
                onDiscard = { onDiscardDraft(draft.id); discarding = null },
            )
        }
    }

    removing?.let { home ->
        Dialog(onDismissRequest = { removing = null }) {
            RemoveUnreadableDialogContent(
                name = home.name,
                onCancel = { removing = null },
                onRemove = { onRemoveUnreadable(home.id); removing = null },
            )
        }
    }
}

/**
 * "Remove this home for good?" — its own composable so the screenshot harness can render it without
 * a live Dialog window, exactly as the rename and discard boxes are.
 *
 * ⚠ It names the true price, which is different from the discard dialog's. An unfinished home loses
 * rooms the user can see; this row's contents are invisible, and the thing being given up is the
 * CHANCE of a future rescue. Someone should decline this dialog knowing exactly that.
 */
@Composable
fun RemoveUnreadableDialogContent(
    name: String,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = VastuTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.lg)
            .background(colors.paper)
            .padding(VastuTheme.spacing.s6),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s4),
    ) {
        VText("Remove $name for good?", style = VastuTheme.type.h3, color = colors.textPrimary)
        VText(
            "This home can't be opened, so we can't show you what's in it. Removing it deletes " +
                "it from your phone — a future app update won't be able to bring it back. " +
                "Your other homes aren't affected.",
            style = VastuTheme.type.body, color = colors.textSecondary,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            VastuButton(
                "Keep it",
                onClick = onCancel,
                large = false,
                modifier = Modifier.weight(1f),
            )
            VastuButton(
                "Remove it",
                onClick = onRemove,
                style = VastuButtonStyle.SECONDARY,
                large = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One home that was started and never finished.
 *
 * ⚠ It shows NO score, on purpose. Nothing here has been through the engine — putting a number on it
 * would be inventing one, and this app's whole promise is that it does not do that. What it shows
 * instead is the one true thing: how much is on the grid, and when it was last touched.
 */
@Composable
private fun DraftRow(draft: SavedDraft, now: Long, onOpen: () -> Unit, onDiscard: () -> Unit) {
    val colors = VastuTheme.colors
    val rooms = draft.roomCount
    VastuListRow(
        title = draft.draft.name ?: "Unfinished home",
        subtitle = "$rooms ${if (rooms == 1) "room" else "rooms"} so far · ${relativeUpdated(draft.updatedAt, now)}",
        modifier = Modifier.clickableTap(
            role = Role.Button,
            onClickLabel = "carry on with this home",
            onClick = onOpen,
        ),
        trailing = {
            // A child tap target, like the pencil on a finished home: it consumes its own tap, so
            // this throws the home away while the rest of the row carries on with it.
            IconTapButton(
                glyph = "✕",
                contentDescription = "Throw away this unfinished home",
                onClick = onDiscard,
            )
            // ⚠ textSecondary, not the sage accent. Measured on this card the accent is 3.64 : 1
            // where 4.5 is required at this size — the same defect the scan screen's CHECK pill
            // shipped with. The "this is a control" signal is carried by the label type style.
            VText("Carry on", style = VastuTheme.type.label, color = colors.textSecondary)
        },
    )
}

/**
 * "Throw this away?" — its own composable so the screenshot harness can render it without a live
 * Dialog window, exactly as the rename box is.
 *
 * ⚠ It names what is being lost. "Are you sure?" tells somebody nothing; "the 5 rooms you placed"
 * tells them whether they mind, and this is the only destructive action on the screen.
 */
@Composable
fun DiscardDraftDialogContent(
    roomCount: Int,
    onCancel: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = VastuTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.lg)
            .background(colors.paper)
            .padding(VastuTheme.spacing.s6),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s4),
    ) {
        VText("Throw away this unfinished home?", style = VastuTheme.type.h3, color = colors.textPrimary)
        VText(
            "The $roomCount ${if (roomCount == 1) "room" else "rooms"} you placed will be gone, " +
                "and we can't bring them back. Your finished homes aren't affected.",
            style = VastuTheme.type.body, color = colors.textSecondary,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            VastuButton(
                "Keep it",
                onClick = onCancel,
                large = false,
                modifier = Modifier.weight(1f),
            )
            VastuButton(
                "Throw it away",
                onClick = onDiscard,
                style = VastuButtonStyle.SECONDARY,
                large = false,
                modifier = Modifier.weight(1f),
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
        // The user's own numbers come BEFORE the explanation (audit C12): at 200 % font this card
        // fills the whole first screenful, and with the reason first the fold cut the card off
        // mid-paragraph with the numbers below it — the one part that answers "what happened to MY
        // home". Numbers under the heading keep that answer on screen at every font size; the why
        // reads on below it.
        notice.changes.forEach { change ->
            VText(
                scoreChangeLine(change) { scoreOutOfTen(it, mark) },
                style = VastuTheme.type.label,
                color = colors.textPrimary,
            )
        }
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VText(notice.reason, style = VastuTheme.type.body, color = colors.textSecondary)
        // s3 rather than s4, measured: at 200 % font the button was showing 33 dp of its 50.5 and
        // losing the rest to the bottom of the list. Every dp above it is a dp of button.
        Spacer(Modifier.height(VastuTheme.spacing.s3))
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
            // ⭐ LOCKED to left-to-right, same reason as the compass and the report's own score:
            // "/10" is a fraction, and a slash before digits is neutral to the bidi algorithm, so
            // under an RTL locale it was drawn as "10/" — a broken number under the score.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(horizontalAlignment = Alignment.End) {
                    VText(scoreOutOfTen(plan.score, mark), style = VastuTheme.type.h2, color = scoreBandColor(plan.score))
                    VText("/10", style = VastuTheme.type.caption, color = colors.textTertiary)
                }
            }
        },
    )
}

/**
 * The rename box (its own composable so the screenshot harness can render it without a live Dialog
 * window / Activity). Pre-filled with the current name; Save is disabled until the name is non-blank.
 *
 * ⭐ AND UNTIL THE NAME IS NOT ALREADY IN USE. Two homes could be given the same name, and the list
 * above then showed two rows a person cannot tell apart — so opening one, or deleting one, was a
 * coin flip on somebody's own saved work. The name is the ONLY thing distinguishing two rows, which
 * is why this is refused at the point of typing rather than patched up afterwards with a number the
 * user did not choose.
 *
 * ⚠ Refused OUT LOUD. A greyed-out button with no sentence next to it is the same silence, dressed
 * differently: the user retypes the same name and gets the same nothing.
 */
@Composable
fun RenameDialogContent(
    currentName: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
    /**
     * Every OTHER name already on the saved-homes screen — finished homes, homes this build cannot
     * open, and unfinished ones. The home being renamed is not in here, so keeping its own name
     * (or only changing its capitals) is always allowed.
     */
    otherNames: List<String> = emptyList(),
) {
    val colors = VastuTheme.colors
    var text by remember(currentName) { mutableStateOf(currentName) }
    // ⚠ The SAME comparison the auto-namer uses, imported rather than re-written. Two definitions of
    // "the same name" is how the box refuses a name the app itself would go on to mint.
    val taken = homeNameAlreadyTaken(text, otherNames)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.lg)
            .background(colors.paper)
            .padding(VastuTheme.spacing.s6),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s4),
    ) {
        VText("Rename this home", style = VastuTheme.type.h3, color = colors.textPrimary)
        // ⚠ The field's OWN error slot, not a line of red text underneath it. The design system
        // already owns this state: it reddens the border and the label as well as printing the
        // sentence, all from tokens whose contrast is already declared — so the message cannot be
        // missed by someone who is looking at the box they are typing in rather than below it.
        VastuTextField(
            value = text,
            onValueChange = { text = it },
            label = "Home name",
            placeholder = "e.g. Dwarka flat",
            error = if (taken) "You already have a home called this. Please pick a different name." else null,
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
                enabled = text.isNotBlank() && !taken,
                large = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
