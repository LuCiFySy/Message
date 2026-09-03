package com.saurabh.messages

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    private const val CHANNEL_ID = "incoming_messages"

    const val ACTION_MARK_READ =
        "com.saurabh.messages.ACTION_MARK_READ"

    const val ACTION_DELETE =
        "com.saurabh.messages.ACTION_DELETE"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming message notifications"
            }

            val manager = context.getSystemService(
                NotificationManager::class.java
            )

            manager.createNotificationChannel(channel)
        }
    }

    fun showMessageNotification(
        context: Context,
        messageId: Long,
        address: String,
        body: String
    ) {
        createChannel(context)

        val openIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val openPendingIntent = PendingIntent.getActivity(
            context,
            messageId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = Intent(
            context,
            NotificationActionReceiver::class.java
        ).apply {
            action = ACTION_DELETE
            putExtra("message_id", messageId)
        }

        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            (messageId + 100000).toInt(),
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val markReadIntent = Intent(
            context,
            NotificationActionReceiver::class.java
        ).apply {
            action = ACTION_MARK_READ
            putExtra("message_id", messageId)
        }

        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            (messageId + 200000).toInt(),
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(address)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
            )
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                android.R.drawable.ic_menu_delete,
                "Delete",
                deletePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "Mark as read",
                markReadPendingIntent
            )
            .build()

        val manager = context.getSystemService(
            NotificationManager::class.java
        )

        manager.notify(messageId.toInt(), notification)
    }
}
