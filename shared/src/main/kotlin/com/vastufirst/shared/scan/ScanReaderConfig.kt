// ScanReaderConfig.kt — the plan reader's transport settings, loaded from data.
//
// ⭐ THE MODEL ID IS NEVER A KOTLIN CONSTANT. Both Llama 4 vision models were retired mid-2026
// (Scout on 17 July, twelve days before this feature was designed); code written from prior
// knowledge would have failed on its first call. Treating "the model was retired" as a first-class,
// config-editable failure mode is the same rule the ruleset already follows: values live in JSON,
// versioned, never compiled in.
//
// Fail-loud on load, exactly like the ruleset loader: a config this app cannot parse is a
// programming error we want at startup with a readable message, not a silent fallback that sends
// requests to nowhere.
package com.vastufirst.shared.scan

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The transport half of a plan read. The prompt is named here and loaded alongside. */
@Serializable
data class ScanReaderConfig(
    val endpoint: String = "",
    val model: String = "",
    /** `none` keeps a reasoning model from billing deliberation tokens on an extraction job. */
    val reasoningEffort: String? = null,
    /**
     * ⭐ The second-opinion model, asked ONCE when the primary classifies the image as not a 2D
     * plan. Measured (plan doc §3p): the primary reads every classic sheet best-in-class for a
     * tenth of a rupee but refuses straight-overhead furnished renders; this model is the only
     * candidate that reads that class. Blank or absent = no escalation, exactly the old
     * behaviour.
     */
    val escalationModel: String? = null,
    /**
     * ⭐⭐ HOW MANY TIMES ONE PHOTOGRAPH IS READ, and the fuller answer kept. See
     * [GroqWire.fullerOf] for the measurement: the same JPEG, same model, same prompt, read twice
     * on 19 Sep 2026, gave 20 rooms one time and 14 the other — the thin read silently dropped four
     * balconies, a lift lobby and a utility, and looked exactly as confident about it.
     *
     * 1 is the old behaviour. 2 doubles the cost of a scan, which is why it is a config value the
     * owner can turn back down without a release rather than a constant compiled into the app.
     * The reads are made concurrently, so the user waits for the slower of the two, not for both.
     */
    val readsPerScan: Int = 1,
    val temperature: Double = 0.0,
    /** Cloudflare fronts the API and 403s an unrecognised client before it reaches Groq. */
    val userAgent: String = "",
    val promptResource: String = "",
    val connectTimeoutMs: Int = 20_000,
    val readTimeoutMs: Int = 90_000,
)

/** A config plus the prompt text it names — everything needed to build one request. */
data class PlanReadRecipe(val config: ScanReaderConfig, val prompt: String)

object ScanReaderConfigLoader {

    const val RESOURCE: String = "/scan/reader-config.json"

    private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Load and validate. Throws — with a readable reason — rather than degrade silently. */
    fun load(resource: String = RESOURCE): PlanReadRecipe {
        val raw = text(resource) ?: error("Scan reader config missing from the app: $resource")
        val config = runCatching { JSON.decodeFromString<ScanReaderConfig>(raw) }
            .getOrElse { error("Scan reader config is not valid JSON ($resource): ${it.message}") }

        val prompt = text(config.promptResource)
            ?: error("Scan reader prompt missing from the app: ${config.promptResource}")
        return validate(config, prompt)
    }

    /**
     * Every rule a config must satisfy, separate from where it was read from — so each one can be
     * tested by handing it a value, rather than by shipping a broken file to find out.
     */
    fun validate(config: ScanReaderConfig, prompt: String): PlanReadRecipe {
        require(config.endpoint.startsWith("https://")) {
            "Scan reader endpoint must be https, was '${config.endpoint}'"
        }
        require(config.model.isNotBlank()) { "Scan reader config names no model" }
        require(config.userAgent.isNotBlank()) {
            "Scan reader config sets no User-Agent — Cloudflare rejects the default one"
        }
        require(config.connectTimeoutMs > 0 && config.readTimeoutMs > 0) {
            "Scan reader timeouts must be positive"
        }
        // ⚠ Every read is a paid scan of a customer's plan, so a typo here does not misbehave
        // quietly — it spends the owner's money once per scan for as long as nobody notices. The
        // ceiling is deliberately low: nothing measured suggests a third read buys what a second does.
        require(config.readsPerScan in 1..3) {
            "Scan reader readsPerScan must be 1, 2 or 3 — was ${config.readsPerScan}, and each one is a paid scan"
        }
        require(prompt.isNotBlank()) { "Scan reader prompt is empty" }
        return PlanReadRecipe(config, prompt.trim())
    }

    private fun text(resource: String): String? =
        ScanReaderConfigLoader::class.java.getResourceAsStream(resource)
            ?.use { it.readBytes().decodeToString() }
}
