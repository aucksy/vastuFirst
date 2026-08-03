// GroqWire.kt — everything about the real call that can be decided WITHOUT a network.
//
// The transport itself (GroqPlanReader) is twenty lines of HttpURLConnection that no test can prove
// without either a key or a fake server. Everything that can actually be wrong — the request shape,
// digging the model's answer out of the envelope, turning an HTTP status into something the user
// sees, reading a rate-limit duration like "2m52.8s" — is pure, and lives here where `:shared:test`
// runs it on every push. Same split that has held for the editor: the arithmetic goes in a pure
// module, the finger (or the socket) stays thin.
package com.vastufirst.shared.scan

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.ceil

object GroqWire {

    private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Headers the reader reads, in the order it prefers them. `x-ratelimit-reset-tokens` is the one
     * that matters: tokens-per-minute is the binding limit (8 000/min against ~2 900 a scan, so two
     * plans back to back and the third waits), and Groq reports the real reset as a duration string.
     */
    const val HEADER_RESET_TOKENS = "x-ratelimit-reset-tokens"
    const val HEADER_RESET_REQUESTS = "x-ratelimit-reset-requests"
    const val HEADER_RETRY_AFTER = "retry-after"

    /** Longest wait we will quote. Beyond this "in a minute" is more use than a precise number. */
    private const val MAX_WAIT_SECONDS = 3600

    /** ASCII-only by discipline: this module is tested on the JVM and runs on Android. */
    private val DURATION_PART = Regex("([0-9]+(?:\\.[0-9]+)?)(ms|h|m|s)")
    private val PLAIN_NUMBER = Regex("^[0-9]+(?:\\.[0-9]+)?$")

    /**
     * One chat-completions request: the measured prompt, then the image as a data URL.
     *
     * Built through the JSON writer rather than string concatenation — the prompt contains quotes,
     * braces and newlines, and hand-escaping it is a bug waiting to happen in the one place where a
     * malformed body looks exactly like a model failure.
     */
    /**
     * [model] defaults to the config's primary; the escalation call passes
     * [ScanReaderConfig.escalationModel] and everything else about the request stays identical —
     * same prompt, same image, same shape — so the two replies are comparable by construction.
     */
    fun requestBody(
        recipe: PlanReadRecipe,
        base64Image: String,
        mime: String = "image/jpeg",
        model: String = recipe.config.model,
    ): String =
        buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", recipe.prompt)
                        }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:$mime;base64,$base64Image")
                            }
                        }
                    }
                }
            }
            putJsonObject("response_format") { put("type", "json_object") }
            put("temperature", recipe.config.temperature)
            recipe.config.reasoningEffort?.takeIf { it.isNotBlank() }
                ?.let { put("reasoning_effort", it) }
        }.toString()

    /** The model's answer, dug out of the chat envelope. Null if the envelope isn't what we expect. */
    fun contentOf(responseBody: String): String? = runCatching {
        val root = JSON.parseToJsonElement(responseBody).jsonObject
        val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
        message?.get("content")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * A successful HTTP call → what the plan says.
     *
     * A reply we cannot parse is [ScanResult.Unavailable], **not** a refusal: a refusal tells the
     * user their plan is the problem and asks them to change it, and it would be a lie here. This
     * failed on our side and trying again is a reasonable thing to suggest.
     */
    fun readOutcome(responseBody: String, imageAspect: Double?): ScanResult {
        val content = contentOf(responseBody) ?: return ScanResult.Unavailable
        val draft = RecordedScans.parseDraft(content) ?: return ScanResult.Unavailable
        return ScanResult.Read(ScanMapper.map(draft, imageAspect))
    }

    /**
     * A non-2xx HTTP status → what the user sees. [header] looks a response header up by name,
     * case-insensitively as far as the caller's client allows.
     *
     * 429 is the only one that is not a failure: it is the free tier working as documented, and it
     * becomes a wait with a real number in it. Everything else — a dead key, Cloudflare, a retired
     * model, a 500 — lands on "we couldn't read your plan just now", which is true, is not the
     * user's fault, and always offers the offline grid.
     */
    fun mapStatus(status: Int, header: (String) -> String?): ScanResult =
        if (status == 429) {
            ScanResult.Busy(
                durationSeconds(header(HEADER_RESET_TOKENS))
                    ?: durationSeconds(header(HEADER_RETRY_AFTER))
                    ?: durationSeconds(header(HEADER_RESET_REQUESTS)),
            )
        } else {
            ScanResult.Unavailable
        }

    /**
     * Groq states rate-limit resets as durations, not seconds: `53.812s`, `2m52.8s`, sometimes
     * `500ms`; `Retry-After` is plain seconds. Rounded UP, because telling someone to try again a
     * moment early just earns them a second 429.
     *
     * Returns null when there is nothing to read, so the screen says "in a minute" rather than
     * inventing a number.
     */
    fun durationSeconds(raw: String?): Int? {
        val text = raw?.trim()?.lowercase() ?: return null
        if (text.isEmpty()) return null

        if (PLAIN_NUMBER.matches(text)) return quantise(text.toDouble())

        var total = 0.0
        var matched = false
        DURATION_PART.findAll(text).forEach { m ->
            val value = m.groupValues[1].toDoubleOrNull() ?: return@forEach
            total += when (m.groupValues[2]) {
                "ms" -> value / 1000.0
                "s" -> value
                "m" -> value * 60.0
                "h" -> value * 3600.0
                else -> 0.0
            }
            matched = true
        }
        return if (matched) quantise(total) else null
    }

    /** At least a second (a "0 seconds" wait reads as broken), never more than an hour. */
    private fun quantise(seconds: Double): Int? {
        if (seconds.isNaN() || seconds < 0.0) return null
        return ceil(seconds).toInt().coerceIn(1, MAX_WAIT_SECONDS)
    }

    /** Visible for tests: the request body as JSON, so its shape can be asserted field by field. */
    fun parseObject(json: String): JsonObject = JSON.parseToJsonElement(json).jsonObject
}
