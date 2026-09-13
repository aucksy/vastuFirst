package com.vastufirst.app.render

import com.vastufirst.app.ui.newplan.SamplePlans
import com.vastufirst.app.ui.newplan.buildEnginePlan
import com.vastufirst.app.update.PublishedRules
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.Intent
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.update.Plans
import com.vastufirst.shared.update.RulesetUpdate
import com.vastufirst.shared.update.UPDATE_PUBLIC_KEY_SPKI_B64

/**
 * ⭐⭐ REAL REPLIES FROM THE CONTROL ROOM, FROZEN, AND PUT THROUGH THE PHONE'S OWN CHECKS.
 *
 * This is what turns "the code looks right" into a picture. Nothing here fakes a price or a score.
 * Each fixture is the exact text a phone was served by the Control Room after a real publish, and
 * every one of them is handed to [RulesetUpdate.consider] with the REAL public key compiled into
 * the app and the REAL rule loader — the same six checks a phone runs. If a signature stopped
 * verifying, if the payload were written differently, if the loader refused the rules, these would
 * return nothing and the screens below would photograph the app's own built-in values instead. So
 * a golden showing the published number is proof the whole chain works, end to end.
 *
 * ⚠ DO NOT REFORMAT THE JSON FIXTURES. A signature is over bytes. One changed space and they stop
 * verifying — which is exactly what they are supposed to do, but it would look like a bug here
 * rather than a demonstration that the check works.
 *
 * Where each came from:
 *
 *  · `signed-reply-price.json`    — a REAL publish on the live Control Room at
 *                                   admin.vastufirst.com, with the single-home price set to ₹849.
 *                                   The live price was put back to ₹699 straight afterwards.
 *  · `signed-reply-harsher.json`  — a publish on a local copy of the Control Room, with a serious
 *                                   problem weighing 20 instead of 8. Deliberately NOT done on the
 *                                   live one: that would have moved the score on every real phone.
 *                                   Same code, same signing pair, so the phone cannot tell them
 *                                   apart — which is the point.
 */
object ControlRoomFixtures {

    private fun reply(name: String): String =
        ControlRoomFixtures::class.java.getResourceAsStream("/update/$name")
            ?.bufferedReader()?.use { it.readText() }
            ?: error(
                "The Control Room fixture '$name' is missing. It lives in " +
                    "app/src/androidUnitTest/resources/update/ and must be committed — a fixture " +
                    "that is only on somebody's laptop is green there and missing here.",
            )

    /** Run a frozen reply through exactly what a phone runs. Fails loudly if a phone would refuse. */
    private fun accept(name: String): RulesetUpdate.Accepted {
        val outcome = RulesetUpdate.consider(
            body = reply(name),
            publicKeySpkiB64 = UPDATE_PUBLIC_KEY_SPKI_B64,
            appVersion = "99.0.0",
            currentVersion = "nothing",
            validate = { parts -> PublishedRules.loadsCleanly(parts) != null },
        )
        return (outcome as? RulesetUpdate.Outcome.Use)?.accepted
            ?: error(
                "A phone would REFUSE the fixture '$name' — ${(outcome as RulesetUpdate.Outcome.Keep).why}. " +
                    "Nothing below this line means anything until that is fixed.",
            )
    }

    // ---- the published price -------------------------------------------------------------------

    private val priced by lazy { accept("signed-reply-price.json") }

    /** The plans that came out of a real publish, checked the way a phone checks them. */
    val publishedPlans: Plans by lazy {
        priced.plans ?: error("the price fixture carried no plans, so it proves nothing")
    }

    /** "₹849" — the number typed into the Control Room, arrived at through the signature check. */
    val publishedPrice: String by lazy {
        publishedPlans.unlockPrice ?: error("the published plans named no price for the unlock screen")
    }

    // ---- the published rule --------------------------------------------------------------------

    private val harsher by lazy { accept("signed-reply-harsher.json") }

    /** The engine, built from rules that were published rather than from the ones built in. */
    val publishedEngine: VastuEngine by lazy {
        VastuEngine(
            PublishedRules.loadsCleanly(harsher.parts)
                ?: error("the published rules would not load, so there is nothing to score with"),
        )
    }

    /**
     * The very same home as [RenderFixtures.sampleAnalysis], scored by the PUBLISHED rules.
     *
     * The only difference between this and the app's own reading of the same home is the rule that
     * was changed in the Control Room, so a golden of each, side by side, is the whole proof.
     */
    val analysisUnderPublishedRules: Analysis by lazy {
        val sample = SamplePlans.all.first()
        publishedEngine.analyze(
            buildEnginePlan(
                rooms = sample.rooms,
                door = sample.door,
                intent = Intent.BUILDING,
                propertyType = PropertyType.INDEPENDENT_HOUSE,
                north = sample.north,
                planId = "fixture",
            )!!,
        )
    }
}
