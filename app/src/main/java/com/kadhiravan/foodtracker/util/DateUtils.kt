package com.kadhiravan.foodtracker.util

import com.kadhiravan.foodtracker.data.local.MealType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormat = SimpleDateFormat("EEE, MMM d", Locale.US)
    private val weekdayFormat = SimpleDateFormat("EEE", Locale.US)
    private val shortDisplayFormat = SimpleDateFormat("MMM d", Locale.US)
    private val dayOfMonthFormat = SimpleDateFormat("d", Locale.US)
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.US)

    // Material3's DatePicker works in UTC epoch millis regardless of device timezone , 
    // this pair converts to/from that without touching [isoFormat] (used for "today", which
    // must stay in the device's local timezone).
    private val isoFormatUtc = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun isoToEpochMillisUtc(iso: String): Long =
        try { isoFormatUtc.parse(iso)!!.time } catch (e: Exception) { System.currentTimeMillis() }

    fun epochMillisUtcToIso(millis: Long): String = isoFormatUtc.format(Date(millis))

    fun today(): String = isoFormat.format(Date())

    fun isoToDisplay(iso: String): String = try {
        displayFormat.format(isoFormat.parse(iso)!!)
    } catch (e: Exception) {
        iso
    }

    /** Compact "MMM d" label (e.g. "Sep 7"), for date-range headers. */
    fun isoToShortDisplay(iso: String): String = try {
        shortDisplayFormat.format(isoFormat.parse(iso)!!)
    } catch (e: Exception) {
        iso
    }

    /** Short weekday label ("Mon") for a yyyy-MM-dd date, for chart axis labels. */
    fun isoToWeekdayLabel(iso: String): String = try {
        weekdayFormat.format(isoFormat.parse(iso)!!).take(1)
    } catch (e: Exception) {
        "?"
    }

    /** Just the day-of-month number ("14") for a yyyy-MM-dd date, for the week day picker. */
    fun dayOfMonthLabel(iso: String): String = try {
        dayOfMonthFormat.format(isoFormat.parse(iso)!!)
    } catch (e: Exception) {
        "?"
    }

    fun offsetFromToday(days: Int): String = offsetDate(today(), days)

    /** Shifts any yyyy-MM-dd date by [days] (negative for earlier), the general form
     * [offsetFromToday] is built on, useful for day-navigation from an arbitrary date. */
    fun offsetDate(iso: String, days: Int): String {
        val cal = Calendar.getInstance()
        cal.time = try { isoFormat.parse(iso)!! } catch (e: Exception) { Date() }
        cal.add(Calendar.DAY_OF_YEAR, days)
        return isoFormat.format(cal.time)
    }

    /** Consecutive-day logging streak ending today. If today has no entry yet, the streak
     * counts from yesterday instead, the streak isn't broken until the day fully ends,
     * so it stays accurate for someone who hasn't logged yet today. */
    fun computeStreak(loggedDates: List<String>): Int {
        val dates = loggedDates.toHashSet()
        var cursor = if (today() in dates) today() else offsetFromToday(-1)
        var streak = 0
        while (cursor in dates) {
            streak++
            cursor = offsetDate(cursor, -1)
        }
        return streak
    }

    /** Whole days between two yyyy-MM-dd dates (positive when [toIso] is later). */
    fun daysBetween(fromIso: String, toIso: String): Int {
        val from = try { isoFormat.parse(fromIso)!! } catch (e: Exception) { Date() }
        val to = try { isoFormat.parse(toIso)!! } catch (e: Exception) { Date() }
        return ((to.time - from.time) / (24 * 60 * 60 * 1000)).toInt()
    }

    /** "September 2026" label for a calendar month header. */
    fun monthYearLabel(iso: String): String = try {
        monthYearFormat.format(isoFormat.parse(iso)!!)
    } catch (e: Exception) {
        iso
    }

    /** Shifts any yyyy-MM-dd date by whole calendar months (negative for earlier). */
    fun offsetMonths(iso: String, months: Int): String {
        val cal = Calendar.getInstance()
        cal.time = try { isoFormat.parse(iso)!! } catch (e: Exception) { Date() }
        cal.add(Calendar.MONTH, months)
        return isoFormat.format(cal.time)
    }

    /** The first day (yyyy-MM-01) of the month containing [iso]. */
    fun firstDayOfMonth(iso: String): String {
        val cal = Calendar.getInstance()
        cal.time = try { isoFormat.parse(iso)!! } catch (e: Exception) { Date() }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return isoFormat.format(cal.time)
    }

    /** Number of days in the month containing [iso] (28-31). */
    fun daysInMonth(iso: String): Int {
        val cal = Calendar.getInstance()
        cal.time = try { isoFormat.parse(iso)!! } catch (e: Exception) { Date() }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /** 0 (Sunday) through 6 (Saturday), used to pad a calendar grid's leading blanks. */
    fun dayOfWeekIndex(iso: String): Int {
        val cal = Calendar.getInstance()
        cal.time = try { isoFormat.parse(iso)!! } catch (e: Exception) { Date() }
        return cal.get(Calendar.DAY_OF_WEEK) - 1
    }

    /** The Sunday on or before [iso], the start of that week, matching the Sun-first grid
     * [dayOfWeekIndex] already assumes for the History calendar. */
    fun startOfWeek(iso: String): String = offsetDate(iso, -dayOfWeekIndex(iso))

    /** A sensible default meal type based on the current hour of day. */
    fun defaultMealTypeForNow(): MealType {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 4..10 -> MealType.BREAKFAST
            in 11..15 -> MealType.LUNCH
            in 16..19 -> MealType.SNACK
            else -> MealType.DINNER
        }
    }
}
