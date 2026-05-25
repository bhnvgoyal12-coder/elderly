package com.simple.elderlylauncher.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Finds a single random photo from the device gallery that was taken on today's
 * month-and-day in a previous year ("On This Day" memory). Pure MediaStore query,
 * no third-party deps, no network.
 *
 * Callers must have the right media permission already (READ_MEDIA_IMAGES on
 * Android 13+, READ_EXTERNAL_STORAGE below). [findToday] silently returns null
 * if the query fails or no photo matches.
 */
object OnThisDayLoader {

    private const val TAG = "OnThisDay"

    data class Memory(val uri: Uri, val takenAtMs: Long, val yearsAgo: Int)

    suspend fun findToday(context: Context): Memory? = withContext(Dispatchers.IO) {
        try {
            val today = Calendar.getInstance()
            val todayMonth = today.get(Calendar.MONTH)
            val todayDay = today.get(Calendar.DAY_OF_MONTH)
            val todayYear = today.get(Calendar.YEAR)

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED
            )

            // Filter to photos taken at least ~10 months ago so we don't show this morning's
            // selfie under "X years ago". A nominal cutoff keeps the experience sentimental.
            val cutoffMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(300)
            val selection = "(${MediaStore.Images.Media.DATE_TAKEN} > 0 AND ${MediaStore.Images.Media.DATE_TAKEN} < ?)"
            val selectionArgs = arrayOf(cutoffMs.toString())

            val matches = mutableListOf<Memory>()
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val cal = Calendar.getInstance()

                while (cursor.moveToNext()) {
                    val takenAtMs = cursor.getLong(dateIdx)
                    if (takenAtMs <= 0L) continue
                    cal.timeInMillis = takenAtMs
                    if (cal.get(Calendar.MONTH) != todayMonth) continue
                    if (cal.get(Calendar.DAY_OF_MONTH) != todayDay) continue
                    val yearsAgo = todayYear - cal.get(Calendar.YEAR)
                    if (yearsAgo < 1) continue

                    val id = cursor.getLong(idIdx)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    matches += Memory(uri = uri, takenAtMs = takenAtMs, yearsAgo = yearsAgo)
                }
            }

            if (matches.isEmpty()) null else matches[Random.nextInt(matches.size)]
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query MediaStore for On This Day", e)
            null
        }
    }
}
