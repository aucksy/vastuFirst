package com.vastufirst.app.engine

import com.vastufirst.app.ui.newplan.buildEnginePlan
import com.vastufirst.app.ui.newplan.frontDoorFromEntrance
import com.vastufirst.app.ui.scan.toGridRooms
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.Intent
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.Verdict
import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScanOutcome
import org.junit.Test
import kotlin.test.assertTrue

/**
 * ⭐⭐ WHAT REAL HOMES ACTUALLY SCORE — the measurement nothing was taking.
 *
 * **Why this exists.** On 9 Aug 2026 the owner asked: *"why does pretty much every house I scan
 * score lower than 4?"* Nobody could answer from the repository, because **no test had ever put a
 * real recorded plan through the real engine and looked at the number.** Every score assertion in
 * the project pins a hand-built fixture — `sample-01` at 31, `sample-02` at 100, the stress corpus
 * at "0..100 and does not crash". All of those can stay green while every genuine flat a customer
 * scans comes out at 1 out of 10, which is exactly what was happening.
 *
 * So this walks the bundled recorded scans — the owner's own flat, two Greencourt flats, the sample
 * 3BHK and the rest — through the same path the app uses (mapper → grid rooms → engine plan →
 * score) and **prints the result**. The numbers land in the CI log, where they can be read before a
 * build is handed over.
 *
 * ⚠ **It deliberately asserts almost nothing yet.** A threshold invented before seeing real output
 * is a threshold that either never fires or fires wrongly; the honest order is measure first, then
 * set the bar on evidence. What it does guarantee is that the corpus is genuinely being scored — a
 * silent zero-plan run would make this file a decoration, which is the failure mode every gate here
 * is written to avoid.
 */
class RealPlanScoreTest {

    private data class Scored(val id: String, val score: Int, val rooms: Int, val defects: Int, val clipped: Int)

    /** One line of the report, marked so CI can lift it out of the test-results XML into the log. */
    private fun say(line: String) = println("SCORE| $line")

    private fun scoreOf(id: String): Scored? {
        val reply = RecordedScans.load(id)?.reply ?: return null
        val placed = ScanMapper.map(reply, imageAspect = 1.0) as? ScanOutcome.Placed ?: return null
        val grid = toGridRooms(placed.rooms, placed.cols, placed.rows)
        val plan = buildEnginePlan(
            rooms = grid,
            door = frontDoorFromEntrance(grid),
            intent = Intent.BUILDING,
            propertyType = PropertyType.FLAT,
            north = 0,
            planId = id,
        ) ?: return null
        val a = VastuEngine().analyze(plan)
        return Scored(
            id = id,
            score = a.score,
            rooms = a.roomResults.count { it.verdict != Verdict.NOT_SCORED },
            defects = a.defects.size,
            // Rooms flagged as a defect that are mostly NOT on forbidden ground — the ones partial
            // credit exists for. Before it, each of these forfeited everything for a corner.
            clipped = a.roomResults.count { it.verdict == Verdict.DEFECT && it.encroachedShare < 0.5 },
        )
    }

    @Test
    fun `every recorded real plan is scored, and the numbers are printed`() {
        val scored = RecordedScans.ids.mapNotNull(::scoreOf)

        // ⚠ Every line carries the SCORE| marker. Gradle swallows test stdout, so CI fishes these
        // lines back out of the test-results XML and echoes them into the log — without the marker
        // the numbers are written to a file nobody opens, which is the same as not measuring.
        say("plan                       score   rooms  problems  clipped-only")
        scored.forEach {
            say(
                it.id.padEnd(26) +
                    (it.score / 10.0).toString().padEnd(8) +
                    it.rooms.toString().padEnd(7) +
                    it.defects.toString().padEnd(10) +
                    it.clipped.toString(),
            )
        }
        val median = scored.map { it.score }.sorted()[scored.size / 2]
        say("median ${median / 10.0} / 10 across ${scored.size} real plans")

        assertTrue(scored.size >= 4, "only ${scored.size} recorded plans scored — the corpus is not being read")
        assertTrue(scored.all { it.score in 0..100 }, "a real plan produced a score outside 0..100")
        assertTrue(scored.all { it.rooms > 0 }, "a real plan scored with no scored rooms at all")
    }
}
