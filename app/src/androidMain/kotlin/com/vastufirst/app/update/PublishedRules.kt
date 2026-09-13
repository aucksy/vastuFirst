package com.vastufirst.app.update

import com.vastufirst.rules.RuleSet
import com.vastufirst.rules.RuleSetLoader
import com.vastufirst.shared.update.Plans
import com.vastufirst.shared.update.RulesetUpdate
import com.vastufirst.shared.update.UPDATE_PUBLIC_KEY_SPKI_B64
import com.vastufirst.shared.update.UPDATE_URL
import java.net.HttpURLConnection
import java.net.URI

/**
 * What this launch is using: the rules that score a home, and the prices the app shows.
 *
 * ⭐ THE ONE RULE EVERYTHING HERE OBEYS: with no signal, a refused download or anything at all
 * going wrong, the app behaves exactly as it did before any of this existed. The rules and the
 * price built into the build are never deleted and are always what it falls back to. Every failure
 * is silent, because from where somebody stands nothing is wrong — their home still scores and the
 * screen still names a price.
 *
 * ⭐ AND A CHANGE TAKES EFFECT AT THE NEXT LAUNCH, NOT MID-SESSION. Read once, at start-up, and
 * held for the life of the process. The alternative — swapping the rules under a report somebody
 * is already reading — would change their score while they looked at it, with no explanation on
 * the screen. So a download landing now is what they see when they next open the app, and this
 * whole file has one moment where anything changes, which is the moment it is created.
 */
class PublishedRules private constructor(
    /** The rules this launch is scoring with. Bundled, or a published set that passed every check. */
    val ruleSet: RuleSet,
    /** The prices this launch shows, or null to use the ones built into the app. */
    val plans: Plans?,
    /** True when the above came from the Control Room rather than from inside the build. */
    val fromControlRoom: Boolean,
    /** Why a stored file was not used, when it was not. For the owner's diagnosis only. */
    val note: String,
) {
    companion object {

        /**
         * Work out what to use, at start-up, without ever failing.
         *
         * Reads what is on disk and checks it from scratch — signature, version, and the app's own
         * loader. Anything it dislikes and the bundled rules are used instead.
         */
        fun atStartup(store: PublishedRulesStore, appVersion: String): PublishedRules {
            val bundled = try {
                RuleSetLoader.loadDefault()
            } catch (t: Throwable) {
                // The rules built into the app cannot load. That is a broken build and there is no
                // recovering from it here — the app has nothing to score with. Loud on purpose:
                // this has never happened and if it does, it must not be hidden by a fallback.
                throw t
            }

            val accepted = try {
                store.readVerified(appVersion) { parts -> loadsCleanly(parts) != null }
            } catch (_: Throwable) {
                null
            }
            if (accepted == null) return PublishedRules(bundled, null, false, "using the rules built into the app")

            val published = loadsCleanly(accepted.parts)
                ?: return PublishedRules(bundled, null, false, "the stored rules would not load")

            return PublishedRules(published, accepted.plans, true, "using version ${accepted.version}")
        }

        /** The app's own loader, over the eight files. Null when it refuses them. */
        fun loadsCleanly(parts: Map<String, String>): RuleSet? = try {
            RuleSetLoader.load { path ->
                // The loader asks for "/ruleset/rooms.json"; the parts are keyed "rooms".
                val name = path.substringAfterLast('/').removeSuffix(".json")
                parts[name] ?: error("no $name")
            }
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Ask the Control Room whether there is anything newer, and keep it if there is.
 *
 * ⚠ NOTHING THIS CLASS DOES AFFECTS THE SESSION IT RUNS IN. It writes a file; [PublishedRules]
 * reads that file at the NEXT start-up. That is deliberate — see the note on PublishedRules.
 *
 * [fetch] is a seam so the whole path can be tested without a network: hand it a function that
 * returns a recorded reply.
 */
class RulesUpdater(
    private val store: PublishedRulesStore,
    private val appVersion: String,
    private val url: String = UPDATE_URL,
    private val fetch: (String) -> String? = ::httpGet,
) {

    /** What happened, for the owner's own diagnosis. Never shown to anybody using the app. */
    sealed interface Result {
        data class Kept(val version: String) : Result
        data class NothingNew(val why: RulesetUpdate.Rejected) : Result
        data object CouldNotAsk : Result
        data object CouldNotSave : Result
    }

    fun refresh(): Result {
        val body = try {
            fetch(url)
        } catch (_: Throwable) {
            null
        } ?: return Result.CouldNotAsk

        val outcome = try {
            RulesetUpdate.consider(
                body = body,
                publicKeySpkiB64 = UPDATE_PUBLIC_KEY_SPKI_B64,
                appVersion = appVersion,
                currentVersion = store.storedVersion(),
                validate = { parts -> PublishedRules.loadsCleanly(parts) != null },
            )
        } catch (_: Throwable) {
            return Result.CouldNotAsk
        }

        return when (outcome) {
            is RulesetUpdate.Outcome.Keep -> Result.NothingNew(outcome.why)
            is RulesetUpdate.Outcome.Use ->
                if (store.save(body)) Result.Kept(outcome.accepted.version) else Result.CouldNotSave
        }
    }
}

/**
 * One plain HTTP GET, with short timeouts.
 *
 * ⚠ The timeouts are the point. This runs while somebody is opening the app; a network that
 * accepts a connection and then says nothing would otherwise hold a background thread for as long
 * as the system allows. Eight seconds and it gives up, which costs nothing at all — the app is
 * already running on the rules built into it.
 */
private fun httpGet(url: String): String? = try {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 8_000
    connection.readTimeout = 8_000
    connection.requestMethod = "GET"
    connection.setRequestProperty("Accept", "application/json")
    try {
        if (connection.responseCode != 200) null
        else connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
} catch (_: Throwable) {
    null
}
