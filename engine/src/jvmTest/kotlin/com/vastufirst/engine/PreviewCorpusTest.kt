package com.vastufirst.engine

import com.vastufirst.shared.Plan
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the preview corpus — the homes the admin panel re-scores before anybody publishes a
 * rule change.
 *
 * **Why this test exists.** The panel cannot run Kotlin test code, so the anchor home had to
 * become a JSON file it can read. The moment there are two copies of the same home, they drift.
 * This test is the thing that stops them: it decodes the JSON and asserts it is byte-for-byte the
 * same plan as [Fixtures.sample01], and that it still scores the number the whole engine is
 * anchored to. Change one and not the other, and the build goes red here.
 *
 * The files live in this module's test resources so they travel on the classpath and cannot go
 * missing the way a relative path can.
 */
class PreviewCorpusTest {

    private val json = Json { ignoreUnknownKeys = false; isLenient = false }

    private fun read(name: String): String =
        PreviewCorpusTest::class.java.getResourceAsStream("/preview/$name")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("Preview corpus file missing: $name")

    @Test
    fun `the sample-01 json is the same home as the kotlin fixture`() {
        val fromJson = json.decodeFromString(Plan.serializer(), read("sample-01.json"))
        assertEquals(Fixtures.sample01(), fromJson, "the JSON copy of sample-01 has drifted from Fixtures.sample01()")
    }

    @Test
    fun `the sample-01 json still scores the anchor number`() {
        val plan = json.decodeFromString(Plan.serializer(), read("sample-01.json"))
        val analysis = VastuEngine().analyze(plan)
        assertEquals(31, analysis.score, "sample-01 read from JSON must still score 31")
        assertEquals(16, analysis.defectPenalty)
        assertNotNull(analysis.doorResult)
    }

    @Test
    fun `the json round-trips through the serializer unchanged`() {
        // The admin panel sends plans back as JSON too. If encoding then decoding changed the
        // home, a preview would score something the phone never would.
        val plan = json.decodeFromString(Plan.serializer(), read("sample-01.json"))
        val again = json.decodeFromString(Plan.serializer(), json.encodeToString(Plan.serializer(), plan))
        assertEquals(plan, again)
    }

    @Test
    fun `every home in the corpus is readable and scores`() {
        // Deliberately derived from the folder rather than a hand-typed list: a home added to the
        // corpus and forgotten here would otherwise never be checked at all.
        val names = corpusFileNames()
        assertTrue(names.isNotEmpty(), "the preview corpus is empty")
        for (name in names) {
            val plan = json.decodeFromString(Plan.serializer(), read(name))
            val analysis = VastuEngine().analyze(plan)
            assertTrue(analysis.score in 0..100, "$name scored outside 0..100: ${analysis.score}")
            assertEquals(plan.id, analysis.planId, "$name came back under a different id")
        }
    }

    private fun corpusFileNames(): List<String> {
        val dir = PreviewCorpusTest::class.java.getResource("/preview") ?: error("no /preview on the classpath")
        return java.io.File(dir.toURI()).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.map { it.name }
            ?.sorted()
            ?: error("could not list the preview corpus")
    }
}
