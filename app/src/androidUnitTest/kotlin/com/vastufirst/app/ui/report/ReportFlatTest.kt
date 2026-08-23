package com.vastufirst.app.ui.report

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.app.render.RenderFixtures
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.Defect
import com.vastufirst.shared.Intent
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.Provenance
import com.vastufirst.shared.Severity
import com.vastufirst.shared.Zone
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐⭐ A FLAT IS NOT TOLD TO MOVE THE BUILDING, pinned (23 August 2026).
 *
 * Product PRD §7.4: *"a flat owner cannot move the building gate, the lift, the shafts or the
 * shared walls… report copy must reflect this rather than suggesting impossible fixes."* Until this
 * release no screen ever asked whether a home was a flat, so every home in the product was read as
 * an independent house and the report cheerfully told flat owners to *"Extend the footprint to
 * square off the North-East corner"*.
 *
 * ⚠ WHY THIS IS A SEPARATE TEST FROM [ReportIntentTest], and not a case added to it. Intent asks
 * *can this reader move a wall at all* — no, once the home is standing. This asks *is this
 * particular wall even theirs* — no, if it is the building's, however early they are. The two
 * overlap for a buyer and a resident and come apart for exactly the case neither covered: somebody
 * **BUILDING**, choosing a flat off-plan, who can still pick their own kitchen and can never square
 * off the tower's corner. Every test below therefore runs on `Intent.BUILDING`, the one branch that
 * still offers layout advice — anything else would pass without proving a thing.
 *
 * ⚠ AND IT BUILDS ITS OWN FINDING RATHER THAN USING THE BUNDLED SAMPLE'S. The sample home is a
 * rectangle drawn on a grid with no site answers, so it raises **none** of the seven
 * building-owned findings — no missing corner, no bulge, no tank, no road. Trusting it would give
 * a green test that never rendered the branch once. This is the same trap that hid the "Start here"
 * revenue leak for weeks (the sample's worst defect happens to be a free room), so it is defeated
 * the same way: by constructing the case deliberately.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class ReportFlatTest {

    /** Word for word the advice the real ruleset attaches to a missing North-East corner. */
    private val buildingFix = "Extend the footprint to square off the North-East corner"

    /** The reasoning, which must survive into the flat report untouched — see CLAUDE.md §2h. */
    private val buildingWhy = "The plan is missing part of its North-East corner"

    /**
     * A real building-owned finding: no room behind it, so it lands in the structural section, and
     * paid, so [unlocked] has to be true or the paywall would be the reason the fix is missing.
     */
    private val buildingDefect = Defect(
        id = "X-04",
        severity = Severity.MAJOR,
        zone = Zone.NE,
        roomId = null,
        ruleSourceId = "X-04",
        provenance = Provenance.DERIV,
        explanation = "$buildingWhy, the ground the tradition values most.",
        layoutFix = "$buildingFix while the plan is drawn.",
        belongsToBuilding = true,
    )

    private fun report(type: PropertyType): Analysis = RenderFixtures.sampleAnalysis.copy(
        propertyType = type,
        // First, so it is also what the "Start here" card above the fold reaches for.
        defects = listOf(buildingDefect) + RenderFixtures.sampleAnalysis.defects,
    )

    private fun androidx.compose.ui.test.ComposeUiTest.show(type: PropertyType) = setContent {
        VastuTheme {
            ReportContent(
                analysis = report(type),
                intent = Intent.BUILDING,
                unlocked = true,
                expandAll = true,
            )
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.count(text: String) =
        onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().size

    /**
     * ⭐ THE GUARD ON THE GUARD. Every assertion below is that something is ABSENT for a flat, and
     * an absent-only test passes just as happily when the card stopped rendering, the fixture lost
     * its finding, or the string was reworded. This proves the same report, at the same intent,
     * really does print that sentence for a house.
     */
    @Test
    fun a_house_is_still_told_to_square_off_its_own_corner() = runComposeUiTest {
        show(PropertyType.INDEPENDENT_HOUSE)
        assertTrue(
            "a HOUSE that is still being built must keep its layout advice — if this fails, every " +
                "other test in this file is passing for the wrong reason",
            count(buildingFix) > 0,
        )
    }

    /** The defect this whole release exists to fix. */
    @Test
    fun a_flat_is_never_told_to_move_the_building() = runComposeUiTest {
        show(PropertyType.FLAT)
        assertTrue(
            "\"$buildingFix\" must not appear anywhere in a FLAT's report — the shell is not theirs " +
                "to move, and they are choosing off-plan, so the intent branch does not catch it",
            count(buildingFix) == 0,
        )
    }

    /** Withholding advice silently would be worse than giving it. The reader is told whose it is. */
    @Test
    fun a_flat_is_told_whose_the_thing_actually_is() = runComposeUiTest {
        show(PropertyType.FLAT)
        onNodeWithText("belongs to the whole building", substring = true).assertExists()
    }

    /**
     * ⭐⭐ NOTHING IS DELETED (CLAUDE.md §2h). The finding, its reasoning and its rank are all
     * exactly what a house sees; only the one impossible instruction is replaced.
     */
    @Test
    fun a_flat_still_reads_the_finding_itself_in_full() = runComposeUiTest {
        show(PropertyType.FLAT)
        assertTrue(
            "a flat must still be TOLD about the missing corner — this change withholds a fix, " +
                "never a finding",
            count(buildingWhy) > 0,
        )
    }

    /** The reader can see which of the two readings they were given, and correct it if it is wrong. */
    @Test
    fun a_flat_report_says_on_its_face_that_it_is_a_flat() = runComposeUiTest {
        show(PropertyType.FLAT)
        // ⚠ EXACT, NOT A SUBSTRING. "flat" ignoring case appears inside several of this release's
        // own new sentences ("not to your flat", "Inside the flat…"), so a substring match would go
        // green with the badge deleted. Only the pill renders the bare word.
        assertTrue(
            "the report must show a FLAT pill beside the intent pill, so the reader can see which " +
                "of the two readings they were given",
            onAllNodesWithText("FLAT", substring = false, ignoreCase = false)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /**
     * The headline sentence, which is the one thing every reader sees. "Nothing is built yet — the
     * layout is still yours" is a straight untruth about a tower.
     */
    @Test
    fun a_flat_is_not_promised_the_whole_layout_is_still_theirs() = runComposeUiTest {
        show(PropertyType.FLAT)
        assertTrue(
            "the building-branch headline must not tell a flat buyer nothing is built yet",
            count("Nothing is built yet") == 0,
        )
        onNodeWithText("the building around it is not", substring = true).assertExists()
    }

    /**
     * ⚠ The other half of the sentence above, and the reason it is not simply the buyer's copy. A
     * flat chosen off-plan CAN still move its own kitchen, and telling that reader they may only
     * have remedies would sell them short in the opposite direction.
     */
    @Test
    fun a_flat_being_built_is_still_offered_what_it_can_change() = runComposeUiTest {
        show(PropertyType.FLAT)
        assertTrue(
            "a flat is not a home that is already standing — it must not be handed the buyer's " +
                "remedies-only sentence",
            count("without moving a wall") == 0,
        )
        onNodeWithText("Inside the flat the layout is still yours", substring = true).assertExists()
    }
}
