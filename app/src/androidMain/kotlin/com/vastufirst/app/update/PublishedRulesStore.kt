package com.vastufirst.app.update

import android.content.Context
import java.io.File

/**
 * Android's [RulesStore]: one file in the app's own files folder, written to a temporary name and
 * renamed over the old one so a half-written download can never be read back.
 */
class PublishedRulesStore(context: Context) : RulesStore() {
    private val file = File(context.filesDir, FILE_NAME)

    override fun readText(): String? = if (file.exists()) file.readText() else null

    override fun writeAtomically(body: String): Boolean {
        val temp = File(file.parentFile, "$FILE_NAME.writing")
        temp.writeText(body)
        if (file.exists()) file.delete()
        return temp.renameTo(file)
    }

    override fun deleteText(): Boolean = !file.exists() || file.delete()

    companion object {
        const val FILE_NAME = "published-rules.json"
        const val NOTHING = RulesStore.NOTHING
    }
}
