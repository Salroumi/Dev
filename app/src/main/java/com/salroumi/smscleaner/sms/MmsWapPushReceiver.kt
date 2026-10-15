package com.salroumi.smscleaner.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Required for default-SMS-app eligibility. This app is a cleaner, not a full
 * messaging app, so MMS downloads are not handled; the UI reminds the user to
 * switch back to their regular messaging app once cleaning is done.
 */
class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("SmsCleaner", "MMS notification received; restore your regular SMS app to download MMS")
    }
}
