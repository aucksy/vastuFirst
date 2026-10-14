package com.vastufirst.rules

/** Android and the JVM tests: the rule files are on the classpath, exactly as before stage 1. */
internal actual fun bundledText(path: String): String? =
    RuleSetLoader::class.java.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
