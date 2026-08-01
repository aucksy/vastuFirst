package com.vastufirst.engine

import com.vastufirst.rules.RuleSetLoader
import com.vastufirst.shared.DoorLocationMethod
import com.vastufirst.shared.Provenance
import com.vastufirst.shared.Verdict
import com.vastufirst.shared.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    // ── W-12 · where the prayer room belongs ──────────────────────────────────────────────────

    @Test
    fun `W-12 the prayer room is ruled for the modern North-East, and is therefore scored`() {
        // ⭐ INVERTED on purpose, 1 August 2026. This test used to assert the OPPOSITE — that the
        // pooja rule still carried its `disputeId` and so returned NOT_SCORED — precisely so the
        // ruling could not be applied by accident. The owner has now ruled, so the same test guards
        // the opposite state: that the ruling is really in the shipped data and has not silently
        // reverted. Inverted rather than deleted, so the reasoning survives in the file.
        val pooja = assertNotNull(
            shipped.rooms.firstOrNull { it.roomType.name == "POOJA" },
            "there must be a pooja rule",
        )
        assertNull(
            pooja.disputeId,
            "a rule that still carries a disputeId is NOT_SCORED — the ruling would not be applied",
        )
        assertEquals(setOf(Zone.NE), pooja.ideal, "the ruling: the modern North-East is ideal")
        assertEquals(setOf(Zone.N, Zone.E), pooja.acceptable, "the two quarters accepted next")
        assertTrue(
            pooja.prohibited.isEmpty(),
            "⭐ nothing is prohibited on purpose. The classical reading puts the shrine at the very " +
                "centre, and prohibiting the centre would print 'defect' on the exact position the " +
                "same report shows as the other school's advice. We decline that reading; we do not " +
                "condemn it.",
        )
        assertEquals(
            Provenance.MOD, pooja.provenance,
            "the ruling is modern practice, and must be tagged as modern rather than as classical text",
        )
    }

    @Test
    fun `ruling on the prayer room did NOT stop the reader being shown both readings`() {
        // ⭐ The whole product promise. Removing the room rule's `disputeId` also removes the path
        // that surfaced W-12 through the rule — the dispute now reaches the report only through its
        // own `appliesTo: POOJA`. If that link were ever dropped, the app would score one school's
        // reading and stop telling anyone the tradition is split, which is worse than not ruling.
        val analysis = VastuEngine(shipped).analyze(Fixtures.sample01(0))
        val w12 = assertNotNull(
            analysis.disputes.firstOrNull { it.id == "W-12" },
            "the prayer-room dispute must still reach a home that HAS a prayer room",
        )
        assertTrue(w12.readingA.text.isNotBlank() && w12.readingB.text.isNotBlank())
        assertNotNull(
            w12.howWeScore,
            "⭐ a dispute we now score must ALSO say which reading the number uses. Showing both " +
                "sides while staying silent about where the score stands is a half-truth.",
        )
    }

    @Test
    fun `the prayer room is now really scored, and a North-West one is not-ideal rather than a defect`() {
        val analysis = VastuEngine(shipped).analyze(Fixtures.sample01(0))
        val pooja = assertNotNull(analysis.roomResults.firstOrNull { it.roomId == "pooja" })
        assertEquals(Zone.NW, pooja.zone, "the worked example's prayer room sits in the North-West")
        assertEquals(
            Verdict.SUBOPTIMAL, pooja.verdict,
            "the honest severity: the North-West is not where the tradition puts a prayer room, but " +
                "the rule prohibits nothing, so it is 'not ideal' and never a fault",
        )
        assertTrue(
            analysis.defects.none { it.roomId == "pooja" },
            "a not-ideal prayer room must raise no defect and therefore no penalty",
        )
    }
}
