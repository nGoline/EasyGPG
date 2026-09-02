package com.ngoline.easygpg.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ngoline.easygpg.MainActivity
import com.ngoline.easygpg.PGPConstants
import com.ngoline.easygpg.R

const val LOG_TAG = "NotificationListener"

/** Carries the notification's id to [MainActivity] so it can be cleared once the message is read. */
const val EXTRA_NOTIFICATION_ID = "notification_id"

/** First id used for a detected-message notification; the old single notification used 1001. */
private const val NOTIFICATION_ID_BASE = 1001

/** How many distinct ids messages are spread across, starting at [NOTIFICATION_ID_BASE]. */
private const val NOTIFICATION_ID_RANGE = 100_000

/**
 * A notification id for [message], stable for the same text.
 *
 * Each detected message gets its own notification, so several encrypted messages no longer
 * collapse into one. Deriving the id from the text rather than counting means an app that reposts
 * or updates its notification — which many messaging apps do on every new message — lands on the
 * same id and replaces the entry instead of stacking a duplicate.
 */
internal fun notificationIdFor(message: String): Int = notificationIdForHash(message.hashCode())

/**
 * Split out so the awkward case can be tested directly: `Int.MIN_VALUE.absoluteValue` is still
 * negative, so the sign is cleared with a mask rather than [Math.abs].
 */
internal fun notificationIdForHash(hash: Int): Int =
    NOTIFICATION_ID_BASE + (hash and Int.MAX_VALUE) % NOTIFICATION_ID_RANGE

class MyNotificationListener : NotificationListenerService() {

    private val channelId = "pgp_detect_channel"

    override fun onListenerConnected() {
        Log.d(LOG_TAG, "Service Connected")
        createNotificationChannel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(LOG_TAG, "Notification from ${sbn.packageName}")
        if (sbn.packageName == "com.ngoline.easygpg"){
            Log.d(LOG_TAG, "Got our own notification!")
        }
        val notificationText = extractNotificationText(sbn.notification)
        val foundMarker = notificationText.trimStart().startsWith(PGPConstants.PGP_MARKER, ignoreCase = true)
        val foundObf = notificationText.trimStart().startsWith(PGPConstants.OBFUSCATED_MARKER)
        if (foundObf) {
            Log.d(LOG_TAG, "Obfuscated PGP message detected at start!")
            if (!notificationText.trimStart().startsWith(PGPConstants.OBFUSCATED_MARKER)) {
                Log.e(LOG_TAG, "Ending marker not found in obfuscated message: $notificationText")
                return
            }
        }
        if (foundMarker || foundObf) {
            Log.d(LOG_TAG, "PGP message detected at start! (obfuscated=$foundObf)")
            notifyUserCanDecrypt(notificationText)
        } else {
            Log.d(LOG_TAG, "No PGP message at start: $notificationText")
        }
    }

    private fun notifyUserCanDecrypt(encryptedMessage: String) {
        // Ensure it is a single line
        val msg = encryptedMessage.replace("\n", "")

        val notificationId = notificationIdFor(msg)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("encrypted_message", msg)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        // The request code must differ per message too. Extras are not part of a PendingIntent's
        // identity, so with a fixed request code every notification would share one PendingIntent
        // and FLAG_UPDATE_CURRENT would point them all at whichever message arrived last.
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_menu_camera)
            .setContentTitle("Encrypted Message Detected")
            .setContentText("Tap to decrypt the PGP message.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            // Deliberately not setAutoCancel(true). That dismisses the notification the instant it
            // is tapped, which is before the app has authenticated — so failing or dismissing the
            // biometric prompt would take the only route back to the message with it. MainActivity
            // cancels this notification itself once the message is actually on screen.
            .setAutoCancel(false)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        val name = "PGP Detection"
        val descriptionText = "Notifies when a PGP message is detected"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun extractNotificationText(notification: Notification): String {
        val extras = notification.extras
        val textComponents = mutableListOf<String>()

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (!text.isNullOrBlank()) textComponents.add(text)

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        if (!title.isNullOrBlank()) textComponents.add(title)

        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        if (!subText.isNullOrBlank()) textComponents.add(subText)

        return textComponents.joinToString(" ")
    }
}