package com.kadhiravan.foodtracker.util

import com.kadhiravan.foodtracker.data.local.MealType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormat = SimpleDateFormat("EEE, MMM d", Locale.US)

    fun today(): String = isoFormat.format(Date())

    fun isoToDisplay(iso: String): String = try {
        displayFormat.format(isoFormat.parse(iso)!!)
    } catch (e: Exception) {
        iso
    }

    fun offsetFromToday(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, days)
        return isoFormat.format(cal.time)
    }

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
