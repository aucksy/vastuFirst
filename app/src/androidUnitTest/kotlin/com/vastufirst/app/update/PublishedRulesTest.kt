package com.vastufirst.app.update

import android.app.Application
import com.vastufirst.shared.update.RulesetUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * ⭐ THE PROMISE THIS WHOLE FEATURE RESTS ON: with no signal, a refused download, a file somebody
 * has edited, or anything at all going wrong, the app works EXACTLY as it did before any of this
 * existed. The rules and the price built into the build are never deleted, and every failure is
 * silent — there is nothing to tell anybody, because from where they stand nothing is wrong.
 *
 * Every test below is a way for it to go wrong, and every one of them has to end with the app
 * still scoring homes.
 *
 * The good reply is a REAL one, captured from a real publish. A hand-written one would only prove
 * this file agrees with itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PublishedRulesTest {

    private lateinit var context: Application
    private lateinit var store: PublishedRulesStore
    private lateinit var file: File

    private fun fixture(name: String): String =
        PublishedRulesTest::class.java.getResourceAsStream("/update/$name")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("missing fixture $name — it must be committed, not only on a laptop")

    private val goodReply by lazy { fixture("signed-reply-price.json") }

    /**
     * Change part of the stored reply, and PROVE the change landed.
     *
     * ⭐⭐ The assertion in here is the important line, and it is here because its absence cost a
     * build. A tamper test built on String.replace() that matches nothing returns the ORIGINAL
     * text — so the test quietly feeds a perfectly good, perfectly signed document to an assertion
     * expecting a refusal, and fails with a message about the wrong thing entirely.
     *
     * ⚠ AND IT HAS TO BE INSIDE THE PAYLOAD. The reply is a wrapper around a payload STRING, and
     * only the payload is signed. Editing the wrapper proves nothing: its version is cross-checked
     * against the payload's own, and everything else in it is either the signature or ignored.
     * Inside the payload the quotes are escaped, so the bytes to match look like BS-quote-key.
     */
    private fun changed(find: String, replaceWith: String): String {
        val meddled = goodReply.replace(find, replaceWith)
        assertTrue(
            "This test meant to change <$find> and found nothing to change, so it would have " +
                "handed a perfectly good, perfectly signed document to an assertion expecting a " +
                "refusal.",
            meddled != goodReply,
        )
        return meddled
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        file = File(context.filesDir, PublishedRulesStore.FILE_NAME)
        if (file.exists()) file.delete()
        store = PublishedRulesStore(context)
    }

    // ---- nothing stored ---------------------------------------------------------------------

    @Test
    fun `with nothing stored the app uses the rules built into it`() {
        val settled = PublishedRules.atStartup(store, "99.0.0")
        assertFalse("nothing was published, so nothing should have come from the Control Room", settled.fromControlRoom)
        assertNotNull("the app must always have rules to score with", settled.ruleSet)
        assertNull("and no price of its own, so the built-in one shows", settled.plans)
        assertEquals(PublishedRulesStore.NOTHING, store.storedVersion())
    }

    // ---- a good one -------------------------------------------------------------------------

    @Test
    fun `a real published reply is kept, and used at the next start-up`() {
        assertTrue(store.save(goodReply))

        val settled = PublishedRules.atStartup(store, "99.0.0")
        assertTrue("a genuine signed reply should have been taken", settled.fromControlRoom)
        assertNotNull(settled.plans)
        assertEquals("₹849", settled.plans?.unlockPrice)
        assertNotNull("it must still be able to score a home", settled.ruleSet)
    }

    @Test
    fun `the stored version is readable without checking the signature, for asking what is newer`() {
        store.save(goodReply)
        // ⚠ JUnit's assertTrue takes the MESSAGE FIRST — the opposite of kotlin.test's. Getting it
        // the wrong way round does not fail a test, it fails to compile.
        assertTrue(store.storedVersion(), store.storedVersion().matches(Regex("""\d{4}\.\d{2}\.\d{2}-\d+""")))
    }

    // ---- every way it can go wrong ends with the app still working ------------------------------

    @Test
    fun `a file somebody has edited is thrown away, silently`() {
        // One character inside the signed bytes. That makes it a different document, and the whole
        // point of signing is that a phone can tell.
        store.save(changed(
            QUOTE + "minAppVersion" + QUOTE + ":" + QUOTE + "0.0.0" + QUOTE,
            QUOTE + "minAppVersion" + QUOTE + ":" + QUOTE + "0.0.1" + QUOTE,
        ))
        val settled = PublishedRules.atStartup(store, "99.0.0")
        assertFalse("a meddled file must never be used", settled.fromControlRoom)
        assertNotNull("and the app must still score homes", settled.ruleSet)
    }

    @Test
    fun `the wrapper is not signed, and a wrapper claiming a different version is caught`() {
        // ⭐ Worth writing down, because it is not obvious and it is what the first version of the
        // test above got wrong: only the payload STRING is signed. The wrapper around it is not,
        // and does not need to be — its version is checked against the payload's own, so a genuine
        // payload re-labelled as a different version is refused as a mismatch rather than accepted.
        store.save(changed(
            "\"version\":\"2026.09.13-7\",\"payload\"",
            "\"version\":\"2099.01.01-1\",\"payload\"",
        ))
        assertFalse(
            "a genuine payload wearing somebody else's version label must not be used",
            PublishedRules.atStartup(store, "99.0.0").fromControlRoom,
        )
    }

    @Test
    fun `a price changed by somebody on the device is thrown away with the rest`() {
        // ⭐ The attack this actually stops: a phone with root, editing OUR price in OUR file.
        store.save(changed(
            QUOTE + "priceInr" + QUOTE + ":849",
            QUOTE + "priceInr" + QUOTE + ":1",
        ))
        val settled = PublishedRules.atStartup(store, "99.0.0")
        assertFalse(settled.fromControlRoom)
        assertNull("a meddled price must never reach the screen", settled.plans)
    }

    @Test
    fun `a file that is not JSON at all is thrown away`() {
        file.writeText("this is not a reply, it is a shopping list")
        val settled = PublishedRules.atStartup(store, "99.0.0")
        assertFalse(settled.fromControlRoom)
        assertNotNull(settled.ruleSet)
    }

    @Test
    fun `an empty file is thrown away`() {
        file.writeText("")
        assertFalse(PublishedRules.atStartup(store, "99.0.0").fromControlRoom)
    }

    @Test
    fun `forgetting puts the app back on the rules built into it`() {
        store.save(goodReply)
        assertTrue(PublishedRules.atStartup(store, "99.0.0").fromControlRoom)
        assertTrue(store.forget())
        assertFalse(PublishedRules.atStartup(store, "99.0.0").fromControlRoom)
    }

    // ---- asking for something newer --------------------------------------------------------------

    @Test
    fun `no network means nothing happens, and nothing is lost`() {
        val updater = RulesUpdater(store, "99.0.0", fetch = { null })
        assertEquals(RulesUpdater.Result.CouldNotAsk, updater.refresh())
        assertFalse("nothing should have been stored", file.exists())
        assertNotNull(PublishedRules.atStartup(store, "99.0.0").ruleSet)
    }

    @Test
    fun `a network that throws is the same as no network`() {
        val updater = RulesUpdater(store, "99.0.0", fetch = { error("the wifi fell over") })
        assertEquals(RulesUpdater.Result.CouldNotAsk, updater.refresh())
        assertFalse(file.exists())
    }

    @Test
    fun `a reply that is rubbish is refused and nothing is stored`() {
        val updater = RulesUpdater(store, "99.0.0", fetch = { "<html>a login page, somehow</html>" })
        val result = updater.refresh()
        assertTrue("got $result", result is RulesUpdater.Result.NothingNew)
        assertFalse(file.exists())
    }

    @Test
    fun `nothing published yet is an ordinary answer, not a failure`() {
        val updater = RulesUpdater(store, "99.0.0", fetch = { """{"published":false}""" })
        assertEquals(
            RulesUpdater.Result.NothingNew(RulesetUpdate.Rejected.NOTHING_PUBLISHED),
            updater.refresh(),
        )
    }

    @Test
    fun `a genuine reply is fetched and kept`() {
        val updater = RulesUpdater(store, "99.0.0", fetch = { goodReply })
        val result = updater.refresh()
        assertTrue("got $result", result is RulesUpdater.Result.Kept)
        assertTrue(file.exists())
        assertEquals("₹849", PublishedRules.atStartup(store, "99.0.0").plans?.unlockPrice)
    }

    @Test
    fun `the same version twice is not downloaded again`() {
        RulesUpdater(store, "99.0.0", fetch = { goodReply }).refresh()
        val second = RulesUpdater(store, "99.0.0", fetch = { goodReply }).refresh()
        assertEquals(
            RulesUpdater.Result.NothingNew(RulesetUpdate.Rejected.NOT_NEWER),
            second,
        )
    }

    @Test
    fun `a refused reply never replaces a good one already stored`() {
        // ⭐ The one that would be worst to get wrong: a phone that had a perfectly good published
        // rule set, and lost it because the next answer was rubbish.
        assertTrue(store.save(goodReply))
        RulesUpdater(store, "99.0.0", fetch = { "not a reply" }).refresh()
        assertEquals("₹849", PublishedRules.atStartup(store, "99.0.0").plans?.unlockPrice)
    }

    companion object {
        /**
         * The two characters that stand for a quote INSIDE the signed payload.
         *
         * The reply is JSON whose "payload" value is itself JSON, so every quote in there
         * is escaped. Written as a constant rather than inline because a backslash before
         * a quote is exactly the thing that gets eaten by whatever writes this file.
         */
        private const val QUOTE = "\\\""
    }
}
