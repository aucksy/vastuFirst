package com.vastufirst.engine

/**
 * Phase 0 placeholder.
 *
 * Phase 1 fills this module with the Vastu engine (Product PRD §4): coordinate system,
 * north rotation about the footprint area centroid, the cardinally-aligned 81-pada grid,
 * zone-assignment strategies, 32-pada door resolution, room→5-verdict evaluation, scoring,
 * cut/extension and defect detection.
 *
 * The Phase 1 exit gate: the §15 worked example (`sample-01`) scores exactly 31, and a
 * rotation-invariance test (rotate plan + door + North together) still scores 31.
 *
 * Pure Kotlin only — no java.time in public API (use kotlinx-datetime / epoch millis),
 * no android.*, no androidx.* (Product PRD §3.1).
 */
internal const val ENGINE_MODULE = "vastufirst-engine"
