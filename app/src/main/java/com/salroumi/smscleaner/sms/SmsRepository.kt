package com.salroumi.smscleaner.sms

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.salroumi.smscleaner.analysis.SmsRecord

/** Reads, writes and deletes messages in the system SMS provider. */
class SmsRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    /** Loads received messages, newest first. */
    fun loadInbox(limit: Int = 5000): List<SmsRecord> {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        val raw = ArrayList<Triple<Long, String, Pair<String, Long>>>()
        resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addrCol = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext() && raw.size < limit) {
                raw += Triple(
                    c.getLong(idCol),
                    c.getString(addrCol).orEmpty(),
                    c.getString(bodyCol).orEmpty() to c.getLong(dateCol),
                )
            }
        }

        val contactCache = HashMap<String, Boolean>()
        return raw.map { (id, address, rest) ->
            SmsRecord(
                id = id,
                address = address,
                body = rest.first,
                date = rest.second,
                fromContact = contactCache.getOrPut(address) { isContact(address) },
            )
        }
    }

    /** Deletes messages by id. Only works while this app is the default SMS app. */
    fun delete(ids: Collection<Long>): Int {
        var deleted = 0
        for (id in ids) {
            deleted += resolver.delete(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id), null, null)
        }
        return deleted
    }

    fun insertIncoming(address: String, body: String, date: Long, subId: Int?): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, date)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            if (subId != null) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        return resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
    }

    fun insertSent(address: String, body: String): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
        }
        return resolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
    }

    private fun isContact(address: String): Boolean {
        if (address.isBlank() || address.any { it.isLetter() }) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        return try {
            resolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
