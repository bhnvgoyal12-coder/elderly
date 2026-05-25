package com.simple.elderlylauncher.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the user's emergency (SOS) contact in SharedPreferences.
 * One contact, name + phone number, kept dead-simple on purpose — the SOS flow
 * has to be reliable and not surprise the user in a stressful moment.
 */
class SosManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "sos_contact"
        private const val KEY_NAME = "name"
        private const val KEY_NUMBER = "number"
    }

    data class SosContact(val name: String, val number: String)

    fun get(): SosContact? {
        val name = prefs.getString(KEY_NAME, null) ?: return null
        val number = prefs.getString(KEY_NUMBER, null) ?: return null
        if (number.isBlank()) return null
        return SosContact(name, number)
    }

    fun set(name: String, number: String) {
        prefs.edit()
            .putString(KEY_NAME, name.ifBlank { number })
            .putString(KEY_NUMBER, number)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun isConfigured(): Boolean = get() != null
}
