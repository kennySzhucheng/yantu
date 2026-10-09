package com.monesy.kaoyan

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 课表（来源：assets/config/timetable.json，可用 tools/gen_timetable.py 从教务 xls 生成）。
 */
object Timetable {

    data class ClassEntry(val slot: Int, val name: String, val room: String, val weeks: Set<Int>)

    var SEMESTER_START: LocalDate = LocalDate.of(2026, 8, 31)
        private set

    var slotLabels: List<String> =
        listOf("第1-2节", "第3-4节", "第5-6节", "第7-8节", "第9-10节", "第11-12节")
        private set

    private var entries: Map<DayOfWeek, List<ClassEntry>> = emptyMap()

    fun init(json: JSONObject) {
        runCatching { LocalDate.parse(json.optString("semesterStart")) }
            .getOrNull()?.let { SEMESTER_START = it }

        json.optJSONArray("slotLabels")?.let { arr ->
            val labels = (0 until arr.length()).map { arr.optString(it) }
            if (labels.isNotEmpty()) slotLabels = labels
        }

        val map = mutableMapOf<DayOfWeek, List<ClassEntry>>()
        val entriesObj = json.optJSONObject("entries")
        if (entriesObj != null) {
            for (day in DayOfWeek.values()) {
                val arr = entriesObj.optJSONArray(day.name) ?: continue
                map[day] = (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    ClassEntry(
                        slot = o.optInt("slot"),
                        name = o.optString("name"),
                        room = o.optString("room"),
                        weeks = parseWeeks(o.optString("weeks")),
                    )
                }
            }
        }
        entries = map
    }

    /** "1-4,6-15" → {1,2,3,4,6,…,15} */
    private fun parseWeeks(expr: String): Set<Int> = expr.split(',').flatMap { part ->
        if (part.contains('-')) {
            val a = part.substringBefore('-').trim().toIntOrNull()
            val b = part.substringAfter('-').trim().toIntOrNull()
            if (a != null && b != null && a <= b) (a..b).toList() else emptyList()
        } else listOfNotNull(part.trim().toIntOrNull())
    }.toSet()

    /** 教学周序号（周一至周日，第 1 周 = SEMESTER_START 所在周） */
    fun weekOf(date: LocalDate): Int {
        val firstMonday = SEMESTER_START.minusDays((SEMESTER_START.dayOfWeek.value - 1).toLong())
        return (Math.floorDiv(ChronoUnit.DAYS.between(firstMonday, date), 7L) + 1).toInt()
    }

    fun classesFor(date: LocalDate): List<ClassEntry> =
        entries[date.dayOfWeek].orEmpty().filter { weekOf(date) in it.weeks }

    /** 供课表管理页读取全部条目 */
    fun allEntries(): Map<DayOfWeek, List<ClassEntry>> = entries

    /** Set<Int> → 紧凑表达式 {1,2,3,4,6} → "1-4,6" */
    fun formatWeeks(weeks: Set<Int>): String {
        if (weeks.isEmpty()) return ""
        val sorted = weeks.sorted()
        val parts = mutableListOf<String>()
        var start = sorted[0]
        var prev = sorted[0]
        for (w in sorted.drop(1)) {
            if (w == prev + 1) { prev = w; continue }
            parts += if (start == prev) "$start" else "$start-$prev"
            start = w; prev = w
        }
        parts += if (start == prev) "$start" else "$start-$prev"
        return parts.joinToString(",")
    }
}
