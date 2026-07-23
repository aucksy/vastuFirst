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
