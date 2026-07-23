package com.vastufirst.shared

import kotlinx.serialization.Serializable

/**
 * Engine inputs (Product PRD §4.1). These are the user's plan — NOT rule data.
 *
 * Deliberately no `facingDirection` anywhere: the engine never scores which way a
 * building faces (§0.4). Orientation enters only as [Plan.northOffsetDegrees], which is
 * a geometry transform, not a scoring term.
 *
 * These input types are [Serializable] purely so the `data` module can persist a plan as
 * JSON and reopen it (Product PRD §5). Serialization is metadata only — it changes no
 * engine behaviour, and every engine test still holds.
 */
@Serializable
data class Plan(
    val id: String,
    val propertyType: PropertyType,
    val intent: Intent,
    val levels: List<Level>,            // ground floor is index 0
    val site: Site? = null,             // null when the user only has a unit plan
    val northOffsetDegrees: Int,        // 0..359 — bearing of true North, clockwise from plan "up"
    val schoolProfile: SchoolProfile = SchoolProfile.TRADITIONAL_8,
)

@Serializable
data class Level(
    val index: Int,
    val outline: List<Point>,           // footprint polygon, any orientation
    val rooms: List<Room> = emptyList(),
    val doors: List<Door> = emptyList(),
    val fixtures: List<Fixture> = emptyList(),
)

@Serializable
data class Room(
    val id: String,
    val type: RoomType,
    val polygon: List<Point>,           // supports L-shapes and non-convex
)

@Serializable
data class Door(
    val id: String,
    val centre: Point,
    val wallStart: Point,
    val wallEnd: Point,
    val isMainEntrance: Boolean,        // exactly one per Level 0 should be true
)

/** Idealised reference rectangle inputs for cut/extension detection (§4.2.7). */
@Serializable
data class Site(
    val plotOutline: List<Point>,
    val roads: List<Road> = emptyList(),
    val trees: List<Tree> = emptyList(),
)

@Serializable
data class Road(val id: String, val bearingDegrees: Int, val pointsAtPlot: Boolean = false)

@Serializable
data class Tree(val id: String, val position: Point, val heavy: Boolean = false)

/** Optional user input — enables the fixture defects (§4.1.1). Absent fixture ⇒ "not assessed". */
@Serializable
data class Fixture(
    val id: String,
    val type: FixtureType,
    val position: Point,
    val facingDegrees: Int? = null,     // BED head direction, platform orientation
    val underRoomId: String? = null,    // "toilet/store beneath a staircase"
)
