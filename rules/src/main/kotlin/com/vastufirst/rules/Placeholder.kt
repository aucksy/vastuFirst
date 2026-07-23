package com.vastufirst.rules

/**
 * Phase 0 placeholder.
 *
 * Phase 1 fills this module with the versioned rule dataset (Product PRD §5.1):
 * meta.json, zones.json, rooms.json, doorPadas.json (exactly 32), defects.json,
 * disputes.json, remedies.json, config.json — plus a loader that validates on load
 * and FAILS LOUDLY at startup rather than mis-scoring silently.
 *
 * Every §4 tunable lives in config.json — no §4 number may be a Kotlin constant.
 */
internal const val RULES_MODULE = "vastufirst-rules"
