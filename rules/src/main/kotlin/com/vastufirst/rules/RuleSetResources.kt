package com.vastufirst.rules

/**
 * Reading the bundled rule dataset off the classpath — the one thing in this module that only a
 * JVM can do.
 *
 * **Why it is a file of its own.** The engine is compiled twice: once for the phone, and once to
 * JavaScript so the admin panel re-scores a home with THIS code instead of a second scorer that
 * drifts. Everything else in `rules` is plain Kotlin and compiles for both. This does not, so the
 * JavaScript build excludes this single file and supplies its own version of the same function,
 * which refuses and says the rules must be handed in. `RuleSetLoader` itself is untouched by the
 * split, and so is every caller of `loadDefault()`.
 *
 * Do not inline this back into RuleSetLoader.kt.
 */
internal fun readResource(path: String): String =
    RuleSetLoader::class.java.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
        ?: error("Rule dataset resource missing: $path")
