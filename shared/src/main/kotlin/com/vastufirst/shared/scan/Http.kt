package com.vastufirst.shared.scan

import kotlinx.coroutines.CoroutineDispatcher

/** One HTTP answer: its status, its body as text, and its headers by name. */
class HttpReply(val status: Int, val body: String, private val headers: (String) -> String?) {
    fun header(name: String): String? = headers(name)
}

/**
 * ⭐ THE ONE THING EACH PHONE DOES ITS OWN WAY on the network (iPhone plan, stage 4): send one request,
 * hand back the answer — or null when nothing came back at all (no network, a timeout). Never throws.
 * Android: HttpURLConnection. iPhone: NSURLSession.
 */
fun interface Http {
    suspend fun exchange(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): HttpReply?
}

/** The phone's own HTTP. */
expect fun platformHttp(): Http

/** Where network work runs: off the main thread on both phones. */
internal expect val readerDispatcher: CoroutineDispatcher
