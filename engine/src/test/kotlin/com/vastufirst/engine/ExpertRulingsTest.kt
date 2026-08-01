package com.vastufirst.engine

import com.vastufirst.rules.RuleSetLoader
import com.vastufirst.shared.DoorLocationMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The two expert rulings that MOVE THE SCORE (Product PRD §13 — M-05 and M-07), pinned to the
 * position chosen in docs/EXPERT-RULINGS.md.
 *
 * ⚠ These are configuration, not code. The whole point of keeping them in the rule JSON is that a
 * real Vastu expert can overturn either one without a rebuild — so what this file guards is not that
 * the values are RIGHT (nobody can test that) but that they are what the document says they are, and
 * that changing one has the effect the document claims. A ruling recorded in a document and quietly
 * contradicted by the shipped config is worse than no document.
 */
class ExpertRulingsTest {

    private val shipped = RuleSetLoader.loadDefault()

    // ── M-05 · how big the centre (Brahmasthan) is ────────────────────────────────────────────

    @Test
    fun `M-05 the centre is the central three-by-three of the nine-square grid`() {
        assertEquals(
            "CENTRAL_3X3", shipped.config.brahmasthanExtent,
            "the ruling in docs/EXPERT-RULINGS.md is the classical Paramasayika centre; if this " +
                "changed, the document and the shipped app now disagree about what the centre IS",
        )
        assertEquals(9, shipped.config.gridSize, "the nine-square grid the centre is measured on")
    }


    // ── M-07 · how the front door's pada is decided ───────────────────────────────────────────

    @Test
    fun `M-07 the door is read as a bearing from the centre`() {
        assertEquals(
            DoorLocationMethod.BEARING_FROM_CENTRE, shipped.config.doorLocationMethod,
            "the ruling in docs/EXPERT-RULINGS.md",
        )
    }

    @Test
    fun `the worked example scores 31 under the ruling, and the alternative is measured not guessed`() {
        val underRuling = VastuEngine(shipped).analyze(Fixtures.sample01(0))
        assertEquals(31, underRuling.score, "the §15 worked example, under the shipped ruling")

        val alternative = shipped.copy(
            config = shipped.config.copy(doorLocationMethod = DoorLocationMethod.PROPORTION_ALONG_WALL),
        )
        val underAlternative = VastuEngine(alternative).analyze(Fixtures.sample01(0))

        // Printed, not asserted to a hard number: this is the figure quoted in the rulings document,
        // and it is read from here rather than invented. If the two ever stop matching, the document
        // is what needs correcting.
        println(
            "M-07 MEASURED · worked example: ruling (bearing from centre) = ${underRuling.score}, " +
                "alternative (proportion along wall) = ${underAlternative.score}; " +
                "door pada ${underRuling.doorResult?.pada?.id} vs ${underAlternative.doorResult?.pada?.id}",
        )
        assertTrue(
            underAlternative.score in 0..100,
            "the alternative must remain a usable score — a ruling nobody could turn on is not reversible",
        )
    }

    // ── the rulings that are SURFACED rather than scored ──────────────────────────────────────

    @Test
    fun `every disputed rule still shows both readings, so no ruling is hidden from the reader`() {
        for (dispute in shipped.disputes) {
            assertTrue(
                dispute.readingA.text.isNotBlank() && dispute.readingB.text.isNotBlank(),
                "${dispute.id} must carry BOTH readings — the product's promise is that where the " +
                    "tradition contradicts itself the reader is shown both sides, not our pick",
            )
            assertTrue(
                !dispute.readingA.school.isNullOrBlank() && !dispute.readingB.school.isNullOrBlank(),
                "${dispute.id} must say WHICH school each reading comes from",
            )
        }
    }

    @Test
    fun `the pooja ruling is deliberately still open, and the engine says so rather than guessing`() {
        // ⚠ W-12 is the one ruling NOT applied. Applying it would start scoring every pooja room,
        // which changes the worked example and every score already saved on a phone — so it stays a
        // declared dispute until the owner says otherwise. That is a decision, not an oversight, and
        // this test is what stops it being applied by accident.
        val pooja = assertNotNull(
            shipped.rooms.firstOrNull { it.roomType.name == "POOJA" },
            "there must be a pooja rule to leave open",
        )
        assertEquals(
            "W-12", pooja.disputeId,
            "the pooja placement must stay a declared dispute until the owner rules on it",
        )
    }
}
