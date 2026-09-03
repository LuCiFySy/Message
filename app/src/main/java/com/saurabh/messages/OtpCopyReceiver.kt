package com.saurabh.messages

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

class OtpCopyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationHelper.ACTION_COPY_OTP) {
            return
        }

        val otp = intent.getStringExtra("otp") ?: return

        val clipboard = context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText("OTP", otp)
        )

        Toast.makeText(
            context,
            "OTP copied",
            Toast.LENGTH_SHORT
        ).show()
    }
}
