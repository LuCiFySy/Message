package com.saurabh.messages

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import java.util.Locale

object NotificationHelper {

    private const val CHANNEL_ID = "incoming_messages"

    const val ACTION_REPLY =
        "com.saurabh.messages.ACTION_REPLY"

    const val REPLY_KEY =
        "com.saurabh.messages.REPLY_KEY"

    const val ACTION_MARK_READ =
        "com.saurabh.messages.ACTION_MARK_READ"

    const val ACTION_DELETE =
        "com.saurabh.messages.ACTION_DELETE"

    const val ACTION_COPY_OTP =
        "com.saurabh.messages.ACTION_COPY_OTP"


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

    private fun getContactName(
        context: Context,
        phoneNumber: String
    ): String {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )

            val projection = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME
            )

            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->

                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(
                        ContactsContract.PhoneLookup.DISPLAY_NAME
                    )

                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)

                        if (!name.isNullOrBlank()) {
                            return name
                        }
                    }
                }
            }

            phoneNumber

        } catch (_: SecurityException) {
            phoneNumber
        } catch (_: Exception) {
            phoneNumber
        }
    }

    private fun extractOtp(body: String): String? {
        val lower = body.lowercase(Locale.ROOT)

        val hasOtpContext = listOf(
            "otp",
            "one-time password",
            "one time password",
            "verification code",
            "verification",
            "security code",
            "passcode",
            "login code"
        ).any { lower.contains(it) }

        if (!hasOtpContext) {
            return null
        }

        return Regex("""(?<!\d)\d{4,8}(?!\d)""")
            .find(body)
            ?.value
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

        val replyIntent = Intent(
            context,
            NotificationActionReceiver::class.java
        ).apply {
            action = NotificationHelper.ACTION_REPLY
            putExtra("message_id", messageId)
            putExtra("address", address)
        }

        val replyRemoteInput = RemoteInput.Builder(
            REPLY_KEY
        )
            .setLabel("Reply")
            .build()

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            (messageId + 300000).toInt(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_MUTABLE
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

        val otp = extractOtp(body)

        val copyOtpPendingIntent = otp?.let {
            val copyIntent = Intent(
                context,
                OtpCopyReceiver::class.java
            ).apply {
                action = ACTION_COPY_OTP
                putExtra("otp", it)
            }

            PendingIntent.getBroadcast(
                context,
                (messageId + 400000).toInt(),
                copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notificationBuilder = NotificationCompat.Builder(
            context,
            CHANNEL_ID
        )
            .setSmallIcon(com.saurabh.messages.R.drawable.ic_notification_message)
            .setLargeIcon(
                android.graphics.BitmapFactory.decodeResource(
                    context.resources,
                    com.saurabh.messages.R.drawable.ic_notification_large
                )
            )
            .setContentTitle(getContactName(context, address))
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
            )
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (otp != null && copyOtpPendingIntent != null) {
            notificationBuilder.addAction(
                android.R.drawable.ic_menu_save,
                "Copy OTP",
                copyOtpPendingIntent
            )

            notificationBuilder.addAction(
                android.R.drawable.ic_menu_delete,
                "Delete",
                deletePendingIntent
            )
        } else {
            notificationBuilder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_send,
                    "Reply",
                    replyPendingIntent
                )
                    .addRemoteInput(replyRemoteInput)
                    .setAllowGeneratedReplies(true)
                    .build()
            )

            notificationBuilder.addAction(
                android.R.drawable.ic_menu_view,
                "Mark as read",
                markReadPendingIntent
            )

            notificationBuilder.addAction(
                android.R.drawable.ic_menu_delete,
                "Delete",
                deletePendingIntent
            )
        }

        val notification = notificationBuilder.build()

        val manager = context.getSystemService(
            NotificationManager::class.java
        )

        manager.notify(messageId.toInt(), notification)
    }
}
