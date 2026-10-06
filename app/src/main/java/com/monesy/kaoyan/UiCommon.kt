package com.monesy.kaoyan

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 界面通用小工具（日期选择器互转、显示名） */

fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun pickerMillisToDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

val weekDayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun dayName(day: Int): String = weekDayNames.getOrElse(day - 1) { "?$day" }

fun dateText(d: LocalDate): String = "${d.monthValue}月${d.dayOfMonth}日"
