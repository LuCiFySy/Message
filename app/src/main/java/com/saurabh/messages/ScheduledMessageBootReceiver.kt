package com.saurabh.messages

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduledMessageBootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val messages =
            ScheduledMessageStore.getAll(context)

        val now = System.currentTimeMillis()

        for (message in messages) {

            if (message.missed) {
                ScheduledMessageNotification.showMissed(
                    context,
                    message
                )
                continue
            }

            if (message.scheduledAt <= now) {

                ScheduledMessageStore.markMissed(
                    context,
                    message.id
                )

                ScheduledMessageNotification.showMissed(
                    context,
                    message
                )

            } else {

                ScheduledMessageReceiver.scheduleAlarm(
                    context,
                    message
                )

                ScheduledMessageNotification.showMissed(
                    context,
                    message
                )
            }
        }
    }
}
