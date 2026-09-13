package com.vastufirst.rules

/**
 * The JavaScript stand-in for the JVM's classpath reader (see `rules/RuleSetResources.kt`, which
 * this build excludes).
 *
 * A browser has no classpath and no bundled dataset, so `RuleSetLoader.loadDefault()` cannot mean
 * anything here. The admin panel always hands its candidate rules in through
 * `RuleSetLoader.load { … }`, which is the path this whole module exists to exercise. Anything
 * reaching this function has taken a wrong turn, so it says so plainly rather than returning
 * empty text and failing later as a confusing validation error.
 */
internal fun readResource(path: String): String =
    error(
        "The rules have to be handed in. A browser has no bundled rule dataset, so " +
            "loadDefault() cannot work here — pass the eight JSON files to score() instead. " +
            "(asked for $path)"
    )
