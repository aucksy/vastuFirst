package com.vastufirst.app.update

import java.net.HttpURLConnection
import java.net.URI

/** Android: the same HttpURLConnection GET the app has always used — 8 s each way, 200 or nothing. */
actual fun defaultRulesFetch(url: String): String? = try {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 8_000
    connection.readTimeout = 8_000
    connection.requestMethod = "GET"
    connection.setRequestProperty("Accept", "application/json")
    try {
        if (connection.responseCode != 200) null
        else connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
} catch (_: Throwable) {
    null
}
