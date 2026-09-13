package com.vastufirst.app.update

import android.content.Context
import com.vastufirst.shared.update.RulesetUpdate
import com.vastufirst.shared.update.UPDATE_PUBLIC_KEY_SPKI_B64
import org.json.JSONObject
import java.io.File

/**
 * Where a phone keeps the rules and prices it was last given, between one launch and the next.
 *
 * ⭐ WHAT IS STORED IS THE SIGNED REPLY, WORD FOR WORD — not the rules pulled out of it.
 *
 * That is the whole design. Storing the unpacked rules would mean trusting, at every later launch,
 * that nothing on the device had touched the file since. Storing the exact bytes that were signed
 * means the signature can be checked again every single time it is read, against the key compiled
 * into this build. A file somebody has edited fails that check and is thrown away, and the phone
 * goes back to the rules built into the app — which are never deleted and always work.
 *
 * ⭐ AND EVERY FAILURE IS SILENT. No disk, no room, a corrupt file, a file written by a newer build
 * — every one of them returns null, and null means "use what is built in", which is exactly what
 * the app did before any of this existed. There is nothing to tell anybody, because from where
 * they stand nothing is wrong: their home still scores.
 */
class PublishedRulesStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    /**
     * What was last accepted, re-checked from scratch, or null.
     *
     * Re-checked means exactly that: the signature is verified again, the version inside the
     * payload is matched against the wrapper again, and [validate] — the app's own loader — is run
     * over the rules again. Nothing is taken on trust because it was trusted once before.
     */
    fun readVerified(appVersion: String, validate: (Map<String, String>) -> Boolean): RulesetUpdate.Accepted? {
        val body = try {
            if (!file.exists()) return null
            file.readText()
        } catch (_: Throwable) {
            return null
        }
        // currentVersion is deliberately a value nothing can equal, so the "not newer" gate cannot
        // reject the very file we are trying to read back.
        val outcome = try {
            RulesetUpdate.consider(body, UPDATE_PUBLIC_KEY_SPKI_B64, appVersion, NOTHING, validate)
        } catch (_: Throwable) {
            return null
        }
        return (outcome as? RulesetUpdate.Outcome.Use)?.accepted
    }

    /** The version stored on disk without checking it, for asking "is there anything newer?". */
    fun storedVersion(): String {
        return try {
            if (!file.exists()) return NOTHING
            JSONObject(file.readText()).optString("version", NOTHING).ifBlank { NOTHING }
        } catch (_: Throwable) {
            NOTHING
        }
    }

    /**
     * Keep a reply that has already been checked.
     *
     * Written to a temporary file and then moved into place, so a phone that dies mid-write leaves
     * either the old file or the new one and never half of either. A half-written file would fail
     * its signature check and be ignored, so the worst case is safe — but "safe" and "silently
     * loses the update every time" are different things.
     */
    fun save(body: String): Boolean = try {
        val temp = File(file.parentFile, "$FILE_NAME.writing")
        temp.writeText(body)
        if (file.exists()) file.delete()
        temp.renameTo(file)
    } catch (_: Throwable) {
        false
    }

    /** Throw away whatever is stored, and go back to the rules built into the app. */
    fun forget(): Boolean = try {
        !file.exists() || file.delete()
    } catch (_: Throwable) {
        false
    }

    companion object {
        const val FILE_NAME = "published-rules.json"

        /**
         * The version string meaning "we have nothing". It can never collide with a real one:
         * a published version always looks like 2026.09.13-1.
         */
        const val NOTHING = "none"
    }
}
