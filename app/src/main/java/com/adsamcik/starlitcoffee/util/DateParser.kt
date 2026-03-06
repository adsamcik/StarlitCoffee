package com.adsamcik.starlitcoffee.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Parses date strings from OCR text in various formats commonly found on coffee bags.
 * Returns epoch milliseconds or null if unparseable.
 */
object DateParser {

    private val FORMATS = listOf(
        // ISO: 2026-02-20
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
        // European common: 20.02.2026, 20/02/2026, 20-02-2026
        SimpleDateFormat("dd.MM.yyyy", Locale.US),
        SimpleDateFormat("dd/MM/yyyy", Locale.US),
        SimpleDateFormat("dd-MM-yyyy", Locale.US),
        // Short year: 09.12.25, 09/12/25
        SimpleDateFormat("dd.MM.yy", Locale.US),
        SimpleDateFormat("dd/MM/yy", Locale.US),
        SimpleDateFormat("dd-MM-yy", Locale.US),
        // Month name: January 15, 2026
        SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH),
        SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH),
        // Day Month Year: 15 January 2026
        SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH),
        SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH),
        // Month Year only: January 2026
        SimpleDateFormat("MMMM yyyy", Locale.ENGLISH),
        // Compact: 20022026, rare but seen
        SimpleDateFormat("ddMMyyyy", Locale.US),
    ).onEach { it.isLenient = false }

    /**
     * Attempts to parse a date string into epoch milliseconds.
     * Tries multiple common formats. Returns null if none match.
     */
    fun parse(dateString: String?): Long? {
        if (dateString.isNullOrBlank()) return null
        val trimmed = dateString.trim()
        for (format in FORMATS) {
            try {
                val date = format.parse(trimmed) ?: continue
                // Sanity check: year should be 2000-2099
                val cal = Calendar.getInstance().apply { time = date }
                val year = cal.get(Calendar.YEAR)
                if (year in 2000..2099) return date.time
            } catch (_: Exception) { }
        }
        return null
    }

    /**
     * Formats epoch milliseconds to a human-readable date string.
     */
    fun format(epochMillis: Long): String {
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMillis))
    }

    /**
     * Returns the number of days between a timestamp and now.
     * Positive = in the past, negative = in the future.
     */
    fun daysAgo(epochMillis: Long): Int {
        val diff = System.currentTimeMillis() - epochMillis
        return (diff / (1000 * 60 * 60 * 24)).toInt()
    }

    /**
     * Freshness assessment for specialty coffee based on days since roast.
     */
    enum class Freshness(val label: String, val emoji: String) {
        RESTING("Resting", "⏳"),
        PEAK("Peak freshness", "✅"),
        GOOD("Still good", "👍"),
        STALE("Getting stale", "⚠️"),
        OLD("Past prime", "🔴"),
    }

    fun assessFreshness(roastDateMillis: Long): Freshness {
        val days = daysAgo(roastDateMillis)
        return when {
            days < 0 -> Freshness.PEAK // Future date = just roasted
            days < 7 -> Freshness.RESTING
            days < 30 -> Freshness.PEAK
            days < 60 -> Freshness.GOOD
            days < 90 -> Freshness.STALE
            else -> Freshness.OLD
        }
    }

    /**
     * Checks if a bag is past its best-before date.
     */
    fun isPastExpiry(expiryDateMillis: Long): Boolean {
        return System.currentTimeMillis() > expiryDateMillis
    }
}
