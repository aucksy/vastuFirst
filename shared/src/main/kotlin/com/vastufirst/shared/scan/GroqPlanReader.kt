// GroqPlanReader.kt — the real reader. One HTTP POST, and deliberately nothing else.
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

        val body = GroqWire
            .requestBody(recipe, Base64.getEncoder().encodeToString(image))
            .toByteArray(Charsets.UTF_8)

        var connection: HttpURLConnection? = null
        runCatching {
            val conn = (URI(recipe.config.endpoint).toURL().openConnection() as HttpURLConnection)
            connection = conn
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = recipe.config.connectTimeoutMs
            conn.readTimeout = recipe.config.readTimeoutMs
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            // Not decoration: Cloudflare fronts this API and answers an unrecognised client with its
            // own 403 (error 1010) before the request ever reaches Groq.
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
