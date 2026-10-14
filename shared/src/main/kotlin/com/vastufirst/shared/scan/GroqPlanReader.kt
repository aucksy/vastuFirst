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
// ⭐ SHARED BY BOTH PHONES since 14 Oct 2026 (iPhone plan, stage 4). The one thing each phone does
// its own way — the HTTP exchange itself — is the [Http] doorway (Http.kt): HttpURLConnection on
// Android (exactly the calls this file made before), NSURLSession on the iPhone. Base64 is Kotlin's
// own, which writes the same standard, padded, unbroken text java.util.Base64's encoder did.
package com.vastufirst.shared.scan

import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class GroqPlanReader(
    private val apiKey: String,
    private val recipe: PlanReadRecipe = ScanReaderConfigLoader.load(),
    private val io: CoroutineDispatcher = readerDispatcher,
    private val http: Http = platformHttp(),
) : PlanReader {

    override suspend fun read(image: ByteArray, imageAspect: Double?, picture: PlanImage?): ScanResult = withContext(io) {
        // A build made without the key must not pretend to read anything. The screen checks this
        // first and says so plainly; this is the belt to that braces.
        if (!recipe.canRead(apiKey) || image.isEmpty()) return@withContext ScanResult.Unavailable

        val base64 = Base64.Default.encode(image)

        // ⭐ THE SAME PHOTOGRAPH, READ MORE THAN ONCE, AND THE FULLER ANSWER KEPT.
        // The reader is not deterministic: on the owner's own flat the identical JPEG came back
        // with 20 rooms one time and 14 the other, the thin read having silently dropped four
        // balconies. GroqWire.fullerOf carries the measurement and the ordering; the count is
        // config, not a constant. Concurrently, so the user waits for the slower read and not for
        // the sum of both.
        val reads = recipe.config.readsPerScan.coerceIn(1, 3)
        val primary = if (reads == 1) {
            postOnce(GroqWire.requestBody(recipe, base64), imageAspect, picture)
                .attributedTo(recipe.config.model)
        } else {
            coroutineScope {
                (1..reads)
                    .map { async { postOnce(GroqWire.requestBody(recipe, base64), imageAspect, picture) } }
                    .awaitAll()
                    .reduce(GroqWire::fullerOf)
                    .attributedTo(recipe.config.model)
            }
        }

        val escalation = recipe.config.escalationModel
        if (escalation.isNullOrBlank() || !refusedAsNot2d(primary)) return@withContext primary

        val second = postOnce(GroqWire.requestBody(recipe, base64, model = escalation), imageAspect, picture)
            .attributedTo(escalation)
        if (second is ScanResult.Read && second.outcome !is ScanOutcome.Refused) second else primary
    }

    /**
     * ⭐ One model, by name, no escalation (owner's testing lever, 4 Aug 2026). The results screen
     * offers "rescan with …" for each configured model so reads can be compared on the same picture;
     * an explicit choice must be deterministic, so the second-opinion machinery deliberately does
     * not run here.
     */
    override suspend fun readWith(model: String, image: ByteArray, imageAspect: Double?, picture: PlanImage?): ScanResult =
        withContext(io) {
            if (!recipe.canRead(apiKey) || image.isEmpty() || model.isBlank()) return@withContext ScanResult.Unavailable
            val base64 = Base64.Default.encode(image)
            postOnce(GroqWire.requestBody(recipe, base64, model = model), imageAspect, picture).attributedTo(model)
        }

    /** Stamp which model produced a successful answer; failures pass through untouched. */
    private fun ScanResult.attributedTo(model: String): ScanResult =
        if (this is ScanResult.Read) copy(readBy = model) else this

    /** True when the model itself said "this image is not a 2D plan" — the only escalation cue. */
    private fun refusedAsNot2d(result: ScanResult): Boolean {
        val outcome = (result as? ScanResult.Read)?.outcome as? ScanOutcome.Refused ?: return false
        return outcome.reason == RefusalReason.NOT_2D
    }

    private suspend fun postOnce(bodyText: String, imageAspect: Double?, picture: PlanImage?): ScanResult {
        val headers = buildMap {
            if (recipe.config.useRelay) {
                // Our relay adds the key itself; this header only tells it the request is the app's.
                put("X-VastuFirst-App", recipe.config.userAgent.ifBlank { "vastufirst" })
            } else {
                put("Authorization", "Bearer $apiKey")
            }
            put("Content-Type", "application/json")
            put("Accept", "application/json")
            put("User-Agent", recipe.config.userAgent)
        }
        val reply = runCatching {
            http.exchange(
                method = "POST",
                url = recipe.target,
                headers = headers,
                body = bodyText,
                connectTimeoutMs = recipe.config.connectTimeoutMs,
                readTimeoutMs = recipe.config.readTimeoutMs,
            )
        }.getOrNull() ?: return ScanResult.Unavailable
        return if (reply.status in 200..299) {
            GroqWire.readOutcome(reply.body, imageAspect, picture)
        } else {
            GroqWire.mapStatus(reply.status) { name -> reply.header(name) }
        }
    }
}
