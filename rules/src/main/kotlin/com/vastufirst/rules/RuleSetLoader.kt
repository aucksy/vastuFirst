package com.vastufirst.rules

import com.vastufirst.shared.DefectDefinition
import com.vastufirst.shared.Dispute
import com.vastufirst.shared.DoorPada
import com.vastufirst.shared.Remedy
import com.vastufirst.shared.RoomRule
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.RulesetConfig
import com.vastufirst.shared.RulesetMeta
import com.vastufirst.shared.ZoneInfo
import kotlinx.serialization.json.Json

/**
 * Loads and validates the versioned rule dataset (Product PRD §5.1).
 *
 * **Fail loud, never mis-score silently.** Every structural guarantee the engine relies on
 * (32 contiguous padas, every RoomType ruled-or-unruled, disputeIds resolve, every defect
 * has a remedy) is checked here at load time. A broken dataset throws at startup rather than
 * producing a wrong score at runtime.
 */
object RuleSetLoader {

    private val json = Json {
        ignoreUnknownKeys = false   // a typo'd key in the dataset is a defect — surface it
        isLenient = false
    }

    private const val DIR = "/ruleset"

    /** Load the dataset bundled in this module's resources. Throws on any validation failure. */
    fun loadDefault(): RuleSet = load { path -> readResource(path) }

    /** Load with an injectable reader — used by tests to prove the "rules are data" property. */
    fun load(read: (String) -> String): RuleSet {
        val meta = json.decodeFromString<RulesetMeta>(read("$DIR/meta.json"))
        val config = json.decodeFromString<RulesetConfig>(read("$DIR/config.json"))
        val zones = json.decodeFromString<List<ZoneInfo>>(read("$DIR/zones.json"))
        val rooms = json.decodeFromString<List<RoomRule>>(read("$DIR/rooms.json"))
        val doorPadas = json.decodeFromString<List<DoorPada>>(read("$DIR/doorPadas.json"))
        val defects = json.decodeFromString<List<DefectDefinition>>(read("$DIR/defects.json"))
        val disputes = json.decodeFromString<List<Dispute>>(read("$DIR/disputes.json"))
        val remedies = json.decodeFromString<List<Remedy>>(read("$DIR/remedies.json"))

        val ruleSet = RuleSet(meta, config, zones, rooms, doorPadas, defects, disputes, remedies)
        validate(ruleSet)
        return ruleSet
    }

    private fun readResource(path: String): String =
        RuleSetLoader::class.java.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
            ?: error("Rule dataset resource missing: $path")

    private fun validate(rs: RuleSet) {
        val errors = mutableListOf<String>()

        // 1. Exactly 32 door padas, ordinals 1..32 contiguous, and every side covers 8.
        if (rs.doorPadas.size != 32) errors += "Expected 32 door padas, found ${rs.doorPadas.size}."
        val ordinals = rs.doorPadas.map { it.ordinal }.sorted()
        if (ordinals != (1..32).toList()) errors += "Door pada ordinals must be 1..32 contiguous; got $ordinals."
        val ids = rs.doorPadas.map { it.id }
        if (ids.toSet().size != ids.size) errors += "Duplicate door pada ids: ${ids.groupingBy { it }.eachCount().filter { it.value > 1 }.keys}."

        // 2. Every RoomType either has a rule or is explicitly listed as unruled.
        val ruled = rs.rooms.map { it.roomType }.toSet()
        val unruled = rs.config.unruledRoomTypes
        val overlap = ruled intersect unruled
        if (overlap.isNotEmpty()) errors += "RoomTypes are both ruled and unruled: $overlap."
        RoomType.entries.forEach { t ->
            if (t !in ruled && t !in unruled) errors += "RoomType $t is neither ruled nor listed as unruled."
        }
        rs.rooms.groupingBy { it.roomType }.eachCount().filter { it.value > 1 }.keys
            .takeIf { it.isNotEmpty() }?.let { errors += "Duplicate room rules for: $it." }

        // 3. Every disputeId referenced by a room resolves to a Dispute.
        val disputeIds = rs.disputes.map { it.id }.toSet()
        rs.rooms.mapNotNull { it.disputeId }.forEach {
            if (it !in disputeIds) errors += "Room references unknown disputeId '$it'."
        }

        // 4. Every defect has ≥1 remedy, and every referenced remedy exists.
        val remedyIds = rs.remedies.map { it.id }.toSet()
        rs.defects.forEach { d ->
            if (d.remedyIds.isEmpty()) errors += "Defect ${d.id} has no remedies."
            d.remedyIds.forEach { r -> if (r !in remedyIds) errors += "Defect ${d.id} references unknown remedy '$r'." }
        }
        if (rs.defects.none { it.id == "X-GEN" }) errors += "Dataset must define the fallback defect 'X-GEN'."

        // 5. Every config lookup table covers its enum.
        listOf("MAJOR", "MODERATE", "MINOR").forEach {
            if (it !in rs.config.penalties) errors += "config.penalties missing severity '$it'."
        }
        listOf("IDEAL", "ACCEPTABLE", "SUBOPTIMAL", "DEFECT").forEach {
            if (it !in rs.config.scorePoints) errors += "config.scorePoints missing verdict '$it'."
        }
        listOf("AUSPICIOUS", "MODERATE", "MIXED", "INAUSPICIOUS").forEach {
            if (it !in rs.config.padaPoints) errors += "config.padaPoints missing pada verdict '$it'."
        }

        if (errors.isNotEmpty()) {
            error("Rule dataset validation failed (${errors.size} problem(s)):\n" + errors.joinToString("\n") { "  - $it" })
        }
    }
}
