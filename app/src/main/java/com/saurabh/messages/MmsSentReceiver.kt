package com.saurabh.messages

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.widget.Toast
import java.io.File

class MmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val resultCode = resultCode
        val contentUriString =
            intent.getStringExtra("content_uri")

        val filePath =
            intent.getStringExtra("file_path")

        if (!contentUriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(contentUriString)

                val values = ContentValues().apply {
                    put(
                        Telephony.Mms.MESSAGE_BOX,
                        if (resultCode == Activity.RESULT_OK) {
                            Telephony.Mms.MESSAGE_BOX_SENT
                        } else {
                            Telephony.Mms.MESSAGE_BOX_FAILED
                        }
                    )
                }

                context.contentResolver.update(
                    uri,
                    values,
                    null,
                    null
                )
            } catch (_: Exception) {
            }
        }

        if (!filePath.isNullOrBlank()) {
            try {
                File(filePath).delete()
            } catch (_: Exception) {
            }
        }

        val message = if (resultCode == Activity.RESULT_OK) {
            "MMS sent"
        } else {
            "MMS failed (error $resultCode)"
        }

        Toast.makeText(
            context,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
