package com.saurabh.messages

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.app.RemoteInput

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra("message_id", -1L)

        if (messageId == -1L) {
            return
        }

        when (intent.action) {
            NotificationHelper.ACTION_REPLY -> {
                replyToMessage(context, intent, messageId)
            }

            NotificationHelper.ACTION_DELETE -> {
                deleteMessage(context, messageId)
            }

            NotificationHelper.ACTION_MARK_READ -> {
                markAsRead(context, messageId)
            }
        }
    }

    private fun replyToMessage(
        context: Context,
        intent: Intent,
        messageId: Long
    ) {
        val address = intent.getStringExtra("address") ?: return

        val results = RemoteInput.getResultsFromIntent(intent)
        val reply = results?.getCharSequence(
            NotificationHelper.REPLY_KEY
        )?.toString()?.trim()

        if (reply.isNullOrEmpty() || address.isBlank()) {
            return
        }

        try {
            SmsManager.getDefault().sendTextMessage(
                address,
                null,
                reply,
                null,
                null
            )

            val threadId = Telephony.Threads.getOrCreateThreadId(
                context,
                address
            )

            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, reply)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.THREAD_ID, threadId)
            }

            context.contentResolver.insert(
                Telephony.Sms.Sent.CONTENT_URI,
                values
            )

            cancelNotification(context, messageId)

        } catch (_: SecurityException) {
            return
        } catch (_: Exception) {
            return
        }
    }

    private fun deleteMessage(
        context: Context,
        messageId: Long
    ) {
        try {
            context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms._ID}=?",
                arrayOf(messageId.toString())
            )
        } catch (_: SecurityException) {
            return
        }

        cancelNotification(context, messageId)
    }

    private fun markAsRead(
        context: Context,
        messageId: Long
    ) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }

            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms._ID}=?",
                arrayOf(messageId.toString())
            )
        } catch (_: SecurityException) {
            return
        }

        cancelNotification(context, messageId)
    }

    private fun cancelNotification(
        context: Context,
        messageId: Long
    ) {
        val manager = context.getSystemService(
            android.app.NotificationManager::class.java
        )

        manager.cancel(messageId.toInt())
    }
}
