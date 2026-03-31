package com.simple.elderlylauncher.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import com.simple.elderlylauncher.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Singleton repository for caching installed apps.
 * Pre-loads app list to speed up app drawer opening.
 */
object AppRepository {

    private var cachedApps: List<AppInfo>? = null
    private var cachedResolveInfos: List<ResolveInfo>? = null
    private val mutex = Mutex()
    private var isLoading = false

    /**
     * Pre-load app metadata (without icons) for fast initial display.
     * Call this from MainActivity to warm up the cache.
     */
    suspend fun preloadApps(context: Context) {
        if (cachedApps != null || isLoading) return

        mutex.withLock {
            if (cachedApps != null) return
            isLoading = true
        }

        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos = pm.queryIntentActivities(mainIntent, PackageManager.GET_META_DATA)
            cachedResolveInfos = resolveInfos

            // Load app info with lightweight placeholder icons first
            val apps = resolveInfos
                .filter { it.activityInfo.packageName != context.packageName }
                .map { resolveInfo ->
                    val appName = resolveInfo.loadLabel(pm).toString()
                    val packageName = resolveInfo.activityInfo.packageName
                    AppInfo(
                        name = appName,
                        packageName = packageName,
                        icon = null // Icons loaded lazily
                    )
                }
                .sortedBy { it.name.lowercase() }

            mutex.withLock {
                cachedApps = apps
                isLoading = false
            }
        }
    }

    /**
     * Get cached apps (fast) or load them if not cached.
     */
    suspend fun getApps(context: Context): List<AppInfo> {
        cachedApps?.let { return it }
        preloadApps(context)
        return cachedApps ?: emptyList()
    }

    /**
     * Load icon for a specific package (called on-demand by adapter).
     */
    fun getAppIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clear cache when apps might have changed.
     */
    fun invalidateCache() {
        cachedApps = null
        cachedResolveInfos = null
    }
}
