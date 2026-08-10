package com.vastufirst.app.ui.scan

import android.content.Context

/**
 * ⭐ WHICH AI READS A PLAN — moved OUT of the scan flow and into Settings (owner, 10 Aug 2026:
 * "Remove the Luna and Gemini model selection and put them in settings for now").
 *
 * It was a row of model names on the screen a customer meets when they are about to photograph
 * their home. It is a comparison lever for us, not a decision for them, and every extra choice on
 * that screen is a reason to hesitate on the one screen the whole paid flow depends on.
 *
 * ⚠ Being a SETTING rather than a per-scan chip changes one real thing, and it is an improvement:
 * the choice now survives a retry. Picking a reader and then hitting "try again" used to fall back
 * to Auto, because the chip lived on a screen that had been rebuilt in between.
 *
 * `null` means Auto, which is the shipping path: the primary model, with a second opinion when its
 * own reply says the picture is not a 2D plan. A named pick reads with that one model only.
 */
interface ReaderChoice {
    /** The model id the user pinned, or null for Auto. */
    fun chosen(): String?
    fun set(model: String?)
}

class AndroidReaderChoice(context: Context) : ReaderChoice {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun chosen(): String? = prefs.getString(KEY, null)

    override fun set(model: String?) {
        prefs.edit().apply { if (model == null) remove(KEY) else putString(KEY, model) }.apply()
    }

    private companion object {
        const val FILE = "vastufirst_reader"
        const val KEY = "reader_model"
    }
}

/** Held in memory only — for the render harness and tests, which must never touch prefs. */
class InMemoryReaderChoice(private var model: String? = null) : ReaderChoice {
    override fun chosen(): String? = model
    override fun set(model: String?) { this.model = model }
}
