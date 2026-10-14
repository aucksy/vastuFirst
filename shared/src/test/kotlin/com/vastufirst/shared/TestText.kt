package com.vastufirst.shared

/**
 * A test fixture's text by classpath-style path ("/update/signed-reply.json"), or null. The JVM reads
 * src/test/resources off the classpath; the iPhone reads the same files compiled into the test code
 * (bundleSharedTestForIos). Pictures are JVM-only and are not reachable here.
 */
internal expect fun testText(path: String): String?
