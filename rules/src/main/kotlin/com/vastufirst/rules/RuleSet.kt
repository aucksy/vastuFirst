package com.vastufirst.rules

import com.vastufirst.shared.DefectDefinition
import com.vastufirst.shared.Dispute
import com.vastufirst.shared.DoorPada
import com.vastufirst.shared.PadaVerdict
import com.vastufirst.shared.Remedy
import com.vastufirst.shared.RoomRule
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.RulesetConfig
import com.vastufirst.shared.RulesetMeta
import com.vastufirst.shared.ZoneInfo

/**
 * The whole in-memory rule dataset, loaded once from the versioned JSON (Product PRD §5.1).
 * Everything the engine reads comes from here — no Vastu value is a Kotlin constant (§0.8).
 */
data class RuleSet(
    val meta: RulesetMeta,
    val config: RulesetConfig,
    val zones: List<ZoneInfo>,
    val rooms: List<RoomRule>,
    val doorPadas: List<DoorPada>,
    val defects: List<DefectDefinition>,
    val disputes: List<Dispute>,
    val remedies: List<Remedy>,
) {
    val version: String get() = meta.version

    private val roomByType: Map<RoomType, RoomRule> = rooms.associateBy { it.roomType }
    private val remedyById: Map<String, Remedy> = remedies.associateBy { it.id }
    private val disputeById: Map<String, Dispute> = disputes.associateBy { it.id }

    fun ruleFor(type: RoomType): RoomRule? = roomByType[type]
    fun isUnruled(type: RoomType): Boolean = type in config.unruledRoomTypes
    fun remedy(id: String): Remedy? = remedyById[id]
    fun remediesFor(def: DefectDefinition): List<Remedy> =
        def.remedyIds.mapNotNull { remedyById[it] }.sortedBy { it.rank }
    fun dispute(id: String): Dispute? = disputeById[id]

    /** Resolve a bearing (0..360, clockwise from North) to its door pada. Handles the N1 wrap. */
    fun padaAtBearing(bearing: Double): DoorPada {
        val b = ((bearing % 360.0) + 360.0) % 360.0
        // Each pada spans [startBearing, startBearing + 11.25). N1 starts at 348.75 and wraps to 360.
        return doorPadas.firstOrNull { b >= it.startBearing && b < it.startBearing + PADA_SPAN }
            ?: doorPadas.first { it.startBearing >= 348.75 } // wrap: b in [348.75, 360)
    }

    companion object {
        const val PADA_SPAN = 11.25
    }
}
