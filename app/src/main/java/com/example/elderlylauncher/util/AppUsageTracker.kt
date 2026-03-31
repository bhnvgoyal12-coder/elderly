package com.example.elderlylauncher.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Tracks app usage for smart sorting in the app drawer.
 * Stores launch count and last launch time for each app.
 */
class AppUsageTracker(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    data class UsageData(
        val launchCount: Int,
        val lastLaunchTime: Long
    )

    /**
     * Record that an app was launched.
     */
    fun recordLaunch(packageName: String) {
        val currentData = getUsageData(packageName)
        val newData = UsageData(
            launchCount = currentData.launchCount + 1,
            lastLaunchTime = System.currentTimeMillis()
        )
        saveUsageData(packageName, newData)
    }

    /**
     * Get usage data for a specific app.
     */
    fun getUsageData(packageName: String): UsageData {
        val json = prefs.getString(packageName, null) ?: return UsageData(0, 0)
        return try {
            val obj = JSONObject(json)
            UsageData(
                launchCount = obj.optInt(KEY_LAUNCH_COUNT, 0),
                lastLaunchTime = obj.optLong(KEY_LAST_LAUNCH, 0)
            )
        } catch (e: Exception) {
            UsageData(0, 0)
        }
    }

    /**
     * Calculate a score for sorting. Higher score = more prominent.
     * Combines frequency (launch count) and recency (time since last launch).
     */
    fun getUsageScore(packageName: String): Double {
        val data = getUsageData(packageName)
        if (data.launchCount == 0) return 0.0

        val now = System.currentTimeMillis()
        val hoursSinceLastLaunch = (now - data.lastLaunchTime) / (1000.0 * 60 * 60)

        // Recency factor: decays over time (half-life of ~24 hours)
        val recencyFactor = Math.exp(-hoursSinceLastLaunch / 24.0)

        // Frequency factor: logarithmic to prevent super-used apps from dominating
        val frequencyFactor = Math.log(data.launchCount + 1.0)

        // Combined score: weight recency more for recent apps, frequency for habitual apps
        return (frequencyFactor * 2.0) + (recencyFactor * 3.0)
    }

    private fun saveUsageData(packageName: String, data: UsageData) {
        val json = JSONObject().apply {
            put(KEY_LAUNCH_COUNT, data.launchCount)
            put(KEY_LAST_LAUNCH, data.lastLaunchTime)
        }
        prefs.edit().putString(packageName, json.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "app_usage_tracker"
        private const val KEY_LAUNCH_COUNT = "count"
        private const val KEY_LAST_LAUNCH = "last"
    }
}
