package com.vastufirst.enginejs

import com.vastufirst.engine.VastuEngine
import com.vastufirst.rules.RuleSet
import com.vastufirst.rules.RuleSetLoader
import com.vastufirst.shared.Plan
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.js.JsExport

/**
 * The whole surface the admin panel talks to.
 *
 * Everything crosses the boundary as JSON text, on purpose: it keeps the exported signatures
 * trivial, and it means the browser is handling exactly the same bytes the phone would.
 *
 * The rules are always handed IN. There is no bundled dataset in a browser, so the panel passes
 * its draft — which is the point: the panel is asking "what would THIS do?", and the answer has
 * to come from the real engine rather than a guess.
 */
@JsExport
object VastuEngineJs {

    /**
     * Check a draft rule set the way the app checks it at start-up.
     *
     * Returns an empty string when the dataset is good. Otherwise it returns the loader's own
     * message, which already lists every problem as a plain sentence — the same text that would
     * stop the app opening, so the panel can show it verbatim and refuse to publish.
     */
    fun check(rulesetJson: String): String =
        try {
            load(rulesetJson)
            ""
        } catch (t: Throwable) {
            t.message ?: "The rules could not be read, and gave no reason."
        }

    /** Score one home under one draft rule set. Returns a JSON [ScoreOutcome]. */
    fun score(rulesetJson: String, planJson: String): String {
        val outcome = try {
            val engine = VastuEngine(load(rulesetJson))
            val plan = lenient.decodeFromString(Plan.serializer(), planJson)
            ScoreOutcome(ok = true, planId = plan.id, analysis = engine.analyze(plan).toDto())
        } catch (t: Throwable) {
            ScoreOutcome(ok = false, planId = "", error = t.message ?: "Could not score this home.")
        }
        return out.encodeToString(ScoreOutcome.serializer(), outcome)
    }

    /**
     * Score a whole corpus of homes under one draft, in a single crossing of the boundary.
     *
     * This is what the publish preview runs: one call, every saved home, so the panel can say how
     * many scores move and by how much. [plansJson] is a JSON array of plans; the answer is a JSON
     * array of outcomes in the same order. One unreadable home never stops the rest.
     */
    fun scoreAll(rulesetJson: String, plansJson: String): String {
        val results = mutableListOf<ScoreOutcome>()
        val engine = try {
            VastuEngine(load(rulesetJson))
        } catch (t: Throwable) {
            val why = t.message ?: "The rules could not be read."
            return out.encodeToString(
                ListSerializer(ScoreOutcome.serializer()),
                listOf(ScoreOutcome(ok = false, planId = "", error = why)),
            )
        }

        val plans: JsonArray = try {
            lenient.parseToJsonElement(plansJson).jsonArray
        } catch (t: Throwable) {
            return out.encodeToString(
                ListSerializer(ScoreOutcome.serializer()),
                listOf(ScoreOutcome(ok = false, planId = "", error = "That is not a list of homes.")),
            )
        }

        for (element in plans) {
            results += try {
                val plan = lenient.decodeFromJsonElement(Plan.serializer(), element)
                ScoreOutcome(ok = true, planId = plan.id, analysis = engine.analyze(plan).toDto())
            } catch (t: Throwable) {
                ScoreOutcome(ok = false, planId = "", error = t.message ?: "Could not read this home.")
            }
        }
        return out.encodeToString(ListSerializer(ScoreOutcome.serializer()), results)
    }

    /** The version stamp and change note of a draft, without scoring anything. */
    fun describe(rulesetJson: String): String {
        val d = try {
            val rs = load(rulesetJson)
            RuleSetInfo(
                ok = true,
                version = rs.version,
                changeNote = rs.changeNote,
                roomRules = rs.rooms.size,
                defects = rs.defects.size,
                remedies = rs.remedies.size,
                disputes = rs.disputes.size,
                doorPadas = rs.doorPadas.size,
                zones = rs.zones.size,
            )
        } catch (t: Throwable) {
            RuleSetInfo(ok = false, error = t.message ?: "The rules could not be read.")
        }
        return out.encodeToString(RuleSetInfo.serializer(), d)
    }

    // ---- the one place rules are turned into a RuleSet ----------------------------------------

    /**
     * Build a [RuleSet] from one JSON object holding the eight files under their bare names —
     * `{"meta": {...}, "config": {...}, "zones": [...], …}`.
     *
     * It goes through `RuleSetLoader.load`, so every structural and editorial check the app runs
     * at start-up runs here too, and a draft the app would refuse is a draft the panel refuses.
     */
    private fun load(rulesetJson: String): RuleSet {
        val bundle = lenient.parseToJsonElement(rulesetJson).jsonObject
        return RuleSetLoader.load { path ->
            val name = path.removePrefix("/ruleset/").removeSuffix(".json")
            val part = bundle[name] ?: error("The rules are missing $name.json.")
            part.toString()
        }
    }

    private val lenient = Json { ignoreUnknownKeys = true; isLenient = false }
    private val out = Json { encodeDefaults = true }
}

@Serializable
data class ScoreOutcome(
    val ok: Boolean,
    val planId: String,
    val analysis: AnalysisDto? = null,
    val error: String? = null,
)

@Serializable
data class RuleSetInfo(
    val ok: Boolean,
    val version: String = "",
    val changeNote: String? = null,
    val roomRules: Int = 0,
    val defects: Int = 0,
    val remedies: Int = 0,
    val disputes: Int = 0,
    val doorPadas: Int = 0,
    val zones: Int = 0,
    val error: String? = null,
)
