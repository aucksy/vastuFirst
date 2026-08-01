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

    /** The free score screen shows a reason's first sentence and nothing more (see §4c-ii). */
    private const val MAX_OPENING_SENTENCE = 120

    /** A real explanation of a rule change, not a label like "updated rules" (see §4f). */
    private const val MIN_CHANGE_NOTE = 120

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

        // 1. Exactly 32 door padas, ordinals 1..32 contiguous, ids unique, EACH SIDE covers 8,
        //    and — the part that actually matters for door resolution — the startBearings tile
        //    [0,360) at exact 11.25° steps with no gap or overlap. Without this a corrupt bearing
        //    silently resolves a door to the wrong (N1) pada instead of failing loud (§5.1).
        if (rs.doorPadas.size != 32) errors += "Expected 32 door padas, found ${rs.doorPadas.size}."
        val ordinals = rs.doorPadas.map { it.ordinal }.sorted()
        if (ordinals != (1..32).toList()) errors += "Door pada ordinals must be 1..32 contiguous; got $ordinals."
        val ids = rs.doorPadas.map { it.id }
        if (ids.toSet().size != ids.size) errors += "Duplicate door pada ids: ${ids.groupingBy { it }.eachCount().filter { it.value > 1 }.keys}."
        listOf(com.vastufirst.shared.Zone.N, com.vastufirst.shared.Zone.E,
            com.vastufirst.shared.Zone.S, com.vastufirst.shared.Zone.W).forEach { side ->
            val n = rs.doorPadas.count { it.side == side }
            if (n != 8) errors += "Door side $side must have 8 padas; found $n."
        }
        val bearings = rs.doorPadas.map { it.startBearing }.sorted()
        bearings.forEachIndexed { i, b ->
            val expected = i * 11.25
            if (kotlin.math.abs(b - expected) > 1e-6) {
                errors += "Door pada startBearings must tile [0,360) at 11.25° steps; index $i is $b, expected $expected."
            }
        }

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

        // 4b. ⭐ EVERY defect must offer something beyond the two universal answers ("move it" and
        //     Vastu Shanti) — or say IN WORDS why it cannot. Thirteen of fifteen problems once
        //     offered the customer the identical two lines, which is what a ₹699 report must never do.
        //     The honest escape is `remedyNote`, not an invented remedy: where the classical texts
        //     record no cure, saying so IS the answer.
        val universal = setOf("structural-correction", "vastu-shanti")
        rs.defects.forEach { d ->
            val specific = d.remedyIds.any { it !in universal }
            if (!specific && d.remedyNote.isNullOrBlank()) {
                errors += "Defect ${d.id} offers only the universal remedies and gives no remedyNote " +
                    "explaining why nothing more specific exists."
            }
        }

        // 4c. ⭐ EVERY (room type, prohibited zone) pair must resolve to its OWN defect definition.
        //     X-GEN stays as the last-resort fallback for anomalies, but a room the app rules on must
        //     never reach the reader as "this room sits in a zone its placement rule prohibits".
        val claimed = HashMap<Pair<RoomType, com.vastufirst.shared.Zone>, MutableList<String>>()
        rs.defects.forEach { d -> d.appliesTo.forEach { m -> claimed.getOrPut(m.roomType to m.zone) { mutableListOf() } += d.id } }
        claimed.filterValues { it.size > 1 }.forEach { (pair, ids) ->
            errors += "More than one defect claims ${pair.first} in ${pair.second}: $ids."
        }
        rs.rooms.forEach { rule ->
            rule.prohibited.forEach { zone ->
                if ((rule.roomType to zone) !in claimed) {
                    errors += "${rule.roomType} is prohibited in $zone but no defect claims that pair — " +
                        "the reader would get the generic X-GEN text instead of a real reason."
                }
            }
        }

        // 4c-ii. ⭐ THE OPENING SENTENCE IS A HEADLINE, and its length is load-bearing. The free
        //        score screen shows only the first sentence of a reason — that IS the free/paid
        //        split. One reason opened with a 201-character sentence and the free screen turned
        //        into a wall of text that pushed the payment notice below the fold on a 320 dp
        //        phone. So the first sentence must say the problem in one breath.
        rs.defects.forEach { d ->
            val cut = d.explanation.indexOf(". ")
            val opening = if (cut <= 0) d.explanation else d.explanation.substring(0, cut + 1)
            if (opening.length > MAX_OPENING_SENTENCE) {
                errors += "Defect ${d.id} opens with a ${opening.length}-character sentence; the free " +
                    "screen shows only that, so it must be at most $MAX_OPENING_SENTENCE."
            }
            if (d.explanation.length <= opening.length) {
                errors += "Defect ${d.id} is one sentence long — the paid report would add nothing to " +
                    "the free preview."
            }
        }

        // 4d. A rule with no rationale leaves the "already right" and "not ideal" sections with a
        //     room name, a direction and nothing else — which is the state the report was in.
        rs.rooms.forEach { r ->
            if (r.rationale.isNullOrBlank()) errors += "Room rule ${r.roomType} has no rationale."
        }

        // 4e. A Tier C/D rule with no plain label printed its raw id ("X-09") to a paying customer.
        rs.defects.forEach { d ->
            val needsLabel = d.requiresFixtureTypes.isNotEmpty() || d.requiresSite
            if (needsLabel && d.notCheckedLabel.isNullOrBlank()) {
                errors += "Defect ${d.id} can land in the 'couldn't check' list but has no notCheckedLabel."
            }
        }

        // 4f. ⭐ THE DATASET MUST BE ABLE TO EXPLAIN ITSELF. Every saved home carries the rule
        //     version it was scored under; when that version moves, the app re-scores those homes and
        //     shows this sentence next to the old and new number. Without it the app would either
        //     change somebody's score in silence or announce the change and refuse to say why — and
        //     a rule edit is exactly the moment nobody remembers to write the sentence. So the build
        //     asks for it here, where the rules live, rather than hoping.
        val note = rs.meta.changeNote
        if (note.isNullOrBlank()) {
            errors += "meta.changeNote is missing — a rule change must ship with the plain-words " +
                "explanation shown to anyone whose saved score it moves."
        } else if (note.length < MIN_CHANGE_NOTE) {
            errors += "meta.changeNote is ${note.length} characters; it is the only thing a user " +
                "gets to read about their score moving, so it must be at least $MIN_CHANGE_NOTE."
        }

        // 5. The Brahmasthan extent must be one the engine actually implements — the config knob
        //    must not silently fall back to 3×3 (M-05 is unresolved; only CENTRAL_3X3 is built).
        if (rs.config.brahmasthanExtent != "CENTRAL_3X3") {
            errors += "config.brahmasthanExtent '${rs.config.brahmasthanExtent}' is not implemented; " +
                "only CENTRAL_3X3 is built (M-05 is unresolved — §13)."
        }
        if (rs.config.gridSize % 3 != 0) errors += "config.gridSize ${rs.config.gridSize} must be divisible by 3."

        // 6. Every config lookup table covers its enum.
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
