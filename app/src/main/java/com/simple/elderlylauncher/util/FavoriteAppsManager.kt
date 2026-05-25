package com.simple.elderlylauncher.util

import android.content.Context
import android.content.SharedPreferences

class FavoriteAppsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "favorite_apps"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_DEFAULTS_SEEDED = "defaults_seeded"
        private const val MAX_FAVORITES = 8 // Limit to keep UI clean

        // Packages pre-added as favorites on first launch.
        // Each is only added if the app is actually installed on the device.
        // Add more entries here to expand the default home set.
        private val DEFAULT_FAVORITES = listOf(
            "com.google.android.youtube"
        )
    }

    fun getFavoriteApps(): List<String> {
        val favoritesString = prefs.getString(KEY_FAVORITES, "") ?: ""
        return if (favoritesString.isEmpty()) {
            emptyList()
        } else {
            favoritesString.split(",").filter { it.isNotEmpty() }
        }
    }

    fun addFavoriteApp(packageName: String): Boolean {
        val favorites = getFavoriteApps().toMutableList()

        // Check if already exists
        if (favorites.contains(packageName)) {
            return false
        }

        // Check max limit
        if (favorites.size >= MAX_FAVORITES) {
            return false
        }

        favorites.add(packageName)
        saveFavorites(favorites)
        return true
    }

    fun removeFavoriteApp(packageName: String) {
        val favorites = getFavoriteApps().toMutableList()
        favorites.remove(packageName)
        saveFavorites(favorites)
    }

    fun isFavorite(packageName: String): Boolean {
        return getFavoriteApps().contains(packageName)
    }

    fun canAddMore(): Boolean {
        return getFavoriteApps().size < MAX_FAVORITES
    }

    private fun saveFavorites(favorites: List<String>) {
        prefs.edit().putString(KEY_FAVORITES, favorites.joinToString(",")).apply()
    }

    /**
     * Seed default favorite apps (e.g. YouTube) on first run only.
     *
     * Runs exactly once per install — tracked by [KEY_DEFAULTS_SEEDED] — so if the
     * user later removes a seeded app it will NOT come back. Each candidate is
     * verified to be actually installed via getLaunchIntentForPackage before being
     * added; missing apps are silently skipped.
     */
    fun seedDefaultFavoritesIfNeeded(context: Context) {
        if (prefs.getBoolean(KEY_DEFAULTS_SEEDED, false)) return

        val pm = context.packageManager
        val favorites = getFavoriteApps().toMutableList()

        for (packageName in DEFAULT_FAVORITES) {
            if (favorites.size >= MAX_FAVORITES) break
            if (favorites.contains(packageName)) continue
            if (pm.getLaunchIntentForPackage(packageName) != null) {
                favorites.add(packageName)
            }
        }

        saveFavorites(favorites)
        prefs.edit().putBoolean(KEY_DEFAULTS_SEEDED, true).apply()
    }
}
