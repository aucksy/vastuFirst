package com.vastufirst.shared

import kotlinx.serialization.Serializable

/**
 * All engine enums (Product PRD §5). Serializable so the rule dataset (§5.1) can be
 * loaded straight from JSON without a parallel wire model.
 *
 * Pure Kotlin — no android.* / androidx.* may ever appear in this module (§3.1).
 */

@Serializable
enum class Zone { N, NE, E, SE, S, SW, W, NW, BRAHMASTHAN }

@Serializable
enum class Provenance { TEXT, DERIV, MOD, DISP }

@Serializable
enum class Intent { BUILDING, BUYING, LIVING }

@Serializable
enum class PropertyType { INDEPENDENT_HOUSE, FLAT }

@Serializable
enum class Verdict { IDEAL, ACCEPTABLE, SUBOPTIMAL, DEFECT, NOT_SCORED }

@Serializable
enum class PadaVerdict { AUSPICIOUS, MODERATE, MIXED, INAUSPICIOUS }

@Serializable
enum class Severity { MAJOR, MODERATE, MINOR }

@Serializable
enum class FixKind { MOVE_IT, REMEDY_IT, RITUAL }

/**
 * The reading this app scores. There is exactly one, and that is the owner's decision
 * (23 August 2026, answering the open expert question about offering two schools at once).
 *
 * SIXTEEN_ZONE and FORTY_FIVE_DEVATA were declared here and never built. Neither was ever
 * written to disk -- no saved home carries a profile of any kind, because nothing persists this
 * field -- so removing them breaks nothing that exists. The type stays, with one value, so an
 * Analysis still records on its face which reading produced the number.
 */
@Serializable
enum class SchoolProfile { TRADITIONAL_8 }

@Serializable
enum class AnomalyKind { CUT, EXTENSION }

@Serializable
enum class RoomType {
    ENTRANCE, KITCHEN, MASTER_BEDROOM, BEDROOM, POOJA, TOILET, BATHROOM,
    LIVING, DINING, STAIRCASE, STUDY, STORE, GUEST_BEDROOM, GARAGE,
    BALCONY, BASEMENT, COURTYARD, UTILITY, CORRIDOR
}

@Serializable
enum class FixtureType {
    OVERHEAD_TANK, UNDERGROUND_WATER, BOREWELL, SEPTIC_TANK,
    STAIRCASE_RUN, BED, KITCHEN_PLATFORM, HEAVY_TREE, CEILING_BEAM, MIRROR
}

/** Zone-assignment strategies (§4.2.6). Per-rule configurable via [RoomRule]. */
@Serializable
enum class ZoneAssignmentStrategy { CENTROID, LARGEST_OVERLAP, ANY_ENCROACHMENT }

/** Door location methods (§4.3.2). Selected in config.json, never hard-coded. */
@Serializable
enum class DoorLocationMethod { BEARING_FROM_CENTRE, PROPORTION_ALONG_WALL }

/** Computability tier for a defect (§4.6). C/D are evaluated only when their input is present. */
@Serializable
enum class DefectTier { A, B, C, D }
