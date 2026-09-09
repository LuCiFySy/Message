package com.saurabh.messages

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

class ScheduledMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id <= 0L) return

        val message = ScheduledMessageStore.get(context, id) ?: return

        if (message.missed) return

        if (System.currentTimeMillis() < message.scheduledAt) {
            scheduleAlarm(context, message)
            return
        }

        // The exact alarm fired while the phone was running.
        // Send the message normally.
        ScheduledMessageSender.send(context, message)
    }

    companion object {
        const val EXTRA_ID = "scheduled_id"

        const val ACTION_ALARM =
            "com.saurabh.messages.ACTION_SCHEDULED_ALARM"

        private fun alarmIntent(
            context: Context,
            id: Long
        ): Intent {
            return Intent(
                context,
                ScheduledMessageReceiver::class.java
            ).apply {
                action = ACTION_ALARM
                data = Uri.parse("scheduled://$id")
                putExtra(EXTRA_ID, id)
            }
        }

        fun canScheduleExactAlarms(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return true
            }

            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE)
                    as AlarmManager

            return alarmManager.canScheduleExactAlarms()
        }

        fun scheduleAlarm(
            context: Context,
            message: ScheduledMessage
        ) {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE)
                    as AlarmManager

            if (!canScheduleExactAlarms(context)) {
                return
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                alarmIntent(context, message.id),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                message.scheduledAt,
                pendingIntent
            )
        }

        fun cancelAlarm(
            context: Context,
            id: Long
        ) {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE)
                    as AlarmManager

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                alarmIntent(context, id),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
