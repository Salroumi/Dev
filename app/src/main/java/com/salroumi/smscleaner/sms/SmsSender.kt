package com.salroumi.smscleaner.sms

import android.content.Context
import android.os.Build
import android.telephony.SmsManager

object SmsSender {

    /** Sends [body] to [address] and records it in the Sent box (required for the default SMS app). */
    fun send(context: Context, address: String, body: String) {
        val manager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val parts = manager.divideMessage(body)
        if (parts.size > 1) {
            manager.sendMultipartTextMessage(address, null, parts, null, null)
        } else {
            manager.sendTextMessage(address, null, body, null, null)
        }
        if (DefaultSmsHelper.isDefault(context)) {
            SmsRepository(context).insertSent(address, body)
        }
    }

    /** Extracts the recipient from sms:/smsto:/mms:/mmsto: URIs. */
    fun recipientFrom(uri: android.net.Uri?): String =
        uri?.schemeSpecificPart?.substringBefore('?')?.replace(",", ";").orEmpty()
}
