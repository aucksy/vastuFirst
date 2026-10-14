package com.vastufirst.shared.update

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Android and the JVM tests: java.security, exactly the check the app has always made. */
internal actual fun verifyRsaSha256(message: ByteArray, signature: ByteArray, spkiDer: ByteArray): Boolean {
    val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(spkiDer))
    val verifier = Signature.getInstance("SHA256withRSA")
    verifier.initVerify(key)
    verifier.update(message)
    return verifier.verify(signature)
}
