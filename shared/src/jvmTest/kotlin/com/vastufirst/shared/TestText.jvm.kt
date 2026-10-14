package com.vastufirst.shared

internal actual fun testText(path: String): String? =
    object {}.javaClass.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
