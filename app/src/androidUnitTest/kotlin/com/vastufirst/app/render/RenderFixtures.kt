package com.vastufirst.app.render

import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.SamplePlans
import com.vastufirst.app.ui.newplan.buildEnginePlan
import com.vastufirst.data.SavedPlan
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.AnalysisNote
import com.vastufirst.shared.AnalysisQuality
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType

/**
 * Static fixture state for the screenshot harness — never a real ViewModel (which needs DI, a
 * repository and a Main dispatcher, all fragile headless; UI-POLISH §6, stateless-content).
 *
 * These are the render-only inputs for the ViewModel-backed screens' stateless `…Content` seams.
 * The [Plan] inside a [SavedPlan] is intentionally minimal: the saved-plans list only reads
 * id/name/intent/score, so the fixture proves the row layout, not the engine.
 */
object RenderFixtures {

    private fun savedPlan(id: String, name: String, intent: Intent, score: Int, updatedAt: Long) = SavedPlan(
        id = id,
        name = name,
        intent = intent,
        propertyType = PropertyType.INDEPENDENT_HOUSE,
        plan = Plan(
            id = id,
            propertyType = PropertyType.INDEPENDENT_HOUSE,
            intent = intent,
            levels = listOf(Level(index = 0, outline = squareOutline())),
            northOffsetDegrees = 0,
        ),
        score = score,
        ruleSetVersion = "fixture",
        unlocked = false,
        createdAt = 0L,
        updatedAt = updatedAt,
    )

    private fun squareOutline() = listOf(
        Point(0.0, 0.0), Point(8.0, 0.0), Point(8.0, 8.0), Point(0.0, 8.0),
    )

    /** A fixed "now" so the "updated …" line renders deterministically in the golden. Home timestamps
     *  are exact whole-day offsets from it → the same relative text ("today" / "2 days ago") in any
     *  test-host timezone. */
    val FIXED_NOW: Long =
        java.time.LocalDate.of(2026, 7, 15).atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()

    private const val ONE_DAY_MS: Long = 24L * 60 * 60 * 1000

    /** Two saved homes — enough to prove the row layout, the score pill, the rename pencil and the
     *  side-by-side spacing; distinct names + times so the compare reads as two different homes. */
    val savedPlans: List<SavedPlan> = listOf(
        savedPlan("p1", "Builder's draft — 2BHK", Intent.BUILDING, 31, updatedAt = FIXED_NOW),
        savedPlan("p2", "Compact 2BHK flat", Intent.BUYING, 68, updatedAt = FIXED_NOW - 2 * ONE_DAY_MS),
    )

    // --- score-driven screens (Mark North, Score, Report) ---
    // The bundled builder's-draft sample, converted the SAME way the app converts it (buildEnginePlan)
    // and scored by the REAL engine, so these screens render against a genuine Analysis — the zone
    // map colours, the ranked defects, the "already right" rows and the not-assessed list are all the
    // engine's actual output, not a hand-faked stand-in.
    private val sample = SamplePlans.all.first()

    val sampleRooms: List<GridRoom> = sample.rooms
    val sampleNorth: Int = sample.north
    val sampleIntent: Intent = Intent.BUILDING

    val sampleAnalysis: Analysis =
        VastuEngine().analyze(
            buildEnginePlan(
                rooms = sample.rooms,
                door = sample.door,
                intent = sampleIntent,
                propertyType = PropertyType.INDEPENDENT_HOUSE,
                north = sample.north,
                planId = "fixture",
            )!!,
        )

    /**
     * ⭐ A HOME WITH NO PROBLEMS, so the report's LOWER sections can actually be looked at.
     *
     * ⚠ The reason this fixture exists is a real gap in the gate. A golden is a viewport, not a whole
     * document — the report is long, so "Not ideal" and "Already right" sit far below the fold on the
     * bundled sample and **no screenshot in the harness has ever shown them**. Those two sections are
     * the heart of this release (rooms rated not ideal used to appear nowhere at all), so shipping
     * them unseen would repeat the exact mistake UI-POLISH exists to prevent.
     *
     * Every room here is in a direction its own rule calls ideal, except the kitchen: the West is
     * neither ideal (South-East), acceptable (North-West) nor prohibited for a kitchen, so it lands
     * in the SUBOPTIMAL band — which is precisely the band that had no section. With no defects to
     * rank, both sections rise to the top of the screen where a picture can catch them.
     */
    private val cleanRooms: List<GridRoom> = listOf(
        GridRoom("c-living", com.vastufirst.shared.RoomType.LIVING, 3, 0, 2, 2),        // North — ideal
        GridRoom("c-kitchen", com.vastufirst.shared.RoomType.KITCHEN, 0, 3, 2, 2),      // West  — NOT IDEAL
        GridRoom("c-bed", com.vastufirst.shared.RoomType.BEDROOM, 6, 3, 2, 2),          // East  — acceptable
        GridRoom("c-master", com.vastufirst.shared.RoomType.MASTER_BEDROOM, 0, 6, 3, 2), // South-West — ideal
    )

    val cleanAnalysis: Analysis =
        VastuEngine().analyze(
            buildEnginePlan(
                rooms = cleanRooms,
                // Cell 5 on the north wall lands on a favourable door position — the bundled sample's
                // door is unfavourable, so between them the two goldens show both readings.
                door = com.vastufirst.app.ui.newplan.GridDoor(com.vastufirst.app.ui.newplan.DoorSide.N, 5),
                intent = Intent.BUILDING,
                propertyType = PropertyType.INDEPENDENT_HOUSE,
                north = 0,
                planId = "fixture-clean",
            )!!,
        )

    /**
     * ⭐ THE PRAYER-ROOM RULING, LIFTED INTO FRAME (1 August 2026).
     *
     * ⚠ Same trap as `cleanAnalysis`, one release later: a golden is a viewport, not a document. The
     * prayer room was ruled for the modern North-East, which means two things reach the reader for
     * the first time — a prayer room carrying a real verdict, and the "Where the schools disagree"
     * card now saying **which reading the score uses**. On the bundled sample both sit far below the
     * fold, so neither would ever be photographed, and the disputes card is the whole promise of the
     * product: we ruled, and we still show you both sides.
     *
     * Three rooms, chosen so the report is SHORT enough for all of it to fit one screen: nothing to
     * rank, one not-ideal card, two already-right cards, then the disputes. The prayer room is in the
     * North-West on purpose — the same place the worked example and the bundled demo put it, so this
     * is exactly what an existing customer will see when they reopen their home.
     *
     * The footprint is a full 8 × 8 square, which matters: a narrower bounding box trips the
     * elongation rule, a defect appears, and everything below it drops out of frame again.
     */
    private val prayerRooms: List<GridRoom> = listOf(
        GridRoom("p-pooja", com.vastufirst.shared.RoomType.POOJA, 0, 0, 2, 2),           // North-West — NOT IDEAL
        GridRoom("p-master", com.vastufirst.shared.RoomType.MASTER_BEDROOM, 0, 6, 3, 2), // South-West — ideal
        GridRoom("p-kitchen", com.vastufirst.shared.RoomType.KITCHEN, 6, 6, 2, 2),       // South-East — ideal
    )

    val prayerRoomAnalysis: Analysis =
        VastuEngine().analyze(
            buildEnginePlan(
                rooms = prayerRooms,
                door = com.vastufirst.app.ui.newplan.GridDoor(com.vastufirst.app.ui.newplan.DoorSide.N, 5),
                intent = Intent.BUILDING,
                propertyType = PropertyType.INDEPENDENT_HOUSE,
                north = 0,
                planId = "fixture-prayer",
            )!!,
        )

    /**
     * ⭐ The "we changed a rule and here is what it did to your number" card, with the REAL sentence
     * from the shipped rule data — so the picture proves the words a customer actually reads, not a
     * placeholder. One home moved and one did not, which is the honest shape of the prayer-room
     * ruling: it re-scores every saved home, and on some of them the number lands where it started.
     */
    val scoreChangeNotice: com.vastufirst.app.ui.home.ScoreChangeNotice =
        com.vastufirst.app.ui.home.ScoreChangeNotice(
            reason = com.vastufirst.rules.RuleSetLoader.loadDefault().changeNote.orEmpty(),
            changes = listOf(
                com.vastufirst.app.ui.home.ScoreChange("p1", "Builder's draft — 2BHK", 31, 31),
                com.vastufirst.app.ui.home.ScoreChange("p2", "Compact 2BHK flat", 68, 71),
            ),
        )

    /** The "plan too sparse to read" state — the app degrades to a friendly guidance card here, never
     *  a bare red 0 ([[vastufirst-no-error-states]]). Derived from the real analysis so every other
     *  field is valid; only the quality + note change. */
    val insufficientAnalysis: Analysis = sampleAnalysis.copy(
        quality = AnalysisQuality.INSUFFICIENT,
        notes = listOf(
            AnalysisNote(
                code = "FIXTURE_INSUFFICIENT",
                message = "Add a few rooms and your front door, and we'll read your home.",
            ),
        ),
    )
}
