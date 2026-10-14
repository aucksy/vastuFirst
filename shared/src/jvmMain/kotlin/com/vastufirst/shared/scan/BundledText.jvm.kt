package com.vastufirst.shared.scan

/** Android and the JVM tests: the classpath, exactly as before the iPhone plan's stage 1. */
internal actual fun bundledText(path: String): String? =
    ScanReaderConfigLoader::class.java.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }
