package com.vastufirst.shared.update

/**
 * True when [signature] is a valid RSA PKCS#1 v1.5 / SHA-256 signature over [message] under the key in
 * [spkiDer] (an X.509 SubjectPublicKeyInfo, DER — the form the Control Room publishes and Java reads).
 *
 * Each phone checks with its own platform code: Android with java.security (unchanged), the iPhone
 * with Apple's Security framework. ⚠ A failed check is SILENT by design (the phone keeps its built-in
 * rules), so a wrong iPhone check would look exactly like nobody publishing. RulesetUpdateTest feeds a
 * REAL published, signed reply to it on both phones and demands "accepted", and a tampered one that
 * must be refused.
 */
internal expect fun verifyRsaSha256(message: ByteArray, signature: ByteArray, spkiDer: ByteArray): Boolean

/**
 * The bare RSA key (PKCS#1 RSAPublicKey) inside an X.509 SubjectPublicKeyInfo, or null if [der] is not
 * one. Apple's key import takes ONLY the bare key — the wrapper Java expects has to come off first.
 *
 *     SEQUENCE { SEQUENCE { algorithm, parameters }, BIT STRING { 0 unused bits, RSAPublicKey } }
 */
internal fun spkiToPkcs1(der: ByteArray): ByteArray? {
    var i = 0
    fun length(): Int? {
        if (i >= der.size) return null
        val first = der[i++].toInt() and 0xFF
        if (first < 0x80) return first
        val count = first and 0x7F
        if (count == 0 || count > 4 || i + count > der.size) return null
        var value = 0
        repeat(count) { value = (value shl 8) or (der[i++].toInt() and 0xFF) }
        return value
    }
    if (der.getOrNull(i++) != SEQUENCE) return null
    length() ?: return null
    if (der.getOrNull(i++) != SEQUENCE) return null
    val algorithm = length() ?: return null
    i += algorithm
    if (der.getOrNull(i++) != BIT_STRING) return null
    val bits = length() ?: return null
    if (bits < 2 || i + bits > der.size) return null
    if (der[i] != 0.toByte()) return null
    return der.copyOfRange(i + 1, i + bits)
}

private const val SEQUENCE: Byte = 0x30
private const val BIT_STRING: Byte = 0x03
