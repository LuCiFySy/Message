package com.saurabh.messages

import android.app.Service
import android.content.Intent
import android.os.IBinder

class RespondViaMessageService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
