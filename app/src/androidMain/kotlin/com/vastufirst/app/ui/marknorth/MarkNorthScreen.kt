package com.vastufirst.app.ui.marknorth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vastufirst.app.ui.common.buildZoneMapModel
import com.vastufirst.app.ui.newplan.GRID
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.shared.Analysis
import com.vastufirst.designsystem.components.NorthDial
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonInline
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.VastuCard
import com.vastufirst.designsystem.components.VastuChip
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.app.ui.common.screenRoot
import kotlin.math.roundToInt

/**
 * Mark North (§6.3 · VastuCompass.dc.html) — the signature screen. Drag the dial, use the slider,
 * the N/E/S/W chips, or type the degree. The centre stays clean and there is NO "best angle"
 * affordance (§0.7).
 *
 * ⛔ TWO THINGS WERE REMOVED ON 10 AUG 2026 (owner) AND MUST NOT COME BACK.
 *
 *  · **The live score.** A number that moved as the dial turned turned this screen into a search for
 *    the best-scoring North, which is exactly the affordance §0.7 forbids. The engine still recomputes
 *    as the dial moves — that is what keeps the "check this before we score" card honest — the number
 *    is simply not shown.
 *  · **"Use my phone's compass."** ⚠ Removing it has a real cost and it is stated here rather than
 *    forgotten: it was by far the easiest route for someone who does not already know which way their
 *    home faces, and a wrong North silently moves every room in the report. Everyone now sets North by
 *    hand. The double-check card below the dial is what stands in its place, and it matters more now.
 */
@Composable
fun MarkNorthScreen(
    vm: NewPlanViewModel,
    onRead: () -> Unit,
    onBack: () -> Unit,
    /**
     * ⭐ The user's own scanned plan, when they arrived here from a scan (owner, 6 Aug 2026). It sits
     * in the dial in place of our redrawn rooms; North still turns the ring around it, exactly as it
     * always has, because the plan under the ring has never rotated.
     */
    planImage: ImageBitmap? = null,
    /** See [MarkNorthContent.nextIsCheck] — true on the scan path, where the check screen is next. */
    nextIsCheck: Boolean = false,
) {
    // Thin wrapper: the ONLY thing that touches the ViewModel, so the screen renders headlessly from
    // fixture state (rooms + north + a live Analysis) in the screenshot harness (UI-POLISH §6).
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    MarkNorthContent(
        rooms = vm.rooms,
        north = vm.north,
        analysis = analysis,
        onNorthChange = vm::updateNorth,
        onRead = onRead,
        nextIsCheck = nextIsCheck,
        onBack = onBack,
        // The real screen nudges; the harness never does. See [MarkNorthContent.hintPulse].
        hintPulse = true,
        cols = vm.gridCols,
        rows = vm.gridRows,
        planImage = planImage,
    )
}

/** Mark North as a pure function of its state — no ViewModel — so the render harness can draw it. */
@Composable
fun MarkNorthContent(
    rooms: List<GridRoom>,
    north: Int,
    analysis: Analysis?,
    onNorthChange: (Int) -> Unit,
    onRead: () -> Unit,
    onBack: () -> Unit,
    cols: Int = GRID,
    rows: Int = GRID,
    planImage: ImageBitmap? = null,
    /**
     * ⚠⚠ DEFAULT OFF, AND THE HARNESS MUST NEVER TURN IT ON. The nudge is an INFINITE animation, and
     * an infinite animation never lets a composition go idle — the screenshot harness waits for idle
     * before it photographs, so a screen with one running is a screen the harness waits on forever.
     * It hung a whole cloud build for forty minutes on 10 Aug 2026, mid-way through re-recording
     * every golden, with no error of any kind: just a step that never finished.
     *
     * The real screen turns it on; every test leaves it off. Same rule as the report's reading
     * animation, which was written this way from the start and is why that one did not hang.
     */
    hintPulse: Boolean = false,
    /**
     * ⭐ TRUE when this screen is followed by "Check what we read" rather than by the report — which
     * is the whole scan path since 11 Aug 2026. North had to move in front of the check screen so
     * that its rows could show each room's direction and one-word result; there is no direction
     * before there is a North.
     *
     * It changes nothing but the words on the button, and it has to: a button reading "read my home"
     * on a screen that opens a checklist is a control naming a screen it does not open, which this
     * project has already logged as a defect twice.
     */
    nextIsCheck: Boolean = false,
) {
    val colors = VastuTheme.colors
    val model = buildZoneMapModel(rooms, analysis, north, cols, rows, planImage)

    Column(
        modifier = Modifier.screenRoot(colors.paper).verticalScroll(rememberScrollState()).padding(VastuTheme.spacing.s6),
    ) {
        // ⛔ No "Step 2 of 3" — see the note on AddHomeScreen. On the scan path this screen is
        // followed by the checking screen and sometimes the front-door screen, so calling it the
        // second of three told the reader they were one screen from the end when they were two or
        // three. Reordering the flow made a wrong number wronger; removing it makes it true.
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText("Which way is North?", style = VastuTheme.type.h2, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText("Drag the N dial, or use the slider. Everything else follows from this.", style = VastuTheme.type.body, color = colors.textSecondary)
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        // ⭐ The nudge runs until the reader first moves North, then never again in this session.
        var touchedNorth by rememberSaveable { mutableStateOf(false) }
        NorthDial(
            model = model,
            onNorthChange = { touchedNorth = true; onNorthChange(it) },
            hintPulse = hintPulse && !touchedNorth,
            contentDescription = "Floor plan compass. North at $north degrees. Drag to set North.",
        )
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        // ⛔ THE COLOUR KEY IS GONE (owner, 17 Aug 2026: *"Remove 'Ideal, Fine, Not Ideal, Defect'
        // line"*). It explained four verdict colours on a screen that scores nothing and asks one
        // question, and under a photographed plan it explained colours that are not even drawn. The
        // report still names every verdict in words beside its own colour, which is where a reader
        // meets them for a reason.
        com.vastufirst.designsystem.components.VastuSlider(
            value = north.toFloat(),
            onValueChange = { onNorthChange(it.roundToInt()) },
            valueRange = 0f..359f,
            contentDescription = "North bearing, $north degrees",
        )
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        // ⛔ THE LIVE SCORE IS GONE (owner, 10 Aug 2026: "Dont show LIVE score") — do not put it back.
        // A number that moved as the dial turned invited the reader to hunt for the North that scored
        // best, which is precisely what Product PRD §0.7 forbids this screen from offering. The
        // bearing itself stays: it is what the reader is actually setting.
        Row(
            modifier = Modifier.fillMaxWidth().clip(VastuTheme.shapes.md).background(colors.surface).padding(VastuTheme.spacing.s4),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            VText("North is at", style = VastuTheme.type.body, color = colors.textTertiary)
            VText("$north°", style = VastuTheme.type.mono, color = colors.textPrimary)
        }
        Spacer(Modifier.height(VastuTheme.spacing.s3))

        // Quick chips.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
            listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270).forEach { (label, deg) ->
                Box(Modifier.weight(1f)) {
                    VastuChip(text = label, selected = north == deg, onClick = { onNorthChange(deg) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        // ⛔ THE DEGREE STEPPER IS GONE (owner, 17 Aug 2026: *"Remove the small section that show the
        // number and degree of North and has Up and Down button. This is already achieved with
        // rotating dial and scroll bar"*). Three controls set one number; two of them are the dial
        // and the slider, and both are adjustable by TalkBack in their own right — which is what the
        // a11y contract actually needed, and it is untouched. The plain "North is at N°" readout
        // above stays: that is the answer, not a fourth way to change it.

        Spacer(Modifier.height(VastuTheme.spacing.s6))

        // ⭐ THE DOUBLE-CHECK. "Are you sure your North is right?" is a question nobody can answer.
        // Where your own kitchen is, is. So the card states what this North MEANS, in the report's own
        // words, and the button that continues is the one that agrees with it — no extra tap, and the
        // user cannot walk past it without having read the claim.
        //
        // ⭐⭐ REWRITTEN 17 AUG 2026 (owner: *"all of the text in this box needs to get better and
        // simpler and easier to understand"*). Three things changed and each one is the same idea:
        // ask a question a person standing in their kitchen can answer.
        //   · The heading was "Check this before we score" — our word, our process, and it named
        //     scoring, which is not what the reader is being asked about.
        //   · The claims were welded into ONE sentence with commas and an "and". Three facts in one
        //     line are three things to hold at once; as separate lines each is a thing you either
        //     agree with or do not.
        //   · "Not right? Turn the dial until it is" told them the control. It now tells them what
        //     to DO — look around the room they are in — which is the only way to answer it.
        val claims = NorthCheck.claims(analysis)
        if (claims.isNotEmpty()) {
            VastuCard(accent = colors.primary) {
                VText("Is this right?", style = VastuTheme.type.h3, color = colors.textPrimary)
                Spacer(Modifier.height(VastuTheme.spacing.s2))
                VText(
                    "With North where you have put it, we read your home like this:",
                    style = VastuTheme.type.body, color = colors.textPrimary,
                )
                Spacer(Modifier.height(VastuTheme.spacing.s2))
                claims.forEach { claim ->
                    VText("· $claim", style = VastuTheme.type.body, color = colors.textPrimary)
                }
                Spacer(Modifier.height(VastuTheme.spacing.s2))
                VText(
                    "Look around your home. If a line is wrong, turn the dial until it is right.",
                    style = VastuTheme.type.bodySm, color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(VastuTheme.spacing.s4))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            VastuButtonInline("Back", onClick = onBack, style = VastuButtonStyle.SECONDARY)
            VastuButton(
                when {
                    // ⚠ "what WE read", word for word the heading of the screen this opens. The
                    // whole point of this flag is that the button names its destination, and "what
                    // you read" named a different screen — one where the reader does the reading.
                    nextIsCheck && claims.isNotEmpty() -> "Yes — check what we read"
                    nextIsCheck -> "Check what we read"
                    claims.isNotEmpty() -> "Yes — read my home"
                    else -> "Read my home"
                },
                onClick = onRead,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(VastuTheme.spacing.s4))
    }
}

// ⛔ `Legend` and `LegendItem` were deleted on 17 Aug 2026 with the colour key they drew. Do not
// bring them back here: this screen asks one question and scores nothing, so a key to four verdict
// colours belonged to a screen that shows verdicts. The report labels every verdict in words beside
// its own colour, which is the rule (colour is never the only carrier) and the right place for it.
