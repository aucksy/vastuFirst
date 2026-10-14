package com.vastufirst.shared

internal actual fun testText(path: String): String? = bundledSharedTestFiles[path]
