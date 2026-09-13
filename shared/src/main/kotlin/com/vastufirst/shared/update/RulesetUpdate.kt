package com.vastufirst.shared.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Taking a newer rule set from the Control Room, safely.
 *
 * ⭐ THE ONE PROMISE THIS FILE KEEPS: a bad publish, a meddled download, a hostile network or a
 * rule set written for a newer app can never stop a phone scoring a home. The rules built into the
 * app are never deleted and are always the fallback, so every failure here is SILENT — there is
 * nothing to show the person, because from where they stand nothing is wrong. Their home still
 * scores.
 *
 * Which is why every single path in here returns null rather than throwing, and why the checks
 * happen in this order:
 *
 *   1. Is there anything published at all?
 *   2. Is it newer than what we already have?
 *   3. Does the signature verify against the key built into this app?
 *   4. Does the payload's own version match the one we were told? (A mismatch means somebody
 *      swapped the wrapper around a signed payload.)
 *   5. Does it need a newer app than this one?
 *   6. Does it pass the app's OWN twenty start-up checks?
 *
 * Only after all six does anything change.
 */
object RulesetUpdate {

    /** What a phone got, once it has been checked and is safe to use. */
    data class Accepted(
        val version: String,
        val changeNote: String,
        val minAppVersion: String,
        /** The eight files, by their bare names, ready for RuleSetLoader.load. */
        val parts: Map<String, String>,
        /** The exact bytes that were signed. Stored so the check can be re-run at next start-up. */
        val payload: String,
        val signature: String,
    )

    /** Why a download was thrown away. Recorded for the owner's own diagnosis; never shown to a user. */
    enum class Rejected {
        NOTHING_PUBLISHED,
        NOT_NEWER,
        UNREADABLE,
        BAD_SIGNATURE,
        VERSION_MISMATCH,
        NEEDS_A_NEWER_APP,
        REFUSED_BY_THE_APPS_OWN_CHECKS,
    }

    sealed interface Outcome {
        data class Use(val accepted: Accepted) : Outcome
        data class Keep(val why: Rejected) : Outcome
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    /** The eight files a rule set is made of, in the order the loader reads them. */
    val PARTS = listOf("meta", "config", "zones", "rooms", "doorPadas", "defects", "disputes", "remedies")

    /**
     * Check a reply from the Control Room.
     *
     * [body] is the raw text of `/rules/latest`. [publicKeySpkiB64] is the public half of the
     * signing pair, compiled into the app. [appVersion] is this build's own version name.
     * [currentVersion] is the version of the rule set in use right now.
     *
     * [validate] is handed the eight files and should run the app's real loader over them. It is
     * injected rather than called directly so this file stays free of the rules module and so the
     * refusal path can be tested without building a broken dataset by hand.
     */
    fun consider(
        body: String,
        publicKeySpkiB64: String,
        appVersion: String,
        currentVersion: String,
        validate: (Map<String, String>) -> Boolean,
    ): Outcome {
        val root: JsonObject = try {
            json.parseToJsonElement(body).jsonObject
        } catch (_: Throwable) {
            return Outcome.Keep(Rejected.UNREADABLE)
        }

        val published = try {
            root["published"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        } catch (_: Throwable) {
            false
        }
        if (!published) return Outcome.Keep(Rejected.NOTHING_PUBLISHED)

        val version = root["version"]?.jsonPrimitive?.contentOrNullSafe()
            ?: return Outcome.Keep(Rejected.UNREADABLE)
        val payload = root["payload"]?.jsonPrimitive?.contentOrNullSafe()
            ?: return Outcome.Keep(Rejected.UNREADABLE)
        val signature = root["signature"]?.jsonPrimitive?.contentOrNullSafe()
            ?: return Outcome.Keep(Rejected.UNREADABLE)

        if (version == currentVersion) return Outcome.Keep(Rejected.NOT_NEWER)

        // ⭐ The signature is checked BEFORE the payload is parsed, let alone trusted. Everything
        // below this line is only reached because these exact bytes came from us.
        if (!verify(payload, signature, publicKeySpkiB64)) return Outcome.Keep(Rejected.BAD_SIGNATURE)

        val inner: JsonObject = try {
            json.parseToJsonElement(payload).jsonObject
        } catch (_: Throwable) {
            return Outcome.Keep(Rejected.UNREADABLE)
        }

        val innerVersion = inner["version"]?.jsonPrimitive?.contentOrNullSafe()
            ?: return Outcome.Keep(Rejected.UNREADABLE)
        // A signed payload with a different wrapper around it is somebody trying to pass an old
        // rule set off as a new one, or the reverse. The bytes are genuine; the claim is not.
        if (innerVersion != version) return Outcome.Keep(Rejected.VERSION_MISMATCH)

        val minAppVersion = inner["minAppVersion"]?.jsonPrimitive?.contentOrNullSafe() ?: "0.0.0"
        if (isOlder(appVersion, minAppVersion)) return Outcome.Keep(Rejected.NEEDS_A_NEWER_APP)

        val changeNote = inner["changeNote"]?.jsonPrimitive?.contentOrNullSafe() ?: ""
        val rulesetObject = try {
            inner["ruleset"]!!.jsonObject
        } catch (_: Throwable) {
            return Outcome.Keep(Rejected.UNREADABLE)
        }

        val parts = mutableMapOf<String, String>()
        for (name in PARTS) {
            val part = rulesetObject[name] ?: return Outcome.Keep(Rejected.UNREADABLE)
            parts[name] = part.toString()
        }

        // The last gate, and the same one the app runs at start-up. If it refuses, this dataset
        // would have stopped the app opening — so it is thrown away and the bundled one stays.
        if (!runCatching { validate(parts) }.getOrDefault(false)) {
            return Outcome.Keep(Rejected.REFUSED_BY_THE_APPS_OWN_CHECKS)
        }

        return Outcome.Use(Accepted(version, changeNote, minAppVersion, parts, payload, signature))
    }

    /**
     * Verify an RSA/SHA-256 signature over the exact bytes of [payload].
     *
     * ⚠ RSA, not Ed25519, and the reason matters: Android can only verify Ed25519 from version 13,
     * and this app runs from version 8. Every older phone would have thrown away every rule set we
     * ever published, silently, for ever — and it would have looked exactly like nobody having
     * updated. RSA with SHA-256 has been in Android since the first release. Do not "upgrade" it.
     */
    fun verify(payload: String, signatureB64Url: String, publicKeySpkiB64: String): Boolean = try {
        val keyBytes = Base64.getDecoder().decode(publicKeySpkiB64.trim())
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(key)
        verifier.update(payload.toByteArray(Charsets.UTF_8))
        verifier.verify(Base64.getUrlDecoder().decode(padded(signatureB64Url.trim())))
    } catch (_: Throwable) {
        // A malformed key, a malformed signature and a forged signature are all the same answer:
        // no. There is nothing here worth telling them apart for.
        false
    }

    /** Java's URL decoder is strict about length; a signature that lost its padding is still valid. */
    private fun padded(s: String): String {
        val remainder = s.length % 4
        return if (remainder == 0) s else s + "=".repeat(4 - remainder)
    }

    /**
     * True when [version] is older than [atLeast]. Both look like 0.24.0.
     *
     * An unreadable version is treated as too old, which is the safe direction: the phone keeps
     * what it has rather than adopting rules it may not be able to honour.
     */
    fun isOlder(version: String, atLeast: String): Boolean {
        val a = parts(version) ?: return true
        val b = parts(atLeast) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x < y
        }
        return false
    }

    private fun parts(v: String): List<Int>? {
        val bits = v.trim().split('.')
        if (bits.isEmpty()) return null
        return bits.map { it.toIntOrNull() ?: return null }
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    runCatching { content }.getOrNull()
