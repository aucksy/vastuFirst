package com.vastufirst.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Rotation invariance (Product PRD §15). Rotating the plan + door + North together by the same
 * angle must yield the identical score. This is the correct, constructible form of the
 * facing-neutrality rule (§0.4): the engine never reads building orientation as a scoring term.
 */
class RotationInvarianceTest {

    private val engine = VastuEngine()

    @Test
    fun `sample-01 scores 31 at north offset 0`() {
        assertEquals(31, engine.analyze(Fixtures.sample01(0)).score)
    }

    @Test
    fun `sample-01 scores 31 under the §15 90-degree rotation`() {
        assertEquals(31, engine.analyze(Fixtures.rotated(90)).score)
    }

    @Test
    fun `score is invariant under rotation at every angle`() {
        for (deg in intArrayOf(0, 37, 45, 90, 128, 180, 213, 270, 315, 359)) {
            val a = engine.analyze(Fixtures.rotated(deg))
            assertEquals(31, a.score, "score changed at $deg° — orientation must not affect the score")
            assertEquals("E6", a.doorResult!!.pada.id, "door pada changed at $deg° — should map back to E6")
        }
    }

    @Test
    fun `a NON-square (L-shaped) plan is also rotation-invariant — proves the area-centroid origin`() {
        // The L's area centroid (~44.3, 44.3) differs from its bbox centre (50, 50). If the engine
        // rotated about the bbox centre instead of the mandated area centroid (§4.0), this
        // round-trip would NOT reconstruct and the score would drift. A square could not catch that.
        val base = Fixtures.lShaped()
        val expected = engine.analyze(base).score
        for (deg in intArrayOf(37, 90, 128, 213, 300)) {
            assertEquals(expected, engine.analyze(Fixtures.rotate(base, deg)).score, "L-shape score changed at $deg°")
        }
    }
}
