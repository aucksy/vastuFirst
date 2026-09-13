package com.vastufirst.shared.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What the app was told to offer, and for how much.
 *
 * ⭐ THE PROMISE THIS FILE KEEPS IS THE SAME AS THE RULES': the words and the price built into the
 * app are never deleted, so a phone with no signal, a refused download or a set of plans that
 * cannot be read shows exactly what it shows today. Every failure here returns null. There is
 * nothing to tell anybody, because from where they stand nothing is wrong.
 *
 * ⚠ WHAT THIS IS NOT. It is not what a customer is charged. The report is sold inside a Play Store
 * app, so once payments are switched on, Google's own listing price is what is taken and nothing
 * published here can change it. This is what the app SAYS — which today, with payments switched
 * off, is the whole of what a customer sees, and later is the number shown until Google answers.
 */
data class PlanTier(
    /** Never changes once somebody has bought it — it is how a purchase is recognised. */
    val id: String,
    val name: String,
    val priceInr: Int,
    /** A reading is one photographed plan sent to be read. */
    val planReadings: Int,
    val fullReports: Int,
    val homes: Int,
    val onSale: Boolean,
    val tagline: String,
    val blurb: String,
) {
    /** "₹1,499" — grouped the Indian way, which is not the same as the rest of the world's. */
    val price: String get() = rupees(priceInr)
}

data class Plans(
    /** The one plan whose price the unlock screen shows in its largest type. */
    val unlockTierId: String,
    val tiers: List<PlanTier>,
) {
    val unlockTier: PlanTier? get() = tiers.firstOrNull { it.id == unlockTierId && it.onSale }

    /** What the unlock screen should show, or null — in which case the app shows its own price. */
    val unlockPrice: String? get() = unlockTier?.price

    val onSale: List<PlanTier> get() = tiers.filter { it.onSale }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = false }

        /**
         * Read a set of plans out of the `plans` object inside a signed payload.
         *
         * Returns null for anything at all that is not exactly right, on purpose. A half-read set
         * of plans is worse than none: it would put a price on a screen that nobody decided.
         */
        fun parse(text: String?): Plans? {
            if (text.isNullOrBlank()) return null
            return try {
                from(json.parseToJsonElement(text).jsonObject)
            } catch (_: Throwable) {
                null
            }
        }

        fun from(root: JsonObject): Plans? = try {
            val unlockTierId = root["unlockTierId"]!!.jsonPrimitive.content
            val tiers = root["tiers"]!!.jsonArray.map { element ->
                val t = element.jsonObject
                PlanTier(
                    id = t["id"]!!.jsonPrimitive.content,
                    name = t["name"]!!.jsonPrimitive.content,
                    priceInr = t["priceInr"]!!.jsonPrimitive.int,
                    planReadings = t["planReadings"]!!.jsonPrimitive.int,
                    fullReports = t["fullReports"]!!.jsonPrimitive.int,
                    homes = t["homes"]!!.jsonPrimitive.int,
                    onSale = t["onSale"]!!.jsonPrimitive.boolean,
                    tagline = t["tagline"]?.jsonPrimitive?.content.orEmpty(),
                    blurb = t["blurb"]?.jsonPrimitive?.content.orEmpty(),
                )
            }
            // A set of plans with nothing in it, or with no plan behind the one the unlock screen
            // is supposed to show, is not a set of plans. The app keeps its own.
            if (tiers.isEmpty()) null
            else if (tiers.none { it.id == unlockTierId }) null
            else Plans(unlockTierId, tiers)
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * A whole number of rupees, written the way it is written in India.
 *
 * ⚠ NOT the same grouping as everywhere else, and the difference starts at five digits: a hundred
 * thousand is 1,00,000 here and 100,000 elsewhere. Written out rather than taken from the phone's
 * locale on purpose — a price is the one number on the screen that must read the same on every
 * phone, including one set to another country, and a golden screenshot of it must not change
 * because a test machine has different locale data.
 */
fun rupees(amount: Int): String {
    val digits = kotlin.math.abs(amount).toString()
    val grouped = if (digits.length <= 3) {
        digits
    } else {
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        // Everything above the last three is grouped in twos, from the right.
        val pairs = rest.reversed().chunked(2).joinToString(",").reversed()
        "$pairs,$last3"
    }
    return (if (amount < 0) "-₹" else "₹") + grouped
}
