package com.vastufirst.app.platform

import android.content.Context
import com.vastufirst.data.PlanPhotoStore
import java.io.File

/**
 * The plan photographs of unfinished homes, on Android: one JPEG per home, in the app's own private
 * storage, named by that home's id.
 *
 * ⚠ `filesDir`, NOT `cacheDir`, and the difference is the whole point. The camera's working shot
 * lives in the cache (see `clearPlanPhotos`) because it matters for one read and Android may delete
 * it whenever it likes. This one has to survive being left alone for a week — that is what "Still to
 * finish" promises about every other part of the home — so it goes where the databases go.
 *
 * ⚠ NOTHING ELSE MAY WRITE INTO THIS FOLDER. It is emptied wholesale by "Delete all my data", and
 * every file in it is assumed to belong to a draft row. See [deleteAll].
 *
 * Every method is total, as the interface requires: a picture that will not write must not take the
 * home down with it.
 */
class AndroidPlanPhotoStore(private val context: Context) : PlanPhotoStore {

    private fun dir(): File = File(context.filesDir, DIR).apply { runCatching { mkdirs() } }

    /**
     * ⚠ The id is used as a FILE NAME, so it is reduced to characters that cannot mean anything to a
     * filesystem. Today's ids ("draft-1755330000000", and the single "current" older builds wrote)
     * already pass through unchanged; this exists so that the day somebody mints an id from a home's
     * NAME — a user-typed string — it cannot address a file outside this folder.
     */
    private fun fileFor(draftId: String): File? {
        val safe = draftId.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .take(120)
        if (safe.isBlank()) return null
        return File(dir(), "$safe.jpg")
    }

    override suspend fun save(draftId: String, jpeg: ByteArray) {
        val f = fileFor(draftId) ?: return
        runCatching { f.writeBytes(jpeg) }
    }

    override suspend fun load(draftId: String): ByteArray? {
        val f = fileFor(draftId) ?: return null
        return runCatching { if (f.isFile) f.readBytes() else null }.getOrNull()
    }

    override suspend fun delete(draftId: String) {
        val f = fileFor(draftId) ?: return
        runCatching { f.delete() }
    }

    /**
     * Empty the folder rather than deleting it: the directory itself is re-made on the next write
     * either way, and listing-then-deleting keeps this honest if a future build ever puts something
     * else alongside — a file that fails to delete must not stop the rest going.
     */
    override suspend fun deleteAll() {
        runCatching { dir().listFiles()?.forEach { it.delete() } }
    }

    private companion object {
        const val DIR = "draft-photos"
    }
}
