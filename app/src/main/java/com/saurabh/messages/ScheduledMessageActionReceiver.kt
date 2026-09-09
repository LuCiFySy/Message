package com.saurabh.messages

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduledMessageActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(
            ScheduledMessageReceiver.EXTRA_ID,
            -1L
        )

        if (id <= 0L) return

        val message =
            ScheduledMessageStore.get(context, id)
                ?: return

        when (intent.action) {

            ACTION_SEND -> {
                ScheduledMessageNotification.dismiss(
                    context,
                    id
                )

                // The schedule remains stored until
                // SmsStatusReceiver confirms RESULT_OK.
                ScheduledMessageSender.send(
                    context,
                    message.copy(missed = false)
                )
            }

            ACTION_CANCEL -> {
                ScheduledMessageReceiver.cancelAlarm(
                    context,
                    id
                )

                ScheduledMessageStore.remove(
                    context,
                    id
                )

                ScheduledMessageNotification.dismiss(
                    context,
                    id
                )
            }
        }
    }

    companion object {

        const val ACTION_SEND =
            "com.saurabh.messages.ACTION_SCHEDULED_SEND"

        const val ACTION_CANCEL =
            "com.saurabh.messages.ACTION_SCHEDULED_CANCEL"
    }
}
