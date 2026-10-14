package com.vastufirst.app.ui.scan

import android.content.Context

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
         *
         * v2 (4 Aug 2026): the reader moved from Groq to OpenRouter (OpenAI model, sometimes a
         * Google second opinion). A yes given to Groq is not a yes to this, so everyone is re-asked.
         */
        const val KEY = "plan_reading_consent_v2"
    }
}
