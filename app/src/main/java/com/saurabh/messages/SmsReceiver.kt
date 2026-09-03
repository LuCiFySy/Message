package com.saurabh.messages

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            return
        }

        Log.d(TAG, "SMS_DELIVER received")

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        if (messages.isEmpty()) {
            Log.d(TAG, "SMS_DELIVER contained no messages")
            return
        }

        val address = messages.first().originatingAddress

        if (address.isNullOrBlank()) {
            Log.e(TAG, "Incoming SMS has no originating address")
            return
        }

        val body = messages.joinToString(separator = "") {
            it.messageBody ?: ""
        }

        if (body.isEmpty()) {
            Log.e(TAG, "Incoming SMS has empty body")
            return
        }

        val date = messages.minOfOrNull { it.timestampMillis }
            ?: System.currentTimeMillis()

        try {
            val threadId = Telephony.Threads.getOrCreateThreadId(
                context,
                address
            )

            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, date)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                put(Telephony.Sms.THREAD_ID, threadId)
            }

            val uri = context.contentResolver.insert(
                Telephony.Sms.Inbox.CONTENT_URI,
                values
            )

            if (uri != null) {
                val messageId = android.content.ContentUris.parseId(uri)

                Log.d(
                    TAG,
                    "SMS stored successfully: address=$address body=$body uri=$uri id=$messageId"
                )

                NotificationHelper.showMessageNotification(
                    context = context,
                    messageId = messageId,
                    address = address,
                    body = body
                )
            } else {
                Log.e(TAG, "SMS provider returned null while inserting message")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to store incoming SMS", e)
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
