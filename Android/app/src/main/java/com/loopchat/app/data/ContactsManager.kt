package com.loopchat.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PhoneContact(
    val id: String,
    val name: String,
    val phoneNumber: String
)

object ContactsManager {

    /**
     * Reads all contacts from the device that have a phone number.
     * Sanitizes phone numbers by removing non-digit characters (optional, but good for raw comparison).
     */
    @SuppressLint("Range")
    suspend fun getDeviceContacts(context: Context): List<PhoneContact> = withContext(Dispatchers.IO) {
        val contactsList = mutableListOf<PhoneContact>()
        
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                val name = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: ""
                val number = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                
                // Keep the raw number, or strictly sanitize it. Here we keep it mostly raw, 
                // but strip spaces/dashes so it matches easily.
                val sanitizedNumber = number.replace(Regex("[\\s\\-\\(\\)]"), "")
                
                if (sanitizedNumber.isNotEmpty()) {
                    contactsList.add(PhoneContact(id, name, sanitizedNumber))
                }
            }
        }
        
        // Return duplicates filtered out by phone number
        return@withContext contactsList.distinctBy { it.phoneNumber }
    }
}
