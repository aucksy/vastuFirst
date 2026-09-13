package com.vastufirst.shared.update

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The phone's side of "a new rule reaches a phone without breaking it".
 *
 * ⭐ The fixture is a REAL reply from the Control Room, captured from a real publish, frozen
 * exactly as it arrived. That is the point: a signature is over bytes, so a test that re-generates
 * the payload would only prove this file agrees with itself. Frozen bytes prove the phone agrees
 * with the thing that actually signs.
 *
 * ⚠ Do not reformat `signed-reply.json`. One changed space and the signature stops verifying,
 * which is exactly what it is supposed to do — but it would look like a bug here rather than a
 * demonstration that the check works.
 */
class RulesetUpdateTest {

    private fun resource(name: String): String =
        RulesetUpdateTest::class.java.getResourceAsStream("/update/$name")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("Test fixture missing: $name")

    private val reply by lazy { resource("signed-reply.json") }
    private val publicKey by lazy { resource("public-key.txt").trim() }

    /** Stands in for the app's real loader. Every test says explicitly whether it should accept. */
    private fun accepting(ok: Boolean): (Map<String, String>) -> Boolean = { ok }

    // ---- the happy path ------------------------------------------------------------------------

    @Test
    fun `a genuine reply is accepted, and carries everything the app needs`() {
        val outcome = RulesetUpdate.consider(reply, publicKey, "0.24.0", "2026.08.23-3", accepting(true))
        val use = outcome as? RulesetUpdate.Outcome.Use
            ?: fail("a genuine, signed rule set was refused: ${(outcome as RulesetUpdate.Outcome.Keep).why}")

        assertTrue(use.accepted.version.matches(Regex("""\d{4}\.\d{2}\.\d{2}-\d+""")), use.accepted.version)
        assertTrue(use.accepted.changeNote.length >= 120, "the note a customer is shown was too short")
        assertEquals(RulesetUpdate.PARTS.toSet(), use.accepted.parts.keys)
        for ((name, text) in use.accepted.parts) {
            assertTrue(text.isNotBlank(), "$name came through empty")
        }
    }

    @Test
    fun `the signature verifies against the key built into the app`() {
        val payload = payloadOf(reply)
        val signature = signatureOf(reply)
        assertTrue(RulesetUpdate.verify(payload, signature, publicKey))
    }

    // ---- everything that must be refused ---------------------------------------------------------

    @Test
    fun `one changed character anywhere in the rules is refused`() {
        // The smallest possible meddling: a single digit inside the payload.
        val meddled = reply.replace("gridSize\\\":9", "gridSize\\\":6")
        assertTrue(meddled != reply, "the fixture did not contain the text this test edits")
        assertRefused(meddled, RulesetUpdate.Rejected.BAD_SIGNATURE)
    }

    @Test
    fun `a signature from somewhere else is refused`() {
        val other = signatureOf(reply).let { "A" + it.substring(1) }
        val swapped = reply.replace(signatureOf(reply), other)
        assertRefused(swapped, RulesetUpdate.Rejected.BAD_SIGNATURE)
    }

    @Test
    fun `no signature at all is refused`() {
        assertRefused(reply.replace(signatureOf(reply), ""), RulesetUpdate.Rejected.BAD_SIGNATURE)
    }

    @Test
    fun `a genuine payload wrapped in a lie about its version is refused`() {
        // The bytes are really ours and the signature really verifies — but the wrapper claims a
        // different version, which is how an old rule set gets passed off as a new one.
        val version = versionOf(reply)
        val lying = reply.replaceFirst("\"version\":\"$version\"", "\"version\":\"2099.01.01-1\"")
        assertTrue(lying != reply, "could not build the lying wrapper")
        assertRefused(lying, RulesetUpdate.Rejected.VERSION_MISMATCH)
    }

    @Test
    fun `rules that need a newer app than this one are refused`() {
        // The published fixture allows 0.24.0. A phone on 0.23.0 must keep what it has rather than
        // adopt rules it may not be able to honour.
        val needed = minAppVersionOf(reply)
        assertTrue(needed != null && needed != "0.0.0",
            "the fixture does not set a minimum app version, so this test proves nothing")
        assertRefused(reply, RulesetUpdate.Rejected.NEEDS_A_NEWER_APP, appVersion = "0.1.0")
    }

    @Test
    fun `a rule set the app's own checks refuse is thrown away`() {
        assertRefused(reply, RulesetUpdate.Rejected.REFUSED_BY_THE_APPS_OWN_CHECKS, validate = accepting(false))
    }

    @Test
    fun `a loader that throws is treated as a refusal, not a crash`() {
        val outcome = RulesetUpdate.consider(reply, publicKey, "0.24.0", "2026.08.23-3") {
            throw IllegalStateException("the loader blew up")
        }
        assertEquals(RulesetUpdate.Rejected.REFUSED_BY_THE_APPS_OWN_CHECKS,
            (outcome as RulesetUpdate.Outcome.Keep).why)
    }

    @Test
    fun `the version already in use is not downloaded again`() {
        assertRefused(reply, RulesetUpdate.Rejected.NOT_NEWER, current = versionOf(reply))
    }

    @Test
    fun `a reply saying nothing is published is not an error`() {
        assertRefused("""{"published":false}""", RulesetUpdate.Rejected.NOTHING_PUBLISHED)
    }

    @Test
    fun `rubbish from the network never throws`() {
        for (rubbish in listOf("", "   ", "not json at all", "[]", "{", """{"published":true}""",
            """{"published":true,"version":"x"}""", "<html>a captive portal login page</html>")) {
            val outcome = RulesetUpdate.consider(rubbish, publicKey, "0.24.0", "2026.08.23-3", accepting(true))
            assertTrue(outcome is RulesetUpdate.Outcome.Keep, "rubbish was accepted: $rubbish")
        }
    }

    @Test
    fun `a broken public key refuses everything rather than accepting everything`() {
        // The direction of this failure is the whole point. A key that cannot be read must mean
        // "trust nothing", never "trust anything".
        for (bad in listOf("", "not-base64!!", "aGVsbG8=")) {
            assertFalse(RulesetUpdate.verify(payloadOf(reply), signatureOf(reply), bad),
                "a broken key accepted a signature")
        }
    }

    // ---- version comparison ------------------------------------------------------------------------

    @Test
    fun `version comparison handles the shapes a real build produces`() {
        assertTrue(RulesetUpdate.isOlder("0.23.0", "0.24.0"))
        assertTrue(RulesetUpdate.isOlder("0.9.0", "0.10.0"), "9 must not sort above 10")
        assertTrue(RulesetUpdate.isOlder("1.0", "1.0.1"))
        assertFalse(RulesetUpdate.isOlder("0.24.0", "0.24.0"))
        assertFalse(RulesetUpdate.isOlder("1.0.0", "0.99.99"))
        assertFalse(RulesetUpdate.isOlder("0.24.1", "0.24.0"))
        // An unreadable version counts as too old: keep what we have.
        assertTrue(RulesetUpdate.isOlder("not-a-version", "0.1.0"))
    }

    // ---- helpers -------------------------------------------------------------------------------------

    private fun assertRefused(
        body: String,
        expected: RulesetUpdate.Rejected,
        appVersion: String = "0.24.0",
        current: String = "2026.08.23-3",
        validate: (Map<String, String>) -> Boolean = accepting(true),
    ) {
        val outcome = RulesetUpdate.consider(body, publicKey, appVersion, current, validate)
        val keep = outcome as? RulesetUpdate.Outcome.Keep
            ?: fail("this should have been refused ($expected) and was accepted instead")
        assertEquals(expected, keep.why)
    }

    private fun outer(body: String) = parser.parseToJsonElement(body).jsonObject

    private fun versionOf(body: String) = outer(body)["version"]!!.jsonPrimitive.content

    private fun signatureOf(body: String) = outer(body)["signature"]!!.jsonPrimitive.content

    /** The signed bytes: a JSON string sitting inside the reply. */
    private fun payloadOf(body: String) = outer(body)["payload"]!!.jsonPrimitive.content

    private fun minAppVersionOf(body: String) =
        parser.parseToJsonElement(payloadOf(body)).jsonObject["minAppVersion"]?.jsonPrimitive?.content

    private companion object {
        val parser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }
}
