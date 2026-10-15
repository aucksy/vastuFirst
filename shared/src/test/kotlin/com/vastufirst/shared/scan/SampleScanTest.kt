package com.vastufirst.shared.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ⭐ "Try a sample plan" is the bundled sample sheet and its recorded reading (owner, 15 Oct 2026).
 * Runs on the JVM AND on the iPhone simulator: the picture travels as bundled text, and both phones
 * must get the same bytes back.
 */
class SampleScanTest {

    @Test
    fun `the sample picture is bundled and decodes to the sample sheet PNG`() {
        val bytes = assertNotNull(SampleScan.pictureBytes(), "the sample picture is missing from the build")
        assertEquals(22475, bytes.size, "the bundled picture is not the plan-01 sheet")
        // The PNG signature: 0x89 P N G.
        assertEquals(0x89, bytes[0].toInt() and 0xFF)
        assertEquals("PNG", bytes.copyOfRange(1, 4).decodeToString())
    }

    @Test
    fun `the sample reads as a placed home with every room the sheet prints`() {
        val outcome = assertNotNull(SampleScan.outcome(), "the sample sheet no longer places")
        assertEquals(8, outcome.rooms.size, "rooms: ${outcome.rooms.map { it.label }}")
        assertTrue(outcome.rooms.all { it.source != null }, "every room needs its box on the photo")
    }

    @Test
    fun `the sample door point sits on the bottom wall of the building`() {
        val b = SampleScan.BUILDING
        assertEquals(b.y + b.h, SampleScan.DOOR_PAGE_Y.toDouble(), 0.01)
        assertTrue(SampleScan.DOOR_PAGE_X > b.x && SampleScan.DOOR_PAGE_X < b.x + b.w)
    }
}
