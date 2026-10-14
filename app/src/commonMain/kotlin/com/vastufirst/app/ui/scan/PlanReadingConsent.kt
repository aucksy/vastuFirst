package com.vastufirst.app.ui.scan


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
 * refusing still gets a full score (the guided grid is judged by the same rules — NOT the same
 * number: only the grid asks the missing-corner question, so shape faults can differ).
 *
 * Stored on the device only. There is no account and nothing to sync.
 */
interface PlanReadingConsent {
    fun isGranted(): Boolean
    fun set(granted: Boolean)
}

/** Consent held in memory only — for the render harness and tests, which must never touch prefs. */
class InMemoryPlanReadingConsent(private var granted: Boolean = false) : PlanReadingConsent {
    override fun isGranted(): Boolean = granted
    override fun set(granted: Boolean) { this.granted = granted }
}
