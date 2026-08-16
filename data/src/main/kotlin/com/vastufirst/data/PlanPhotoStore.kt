package com.vastufirst.data

/**
 * Where an unfinished home's plan PHOTOGRAPH is kept.
 *
 * WHY THIS EXISTS: a home read off a photograph used to lose that photograph the moment the reader
 * walked away from it. The picture lived in one process-wide slot and was documented as "never
 * persisted", so resuming from "Still to finish" put the reader on the North dial with our redrawn
 * coloured squares under the ring — the builder's canvas they had asked to be kept out of the photo
 * flow, arriving by the back door. The routing was right; there was simply nothing left to draw.
 *
 * ⚠ WHY IT IS NOT A COLUMN IN THE DRAFT ROW. [DraftSnapshot] is JSON in a TEXT column, and a plan
 * photograph is one to three megabytes. Base64 in that column would be carried by every read of the
 * saved-homes list — a list that decodes every draft on every emission just to count its rooms — so
 * the picture is a file beside the row, keyed by the same id, and this row's JSON stays small.
 *
 * ⚠ WHY IT IS AN INTERFACE HERE RATHER THAN A FILE PATH THERE. `data` is one of the four pure
 * modules and resolves zero Android (`scripts/check-boundaries.sh`); "the app's private storage" is
 * an Android idea. The contract lives here so [PlanRepository] can guarantee the one rule that
 * matters — a photograph never outlives the home it was taken for — and the Android side supplies
 * the folder.
 *
 * Every method is TOTAL. A photograph that cannot be written is not worth failing a save over: the
 * home, its rooms and its score are the product, and the picture is how it is shown.
 */
interface PlanPhotoStore {
    /** Keep [jpeg] as the picture of the unfinished home [draftId]. Replaces any earlier one. */
    suspend fun save(draftId: String, jpeg: ByteArray)

    /** The picture of unfinished home [draftId], or null when there isn't one. */
    suspend fun load(draftId: String): ByteArray?

    /** Forget one home's picture — it was thrown away, or it became a finished home. */
    suspend fun delete(draftId: String)

    /** Every picture this store holds. "Delete all my data" promises the photographs too. */
    suspend fun deleteAll()
}

/**
 * The store for builds and tests that have no filesystem to put pictures in — the JVM unit tests,
 * and any future target before its own store is written.
 *
 * ⚠ It REMEMBERS NOTHING on purpose, rather than throwing. Everything above it is written so that a
 * missing photograph degrades to "show the zone map", which is exactly what the app did before this
 * existed — so a target with no store behaves like the previous release rather than crashing.
 */
object NoPlanPhotoStore : PlanPhotoStore {
    override suspend fun save(draftId: String, jpeg: ByteArray) = Unit
    override suspend fun load(draftId: String): ByteArray? = null
    override suspend fun delete(draftId: String) = Unit
    override suspend fun deleteAll() = Unit
}
