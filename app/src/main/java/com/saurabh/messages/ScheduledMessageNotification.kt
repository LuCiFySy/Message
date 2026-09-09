package com.saurabh.messages

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object ScheduledMessageNotification {

    private const val CHANNEL_ID = "scheduled_messages"
    private const val SENT_NOTIFICATION_ID = 900000

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Scheduled messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Scheduled message notifications"
            }

            context.getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(channel)
        }
    }

    fun showMissed(
        context: Context,
        message: ScheduledMessage
    ) {
        createChannel(context)

        val sendIntent = Intent(
            context,
            ScheduledMessageActionReceiver::class.java
        ).apply {
            action = ScheduledMessageActionReceiver.ACTION_SEND
            putExtra(
                ScheduledMessageReceiver.EXTRA_ID,
                message.id
            )
        }

        val cancelIntent = Intent(
            context,
            ScheduledMessageActionReceiver::class.java
        ).apply {
            action = ScheduledMessageActionReceiver.ACTION_CANCEL
            putExtra(
                ScheduledMessageReceiver.EXTRA_ID,
                message.id
            )
        }

        val sendPendingIntent =
            PendingIntent.getBroadcast(
                context,
                (message.id xor (message.id ushr 32)).toInt(),
                sendIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val cancelPendingIntent =
            PendingIntent.getBroadcast(
                context,
                (message.id xor (message.id ushr 32)).toInt() + 1,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_ID
        )
            .setSmallIcon(
                com.saurabh.messages.R.drawable.ic_notification_message
            )
            .setContentTitle("Scheduled message")
            .setContentText(message.body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(message.body)
            )
            .setPriority(
                NotificationCompat.PRIORITY_HIGH
            )
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Send",
                sendPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_delete,
                "Cancel",
                cancelPendingIntent
            )
            .build()

        context.getSystemService(
            NotificationManager::class.java
        ).notify(
            notificationId(message.id),
            notification
        )
    }

    fun showSent(context: Context) {
        createChannel(context)

        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_ID
        )
            .setSmallIcon(
                com.saurabh.messages.R.drawable.ic_notification_message
            )
            .setContentTitle("Scheduled message sent")
            .setContentText("Your scheduled message was sent.")
            .setAutoCancel(true)
            .setPriority(
                NotificationCompat.PRIORITY_DEFAULT
            )
            .build()

        context.getSystemService(
            NotificationManager::class.java
        ).notify(
            SENT_NOTIFICATION_ID,
            notification
        )
    }

    fun dismiss(
        context: Context,
        id: Long
    ) {
        context.getSystemService(
            NotificationManager::class.java
        ).cancel(notificationId(id))
    }

    private fun notificationId(id: Long): Int {
        return (id xor (id ushr 32)).toInt()
    }
}
