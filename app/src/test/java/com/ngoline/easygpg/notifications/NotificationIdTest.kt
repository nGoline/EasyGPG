package com.ngoline.easygpg.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The id decides whether two detected messages appear as two notifications or silently overwrite
 * one another, so the properties it has to hold are worth pinning: stable per message, different
 * across messages, and never negative or out of band.
 */
class NotificationIdTest {

    private val validRange = 1001..101_000

    private val messages = listOf(
        "-----BEGIN PGP MESSAGE-----hQIMA7Jk9v2mQ1xLAQ//abc=-----END PGP MESSAGE-----",
        "-----BEGIN PGP MESSAGE-----hQIMA7Jk9v2mQ1xLAQ//xyz=-----END PGP MESSAGE-----",
        "00023CD1hQIMA7Jk9v2mQ1xLAQ00023CD1",
        "",
    )

    @Test
    fun `the same message always gets the same id`() {
        for (message in messages) {
            assertEquals(
                "id for '$message' should be stable",
                notificationIdFor(message),
                notificationIdFor(message),
            )
        }
    }

    @Test
    fun `a reposted message replaces rather than duplicates`() {
        // Messaging apps repost or update their notification as new messages arrive, and each
        // repost reaches the listener again. The same text must land on the same notification.
        val message = messages[0]
        val first = notificationIdFor(message)
        val second = notificationIdFor(String(message.toCharArray()))

        assertEquals(first, second)
    }

    @Test
    fun `different messages get different ids`() {
        val ids = messages.map { notificationIdFor(it) }

        assertEquals("distinct messages collided: $ids", ids.size, ids.toSet().size)
    }

    @Test
    fun `ids stay inside the reserved band`() {
        for (message in messages) {
            val id = notificationIdFor(message)
            assertTrue("id $id for '$message' outside $validRange", id in validRange)
        }
    }

    @Test
    fun `a hash of Int MIN_VALUE still yields a usable id`() {
        // Math.abs(Int.MIN_VALUE) is still Int.MIN_VALUE, which would produce a negative id and,
        // via the request code, a PendingIntent shared between unrelated messages.
        val id = notificationIdForHash(Int.MIN_VALUE)

        assertTrue("id $id outside $validRange", id in validRange)
    }

    @Test
    fun `every extreme hash yields a usable id`() {
        for (hash in listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -1, 0, 1, Int.MAX_VALUE)) {
            val id = notificationIdForHash(hash)
            assertTrue("id $id for hash $hash outside $validRange", id in validRange)
        }
    }

    @Test
    fun `ids are spread rather than clustered on one value`() {
        // A mistake such as masking with the wrong constant can quietly map everything onto a
        // single id, which is exactly the bug being fixed.
        val ids = (1..500).map { notificationIdFor("message number $it") }.toSet()

        assertTrue("500 messages produced only ${ids.size} distinct ids", ids.size > 450)
    }

    @Test
    fun `the base id matches the notification this replaces`() {
        // The old code posted every detection to id 1001; staying in that band means an upgrade
        // does not leave an orphaned notification behind.
        assertNotEquals(0, notificationIdFor("anything"))
        assertTrue(notificationIdFor("anything") >= 1001)
    }
}
