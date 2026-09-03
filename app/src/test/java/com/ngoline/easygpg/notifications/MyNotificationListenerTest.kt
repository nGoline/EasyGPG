package com.ngoline.easygpg.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The dismissal contract, driven through the real entry point.
 *
 * A notification that clears itself on tap loses the message: the tap happens before MainActivity
 * has authenticated, and a failed or dismissed biometric prompt then finishes the activity with
 * nothing on screen and nothing left to tap. The listener only fires when a notification is
 * *posted*, so the message cannot be recovered.
 */
@RunWith(RobolectricTestRunner::class)
class MyNotificationListenerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    private lateinit var listener: MyNotificationListener

    private val pgpMessage =
        "-----BEGIN PGP MESSAGE-----\nhQIMA7Jk9v2mQ1xLAQ//aaa=\n-----END PGP MESSAGE-----"
    private val otherPgpMessage =
        "-----BEGIN PGP MESSAGE-----\nhQIMA7Jk9v2mQ1xLAQ//zzz=\n-----END PGP MESSAGE-----"

    @Before
    fun setUp() {
        listener = Robolectric.buildService(MyNotificationListener::class.java).create().get()
        listener.onListenerConnected()
    }

    /** A notification from some other app, as the listener would receive it. */
    private fun incoming(text: String, id: Int = 1): StatusBarNotification {
        val source = Notification.Builder(context, "someone_elses_channel")
            .setContentTitle("A friend")
            .setContentText(text)
            .build()
        source.extras = Bundle(source.extras).apply {
            putCharSequence(Notification.EXTRA_TEXT, text)
        }
        @Suppress("DEPRECATION") // The only constructor the platform exposes takes the old score.
        return StatusBarNotification(
            "com.example.chat",
            "com.example.chat",
            id,
            "tag$id",
            0,
            0,
            0, // score, unused since API 21
            source,
            Process.myUserHandle(),
            System.currentTimeMillis(),
        )
    }

    private fun posted(): List<StatusBarNotification> =
        shadowOf(manager).activeNotifications.toList()

    /** The encrypted message the given notification would hand to the decrypt screen. */
    private fun messageBehind(sbn: StatusBarNotification): String? =
        shadowOf(sbn.notification.contentIntent).savedIntent.getStringExtra("encrypted_message")

    /** The notification id the given notification tells MainActivity to cancel. */
    private fun idBehind(sbn: StatusBarNotification): Int =
        shadowOf(sbn.notification.contentIntent).savedIntent
            .getIntExtra(EXTRA_NOTIFICATION_ID, -1)

    @Test
    fun `a detected PGP message raises a notification`() {
        listener.onNotificationPosted(incoming(pgpMessage))

        assertEquals(1, posted().size)
    }

    @Test
    fun `an ordinary message raises nothing`() {
        listener.onNotificationPosted(incoming("lunch at one?"))

        assertTrue(posted().isEmpty())
    }

    @Test
    fun `the notification is not dismissed simply by tapping it`() {
        // The regression this guards: FLAG_AUTO_CANCEL lets the system clear the notification the
        // instant it is tapped, which is before authentication has been asked for, let alone
        // passed. Cancelling is MainActivity's job, once the message is actually on screen.
        listener.onNotificationPosted(incoming(pgpMessage))

        val flags = posted().single().notification.flags
        assertEquals(
            "FLAG_AUTO_CANCEL must not be set",
            0,
            flags and Notification.FLAG_AUTO_CANCEL,
        )
    }

    @Test
    fun `the notification tells MainActivity which id to cancel`() {
        // MainActivity cancels by this id after the message reaches the decrypt screen. If it
        // does not match what the notification was posted under, the wrong one is cleared.
        listener.onNotificationPosted(incoming(pgpMessage))

        val sbn = posted().single()
        assertEquals(sbn.id, idBehind(sbn))
    }

    @Test
    fun `two different messages raise two notifications`() {
        listener.onNotificationPosted(incoming(pgpMessage, id = 1))
        listener.onNotificationPosted(incoming(otherPgpMessage, id = 2))

        assertEquals(2, posted().size)
    }

    @Test
    fun `each notification opens its own message`() {
        // The subtle one. Extras are not part of a PendingIntent's identity, so a fixed request
        // code would give both notifications the same PendingIntent and FLAG_UPDATE_CURRENT would
        // point both at whichever message arrived last.
        listener.onNotificationPosted(incoming(pgpMessage, id = 1))
        listener.onNotificationPosted(incoming(otherPgpMessage, id = 2))

        val messages = posted().map { messageBehind(it) }
        assertEquals("both notifications opened the same message: $messages", 2, messages.toSet().size)
        assertNotEquals(messages[0], messages[1])
    }

    @Test
    fun `the same message arriving again replaces its notification`() {
        // Messaging apps repost or update their notification as messages arrive, and every repost
        // reaches the listener again. That must not stack duplicates.
        listener.onNotificationPosted(incoming(pgpMessage, id = 1))
        listener.onNotificationPosted(incoming(pgpMessage, id = 2))

        assertEquals(1, posted().size)
    }

    @Test
    fun `cancelling the id from the intent clears that notification and no other`() {
        // What MainActivity does once the message is on screen.
        listener.onNotificationPosted(incoming(pgpMessage, id = 1))
        listener.onNotificationPosted(incoming(otherPgpMessage, id = 2))
        val first = posted().first { messageBehind(it)?.contains("aaa") == true }
        val survivorMessage = posted().first { it.id != first.id }.let { messageBehind(it) }

        manager.cancel(idBehind(first))

        assertEquals(1, posted().size)
        assertEquals(survivorMessage, messageBehind(posted().single()))
        assertNull(posted().singleOrNull { it.id == first.id })
    }
}
