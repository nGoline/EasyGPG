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
import com.ngoline.easygpg.R

class MyNotificationListener : NotificationListenerService() {

    private val channelId = "pgp_detect_channel"

    override fun onListenerConnected() {
        Log.d("NotificationListener", "Service Connected")
        createNotificationChannel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d("NotificationListener", "Notification from ${sbn.packageName}")
        if (sbn.packageName == "com.ngoline.easygpg"){
            Log.d("NotificationListener", "Got our own notification!")
        }
        val notificationText = extractNotificationText(sbn.notification)

        if (notificationText.trimStart().startsWith("-----BEGIN PGP MESSAGE-----", ignoreCase = true)) {
            Log.d("NotificationListener", "PGP message detected at start!")
            notifyUserCanDecrypt(notificationText)
        } else {
            Log.d("NotificationListener", "No PGP message at start: $notificationText")
        }
    }

    private fun notifyUserCanDecrypt(encryptedMessage: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("encrypted_message", encryptedMessage)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_menu_camera)
            .setContentTitle("Encrypted Message Detected")
            .setContentText("Tap to decrypt the PGP message.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001, notification)
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