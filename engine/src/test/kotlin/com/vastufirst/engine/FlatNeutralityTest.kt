package com.vastufirst.engine

import com.vastufirst.rules.RuleSetLoader
import com.vastufirst.shared.Door
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.Room
import com.vastufirst.shared.RoomType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ⭐⭐ A FLAT SCORES EXACTLY WHAT A HOUSE SCORES — and carries the flag that changes the ADVICE.
 *
 * Product PRD §7.4 says the flat path must be honest about what a flat owner cannot move. It does
 * **not** say a flat scores differently, and inventing that would be answering open expert question
 * A-03 — *"does Vastu offer a flat buyer anything beyond 'choose a different flat'?"* — which nobody
 * has ruled on. A toilet in the North-East is precisely as much of a problem seven floors up.
 *
 * So the split is: the NUMBER is identical, and one boolean on seven findings tells the report which
 * advice it may offer. This pins both halves — the same shape as [IntentNeutralityTest], for the
 * same reason: `propertyType` travels into the engine and out again on the Analysis, so it would
 * cost one careless line to start weighting a rule by it, and a home scoring 3.1 as a house and 3.4
 * as a flat would look to everybody like an ordinary difference of opinion.
 */
class FlatNeutralityTest {

    private val ruleSet = RuleSetLoader.loadDefault()

    /**
     * ⭐ The building's own fabric — the seven a flat owner can never move, whoever they are.
     *
     * The shell's missing corner and its bulge and how long and narrow it is; the front door, which
     * in a flat is a hole in a structural wall shared with the lobby; the water tank and the sump,
     * which serve every flat in the tower; the road at the gate and the tree in the compound.
     */
    private val building = setOf("X-04", "X-05", "X-06", "X-09", "X-11", "X-12", "X-15")

    @Test
    fun `exactly the seven building-owned findings carry the flag`() {
        val flagged = ruleSet.defects.filter { it.belongsToBuilding }.map { it.id }.toSet()
        assertEquals(
            building, flagged,
            "the set that a flat owner cannot act on is a product decision, not an incidental one. " +
                "Adding a defect here withholds advice from every flat; removing one hands a flat " +
                "owner an instruction to move a building they own one floor of.",
        )
    }

    /**
     * ⚠ THE ONE THAT LOOKS LIKE IT BELONGS AND DOES NOT. A staircase on the centre of the home is
     * excluded on purpose: a duplex flat's internal stair really is the owner's, so flagging it
     * would withhold advice somebody can genuinely act on. When in doubt the finding keeps its fix.
     */
    @Test
    fun `a staircase on the centre is not treated as the building's`() {
        assertFalse(
            ruleSet.defects.first { it.id == "X-03" }.belongsToBuilding,
            "an internal staircase in a duplex flat IS the owner's — do not withhold its fix",
        )
    }

    /** Every flagged finding must actually HAVE a fix to withhold, or the flag is decorative. */
    @Test
    fun `every building-owned finding carries the layout advice it is withholding`() {
        for (d in ruleSet.defects.filter { it.belongsToBuilding }) {
            assertTrue(
                !d.layoutFix.isNullOrBlank(),
                "${d.id} is flagged as the building's but offers no layout change, so the flag " +
                    "changes nothing a reader would ever see",
            )
        }
    }

    @Test
    fun `the same home scores identically as a house and as a flat`() {
        val results = PropertyType.entries.associateWith { type ->
            VastuEngine().analyze(Fixtures.sample01().copy(propertyType = type))
        }
        val house = results.getValue(PropertyType.INDEPENDENT_HOUSE)

        for ((type, a) in results) {
            assertEquals(house.score, a.score, "the score must not depend on house-or-flat ($type)")
            assertEquals(house.base, a.base, "nor the base ($type)")
            assertEquals(house.defectPenalty, a.defectPenalty, "nor the penalty ($type)")
            assertEquals(house.quality, a.quality, "nor how confident we are ($type)")
            assertEquals(
                house.defects.map { it.id to it.zone }, a.defects.map { it.id to it.zone },
                "the same problems, in the same order — a flat is told everything a house is ($type)",
            )
            assertEquals(
                house.roomResults.map { it.roomId to it.verdict },
                a.roomResults.map { it.roomId to it.verdict },
                "and the same verdict on every room ($type)",
            )
            assertEquals(
                house.doorResult?.verdict, a.doorResult?.verdict,
                "and the same reading of the front door ($type)",
            )
        }
    }

    /** The report branches on this, so it has to survive the engine unaltered. */
    @Test
    fun `the answer is carried through to the report unchanged`() {
        for (type in PropertyType.entries) {
            val a = VastuEngine().analyze(Fixtures.sample01().copy(propertyType = type))
            assertEquals(type, a.propertyType, "the report branches on this — it must not be rewritten")
        }
    }

    /**
     * ⭐⭐ THE FLAG HAS TO REACH THE RUNTIME FINDING, not merely sit in the dataset — and there are
     * **two** separate places a [com.vastufirst.shared.Defect] is constructed.
     *
     * The detector builds almost all of them; the engine builds the long-and-narrow one itself,
     * inline. Setting the flag in only one is exactly the drift CLAUDE.md §2g is about, and it
     * would have been invisible: the shipped sample home is a rectangle and raises neither of the
     * shapes below, so nothing already in this suite would have gone red.
     *
     * ⚠ Both geometries are deliberate. **X-04** (a missing North-East corner) comes out of the
     * DETECTOR; **X-15** (long and narrow) comes out of the ENGINE. One test, both paths.
     */
    @Test
    fun `the flag survives onto the findings the report actually reads, from both builders`() {
        // ⚠ A real front door on the south wall of each shape. The engine's sanitizer treats a
        // doorless plan as a lesser read, and a fixture that quietly degrades would make both
        // assertions below vacuous rather than red.
        fun plan(outline: List<Point>, doorY: Double) = Plan(
            id = "f",
            propertyType = PropertyType.FLAT,
            intent = Intent.BUILDING,
            levels = listOf(
                Level(
                    index = 0,
                    outline = outline,
                    rooms = listOf(Room("m", RoomType.MASTER_BEDROOM, Fixtures.rect(4.0, 4.0, 20.0, 20.0))),
                    doors = listOf(
                        Door("d", Point(50.0, doorY), Point(0.0, doorY), Point(100.0, doorY), true),
                    ),
                ),
            ),
            northOffsetDegrees = 0,
        )

        // The detector's path — an L missing its North-East corner.
        val cut = VastuEngine().analyze(
            plan(
                listOf(
                    Point(0.0, 0.0), Point(100.0, 0.0), Point(100.0, 60.0),
                    Point(60.0, 60.0), Point(60.0, 100.0), Point(0.0, 100.0),
                ),
                doorY = 100.0,
            ),
        )
        assertTrue(
            cut.defects.any { it.id == "X-04" && it.belongsToBuilding },
            "a missing North-East corner must arrive at the report flagged as the building's — " +
                "otherwise a flat is told to extend a footprint it does not own",
        )

        // The engine's own path — a footprint about 3.6 : 1.
        val long = VastuEngine().analyze(plan(Fixtures.rect(0.0, 0.0, 100.0, 28.0), doorY = 28.0))
        assertTrue(
            long.defects.any { it.id == "X-15" && it.belongsToBuilding },
            "the long-and-narrow finding is built by the engine, NOT the detector — a flag added " +
                "to one constructor and not the other fails exactly here and nowhere else",
        )

        // And nothing gains a flag it has no business carrying.
        for (d in (cut.defects + long.defects)) {
            assertEquals(
                ruleSet.defects.first { it.id == d.id }.belongsToBuilding,
                d.belongsToBuilding,
                "${d.id} disagrees with the ruleset about whose it is",
            )
        }
    }
}
