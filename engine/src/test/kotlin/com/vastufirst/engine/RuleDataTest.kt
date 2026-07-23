package com.vastufirst.engine

import com.vastufirst.rules.RuleSetLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Rules are data, not code" (Product PRD §0.8, Impl PRD §Phase 1 gate): swapping a value in the
 * JSON changes engine output with **no recompile**. Here we feed the loader the real dataset for
 * every file except config.json, which we hand it as a modified JSON string, and watch the score
 * for the same plan move.
 */
class RuleDataTest {

    private fun realResource(path: String): String =
        RuleDataTest::class.java.getResourceAsStream(path)!!.bufferedReader().use { it.readText() }

    @Test
    fun `raising the MAJOR penalty in config JSON lowers the same plan's score, no code change`() {
        // Baseline from the shipped dataset.
        assertEquals(31, VastuEngine().analyze(Fixtures.sample01()).score)

        // Same everything, but config.json now carries a heavier MAJOR penalty (8 → 20).
        val modifiedConfig = """
            {
              "penalties": { "MAJOR": 20, "MODERATE": 4, "MINOR": 1 },
              "unruledRoomTypes": ["BATHROOM", "COURTYARD", "UTILITY", "CORRIDOR"]
            }
        """.trimIndent()

        val swapped = RuleSetLoader.load { path ->
            if (path.endsWith("config.json")) modifiedConfig else realResource(path)
        }
        val score = VastuEngine(swapped).analyze(Fixtures.sample01()).score
        // penalty = min(20 + 20, cap 30) = 30 → round(47.27 − 30) = 17.
        assertTrue(score < 31, "editing JSON must change the score without recompiling; got $score")
        assertEquals(17, score)
    }

    @Test
    fun `the shipped dataset loads and validates`() {
        val rs = RuleSetLoader.loadDefault()
        assertEquals(32, rs.doorPadas.size)
        assertEquals("2026.07.19-1", rs.version)
    }
}
