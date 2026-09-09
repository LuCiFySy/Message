package com.saurabh.messages

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager

object ScheduledMessageSender {

    fun send(
        context: Context,
        message: ScheduledMessage
    ) {
        try {
            val smsManager = SmsManager.getDefault()

            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, message.address)
                put(Telephony.Sms.BODY, message.body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(
                    Telephony.Sms.TYPE,
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX
                )

                if (message.threadId.isNotBlank()) {
                    put(
                        Telephony.Sms.THREAD_ID,
                        message.threadId
                    )
                }

                put(
                    Telephony.TextBasedSmsColumns.STATUS,
                    Telephony.TextBasedSmsColumns.STATUS_PENDING
                )
            }

            val messageUri = context.contentResolver.insert(
                Telephony.Sms.Sent.CONTENT_URI,
                values
            )

            val messageId =
                messageUri?.lastPathSegment?.toLongOrNull() ?: -1L

            if (messageId <= 0L) {
                showFailed(context, message)
                return
            }

            val sentIntent = Intent(
                context,
                SmsStatusReceiver::class.java
            ).apply {
                action = SmsStatusReceiver.ACTION_SMS_SENT
                putExtra("message_id", messageId)
                putExtra("scheduled_id", message.id)
            }

            val deliveryIntent = Intent(
                context,
                SmsStatusReceiver::class.java
            ).apply {
                action = SmsStatusReceiver.ACTION_SMS_DELIVERED
                putExtra("message_id", messageId)
            }

            val sentPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    messageId.hashCode(),
                    sentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                )

            val deliveryPendingIntent =
                if (
                    context.getSharedPreferences(
                        "messages_settings",
                        Context.MODE_PRIVATE
                    ).getBoolean("delivery_reports", true)
                ) {
                    PendingIntent.getBroadcast(
                        context,
                        messageId.hashCode() + 1,
                        deliveryIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                    )
                } else {
                    null
                }

            smsManager.sendTextMessage(
                message.address,
                null,
                message.body,
                sentPendingIntent,
                deliveryPendingIntent
            )

        } catch (_: Exception) {
            showFailed(context, message)
        }
    }

    private fun showFailed(
        context: Context,
        message: ScheduledMessage
    ) {
        ScheduledMessageStore.markMissed(
            context,
            message.id
        )

        ScheduledMessageNotification.showMissed(
            context,
            message.copy(missed = true)
        )
    }
}
