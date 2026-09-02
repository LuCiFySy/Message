package com.saurabh.messages

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra("message_id", -1L)

        if (messageId == -1L) {
            return
        }

        when (intent.action) {
            NotificationHelper.ACTION_DELETE -> {
                deleteMessage(context, messageId)
            }

            NotificationHelper.ACTION_MARK_READ -> {
                markAsRead(context, messageId)
            }
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
