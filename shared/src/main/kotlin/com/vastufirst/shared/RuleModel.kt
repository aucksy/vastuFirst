package com.vastufirst.shared

import kotlinx.serialization.Serializable

/**
 * The rule dataset (Product PRD §5, §5.1). Every one of these is loaded from versioned
 * JSON in the `rules` module — none is a Kotlin constant (§0.8). An expert ruling (§13)
 * is a data edit here, never a recompile.
 */

@Serializable
data class ZoneInfo(
    val zone: Zone,
    val sanskrit: String? = null,
    val deity: String? = null,
    val element: String? = null,
    val domain: String? = null,
    val sourceId: String,
)

@Serializable
data class RoomRule(
    val roomType: RoomType,
    val weight: Double,
    val ideal: Set<Zone> = emptySet(),
    val acceptable: Set<Zone> = emptySet(),
    val prohibited: Set<Zone> = emptySet(),
    val provenance: Provenance,
    val sourceId: String,                       // "R-02" -> knowledge base
    val citation: String? = null,               // only when provenance == TEXT
    val note: String? = null,
    /**
     * WHY this room wants those directions, in the reader's own language — the sentence a paying
     * customer gets when a room is already right, and when it is merely "not ideal". It carries this
     * rule's own [provenance] and [sourceId], so it is as traceable as the placement itself.
     */
    val rationale: String? = null,
    val disputeId: String? = null,              // non-null => NOT_SCORED (§4.4.1)
    /** True for rooms scored elsewhere or pending a ruling (ENTRANCE via door; BASEMENT R-15). */
    val excludeFromScore: Boolean = false,
    val positiveStrategy: ZoneAssignmentStrategy = ZoneAssignmentStrategy.LARGEST_OVERLAP,
    val defectStrategy: ZoneAssignmentStrategy = ZoneAssignmentStrategy.ANY_ENCROACHMENT,
)

@Serializable
data class DoorPada(
    val id: String,                             // "E6"
    val ordinal: Int,                           // 1..32 clockwise from North (bearing/11.25 + 1)
    val name: String? = null,                   // null where sources disagree / absent (E7, S7)
    val side: Zone,                             // N / E / S / W
    val startBearing: Double,                   // degrees clockwise from North, inclusive
    val verdict: PadaVerdict,
    val domain: String? = null,
    val provenance: Provenance = Provenance.DERIV,
)

@Serializable
data class DisputeReading(val label: String, val text: String, val school: String? = null)

@Serializable
data class Dispute(
    val id: String,                             // "W-12"
    val title: String,
    val readingA: DisputeReading,
    val readingB: DisputeReading,
    val appliesTo: RoomType? = null,
    val appliesToZone: Zone? = null,
    /**
     * ⭐ Which side of this dispute the NUMBER stands on, in the reader's own words — or null where
     * the score genuinely stands on neither.
     *
     * Most disputes are surfaced and not scored, and for those "here are both readings" is the whole
     * truth. Once the owner rules on one (W-12, the prayer room, 1 August 2026), showing both
     * readings and saying nothing about which one moved the score would be a half-truth in the one
     * product whose promise is that it does not quietly pick a side. Both readings still appear; this
     * sentence says where the number sits.
     */
    val howWeScore: String? = null,
)

@Serializable
data class Remedy(
    val id: String,
    val kind: FixKind,
    val text: String,
    val provenance: Provenance,                 // MOD for gadgets, TEXT for shanti
    val rank: Int,                              // lower = shown first (§7.2)
    val productSku: String? = null,
)

/** A (room type, prohibited zone) trigger for a room-derived structural defect. */
@Serializable
data class RoomZoneMatch(val roomType: RoomType, val zone: Zone)

/**
 * A defect definition from defects.json (§5.1 `DefectDefinition[]`). The engine instantiates
 * a runtime [Defect] from one of these when its trigger fires.
 */
@Serializable
data class DefectDefinition(
    val id: String,                             // "X-01" or "X-GEN"
    val title: String,
    val severity: Severity,
    val provenance: Provenance,
    val tier: DefectTier,
    val explanation: String,
    val layoutFix: String? = null,
    /**
     * ⭐ The honest line above the remedies when the classical texts record no cure for this defect
     * that leaves the element where it is. The product's whole differentiator is that it does not
     * make Vastu up — so where nothing exists we say so instead of filling the table.
     */
    val remedyNote: String? = null,
    /** Tier C/D only: what we could not check, in the reader's words — never the raw defect id. */
    val notCheckedLabel: String? = null,
    /** Tier C/D only: how the reader gets it checked. */
    val notCheckedHow: String? = null,
    val remedyIds: List<String> = emptyList(),  // must be non-empty (loader validates)
    /** Room-derived triggers: a DEFECT-verdict room of this type in this zone raises it. */
    val appliesTo: List<RoomZoneMatch> = emptyList(),
    /** Tier C: evaluated only when a fixture of one of these types is present. */
    val requiresFixtureTypes: List<FixtureType> = emptyList(),
    /** Tier D: evaluated only when a Site is present. */
    val requiresSite: Boolean = false,
)

@Serializable
data class RulesetMeta(
    val version: String,
    val kbDraft: String? = null,
    /**
     * ⭐ WHAT CHANGED IN THIS VERSION OF THE RULES, in the reader's own words.
     *
     * A saved home carries the rule version it was scored under. When the rules move, the app
     * re-scores every saved home and shows this sentence next to the old and new number — because a
     * score that shifts on its own, with no explanation, is the difference between an honest product
     * and a spooky one. It lives in the rule data rather than in the app so that a rule edit and its
     * explanation can never ship apart.
     */
    val changeNote: String? = null,
)
