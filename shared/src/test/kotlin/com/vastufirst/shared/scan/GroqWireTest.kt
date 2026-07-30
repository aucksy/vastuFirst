package com.vastufirst.shared.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The real call, minus the socket.
 *
 * Everything asserted here was verified against the live API on 30 July 2026 before a line of it was
 * written: HTTP 200 from `qwen/qwen3.6-27b` with `reasoning_effort: none`, 2 143 prompt + 751
 * completion tokens, and the reset headers reading `53.812s` (tokens) and `2m52.8s` (requests). The
 * header formats in these tests are those two strings verbatim, not a guess at the shape.
 */
class GroqWireTest {

    private val recipe = ScanReaderConfigLoader.load()
    private val json = Json { prettyPrint = false }

    // ---- the request ------------------------------------------------------------------------

    @Test
    fun `request carries the model, the prompt and the image as a data url`() {
        val body = GroqWire.parseObject(GroqWire.requestBody(recipe, "QUJD"))

        assertEquals(recipe.config.model, body["model"]?.jsonPrimitive?.content)
        assertEquals("json_object", body["response_format"]?.jsonObject?.get("type")?.jsonPrimitive?.content)

        val content = body["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray
        assertEquals(2, content.size, "one text part and one image part")
        assertEquals("text", content[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals(recipe.prompt, content[0].jsonObject["text"]?.jsonPrimitive?.content)
        assertEquals("image_url", content[1].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals(
            "data:image/jpeg;base64,QUJD",
            content[1].jsonObject["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `reasoning effort is sent when configured and omitted when it is not`() {
        // ⭐ The cost lever: reasoning tokens bill as output, and reading a plan is extraction rather
        // than deliberation. 21 paise a scan with this off, 62 with it on.
        assertEquals(
            "none",
            GroqWire.parseObject(GroqWire.requestBody(recipe, "QUJD"))["reasoning_effort"]
                ?.jsonPrimitive?.content,
        )

        val without = recipe.copy(config = recipe.config.copy(reasoningEffort = null))
        assertNull(GroqWire.parseObject(GroqWire.requestBody(without, "QUJD"))["reasoning_effort"])
        val blank = recipe.copy(config = recipe.config.copy(reasoningEffort = "  "))
        assertNull(GroqWire.parseObject(GroqWire.requestBody(blank, "QUJD"))["reasoning_effort"])
    }

    @Test
    fun `the prompt survives JSON encoding intact`() {
        // It contains quotes, braces, pipes and newlines — exactly the characters that break
        // hand-built request strings, in the one place where a malformed body is indistinguishable
        // from a model failure.
        val text = GroqWire.parseObject(GroqWire.requestBody(recipe, "QUJD"))["messages"]!!
            .jsonArray.single().jsonObject["content"]!!
            .jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("\"2D_PLAN\""), "the quoted classification names survived")
        assertTrue(text.contains("\n"), "the line breaks survived")
        assertEquals(recipe.prompt, text)
    }

    // ---- the reply -------------------------------------------------------------------------

    /** The chat envelope the API actually returns, wrapped around a given model answer. */
    private fun envelope(answer: String): String = """
        {"id":"chatcmpl-1","object":"chat.completion","model":"${recipe.config.model}",
         "choices":[{"index":0,"message":{"role":"assistant","content":${json.encodeToString(answer)}},
         "finish_reason":"stop"}],
         "usage":{"prompt_tokens":2143,"completion_tokens":751,"total_tokens":2894}}
    """.trimIndent()

    @Test
    fun `a real recorded reply travels the whole way from HTTP body to outcome`() {
        val recorded = assertNotNull(RecordedScans.load(RecordedScans.CLEAN))
        val result = GroqWire.readOutcome(envelope(json.encodeToString(recorded.reply)), 1.0)

        val read = assertIs<ScanResult.Read>(result)
        // Identical to feeding the draft straight to the mapper: the transport adds nothing and
        // loses nothing.
        assertEquals(ScanMapper.map(recorded.reply, 1.0), read.outcome)
        assertIs<ScanOutcome.Placed>(read.outcome)
    }

    @Test
    fun `a dense real reply still lands in Assisted through the wire`() {
        val recorded = assertNotNull(RecordedScans.load(RecordedScans.DENSE))
        val read = assertIs<ScanResult.Read>(
            GroqWire.readOutcome(envelope(json.encodeToString(recorded.reply)), 0.8),
        )
        assertIs<ScanOutcome.Assisted>(read.outcome)
    }

    @Test
    fun `a reply we cannot parse is unavailable, never a refusal`() {
        // ⭐ The distinction matters to the user: a refusal says "your plan is the problem, change
        // it", which would be a lie when the failure is ours. Unavailable says "try again".
        assertEquals(ScanResult.Unavailable, GroqWire.readOutcome(envelope("I'm sorry, I can't help."), 1.0))
        assertEquals(ScanResult.Unavailable, GroqWire.readOutcome("not json at all", 1.0))
        assertEquals(ScanResult.Unavailable, GroqWire.readOutcome("""{"choices":[]}""", 1.0))
        assertEquals(ScanResult.Unavailable, GroqWire.readOutcome(envelope(""), 1.0))
    }

    @Test
    fun `content is dug out of the envelope`() {
        assertEquals("hello", GroqWire.contentOf(envelope("hello")))
        assertNull(GroqWire.contentOf("{}"))
        assertNull(GroqWire.contentOf("""{"choices":[{"message":{}}]}"""))
    }

    // ---- statuses --------------------------------------------------------------------------

    private fun headers(vararg pairs: Pair<String, String>): (String) -> String? {
        val map = pairs.toMap()
        return { name -> map[name] }
    }

    @Test
    fun `429 becomes a wait with the real number from the token reset header`() {
        val busy = assertIs<ScanResult.Busy>(
            GroqWire.mapStatus(
                429,
                headers(
                    GroqWire.HEADER_RESET_TOKENS to "53.812s",
                    GroqWire.HEADER_RESET_REQUESTS to "2m52.8s",
                ),
            ),
        )
        // Tokens-per-minute is the binding limit, so the token reset is the one quoted — and it is
        // rounded UP, because sending someone back a moment early just earns them a second 429.
        assertEquals(54, busy.retryAfterSeconds)
    }

    @Test
    fun `429 falls back through Retry-After to the request reset, then to no number at all`() {
        assertEquals(
            30,
            assertIs<ScanResult.Busy>(
                GroqWire.mapStatus(429, headers(GroqWire.HEADER_RETRY_AFTER to "30")),
            ).retryAfterSeconds,
        )
        assertEquals(
            173,
            assertIs<ScanResult.Busy>(
                GroqWire.mapStatus(429, headers(GroqWire.HEADER_RESET_REQUESTS to "2m52.8s")),
            ).retryAfterSeconds,
        )
        // No header, no invented number — the screen then says "in a minute".
        assertNull(assertIs<ScanResult.Busy>(GroqWire.mapStatus(429, headers())).retryAfterSeconds)
    }

    @Test
    fun `every other failure is unavailable, including a retired model and a dead key`() {
        // 400 is what a decommissioned model id returns; 401 a bad key; 403 Cloudflare's own block.
        for (status in listOf(400, 401, 403, 404, 413, 500, 502, 503)) {
            assertEquals(ScanResult.Unavailable, GroqWire.mapStatus(status) { null }, "status $status")
        }
    }

    // ---- durations -------------------------------------------------------------------------

    @Test
    fun `rate-limit durations parse in every shape the API uses`() {
        assertEquals(54, GroqWire.durationSeconds("53.812s"))
        assertEquals(173, GroqWire.durationSeconds("2m52.8s"))
        assertEquals(1, GroqWire.durationSeconds("500ms"))
        assertEquals(1, GroqWire.durationSeconds("7ms"))       // never a "0 second" wait
        assertEquals(600, GroqWire.durationSeconds("10m"))
        assertEquals(582, GroqWire.durationSeconds("582s"))    // the real value seen during E3
        assertEquals(30, GroqWire.durationSeconds("30"))       // Retry-After is plain seconds
        assertEquals(90, GroqWire.durationSeconds("1m30s"))
        assertEquals(3600, GroqWire.durationSeconds("2h"))     // capped: past an hour, say "a minute"
        assertEquals(54, GroqWire.durationSeconds(" 53.812S "))
    }

    @Test
    fun `an unreadable duration yields no number rather than a wrong one`() {
        assertNull(GroqWire.durationSeconds(null))
        assertNull(GroqWire.durationSeconds(""))
        assertNull(GroqWire.durationSeconds("   "))
        assertNull(GroqWire.durationSeconds("soon"))
        assertNull(GroqWire.durationSeconds("-5"))
    }
}
