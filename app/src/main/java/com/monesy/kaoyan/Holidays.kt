package com.monesy.kaoyan

import org.json.JSONArray
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 节假日（来源：assets/config/holidays.json）。
 * official=true 表示官方通知原文日期；false 表示按惯例推算的预计安排。
 */

data class HolidayRange(
    val start: LocalDate,
    val end: LocalDate,
    val name: String,
    val isOfficial: Boolean,
) {
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
    fun dayOf(date: LocalDate): Long = ChronoUnit.DAYS.between(start, date) + 1
    fun lengthDays(): Long = ChronoUnit.DAYS.between(start, end) + 1
}

object Holidays {

    var list: List<HolidayRange> = emptyList()
        private set

    fun init(arr: JSONArray?) {
        if (arr == null) return
        list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val start = runCatching { LocalDate.parse(o.optString("start")) }.getOrNull() ?: return@mapNotNull null
            val end = runCatching { LocalDate.parse(o.optString("end")) }.getOrNull() ?: return@mapNotNull null
            HolidayRange(start, end, o.optString("name"), o.optBoolean("official", false))
        }
    }

    fun holidayFor(date: LocalDate): HolidayRange? = list.firstOrNull { it.contains(date) }

    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
}
