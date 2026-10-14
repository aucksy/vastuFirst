package com.vastufirst.engine

import com.vastufirst.shared.Analysis
import com.vastufirst.shared.Plan
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Static guard for §0.4 / §15: the engine exposes no facing-direction concept as a scoring input.
 * `northOffsetDegrees` is allowed — it is a geometry transform, echoed back for provenance, and
 * the rotation-invariance test proves it does not move the score.
 */
class FacingNeutralityTest {

    @Test
    fun `Analysis exposes no facingDirection field`() {
        val offenders = Analysis::class.memberProperties
            .map { it.name }
            .filter { it.contains("facing", ignoreCase = true) || it.contains("orientation", ignoreCase = true) }
        if (offenders.isNotEmpty()) fail("Analysis must not expose a facing/orientation scoring field: $offenders")
    }

    @Test
    fun `Plan carries no facingDirection either — only northOffsetDegrees`() {
        val names = Plan::class.memberProperties.map { it.name }
        assertTrue(names.none { it.contains("facing", ignoreCase = true) })
        assertTrue("northOffsetDegrees" in names)
    }
}
