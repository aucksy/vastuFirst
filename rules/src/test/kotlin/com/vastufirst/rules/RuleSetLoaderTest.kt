package com.vastufirst.rules

import com.vastufirst.shared.RoomType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The loader validates on load and FAILS LOUDLY (Product PRD §5.1) — a broken dataset throws at
 * startup rather than silently mis-scoring at runtime.
 */
class RuleSetLoaderTest {

    private fun realResource(path: String): String =
        RuleSetLoaderTest::class.java.getResourceAsStream(path)!!.bufferedReader().use { it.readText() }

    @Test
    fun `the shipped dataset loads and passes every validation`() {
        val rs = RuleSetLoader.loadDefault()
        assertEquals(32, rs.doorPadas.size)
        assertEquals((1..32).toList(), rs.doorPadas.map { it.ordinal }.sorted())
        // Every RoomType is either ruled or listed as unruled.
        RoomType.entries.forEach { t ->
            assertTrue(rs.ruleFor(t) != null || rs.isUnruled(t), "$t is neither ruled nor unruled")
        }
        // Every referenced dispute and remedy resolves.
        rs.rooms.mapNotNull { it.disputeId }.forEach { assertTrue(rs.dispute(it) != null) }
        rs.defects.forEach { d -> assertTrue(d.remedyIds.all { rs.remedy(it) != null } && d.remedyIds.isNotEmpty()) }
    }

    @Test
    fun `a dataset missing padas is rejected loudly`() {
        val ex = assertFailsWith<IllegalStateException> {
            RuleSetLoader.load { path ->
                if (path.endsWith("doorPadas.json")) "[]" else realResource(path)
            }
        }
        assertTrue(ex.message!!.contains("32 door padas"), "expected a clear pada-count failure")
    }

    @Test
    fun `an unimplemented Brahmasthan extent is rejected loudly, not silently run as 3x3`() {
        val broken = """{"brahmasthanExtent":"ONE_NINTH_BY_AREA","unruledRoomTypes":["BATHROOM","COURTYARD","UTILITY","CORRIDOR"]}"""
        val ex = assertFailsWith<IllegalStateException> {
            RuleSetLoader.load { path ->
                if (path.endsWith("config.json")) broken else realResource(path)
            }
        }
        assertTrue(ex.message!!.contains("brahmasthanExtent"))
    }

    /**
     * ⭐ THE REPORT'S CENTRAL PROMISE, pinned in the dataset rather than in a screen.
     *
     * Thirteen of fifteen problems in a ₹699 report once offered the customer the identical two
     * lines — "move it" and Vastu Shanti — because every (room, prohibited zone) pair that had no
     * X-id of its own fell through to one generic definition. These three assertions are what stop
     * that returning: every ruled pair is claimed by its own defect, every defect says something
     * beyond the two universal answers, and every room can say why it wants the directions it wants.
     */
    @Test
    fun `every prohibited placement has its own reason and its own remedies`() {
        val rs = RuleSetLoader.loadDefault()
        val universal = setOf("structural-correction", "vastu-shanti")

        rs.rooms.forEach { rule ->
            rule.prohibited.forEach { zone ->
                val claiming = rs.defects.filter { d -> d.appliesTo.any { it.roomType == rule.roomType && it.zone == zone } }
                assertEquals(1, claiming.size, "${rule.roomType} in $zone must be claimed by exactly one defect")
                val def = claiming.first()
                assertTrue(def.id != "X-GEN", "${rule.roomType} in $zone is claimed by the fallback itself")
                assertTrue(
                    def.explanation.length > 120,
                    "${def.id} restates the rule instead of explaining it (${def.explanation.length} chars)",
                )
                assertTrue(
                    def.remedyIds.any { it !in universal },
                    "${def.id} offers only the two universal remedies",
                )
            }
        }

        // Every defect — including the ones no room triggers — either says something specific or
        // says in words why nothing specific exists. Never a table filled with an invented remedy.
        rs.defects.forEach { d ->
            assertTrue(
                d.remedyIds.any { it !in universal } || !d.remedyNote.isNullOrBlank(),
                "${d.id} has neither a specific remedy nor a note explaining the absence",
            )
        }

        // A room with no rationale leaves "already right" and "not ideal" as a name and a tick.
        rs.rooms.forEach { assertTrue(!it.rationale.isNullOrBlank(), "${it.roomType} has no rationale") }
    }

    @Test
    fun `a defect offering only the universal remedies, with no explanation of why, is rejected`() {
        val broken = """[{"id":"X-GEN","title":"t","severity":"MODERATE","provenance":"DERIV","tier":"A",
            "explanation":"e","remedyIds":["structural-correction","vastu-shanti"]}]"""
        val ex = assertFailsWith<IllegalStateException> {
            RuleSetLoader.load { path -> if (path.endsWith("defects.json")) broken else realResource(path) }
        }
        assertTrue(ex.message!!.contains("universal remedies"), "expected the two-identical-remedies guard to fire")
    }

    @Test
    fun `a rule that can land in 'couldn't check' with no plain label is rejected`() {
        // The raw id used to be printed to the customer: "· X-09".
        val broken = """[{"id":"X-GEN","title":"t","severity":"MODERATE","provenance":"DERIV","tier":"C",
            "explanation":"e","remedyNote":"n","remedyIds":["structural-correction"],
            "requiresFixtureTypes":["HEAVY_TREE"]}]"""
        val ex = assertFailsWith<IllegalStateException> {
            RuleSetLoader.load { path -> if (path.endsWith("defects.json")) broken else realResource(path) }
        }
        assertTrue(ex.message!!.contains("notCheckedLabel"), "expected the raw-code guard to fire")
    }

    @Test
    fun `a defect with no remedy is rejected loudly`() {
        // Replace defects.json with a single defect that has an empty remedy list.
        val broken = """[{"id":"X-01","title":"t","severity":"MAJOR","provenance":"DERIV","tier":"A","explanation":"e","remedyIds":[]}]"""
        val ex = assertFailsWith<IllegalStateException> {
            RuleSetLoader.load { path ->
                if (path.endsWith("defects.json")) broken else realResource(path)
            }
        }
        assertTrue(ex.message!!.contains("no remedies") || ex.message!!.contains("X-GEN"))
    }
}
