package com.vastufirst.app.render

import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.SamplePlans
import com.vastufirst.app.ui.newplan.buildEnginePlan
import com.vastufirst.data.SavedDraft
import com.vastufirst.data.SavedPlan
import com.vastufirst.data.UnreadableHome
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.editor.DraftRoom
import com.vastufirst.shared.editor.DraftSnapshot
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

    /** A saved row this build cannot decode — the name and timestamp columns still read, which is
     *  what lets the card name it and offer removing exactly this one (audit B7). The date is an
     *  exact whole-day offset for the same timezone-stability reason as [savedPlans]. */
    val unreadableHome: UnreadableHome =
        UnreadableHome(id = "u1", name = "Home 3", updatedAt = FIXED_NOW - 12 * ONE_DAY_MS)

    /** Two saved homes — enough to prove the row layout, the score pill, the rename pencil and the
     *  side-by-side spacing; distinct names + times so the compare reads as two different homes. */
    val savedPlans: List<SavedPlan> = listOf(
        savedPlan("p1", "Builder's draft — 2BHK", Intent.BUILDING, 31, updatedAt = FIXED_NOW),
        savedPlan("p2", "Compact 2BHK flat", Intent.BUYING, 68, updatedAt = FIXED_NOW - 2 * ONE_DAY_MS),
    )

    /**
     * ⭐ Two homes started and never finished (v0.6.6) — the rows that make "carry on" a choice
     * instead of something the app does behind the user's back.
     *
     * ⚠ One of them has NO name, and that is the ordinary case, not an edge one: a home is only named
     * when it is first saved, so almost every unfinished home is nameless and the row must read
     * properly without one. The other carries a name because it was reopened from a scan. Both show a
     * room count and a time and NO score — nothing here has been through the engine, and putting a
     * number on it would be inventing one.
     */
    val savedDrafts: List<SavedDraft> = listOf(
        SavedDraft(
            id = "draft-1",
            draft = DraftSnapshot(
                rooms = listOf(
                    DraftRoom("d1", com.vastufirst.shared.RoomType.LIVING, 0, 0, 3, 2),
                    DraftRoom("d2", com.vastufirst.shared.RoomType.KITCHEN, 4, 0, 2, 2),
                    DraftRoom("d3", com.vastufirst.shared.RoomType.MASTER_BEDROOM, 0, 3, 3, 3),
                    DraftRoom("d4", com.vastufirst.shared.RoomType.TOILET, 5, 4, 1, 1),
                    DraftRoom("d5", com.vastufirst.shared.RoomType.BALCONY, 0, 7, 6, 1),
                ),
            ),
            updatedAt = FIXED_NOW,
        ),
        SavedDraft(
            id = "draft-2",
            draft = DraftSnapshot(
                name = "Green Court 1BHK",
                rooms = listOf(DraftRoom("e1", com.vastufirst.shared.RoomType.BEDROOM, 1, 1, 2, 3)),
            ),
            updatedAt = FIXED_NOW - 3 * ONE_DAY_MS,
        ),
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

    // --- a SCANNED home: the owner's OWN sheet, and the words printed on it ---
    /**
     * ⭐⭐ THE OWNER'S PLAN, READ THE WAY THE APP READS IT — the recorded reply for `plan-020` put
     * through the real mapper and the real grid conversion, so these rooms carry the captions his
     * sheet actually prints ("FOYER", "MASTER BEDROOM", the three-section balcony).
     *
     * ⚠ It exists because of a blind spot, not for variety. Every report golden in this harness
     * renders a home DRAWN BY HAND: no photograph, no printed captions, so the report's picture
     * section and its printed room names appeared in **no picture at any configuration**. That is the
     * "a golden is a viewport" trap landing on the two things the 11 Aug 2026 release is about — the
     * plan's own names on the report, and how big the picture is.
     */
    private val scanned: com.vastufirst.shared.scan.ScanOutcome.Placed =
        com.vastufirst.shared.scan.ScanMapper.map(
            com.vastufirst.shared.scan.RecordedScans.load(
                com.vastufirst.shared.scan.RecordedScans.PLAN_020,
            )!!.reply,
            imageAspect = 1399.0 / 1389.0,
        ) as com.vastufirst.shared.scan.ScanOutcome.Placed

    val scannedRooms: List<GridRoom> =
        com.vastufirst.app.ui.scan.toGridRooms(scanned.rooms, scanned.cols, scanned.rows)

    /** The scan's own rooms, as "Check what we read" lists them (before the grid conversion). */
    val scannedScanRooms: List<com.vastufirst.shared.scan.ScannedRoom> = scanned.rooms

    /** The rectangles each room was read from, for drawing them on the photograph. */
    val scannedPlanRooms: List<com.vastufirst.app.ui.common.PlanRoom> =
        com.vastufirst.app.ui.scan.planRoomsOf(scanned.rooms)

    val scannedCols: Int = scanned.cols
    val scannedRows: Int = scanned.rows

    val scannedAnalysis: Analysis =
        VastuEngine().analyze(
            buildEnginePlan(
                rooms = scannedRooms,
                // His sheet prints FOYER, so the front door is read off it rather than asked for.
                door = com.vastufirst.app.ui.newplan.frontDoorFromEntrance(scannedRooms),
                intent = Intent.BUILDING,
                propertyType = PropertyType.FLAT,
                north = 0,
                planId = "scanned-fixture",
            )!!,
        )

    /**
     * ⭐ What "Check what we read" puts on each row since 11 Aug 2026 — the one-word result and the
     * direction, from [scannedAnalysis], through the SAME mapper the report uses. Real verdicts on
     * the owner's own flat, so the pills carry their real lengths.
     */
    val scannedReadings: Map<String, com.vastufirst.app.ui.scan.RoomReading> =
        com.vastufirst.app.ui.scan.roomReadings(scannedAnalysis)

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
