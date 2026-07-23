package com.vastufirst.engine

import com.vastufirst.shared.PadaVerdict
import com.vastufirst.shared.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The second bundled sample (Product PRD §6.2c). A deliberately clean plan — every room in its
 * ideal zone, an auspicious door — proving the engine also rewards good layouts and is not merely
 * a fault-finder (it earns the "Already right — leave alone" section of the report, §6.5).
 */
class Sample02Test {

    private val a = VastuEngine().analyze(Fixtures.sample02())

    @Test
    fun `a fully-ideal plan scores 100 with no defects`() {
        assertEquals(100, a.score)
        assertTrue(a.defects.isEmpty())
        assertTrue(a.roomResults.all { it.verdict == Verdict.IDEAL })
    }

    @Test
    fun `the auspicious door is scored as such`() {
        assertEquals(PadaVerdict.AUSPICIOUS, a.doorResult!!.verdict)
        assertEquals("N3", a.doorResult!!.pada.id)
    }
}
