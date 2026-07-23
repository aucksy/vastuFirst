package com.vastufirst.shared

/**
 * Phase 0 placeholder so the module compiles with a non-empty source set.
 *
 * Phase 1 fills this module with the real data model from Product PRD §5:
 * the enums (Zone, Verdict, PadaVerdict, Severity, Intent, PropertyType,
 * Provenance, RoomType, FixtureType, …) and the DTO/result types
 * (RoomRule, DoorPada, Defect, RoomResult, DoorResult, ZoneAnomaly, Analysis).
 *
 * Nothing in this module may import android.* or androidx.* — it is shared with
 * iOS as pure commonMain (Product PRD §3.1).
 */
internal const val SHARED_MODULE = "vastufirst-shared"
