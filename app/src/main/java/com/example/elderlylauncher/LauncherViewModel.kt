package com.example.elderlylauncher

import android.content.ContentResolver
import android.provider.ContactsContract
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.elderlylauncher.model.FavoriteContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel : ViewModel() {

    private val _favoriteContacts = MutableLiveData<List<FavoriteContact>>()
    val favoriteContacts: LiveData<List<FavoriteContact>> = _favoriteContacts

    fun loadFavoriteContacts(contentResolver: ContentResolver) {
        viewModelScope.launch {
            val contacts = withContext(Dispatchers.IO) {
                queryStarredContacts(contentResolver)
            }
            _favoriteContacts.value = contacts.take(6) // Limit to 6 for UI
        }
    }

    private fun queryStarredContacts(contentResolver: ContentResolver): List<FavoriteContact> {
        val contacts = mutableListOf<FavoriteContact>()

        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.STARRED
        )

        val selection = "${ContactsContract.Contacts.STARRED} = ?"
        val selectionArgs = arrayOf("1")
        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"

        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)

            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex) ?: "Unknown"
                val photoUri = cursor.getString(photoIndex)

                // Get phone number for this contact
                val phoneNumber = getPhoneNumber(contentResolver, contactId)

                if (phoneNumber != null) {
                    contacts.add(
                        FavoriteContact(
                            id = contactId,
                            name = name,
                            phoneNumber = phoneNumber,
                            photoUri = photoUri
                        )
                    )
                }
            }
        }

        return contacts
    }

    private fun getPhoneNumber(contentResolver: ContentResolver, contactId: Long): String? {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId.toString())

        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val numberIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                return cursor.getString(numberIndex)
            }
        }
        return null
    }
}
