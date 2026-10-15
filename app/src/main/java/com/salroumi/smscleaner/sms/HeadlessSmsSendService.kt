package com.salroumi.smscleaner.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager

/** Handles "respond via message" quick replies (e.g. rejecting a call with a text). */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val recipients = SmsSender.recipientFrom(intent.data)
            if (!text.isNullOrBlank() && recipients.isNotBlank()) {
                recipients.split(';').filter { it.isNotBlank() }.forEach { SmsSender.send(this, it.trim(), text) }
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
