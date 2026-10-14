package com.vastufirst.app.update

import com.vastufirst.shared.update.RulesetUpdate
import com.vastufirst.shared.update.UPDATE_PUBLIC_KEY_SPKI_B64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The one stored copy of the last rule set the Control Room published and this phone accepted.
 *
 * ⭐ Shared by both phones since 14 Oct 2026 (iPhone plan, stage 4): the checks are here once; each
 * phone only says how to keep ONE text file (Android: the app's files folder; iPhone: Application
 * Support, marked do-not-back-up). Every path is silent and returns a safe answer, because a broken
 * download must never stop a home from scoring — the built-in rules are always the fallback.
 */
abstract class RulesStore {
    /** The stored reply, or null when there is none or it cannot be read. */
    protected abstract fun readText(): String?

    /** Replace the stored reply with [body] in one step. False when it could not be written. */
    protected abstract fun writeAtomically(body: String): Boolean

    /** Remove the stored reply. True when there is none afterwards. */
    protected abstract fun deleteText(): Boolean

    /** The stored reply, checked again from scratch (signature, version, the app's own checks). */
    fun readVerified(appVersion: String, validate: (Map<String, String>) -> Boolean): RulesetUpdate.Accepted? {
        val body = try {
            readText()
        } catch (_: Throwable) {
            null
        } ?: return null
        val outcome = try {
            RulesetUpdate.consider(body, UPDATE_PUBLIC_KEY_SPKI_B64, appVersion, NOTHING, validate)
        } catch (_: Throwable) {
            return null
        }
        return (outcome as? RulesetUpdate.Outcome.Use)?.accepted
    }

    /** The version of the stored set, or [NOTHING]. */
    fun storedVersion(): String = try {
        val text = readText() ?: return NOTHING
        val version = Json.parseToJsonElement(text).jsonObject["version"]?.jsonPrimitive?.content
        if (version.isNullOrBlank()) NOTHING else version
    } catch (_: Throwable) {
        NOTHING
    }

    fun save(body: String): Boolean = try {
        writeAtomically(body)
    } catch (_: Throwable) {
        false
    }

    fun forget(): Boolean = try {
        deleteText()
    } catch (_: Throwable) {
        false
    }

    companion object {
        const val NOTHING = "none"
    }
}

/** Download [url] as text: null on any failure or any answer but 200. Off the main thread. */
expect fun defaultRulesFetch(url: String): String?
