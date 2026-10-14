package com.vastufirst.rules

/** iPhone: the same rule files, compiled into the code from src/main/resources (bundleRulesForIos). */
internal actual fun bundledText(path: String): String? = bundledRuleFiles[path]
