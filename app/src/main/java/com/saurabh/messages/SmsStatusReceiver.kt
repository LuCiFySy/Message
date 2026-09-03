package com.saurabh.messages

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra("message_id", -1L)
        if (messageId <= 0L) return

        val action = intent.action ?: return

        if (action == ACTION_SMS_SENT) {
            val values = ContentValues().apply {
                put(
                    Telephony.Sms.TYPE,
                    if (resultCode == Activity.RESULT_OK) {
                        Telephony.Sms.MESSAGE_TYPE_SENT
                    } else {
                        Telephony.Sms.MESSAGE_TYPE_FAILED
                    }
                )
            }

            try {
                context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms._ID}=?",
                    arrayOf(messageId.toString())
                )

                notifyConversationChanged(context, messageId)
            } catch (_: Exception) {
            }
        } else if (action == ACTION_SMS_DELIVERED) {
            val values = ContentValues().apply {
                put(Telephony.TextBasedSmsColumns.STATUS, Telephony.TextBasedSmsColumns.STATUS_COMPLETE)
            }

            try {
                context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms._ID}=?",
                    arrayOf(messageId.toString())
                )

                notifyConversationChanged(context, messageId)
            } catch (_: Exception) {
            }
        }
    }

    private fun notifyConversationChanged(context: Context, messageId: Long) {
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID),
                "${Telephony.Sms._ID}=?",
                arrayOf(messageId.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val threadId = cursor.getLong(0)

                    context.sendBroadcast(
                        Intent(ConversationActivity.ACTION_MESSAGES_CHANGED).apply {
                            setPackage(context.packageName)
                            putExtra("thread_id", threadId)
                        }
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val ACTION_SMS_SENT =
            "com.saurabh.messages.ACTION_SMS_SENT"

        const val ACTION_SMS_DELIVERED =
            "com.saurabh.messages.ACTION_SMS_DELIVERED"
    }
}
