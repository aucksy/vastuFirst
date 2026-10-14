package com.vastufirst.app.ui.scan

import android.content.Context

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
