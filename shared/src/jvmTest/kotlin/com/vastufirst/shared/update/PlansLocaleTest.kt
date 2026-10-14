package com.vastufirst.shared.update

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * JVM-only: it changes the JVM's default locale, which an iPhone test cannot do. Moved out of
 * PlansTest when that file started running on the iPhone simulator too (iPhone plan, stage 1).
 */
class PlansLocaleTest {
    @Test
    fun `rupees do not depend on what country the phone thinks it is in`() {
        // Written out rather than taken from the phone's locale on purpose: a golden screenshot of
        // a price must not change because a test machine has different locale data.
        val before = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            assertEquals("₹1,00,000", rupees(100000))
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("₹1,00,000", rupees(100000))
        } finally {
            java.util.Locale.setDefault(before)
        }
    }
}
