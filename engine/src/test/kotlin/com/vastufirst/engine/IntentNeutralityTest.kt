package com.vastufirst.engine

import com.vastufirst.shared.Intent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ⭐ WHY YOU ARE HERE DOES NOT CHANGE WHAT YOUR HOME SCORES.
 *
 * v0.6.6 reordered the three answers on the first screen (buying leads now) and made the paid report
 * branch on them much harder: someone BUYING or ALREADY LIVING somewhere is shown remedies only,
 * where BUYING used to be drawn exactly like BUILDING. That is a presentation decision, and it must
 * stay one.
 *
 * ⚠ The danger it guards is specific and quiet. `intent` travels all the way into the engine and out
 * again on the Analysis, so it would cost one careless line to start weighting a rule by it — and a
 * home that scored 3.1 as "building" and 3.4 as "buying" would be indefensible in a paid product,
 * while looking to everybody like a normal difference of opinion. Nothing on screen would flag it.
 * The engine's own promise is that it scores the LAYOUT (Product PRD §0.4 already says it never
 * scores which way a building faces); this pins the same promise for why the owner opened the app.
 *
 * Every number the report and the score screen draw from is compared, not just the headline score.
 */
class IntentNeutralityTest {

    @Test
    fun `the same home scores identically whoever is asking`() {
        val results = Intent.entries.associateWith { intent ->
            VastuEngine().analyze(Fixtures.sample01().copy(intent = intent))
        }
        val building = results.getValue(Intent.BUILDING)

        for ((intent, a) in results) {
            assertEquals(building.score, a.score, "the score must not depend on intent ($intent)")
            assertEquals(building.base, a.base, "nor the base ($intent)")
            assertEquals(building.defectPenalty, a.defectPenalty, "nor the penalty ($intent)")
            assertEquals(building.quality, a.quality, "nor how confident we are ($intent)")
            // The ranked problems and every room verdict — the whole of what the reader is shown,
            // in the same order. Only the WRAPPING around them is allowed to differ by intent.
            assertEquals(
                building.defects.map { it.ruleId to it.zone }, a.defects.map { it.ruleId to it.zone },
                "the same problems, in the same order, whoever is asking ($intent)",
            )
            assertEquals(
                building.roomResults.map { it.id to it.verdict }, a.roomResults.map { it.id to it.verdict },
                "and the same verdict on every room ($intent)",
            )
            assertEquals(
                building.doorResult?.verdict, a.doorResult?.verdict,
                "and the same reading of the front door ($intent)",
            )
        }
    }

    /**
     * And the one thing that IS allowed to differ travels: the report reads `intent` off the analysis
     * to choose between "what to change" and "what to do now", so it has to arrive unaltered.
     */
    @Test
    fun `the answer is carried through to the report unchanged`() {
        for (intent in Intent.entries) {
            val a = VastuEngine().analyze(Fixtures.sample01().copy(intent = intent))
            assertEquals(intent, a.intent, "the report branches on this — it must not be rewritten")
        }
    }
}
