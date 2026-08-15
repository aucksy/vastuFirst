package com.vastufirst.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Two saved homes must never end up with the same NAME. The name is the only thing telling two rows
 * of the saved-homes list apart, so a duplicate makes opening — or deleting — the right home a coin
 * flip on somebody's own saved work.
 *
 * "The same name" is judged the way the person reading the list judges it, not the way a string
 * comparison does: surrounding blanks, doubled blanks and capitals are all invisible on screen.
 */
class HomeNameTest {

    @Test fun `capitals do not make a different name`() {
        assertEquals(homeNameKey("Dwarka Flat"), homeNameKey("dwarka flat"))
    }

    @Test fun `surrounding and doubled blanks do not make a different name`() {
        assertEquals(homeNameKey("Dwarka flat"), homeNameKey("  Dwarka   flat "))
    }

    @Test fun `a tab reads as a blank, like it looks on screen`() {
        assertEquals(homeNameKey("Dwarka flat"), homeNameKey("Dwarka\tflat"))
    }

    @Test fun `genuinely different names stay different`() {
        assertFalse(homeNameKey("Dwarka flat") == homeNameKey("Dwarka house"))
    }

    // --- the box that refuses the rename ---

    @Test fun `a name another home already has is refused`() {
        assertTrue(homeNameAlreadyTaken("Dwarka flat", listOf("Home 1", "Dwarka flat")))
    }

    @Test fun `a name another home has in different capitals is refused`() {
        assertTrue(homeNameAlreadyTaken("DWARKA FLAT", listOf("Dwarka flat")))
    }

    @Test fun `a free name is allowed`() {
        assertFalse(homeNameAlreadyTaken("Dwarka flat", listOf("Home 1", "Home 2")))
    }

    @Test fun `a blank name is not reported as taken — blank is refused separately`() {
        // The box already refuses an empty name on its own. Reporting blank as "taken" would put a
        // sentence about another home in front of somebody who has simply cleared the field.
        assertFalse(homeNameAlreadyTaken("   ", listOf("Home 1")))
    }

    @Test fun `keeping your own name is allowed — the home being renamed is not in the list`() {
        // The caller passes every OTHER home. That is what lets somebody open the box, change
        // nothing, and press Save; and what lets them fix only the capitals of their own name.
        assertFalse(homeNameAlreadyTaken("Dwarka flat", listOf("Home 1", "Home 2")))
    }

    // --- the name a brand-new home is given ---

    @Test fun `a new home gets the next free number`() {
        assertEquals("Home 3", freshHomeName(listOf("Home 1", "Home 2")))
    }

    @Test fun `the first home is Home 1`() {
        assertEquals("Home 1", freshHomeName(emptyList()))
    }

    /**
     * ⭐ The hole plain numbering leaves open, and the reason [freshHomeName] exists.
     *
     * A user renames a home to "home 4" in lower case. The number search only recognises the exact
     * form "Home 4", so it does not see that name at all and answers 4 — and the app would then mint
     * a second home called "Home 4", producing exactly the two indistinguishable rows this whole
     * file is about. Stepping past taken names closes it.
     */
    @Test fun `a lower-case rename cannot be handed back to a new home`() {
        val saved = listOf("Home 1", "Home 2", "Home 3", "home 4")
        assertEquals(4, nextHomeNumber(saved))              // the hole, stated: it offers "Home 4"
        assertEquals("Home 5", freshHomeName(saved))        // and this is what closes it
    }

    @Test fun `it steps over a whole run of taken names`() {
        assertEquals("Home 4", freshHomeName(listOf("home 1", "HOME 2", "Home  3")))
    }
}
