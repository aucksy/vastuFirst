package com.vastufirst.shared

import kotlinx.serialization.Serializable

/**
 * Plan-space point. X increases EAST (right). Y increases NORTH (up).
 * Units are arbitrary and self-consistent within one plan — the engine is scale-free
 * (Product PRD §4.0). Android UI works in y-down screen space and MUST convert at the
 * boundary; the engine never sees screen coordinates.
 */
@Serializable
data class Point(val x: Double, val y: Double)
