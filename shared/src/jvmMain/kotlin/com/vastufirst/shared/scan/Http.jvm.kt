package com.vastufirst.shared.scan

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android and the JVM: HttpURLConnection, the exact calls GroqPlanReader made before the iPhone plan's
 * stage 4 — fixed-length streaming, the error stream read on a non-2xx, disconnect at the end.
 */
actual fun platformHttp(): Http = Http { method, url, headers, body, connectTimeoutMs, readTimeoutMs ->
    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        runCatching {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            connection = conn
            conn.requestMethod = method
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            headers.forEach { (name, value) -> conn.setRequestProperty(name, value) }
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().decodeToString() }.orEmpty()
            HttpReply(status, text) { name -> conn.getHeaderField(name) }
        }.also {
            runCatching { connection?.disconnect() }
        }.getOrNull()
    }
}

internal actual val readerDispatcher: CoroutineDispatcher = Dispatchers.IO
