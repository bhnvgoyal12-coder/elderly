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
        private const val MAX_FAVORITES = 8 // Limit to keep UI clean
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
}
