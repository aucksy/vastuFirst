// GroqPlanReader.kt — the real reader. One HTTP POST, and deliberately nothing else.
//
// (The name is history, the class is not Groq's: it speaks the OpenAI chat-completions shape to
// whatever `reader-config.json` names — Groq until 3 Aug 2026, OpenRouter since. The provider is
// config data; this file is a socket.)
//
// Everything that can be wrong is next door in GroqWire (pure, tested on every push). What is left
// here is a socket, and it obeys three rules:
//
//   1. NEVER THROWS. A failure is a `ScanResult`, because §6.2b requires the fallback to the guided
//      grid to happen "without an error state" — a thrown exception in a ViewModel coroutine is an
//      error state with a crash attached.
//   2. NEVER LOGS the key, the request body or the reply. The body carries a photograph of somebody's
//      home; logcat is readable by anyone with a cable.
//   3. NEVER RUNS ON THE MAIN THREAD. The dispatch is inside this class, not left to the caller: a
//      blocking suspend function that trusts every future caller to remember `withContext` is one
//      refactor away from an ANR.
//
// ⭐ THE SECOND OPINION. Measured across six models on seven real sheets (plan doc §3p): the primary
// model reads every classic 2D sheet best-in-class for a tenth of a rupee, but classifies a
// straight-overhead FURNISHED render as "not a 2D plan" — a class the escalation model reads at
// 100 % arrangement. So when (and only when) the primary's own reply says NOT_2D, one more call is
// made with the escalation model, and its answer is used only if it actually reads the plan. A
// genuine photo-of-not-a-plan refuses twice and the user sees the original honest refusal.
//
// No dependency added to the app: `HttpURLConnection` and `java.util.Base64` are both in the
// platform (Base64 since API 26, which is our minimum), so the first networked feature in an
// otherwise offline app costs 0 KB of the 30 MB budget.
package com.vastufirst.shared.scan

import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GroqPlanReader(
    private val apiKey: String,
    private val recipe: PlanReadRecipe = ScanReaderConfigLoader.load(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : PlanReader {

    override suspend fun read(image: ByteArray, imageAspect: Double?): ScanResult = withContext(io) {
        // A build made without the key must not pretend to read anything. The screen checks this
        // first and says so plainly; this is the belt to that braces.
        if (apiKey.isBlank() || image.isEmpty()) return@withContext ScanResult.Unavailable

        val base64 = Base64.getEncoder().encodeToString(image)
        val primary = postOnce(GroqWire.requestBody(recipe, base64), imageAspect)

        val escalation = recipe.config.escalationModel
        if (escalation.isNullOrBlank() || !refusedAsNot2d(primary)) return@withContext primary

        val second = postOnce(GroqWire.requestBody(recipe, base64, model = escalation), imageAspect)
        if (second is ScanResult.Read && second.outcome !is ScanOutcome.Refused) second else primary
    }

    /** True when the model itself said "this image is not a 2D plan" — the only escalation cue. */
    private fun refusedAsNot2d(result: ScanResult): Boolean {
        val outcome = (result as? ScanResult.Read)?.outcome as? ScanOutcome.Refused ?: return false
        return outcome.reason == RefusalReason.NOT_2D
    }

    /** One POST → one [ScanResult]. Never throws; see the rules at the top of the file. */
    private fun postOnce(bodyText: String, imageAspect: Double?): ScanResult {
        val body = bodyText.toByteArray(Charsets.UTF_8)
        var connection: HttpURLConnection? = null
        return runCatching {
            val conn = (URI(recipe.config.endpoint).toURL().openConnection() as HttpURLConnection)
            connection = conn
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = recipe.config.connectTimeoutMs
            conn.readTimeout = recipe.config.readTimeoutMs
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            // Not decoration: Cloudflare fronts these APIs and answers an unrecognised client with
            // its own 403 (error 1010) before the request ever reaches the model.
            conn.setRequestProperty("User-Agent", recipe.config.userAgent)
            // Known length, so ~180 KB of base64 isn't buffered a second time inside the connection.
            conn.setFixedLengthStreamingMode(body.size)

            conn.outputStream.use { it.write(body) }

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().decodeToString() }.orEmpty()

            if (status in 200..299) {
                GroqWire.readOutcome(text, imageAspect)
            } else {
                GroqWire.mapStatus(status) { name -> conn.getHeaderField(name) }
            }
        }.also {
            runCatching { connection?.disconnect() }
        }.getOrElse {
            // Offline, DNS, timeout, TLS, a truncated reply: all the same thing to the user, and all
            // of them are worth trying again. The exception itself is deliberately not logged.
            ScanResult.Unavailable
        }
    }
}
