package com.simple.elderlylauncher.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Repository for fetching news headlines from Google News RSS feed.
 * Automatically uses the device's locale for localized news.
 */
object NewsRepository {

    private const val TAG = "NewsRepository"
    private const val MAX_HEADLINES = 5
    private const val TIMEOUT_MS = 10000

    /**
     * Builds Google News RSS URL based on device locale.
     * Format: https://news.google.com/rss?hl={lang}-{country}&gl={country}&ceid={country}:{lang}
     */
    private fun getLocalizedNewsUrl(): String {
        val locale = Locale.getDefault()
        val country = locale.country.uppercase().ifEmpty { "US" }
        val language = locale.language.lowercase().ifEmpty { "en" }

        return "https://news.google.com/rss?hl=$language-$country&gl=$country&ceid=$country:$language"
    }

    data class NewsItem(
        val title: String,
        val link: String,
        val source: String,
        val pubDate: String
    )

    sealed class NewsResult {
        data class Success(val items: List<NewsItem>) : NewsResult()
        data class Error(val message: String) : NewsResult()
    }

    /**
     * Fetches top news headlines from Google News.
     * Returns a list of NewsItem on success, or an error message on failure.
     */
    suspend fun fetchHeadlines(): NewsResult = withContext(Dispatchers.IO) {
        try {
            val rssContent = fetchRssFeed()
            val items = parseRssFeed(rssContent)
            NewsResult.Success(items.take(MAX_HEADLINES))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch news", e)
            NewsResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun fetchRssFeed(): String {
        val rssUrl = getLocalizedNewsUrl()
        Log.d(TAG, "Fetching news from: $rssUrl")
        val url = URL(rssUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")

        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRssFeed(rssContent: String): List<NewsItem> {
        val items = mutableListOf<NewsItem>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(rssContent))

            var eventType = parser.eventType
            var insideItem = false
            var currentTitle = ""
            var currentLink = ""
            var currentSource = ""
            var currentPubDate = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "item" -> {
                                insideItem = true
                                currentTitle = ""
                                currentLink = ""
                                currentSource = ""
                                currentPubDate = ""
                            }
                            "title" -> {
                                if (insideItem) {
                                    currentTitle = readText(parser)
                                }
                            }
                            "link" -> {
                                if (insideItem) {
                                    currentLink = readText(parser)
                                }
                            }
                            "source" -> {
                                if (insideItem) {
                                    currentSource = readText(parser)
                                }
                            }
                            "pubDate" -> {
                                if (insideItem) {
                                    currentPubDate = formatPubDate(readText(parser))
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item" && insideItem) {
                            insideItem = false
                            // Clean up title - remove source suffix if present
                            val cleanTitle = cleanTitle(currentTitle, currentSource)
                            if (cleanTitle.isNotBlank()) {
                                items.add(
                                    NewsItem(
                                        title = cleanTitle,
                                        link = currentLink,
                                        source = currentSource.ifBlank { "News" },
                                        pubDate = currentPubDate
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing RSS", e)
        }

        return items
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result.trim()
    }

    private fun cleanTitle(title: String, source: String): String {
        // Google News often appends " - Source Name" to titles
        return if (source.isNotBlank() && title.endsWith(" - $source")) {
            title.removeSuffix(" - $source")
        } else {
            // Try to remove any " - Source" pattern at the end
            title.replace(Regex(" - [^-]+$"), "")
        }
    }

    private fun formatPubDate(pubDate: String): String {
        // Input format: "Fri, 24 Jan 2025 10:30:00 GMT"
        // Output: "Jan 24" or similar simple format
        return try {
            val parts = pubDate.split(" ")
            if (parts.size >= 4) {
                "${parts[2]} ${parts[1]}" // "24 Jan"
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}
