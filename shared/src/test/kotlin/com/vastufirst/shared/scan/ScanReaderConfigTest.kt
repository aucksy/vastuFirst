package com.vastufirst.shared.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The reader's settings are DATA, and these are the properties that must survive an edit to them.
 *
 * The point is not to freeze the file — a retired model has to be changeable without a release, which
 * is the whole reason it is not a Kotlin constant. The point is that the two things which are not
 * really settings at all cannot be changed by accident: the safety rule that keeps Vastu out of the
 * prompt, and the words the accuracy figures were measured with.
 */
class ScanReaderConfigTest {

    private val recipe = ScanReaderConfigLoader.load()

    @Test
    fun `the bundled config loads and points at a real endpoint`() {
        assertTrue(recipe.config.endpoint.startsWith("https://"), "must be https")
        assertTrue(recipe.config.model.isNotBlank(), "a model must be named")
        assertTrue(recipe.config.userAgent.isNotBlank(), "Cloudflare 403s the default client")
        assertTrue(recipe.config.readTimeoutMs >= 30_000, "a plan read took up to 1.6 s of model time")
    }

    @Test
    fun `no reasoning lever is set for the OpenRouter reader`() {
        // The Groq-era `reasoning_effort: none` was that provider's cost lever. On OpenRouter the
        // primary model spends ~350 hidden reasoning tokens a scan unprompted (measured, plan doc
        // §3p) and the field would be forwarded to providers that may reject it. Absent is the
        // deliberate state; if someone sets it, they should be re-measuring cost, not drifting.
        assertEquals(null, recipe.config.reasoningEffort)
    }

    @Test
    fun `the second opinion is configured, and it is a different model`() {
        // The primary refuses straight-overhead furnished renders as not-2D; the escalation model
        // is the only measured candidate that reads that class (plan doc §3p). Blank would silently
        // turn the feature off; the same model would double-spend for the same answer.
        val escalation = recipe.config.escalationModel
        assertTrue(!escalation.isNullOrBlank(), "an escalation model must be named")
        assertTrue(escalation != recipe.config.model, "the second opinion must be a different model")
    }

    @Test
    fun `the prompt still asks the three questions the feature is built on`() {
        val prompt = recipe.prompt
        // L0 triage — one real upload in five was a 3D render or not a plan at all.
        assertTrue(prompt.contains("2D_PLAN"), "triage classification")
        assertTrue(prompt.contains("3D_RENDER"), "triage classification")
        assertTrue(prompt.contains("NOT_A_PLAN"), "triage classification")
        // L1 identity — the model's strongest skill, and the actual product.
        assertTrue(prompt.contains("hasRoomLabels"), "labels are a precondition, owner decision")
        assertTrue(prompt.contains("exactly as printed"), "verbatim captions, so the user checks our reading")
        // ⭐⭐ L2b — the size the PLAN PRINTS, which is where every measurement now comes from. The
        // reader's rectangles are a template on half of real plans (three stock sizes, everything on
        // a 0.05 lattice), and no wording fixes that; printed text is the one thing it reads at ~95 %.
        // Asking for this took the owner's own flat from 0 of 15 rooms sized to 15 of 15.
        assertTrue(prompt.contains("\"size\""), "the printed size must be asked for in its own field")
        assertTrue(prompt.contains("never invent one"), "and never fabricated when the plan prints none")
        // L2 arrangement — normalised to the outer wall, so the model never sees our grid.
        assertTrue(prompt.contains("OUTER WALL"), "coordinates are relative to the building")
        // The three things that are not rooms and would corrupt a footprint if returned as rooms.
        assertTrue(prompt.contains("Do NOT report doors, windows, furniture"), "S3 and the clutter rule")
    }

    @Test
    fun `no Vastu vocabulary reaches the model`() {
        // ⭐ SAFETY RULE S1, now mechanical instead of a note in a document.
        //
        // Asked for a room's *sector*, the model returned byte-identical answers for a clean render
        // and a badly skewed photo — it was not reading the image — and its mistakes moved toward
        // doctrine (master bedroom to the south-west, kitchen to the east: the textbook positions,
        // not the drawing's). A reader that nudges homes toward canonical placement makes every home
        // score better than it is, and the score is what the customer pays for. So the model is a
        // neutral document reader and nothing else; our own code does every bit of the Vastu.
        val lower = recipe.prompt.lowercase()
        for (word in listOf("vastu", "zone", "sector", "auspicious", "brahmasthan", "direction", "mandala")) {
            assertTrue(!lower.contains(word), "the prompt must not mention '$word' — safety rule S1")
        }
    }

    @Test
    fun `a broken config fails loudly rather than silently`() {
        // Fail-loud is the ruleset loader's contract too: a config the app cannot use is a
        // programming error we want at startup with a readable message, not requests to nowhere.
        assertFailsWith<IllegalStateException> { ScanReaderConfigLoader.load("/scan/does-not-exist.json") }
        assertFailsWith<IllegalStateException> { ScanReaderConfigLoader.load("/scan/plan-read-prompt.txt") }
    }
}
