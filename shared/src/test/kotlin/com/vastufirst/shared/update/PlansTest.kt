package com.vastufirst.shared.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plans and prices, read from what the Control Room publishes.
 *
 * ⭐ THE THING THESE TESTS ARE REALLY PROTECTING is the sentence "with no signal the app works
 * exactly as it does today". Every way a set of plans can be wrong has to end with null, because
 * null is what makes the app fall back to the price built into it. A half-read set of plans would
 * put a number on a money screen that nobody decided.
 */
class PlansTest {

    private val good = """
        {
          "unlockTierId": "one-home",
          "tiers": [
            { "id": "one-home", "name": "One home", "priceInr": 849, "planReadings": 3,
              "fullReports": 3, "homes": 1, "onSale": true,
              "tagline": "The home you are about to sign for.", "blurb": "Every finding in full." },
            { "id": "three-homes", "name": "Three homes", "priceInr": 1499, "planReadings": 10,
              "fullReports": 10, "homes": 3, "onSale": true, "tagline": "", "blurb": "" }
          ]
        }
    """.trimIndent()

    // ---- reading a good one -----------------------------------------------------------------

    @Test
    fun `a published set of plans is read whole`() {
        val plans = Plans.parse(good) ?: error("a perfectly good set of plans was refused")
        assertEquals(2, plans.tiers.size)
        assertEquals("one-home", plans.unlockTier?.id)
        assertEquals(849, plans.unlockTier?.priceInr)
        assertEquals(3, plans.unlockTier?.planReadings)
        assertEquals(1, plans.unlockTier?.homes)
        assertEquals("The home you are about to sign for.", plans.unlockTier?.tagline)
    }

    @Test
    fun `the price the unlock screen shows is the one marked, written in rupees`() {
        assertEquals("₹849", Plans.parse(good)?.unlockPrice)
    }

    @Test
    fun `only the plans on sale are offered`() {
        val withOneOff = """
            {
              "unlockTierId": "one-home",
              "tiers": [
                { "id": "one-home", "name": "One home", "priceInr": 849, "planReadings": 3,
                  "fullReports": 3, "homes": 1, "onSale": true, "tagline": "", "blurb": "" },
                { "id": "three-homes", "name": "Three homes", "priceInr": 1499, "planReadings": 10,
                  "fullReports": 10, "homes": 3, "onSale": false, "tagline": "", "blurb": "" }
              ]
            }
        """.trimIndent()
        val plans = Plans.parse(withOneOff) ?: error("refused")
        assertEquals(1, plans.onSale.size)
        assertEquals(2, plans.tiers.size, "a plan that is off is still known about, just not offered")
    }

    // ---- every way it can be wrong ends the same way ------------------------------------------

    @Test
    fun `nothing at all is not a set of plans`() {
        assertNull(Plans.parse(null))
        assertNull(Plans.parse(""))
        assertNull(Plans.parse("   "))
    }

    @Test
    fun `rubbish is not a set of plans`() {
        assertNull(Plans.parse("this is not json"))
        assertNull(Plans.parse("[1, 2, 3]"))
        assertNull(Plans.parse("{}"))
    }

    @Test
    fun `a set with no plans in it is refused`() {
        assertNull(Plans.parse("""{ "unlockTierId": "x", "tiers": [] }"""))
    }

    @Test
    fun `a set whose unlock plan does not exist is refused`() {
        // ⚠ The one that would otherwise be shown as a blank where the biggest number on the money
        // screen belongs. Refusing the whole set means the app shows its own price instead.
        assertNull(Plans.parse(good.replace(""""unlockTierId": "one-home"""", """"unlockTierId": "vanished"""")))
    }

    @Test
    fun `a plan missing a field is refused rather than half read`() {
        // Spelled out one field at a time rather than cut out of the good one, so each case is
        // certain to be testing the field it names.
        fun oneTier(fields: String) = """{ "unlockTierId": "one-home", "tiers": [ { $fields } ] }"""

        assertNull(Plans.parse(oneTier(
            """"id": "one-home", "name": "One home", "planReadings": 3, "fullReports": 3, "homes": 1, "onSale": true""",
        )), "no price")
        assertNull(Plans.parse(oneTier(
            """"id": "one-home", "name": "One home", "priceInr": 849, "planReadings": 3, "fullReports": 3, "onSale": true""",
        )), "no homes")
        assertNull(Plans.parse(oneTier(
            """"id": "one-home", "name": "One home", "priceInr": 849, "planReadings": 3, "fullReports": 3, "homes": 1""",
        )), "not said whether it is on sale")
        assertNull(Plans.parse(oneTier(
            """"id": "one-home", "priceInr": 849, "planReadings": 3, "fullReports": 3, "homes": 1, "onSale": true""",
        )), "no name")
        assertNull(Plans.parse(oneTier(
            """"name": "One home", "priceInr": 849, "planReadings": 3, "fullReports": 3, "homes": 1, "onSale": true""",
        )), "no id at all")
    }

    @Test
    fun `a price that is not a whole number is refused`() {
        assertNull(Plans.parse(good.replace(""""priceInr": 849""", """"priceInr": 849.5""")))
        assertNull(Plans.parse(good.replace(""""priceInr": 849""", """"priceInr": "free"""")))
    }

    @Test
    fun `words that are not there simply come back empty, and do not lose the plan`() {
        val noWords = """
            {
              "unlockTierId": "one-home",
              "tiers": [
                { "id": "one-home", "name": "One home", "priceInr": 849, "planReadings": 3,
                  "fullReports": 3, "homes": 1, "onSale": true }
              ]
            }
        """.trimIndent()
        val plans = Plans.parse(noWords) ?: error("missing words should not lose the plans")
        assertEquals("", plans.unlockTier?.tagline)
        assertEquals("", plans.unlockTier?.blurb)
        assertEquals("₹849", plans.unlockPrice, "and the price still arrives")
    }

    @Test
    fun `a plan the app does not understand is ignored rather than fatal`() {
        // The Control Room may one day publish a field this build has never heard of. It must take
        // the rest and carry on, not throw the whole thing away.
        val extra = good.replace(""""id": "one-home",""", """"id": "one-home", "somethingNew": {"a": 1},""")
        assertEquals("₹849", Plans.parse(extra)?.unlockPrice)
    }

    @Test
    fun `a plan marked to show but switched off shows nothing, so the app keeps its own price`() {
        val off = """
            {
              "unlockTierId": "one-home",
              "tiers": [
                { "id": "one-home", "name": "One home", "priceInr": 849, "planReadings": 3,
                  "fullReports": 3, "homes": 1, "onSale": false, "tagline": "", "blurb": "" }
              ]
            }
        """.trimIndent()
        val plans = Plans.parse(off) ?: error("refused")
        assertNull(plans.unlockPrice, "an unlock plan that is not on sale must not set a price")
    }

    // ---- rupees, written the way they are written in India ------------------------------------

    @Test
    fun `rupees are grouped the Indian way, which is not the same as everywhere else`() {
        assertEquals("₹0", rupees(0))
        assertEquals("₹99", rupees(99))
        assertEquals("₹699", rupees(699))
        assertEquals("₹1,499", rupees(1499))
        assertEquals("₹3,999", rupees(3999))
        assertEquals("₹99,999", rupees(99999))
        // ⭐ Here is where it stops matching the rest of the world. A hundred thousand is 1,00,000
        // in India and 100,000 everywhere else, and a price screen getting that wrong reads as
        // foreign to exactly the people this app is for.
        assertEquals("₹1,00,000", rupees(100000))
        assertEquals("₹12,34,567", rupees(1234567))
    }

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

    @Test
    fun `the key built into the app is the same one the signature tests use`() {
        // ⚠ If these two ever disagree, every phone silently refuses every rule set we publish, for
        // ever, and the only symptom is that nobody seems to get the update.
        val fromResource = PlansTest::class.java.getResourceAsStream("/update/public-key.txt")
            ?.bufferedReader()?.use { it.readText() }?.trim()
            ?: error("the public key fixture is missing")
        assertEquals(fromResource, UPDATE_PUBLIC_KEY_SPKI_B64)
        assertTrue(UPDATE_URL.startsWith("https://"), "a rule set must never travel in the clear")
    }
}
