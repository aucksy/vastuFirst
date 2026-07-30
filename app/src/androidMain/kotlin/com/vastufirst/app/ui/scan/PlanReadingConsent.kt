package com.vastufirst.app.ui.scan

import android.content.Context

/**
 * Whether the user has agreed to a picture of their home leaving the phone.
 *
 * NFR §10 and India's DPDP Act: a floor plan is personal data, and scan is the first feature in this
 * app that sends any off the device. Everything else — drawing, scoring, saving, the report — is
 * computed on the phone and always will be, which is what makes an honest alternative possible: this
 * consent has something to say "no" to.
 *
 * Four properties the law asks for, and each is a line of code rather than a paragraph of intent:
 * the ask is **specific** (one purpose, named recipient), it is **prior** (the gate sits before the
 * scan screen, so nothing can be sent before it is answered), it is **withdrawable** (Settings), and
 * refusing costs the user nothing (the guided grid produces the identical score).
 *
 * Stored on the device only. There is no account and nothing to sync.
 */
interface PlanReadingConsent {
    fun isGranted(): Boolean
    fun set(granted: Boolean)
}

class AndroidPlanReadingConsent(context: Context) : PlanReadingConsent {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun isGranted(): Boolean = prefs.getBoolean(KEY, false)

    override fun set(granted: Boolean) {
        prefs.edit().putBoolean(KEY, granted).apply()
    }

    private companion object {
        const val FILE = "vastufirst_privacy"

        /**
         * Versioned on purpose. If what we send, or who we send it to, ever changes, this key changes
         * with it and every user is asked again — consent to one thing is not consent to another.
         */
        const val KEY = "plan_reading_consent_v1"
    }
}

/** Consent held in memory only — for the render harness and tests, which must never touch prefs. */
class InMemoryPlanReadingConsent(private var granted: Boolean = false) : PlanReadingConsent {
    override fun isGranted(): Boolean = granted
    override fun set(granted: Boolean) { this.granted = granted }
}
