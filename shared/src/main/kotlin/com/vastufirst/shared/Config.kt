package com.vastufirst.shared

import kotlinx.serialization.Serializable

/**
 * config.json (Product PRD §5.1) — every tunable named in §4 lives here, never as a
 * Kotlin constant (§0.8). Swapping a value changes engine output with no recompile.
 *
 * Enum-keyed lookups use String keys (enum.name) so the JSON stays plain and the
 * disputed/open values (§13) are a one-line edit for a non-developer reviewer.
 */
@Serializable
data class RulesetConfig(
    val gridSize: Int = 9,
    /** §4.2 / M-05: central 3×3 of 9×9 is the default Brahmasthan extent. */
    val brahmasthanExtent: String = "CENTRAL_3X3",
    /** §4.3.2 / P-08: bearing from centre is the default door method. */
    val doorLocationMethod: DoorLocationMethod = DoorLocationMethod.BEARING_FROM_CENTRE,
    /** §4.5.1: the entrance is the highest-weighted element. */
    val doorWeight: Double = 3.0,
    /** §4.3.3: a door within this many degrees of a pada boundary spans both. */
    val padaBoundaryToleranceDeg: Double = 1.0,
    /** §4.3.3: the near-universally condemned SW-corner arc → X-06. */
    val swCornerArcStartDeg: Double = 213.75,
    val swCornerArcEndDeg: Double = 236.25,
    val encroachment: EncroachmentConfig = EncroachmentConfig(),
    val tieBreak: TieBreakConfig = TieBreakConfig(),
    val anomaly: AnomalyConfig = AnomalyConfig(),
    /** §4.5.2 penalty weights by [Severity].name, and the total cap. */
    val penalties: Map<String, Int> = mapOf("MAJOR" to 8, "MODERATE" to 4, "MINOR" to 1),
    val penaltyCap: Int = 30,
    /** §4.5 room points by [Verdict].name (scored verdicts only). */
    val scorePoints: Map<String, Int> = mapOf(
        "IDEAL" to 100, "ACCEPTABLE" to 70, "SUBOPTIMAL" to 45, "DEFECT" to 10,
    ),
    /** §4.5.1 door points by [PadaVerdict].name. */
    val padaPoints: Map<String, Int> = mapOf(
        "AUSPICIOUS" to 100, "MODERATE" to 60, "MIXED" to 50, "INAUSPICIOUS" to 15,
    ),
    /** §4.4.2: room types with no placement rule; evaluate to NOT_SCORED. */
    val unruledRoomTypes: Set<RoomType> = emptySet(),
    /** Plot elongation (research: square/rectangle ideal within 1:1–1:2). A note at the soft flag,
     *  a MINOR defect beyond the hard flag. Measured on the footprint's OWN axes (rotation-free). */
    val aspectSoftFlag: Double = 1.5,
    val aspectHardFlag: Double = 2.0,
)

@Serializable
data class EncroachmentConfig(
    /** §4.2.6: ANY_ENCROACHMENT needs overlap ≥ this fraction of the room's own area … */
    val roomAreaFraction: Double = 0.02,
    /** … AND ≥ this fraction of the analysis-rectangle area. Both mandatory. */
    val analysisRectAreaFraction: Double = 0.005,
    /**
     * ⭐ PARTIAL CREDIT FOR A ROOM THAT ONLY CLIPS A FORBIDDEN ZONE (owner decision, 9 Aug 2026).
     *
     * Off, a room loses everything the moment it crosses the line by the threshold above — about
     * 2 % of itself, roughly a five-square-foot corner on a thousand-square-foot flat. It drops
     * from as much as 100 points to 10 with no gradient in between, and the same corner is then
     * charged a second time as a penalty. Measured across five real recorded plans, that one
     * behaviour is why ordinary homes score under 4: it took the bundled 3BHK to 0.5 and a real
     * Greencourt flat to 1.0.
     *
     * On, the room keeps the points its MAIN zone earns, reduced in proportion to how much of the
     * room actually sits on forbidden ground, and the penalty ramps the same way.
     *
     * ⚠ THIS NEVER SILENCES A FINDING, and that distinction is the whole design. The verdict stays
     * DEFECT and the defect is still raised, explained and remedied exactly as before — a toilet
     * clipping the North-East is still reported as a toilet clipping the North-East. Only the
     * arithmetic softens. Wiring the credit into the verdict instead would delete the finding from
     * the report, which is the one way this change could cost real accuracy.
     */
    val proportionalCredit: Boolean = false,
    /**
     * The share of a room that must sit on forbidden ground before its defect counts at FULL
     * penalty. Below it the penalty ramps linearly; at or above it, nothing is discounted.
     * Only consulted when [proportionalCredit] is on.
     */
    val fullPenaltyShare: Double = 0.5,
)

@Serializable
data class TieBreakConfig(
    /** §4.2.6: two zones within this fraction of each other count as tied. */
    val withinFraction: Double = 0.01,
    /** §4.2.6: fixed precedence when still tied — most sensitive first. */
    val zonePrecedence: List<Zone> = listOf(
        Zone.BRAHMASTHAN, Zone.NE, Zone.SW, Zone.SE, Zone.N, Zone.E, Zone.S, Zone.W, Zone.NW,
    ),
)

@Serializable
data class AnomalyConfig(
    /** §4.2.7: a side needs ≥ this fraction of its edge length at one offset to be modal. */
    val modalEdgeFraction: Double = 0.60,
    /** §4.2.7: anomaly area ≥ this fraction of the reference rectangle counts (MAJOR). */
    val majorAreaFraction: Double = 0.10,
    /** §4.2.7: between this and major = MINOR; below this = ignored. */
    val minorAreaFraction: Double = 0.05,
)
