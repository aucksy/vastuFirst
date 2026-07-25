package com.vastufirst.data

import kotlin.test.Test
import kotlin.test.assertEquals

class NextHomeNumberTest {

    @Test fun `no homes yet starts at 1`() {
        assertEquals(1, nextHomeNumber(emptyList()))
    }

    @Test fun `sequential homes give the next number`() {
        assertEquals(3, nextHomeNumber(listOf("Home 1", "Home 2")))
    }

    @Test fun `uses max plus one, not count — a delete never collides`() {
        // "Home 2" was deleted; the survivors are Home 1 and Home 3. count+1 would give 3 (a
        // duplicate of the existing Home 3); max+1 correctly gives 4.
        assertEquals(4, nextHomeNumber(listOf("Home 1", "Home 3")))
    }

    @Test fun `ignores renamed and unrelated names`() {
        // Only exact "Home N" names count toward the sequence; a user-renamed home is skipped.
        assertEquals(3, nextHomeNumber(listOf("Home 1", "Dwarka flat", "Home 2", "My home", "Home")))
    }

    @Test fun `trims surrounding whitespace before matching`() {
        assertEquals(6, nextHomeNumber(listOf("  Home 5  ")))
    }

    @Test fun `substring matches do not count`() {
        // "Home 9 (old)" is not an exact "Home N", so it must not seed the sequence at 10.
        assertEquals(1, nextHomeNumber(listOf("Home 9 (old)", "Second Home 4")))
    }
}
