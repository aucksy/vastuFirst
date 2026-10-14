@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vastufirst.shared.update

import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyVerifySignature
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256

/**
 * iPhone: Apple's Security framework. The key arrives in Java's X.509 wrapper, and Apple's import
 * takes only the bare RSA key inside it — so [spkiToPkcs1] takes the wrapper off first (the trap the
 * iPhone plan lists as risk 8: without it every published rule set would be refused, silently).
 */
internal actual fun verifyRsaSha256(message: ByteArray, signature: ByteArray, spkiDer: ByteArray): Boolean {
    val bareKey = spkiToPkcs1(spkiDer) ?: return false
    if (message.isEmpty() || signature.isEmpty()) return false
    val keyData = bareKey.toCFData() ?: return false
    val messageData = message.toCFData()
    val signatureData = signature.toCFData()
    val attributes = CFDictionaryCreateMutable(null, 2, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    try {
        CFDictionaryAddValue(attributes, kSecAttrKeyType, kSecAttrKeyTypeRSA)
        CFDictionaryAddValue(attributes, kSecAttrKeyClass, kSecAttrKeyClassPublic)
        val key = SecKeyCreateWithData(keyData, attributes, null) ?: return false
        try {
            return SecKeyVerifySignature(key, kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256, messageData, signatureData, null)
        } finally {
            CFRelease(key)
        }
    } finally {
        CFRelease(attributes)
        CFRelease(keyData)
        if (messageData != null) CFRelease(messageData)
        if (signatureData != null) CFRelease(signatureData)
    }
}

private fun ByteArray.toCFData(): CFDataRef? = usePinned { pinned ->
    CFDataCreate(null, pinned.addressOf(0).reinterpret<UByteVar>(), size.toLong())
}
