package com.vastufirst.shared.scan

/** iPhone: the same files, compiled in from src/main/resources (bundleSharedForIos). */
internal actual fun bundledText(path: String): String? = bundledSharedFiles[path]
