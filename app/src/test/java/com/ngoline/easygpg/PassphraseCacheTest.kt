package com.ngoline.easygpg

import android.content.Intent
import android.os.Looper
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock

/**
 * The cache holds the one secret the app most wants to forget on time, so these tests pin the
 * eviction contract: the passphrase must not outlive its chosen duration, must not survive a
 * screen off, and must never be handed out as a buffer the cache still owns.
 */
@RunWith(RobolectricTestRunner::class)
class PassphraseCacheTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        // The cache is an object, so state leaks between tests unless it is reset both ways.
        PassphraseCache.clear()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @After
    fun tearDown() {
        PassphraseCache.clear()
    }

    @Test
    fun `a stored passphrase is readable back`() {
        PassphraseCache.store(context, "hunter2".toCharArray(), PassphraseCache.Remember.ONE_HOUR)

        assertArrayEquals("hunter2".toCharArray(), PassphraseCache.get())
    }

    @Test
    fun `nothing is cached to begin with`() {
        assertNull(PassphraseCache.get())
    }

    @Test
    fun `storing copies the passphrase instead of keeping the caller's buffer`() {
        val passphrase = "hunter2".toCharArray()
        PassphraseCache.store(context, passphrase, PassphraseCache.Remember.ONE_HOUR)

        // The caller owns its buffer and is expected to wipe it; that must not empty the cache.
        passphrase.wipe()

        assertArrayEquals("hunter2".toCharArray(), PassphraseCache.get())
    }

    @Test
    fun `each read hands out a separate buffer the caller may wipe`() {
        PassphraseCache.store(context, "hunter2".toCharArray(), PassphraseCache.Remember.ONE_HOUR)

        val first = PassphraseCache.get()!!
        assertNotSame(first, PassphraseCache.get())

        first.wipe()

        assertArrayEquals("hunter2".toCharArray(), PassphraseCache.get())
    }

    @Test
    fun `a timed entry survives up to its expiry`() {
        PassphraseCache.store(context, "hunter2".toCharArray(), PassphraseCache.Remember.ONE_HOUR)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(59))

        assertArrayEquals("hunter2".toCharArray(), PassphraseCache.get())
    }

    @Test
    fun `a timed entry is evicted once its duration has passed`() {
        PassphraseCache.store(context, "hunter2".toCharArray(), PassphraseCache.Remember.ONE_HOUR)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(61))

        assertNull(PassphraseCache.get())
    }

    @Test
    fun `a timed entry is refused on read even if its eviction never ran`() {
        PassphraseCache.store(context, "hunter2".toCharArray(), PassphraseCache.Remember.ONE_HOUR)

        // Advance the clock without letting the main looper run, so the posted eviction does not
        // fire. This isolates the second line of defence: a read past the expiry must refuse and
        // wipe, even when the active eviction was starved.
        ShadowSystemClock.advanceBy(Duration.ofMinutes(61))

        assertNull(PassphraseCache.get())
    }

    @Test
    fun `a one day entry outlives an hour`() {
        PassphraseCache.store(context, "hunter2".toCharArray(), PassphraseCache.Remember.ONE_DAY)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofHours(2))
        assertArrayEquals("hunter2".toCharArray(), PassphraseCache.get())

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofHours(23))
        assertNull(PassphraseCache.get())
    }

    @Test
    fun `an until-screen-off entry is dropped when the screen goes off`() {
        PassphraseCache.store(
            context,
            "hunter2".toCharArray(),
            PassphraseCache.Remember.UNTIL_SCREEN_OFF,
        )
        assertArrayEquals("hunter2".toCharArray(), PassphraseCache.get())

        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(PassphraseCache.get())
    }

    @Test
    fun `an until-screen-off entry ignores the passage of time alone`() {
        PassphraseCache.store(
            context,
            "hunter2".toCharArray(),
            PassphraseCache.Remember.UNTIL_SCREEN_OFF,
        )

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofDays(2))

        assertArrayEquals("hunter2".toCharArray(), PassphraseCache.get())
    }

    @Test
    fun `storing again replaces the previous entry and its timer`() {
        PassphraseCache.store(context, "first".toCharArray(), PassphraseCache.Remember.ONE_HOUR)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(50))

        PassphraseCache.store(context, "second".toCharArray(), PassphraseCache.Remember.ONE_HOUR)
        // The first entry's timer would have fired here; it must not evict the new one.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(20))

        assertArrayEquals("second".toCharArray(), PassphraseCache.get())
    }

    @Test
    fun `clear forgets the passphrase`() {
        PassphraseCache.store(context, "hunter2".toCharArray(), PassphraseCache.Remember.ONE_HOUR)

        PassphraseCache.clear()

        assertNull(PassphraseCache.get())
    }

    @Test
    fun `the chosen duration is remembered but the passphrase is not persisted`() {
        PassphraseCache.store(context, "hunter2".toCharArray(), PassphraseCache.Remember.ONE_DAY)

        assertEquals(PassphraseCache.Remember.ONE_DAY, PassphraseCache.lastRemember(context))

        val stored = PreferenceManager.getDefaultSharedPreferences(context).all
        for ((key, value) in stored) {
            assertEquals("preference '$key' must not hold the passphrase", false, value == "hunter2")
        }
    }

    @Test
    fun `the default duration is used until one has been chosen`() {
        assertEquals(PassphraseCache.DEFAULT, PassphraseCache.lastRemember(context))
    }

    @Test
    fun `an unknown stored duration falls back to the default`() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString("passphrase_cache_last_remember", "SOME_REMOVED_OPTION")
            .commit()

        assertEquals(PassphraseCache.DEFAULT, PassphraseCache.lastRemember(context))
    }
}
