package com.monesy.kaoyan

import android.content.Context
import android.provider.CalendarContract.Instances
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DayEvent(
    val title: String,
    val timeText: String,
    val location: String?,
)

/** 读取手机日历某一天的事件（需要 READ_CALENDAR 权限）。 */
fun readDayEvents(context: Context, date: LocalDate): List<DayEvent> {
    val zone = ZoneId.systemDefault()
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    val uri = Instances.CONTENT_URI.buildUpon()
        .appendQueryParameter(Instances.BEGIN, dayStart.toString())
        .appendQueryParameter(Instances.END, dayEnd.toString())
        .build()
    val projection = arrayOf(
        Instances.TITLE,
        Instances.BEGIN,
        Instances.ALL_DAY,
        Instances.EVENT_LOCATION,
    )
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    val out = mutableListOf<DayEvent>()
    context.contentResolver.query(uri, projection, null, null, Instances.BEGIN + " ASC")?.use { cursor ->
        while (cursor.moveToNext()) {
            val title = cursor.getString(0) ?: continue
            val begin = cursor.getLong(1)
            val allDay = cursor.getInt(2) == 1
            val loc = cursor.getString(3)
            val timeText = if (allDay) "全天"
            else LocalDateTime.ofInstant(Instant.ofEpochMilli(begin), zone).format(fmt)
            out.add(DayEvent(title, timeText, loc?.takeIf { it.isNotBlank() }))
        }
    }
    return out
}
