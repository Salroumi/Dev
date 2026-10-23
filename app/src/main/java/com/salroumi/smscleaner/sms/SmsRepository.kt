package com.salroumi.smscleaner.sms

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.salroumi.smscleaner.analysis.SmsRecord

/**
 * Reads, writes and deletes messages in the system SMS/MMS provider.
 * MMS records use negative ids so they never collide with SMS ids.
 */
class SmsRepository(private val context: Context) {

    private val resolver get() = context.contentResolver
    private val contactCache = HashMap<String, String?>()

    /** Loads all SMS (received and sent) and MMS, newest first. */
    fun loadAll(): List<SmsRecord> {
        contactCache.clear()
        val all = loadSms() + loadMms()
        return all.sortedByDescending { it.date }
    }

    private fun loadSms(): List<SmsRecord> {
        val out = ArrayList<SmsRecord>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
        )
        resolver.query(Telephony.Sms.CONTENT_URI, projection, null, null, "${Telephony.Sms.DATE} DESC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addrCol = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeCol = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            while (c.moveToNext()) {
                val type = c.getInt(typeCol)
                if (type == Telephony.Sms.MESSAGE_TYPE_DRAFT) continue
                val address = c.getString(addrCol).orEmpty()
                val name = contactName(address)
                out += SmsRecord(
                    id = c.getLong(idCol),
                    address = address,
                    body = c.getString(bodyCol).orEmpty(),
                    date = c.getLong(dateCol),
                    fromContact = name != null,
                    contactName = name,
                    sent = type != Telephony.Sms.MESSAGE_TYPE_INBOX,
                )
            }
        }
        return out
    }

    private fun loadMms(): List<SmsRecord> = try {
        // Text of every MMS, read in a single query and grouped by message id.
        val texts = HashMap<Long, StringBuilder>()
        resolver.query(
            Uri.parse("content://mms/part"),
            arrayOf("mid", "text"),
            "ct = ?",
            arrayOf("text/plain"),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val text = c.getString(1) ?: continue
                texts.getOrPut(c.getLong(0)) { StringBuilder() }.append(text)
            }
        }

        val out = ArrayList<SmsRecord>()
        resolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX),
            null,
            null,
            "${Telephony.Mms.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val box = c.getInt(2)
                if (box == Telephony.Mms.MESSAGE_BOX_DRAFTS) continue
                val sent = box != Telephony.Mms.MESSAGE_BOX_INBOX
                val address = mmsAddress(id, sent)
                val name = contactName(address)
                out += SmsRecord(
                    id = -id,
                    address = address,
                    body = texts[id]?.toString().orEmpty(),
                    date = c.getLong(1) * 1000, // MMS dates are in seconds
                    fromContact = name != null,
                    contactName = name,
                    sent = sent,
                    isMms = true,
                )
            }
        }
        out
    } catch (e: Exception) {
        Log.w("SmsCleaner", "Could not read MMS", e)
        emptyList()
    }

    private fun mmsAddress(id: Long, sent: Boolean): String {
        // PDU header types: 137 = FROM, 151 = TO.
        val wanted = if (sent) 151 else 137
        resolver.query(
            Uri.parse("content://mms/$id/addr"),
            arrayOf("address", "type"),
            null,
            null,
            null,
        )?.use { c ->
            var fallback = ""
            while (c.moveToNext()) {
                val address = c.getString(0).orEmpty()
                if (address == "insert-address-token") continue
                if (c.getInt(1) == wanted) return address
                if (fallback.isEmpty()) fallback = address
            }
            return fallback
        }
        return ""
    }

    /** Deletes messages by id. Only works while this app is the default SMS app. */
    fun delete(ids: Collection<Long>): Int {
        var deleted = 0
        for (id in ids) {
            val uri = if (id < 0) {
                ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, -id)
            } else {
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id)
            }
            deleted += resolver.delete(uri, null, null)
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

    /** Contact display name for a phone number, or null if it isn't a saved contact. */
    private fun contactName(address: String): String? = contactCache.getOrPut(address) {
        if (address.isBlank() || address.any { it.isLetter() }) return@getOrPut null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) return@getOrPut null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        try {
            resolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }
}
