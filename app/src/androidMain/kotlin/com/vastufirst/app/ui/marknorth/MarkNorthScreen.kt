package com.vastufirst.app.ui.marknorth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
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
import com.vastufirst.designsystem.components.VastuInfoLine
import com.vastufirst.designsystem.components.dialAspectFor
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.app.ui.common.screenRoot
import kotlin.math.roundToInt

/**
 * Mark North (§6.3 · VastuCompass.dc.html) — the signature screen. Drag the dial, use the slider, or
 * the N/E/S/W chips. The centre stays clean and there is NO "best angle" affordance (§0.7).
 *
 * ⭐ WHERE IT IS MET, since 30 Sep 2026. A home drawn by hand, the sample, "Change North" on a report,
 * and "Carry on" with an unfinished scanned home all come here. A FIRST SCAN does not: its North is set
 * on the scan result itself, under "We read N rooms", with the very same [NorthControls] — so a read
 * home reaches its report one screen sooner (owner, 30 Sep 2026: *"let the user get to the point
 * quickly"*).
 *
 * ⛔ TWO THINGS WERE REMOVED ON 10 AUG 2026 (owner) AND MUST NOT COME BACK.
 *
 *  · **The live score.** A number that moved as the dial turned turned this screen into a search for
 *    the best-scoring North, which is exactly the affordance §0.7 forbids.
 *  · **"Use my phone's compass."** ⚠ Removing it has a real cost and it is stated here rather than
 *    forgotten: it was the easiest route for someone who does not already know which way their home
 *    faces, and a wrong North silently moves every room in the report. The "Is this right?" card is
 *    what stands in its place.
 */
@Composable
fun MarkNorthScreen(
    vm: NewPlanViewModel,
    onRead: () -> Unit,
    onBack: () -> Unit,
    /** The user's own scanned plan, when this home was read off one. It sits inside the dial. */
    planImage: ImageBitmap? = null,
    /** See [MarkNorthContent.returnsToReport]. */
    returnsToReport: Boolean = false,
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
        onBack = onBack,
        // The real screen nudges; the harness never does. See [NorthControls.hintPulse].
        hintPulse = true,
        cols = vm.gridCols,
        rows = vm.gridRows,
        planImage = planImage,
        returnsToReport = returnsToReport,
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
    /** ⚠ DEFAULT OFF, AND THE HARNESS MUST NEVER TURN IT ON — see [NorthControls]. */
    hintPulse: Boolean = false,
    /**
     * TRUE when this was opened from a finished report ("Change North"). Nothing about the dial
     * changes — only the button, which then hands the reader back to that report and says so. A
     * button naming a screen it does not open is a defect this project has logged more than once.
     */
    returnsToReport: Boolean = false,
) {
    val colors = VastuTheme.colors
    BoxWithConstraints(Modifier.screenRoot(colors.paper)) {
        val dialCap = maxHeight * NORTH_DIAL_SCREEN_SHARE
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(VastuTheme.spacing.s6)) {
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText("Which way is North?", style = VastuTheme.type.h2, color = colors.textPrimary)
            Spacer(Modifier.height(VastuTheme.spacing.s1))
            NorthInstruction()
            Spacer(Modifier.height(VastuTheme.spacing.s3))

            NorthControls(
                rooms = rooms, north = north, analysis = analysis, onNorthChange = onNorthChange,
                cols = cols, rows = rows, planImage = planImage, hintPulse = hintPulse, maxDialHeight = dialCap,
            )
            // The button answers the card's question when there is a card — see [NorthControls].
            val claims = NorthCheck.claims(analysis).isNotEmpty()

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                VastuButtonInline("Back", onClick = onBack, style = VastuButtonStyle.SECONDARY)
                VastuButton(
                    when {
                        returnsToReport && claims -> "Yes — back to my report"
                        returnsToReport -> "Back to my report"
                        claims -> "Yes — read my home"
                        else -> "Read my home"
                    },
                    onClick = onRead,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(VastuTheme.spacing.s4))
        }
    }
}

/**
 * The one line of instruction above the dial, with its reasons behind the **i** (owner's standing
 * rule: a sentence that explains rather than answers goes behind a small i — moved, never deleted).
 * Shared by this screen and the scan result, which ask the same question.
 */
@Composable
fun NorthInstruction() {
    VastuInfoLine(
        label = "Turn the dial so N points North on your plan.",
        info = "Use your plan's North arrow if it has one. The slider and the N, E, S, W buttons " +
            "do the same. Every direction in your report follows from this.",
        tag = "north.help",
    )
}

/**
 * ⭐⭐ THE NORTH CONTROLS — the dial on the plan, the slider, the bearing, the four quick chips and the
 * "Is this right?" card. ONE composable, drawn by this screen and by the scan result, so the two
 * places a reader sets North cannot drift apart (CLAUDE.md §2g).
 *
 * The caller's button answers the card's question when there is a card ("Yes — …"); it asks
 * [NorthCheck.claims] the same question this does, so the two cannot disagree.
 *
 * [maxDialHeight] is the most of the screen the dial may take. Sized by width alone, the dial on a
 * phone turned sideways was about 806 dp tall on a 480 dp screen — its centre and lower half below the
 * bottom edge while the reader was turning it. The cap is far above the width in portrait and changes
 * nothing there.
 */
@Composable
fun NorthControls(
    rooms: List<GridRoom>,
    north: Int,
    analysis: Analysis?,
    onNorthChange: (Int) -> Unit,
    cols: Int = GRID,
    rows: Int = GRID,
    planImage: ImageBitmap? = null,
    /**
     * ⚠⚠ DEFAULT OFF, AND THE HARNESS MUST NEVER TURN IT ON. The nudge is an INFINITE animation, and
     * an infinite animation never lets a composition go idle — the screenshot harness waits for idle
     * before it photographs. It hung a whole cloud build for forty minutes on 10 Aug 2026.
     */
    hintPulse: Boolean = false,
    maxDialHeight: Dp = Dp.Unspecified,
) {
    val colors = VastuTheme.colors
    val model = buildZoneMapModel(rooms, analysis, north, cols, rows, planImage)
    val aspect = planImage?.let { dialAspectFor(it.width, it.height) } ?: 1f

    // ⭐ The nudge runs until the reader first moves North, then never again in this session.
    var touchedNorth by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val dialWidth = if (maxDialHeight == Dp.Unspecified) maxWidth else minOf(maxWidth, maxDialHeight * aspect)
        NorthDial(
            model = model,
            onNorthChange = { touchedNorth = true; onNorthChange(it) },
            hintPulse = hintPulse && !touchedNorth,
            contentDescription = "Floor plan compass. North at $north degrees. Drag to set North.",
            modifier = Modifier.width(dialWidth),
        )
    }
    Spacer(Modifier.height(VastuTheme.spacing.s4))

    // ⛔ THE COLOUR KEY IS GONE (owner, 17 Aug 2026). The report names every verdict in words beside
    // its own colour, which is where a reader meets them for a reason.
    com.vastufirst.designsystem.components.VastuSlider(
        value = north.toFloat(),
        onValueChange = { onNorthChange(it.roundToInt()) },
        valueRange = 0f..359f,
        contentDescription = "North bearing, $north degrees",
    )
    Spacer(Modifier.height(VastuTheme.spacing.s4))

    // ⛔ THE LIVE SCORE IS GONE (owner, 10 Aug 2026: "Dont show LIVE score") — do not put it back.
    // The bearing itself stays: it is what the reader is actually setting.
    Row(
        modifier = Modifier.fillMaxWidth().clip(VastuTheme.shapes.md).background(colors.surface).padding(VastuTheme.spacing.s4),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        VText("North is at", style = VastuTheme.type.body, color = colors.textTertiary)
        VText("$north°", style = VastuTheme.type.mono, color = colors.textPrimary)
    }
    Spacer(Modifier.height(VastuTheme.spacing.s3))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
        listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270).forEach { (label, deg) ->
            Box(Modifier.weight(1f)) {
                VastuChip(text = label, selected = north == deg, onClick = { onNorthChange(deg) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    // ⛔ THE DEGREE STEPPER IS GONE (owner, 17 Aug 2026). The dial and the slider both set the number,
    // and both are adjustable by TalkBack in their own right.

    Spacer(Modifier.height(VastuTheme.spacing.s6))

    // ⭐ THE DOUBLE-CHECK. "Are you sure your North is right?" is a question nobody can answer. Where
    // your own kitchen is, is. So the card states what this North MEANS, in the report's own words,
    // and the button that continues is the one that agrees with it.
    //
    // ⭐ Since 30 Sep 2026 the two sentences that explained the card are behind its **i**; the claims
    // themselves — the part a reader answers — stay on the page.
    val claims = NorthCheck.claims(analysis)
    if (claims.isNotEmpty()) {
        VastuCard(accent = colors.primary) {
            VastuInfoLine(
                label = "Is this right?",
                info = "With North where you have put it, we read your home like this. Look around " +
                    "your home: if a line is wrong, turn the dial until it is right.",
                style = VastuTheme.type.h3,
                color = colors.textPrimary,
                tag = "north.check",
            )
            Spacer(Modifier.height(VastuTheme.spacing.s1))
            claims.forEach { claim ->
                VText("· $claim", style = VastuTheme.type.body, color = colors.textPrimary)
            }
        }
        Spacer(Modifier.height(VastuTheme.spacing.s4))
    }
}

/**
 * The most of the screen's height the dial may take. Chosen so that on a landscape phone (480 dp tall)
 * the heading, the one-line instruction and the WHOLE dial are on the first screenful together.
 */
const val NORTH_DIAL_SCREEN_SHARE = 0.66f

// ⛔ `Legend` and `LegendItem` were deleted on 17 Aug 2026 with the colour key they drew. Do not bring
// them back here: this screen asks one question and scores nothing.
