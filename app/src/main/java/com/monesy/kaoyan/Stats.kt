package com.monesy.kaoyan

import kotlinx.coroutines.flow.first
import java.time.LocalDate

fun fmtMinutes(m: Int): String = when {
    m < 60 -> "${m}分钟"
    m % 60 == 0 -> "${m / 60}小时"
    else -> String.format("%.1f小时", m / 60.0)
}

object Stats {

    data class WeekSummary(
        val totalMinutes: Int,
        val checkedCount: Int,
        val taskCount: Int,
        val streak: Int,
        val targetText: String,
        val targetMinHours: Int,
        val targetMaxHours: Int,
    ) {
        val hoursText: String
            get() = if (totalMinutes % 60 == 0) "${totalMinutes / 60} 小时"
            else "%.1f 小时".format(totalMinutes / 60.0)
    }

    /** 统计 reportDate 所在周（周一起，至 reportDate）的打卡情况与估算投入，按用户调整后的时长计算 */
    suspend fun weekSummary(store: Store, reportDate: LocalDate): WeekSummary {
        val overrides = store.durationOverrides.first()
        val monday = reportDate.minusDays((reportDate.dayOfWeek.value - 1).toLong())
        var minutes = 0
        var checked = 0
        var total = 0
        for (i in 0..6L) {
            val d = monday.plusDays(i)
            if (d.isAfter(reportDate)) break
            val stage = Plan.stageFor(d)
            val tasks = Plan.tasksFor(stage, d.dayOfWeek)
            val done = store.checkinFor(d).first()
            total += tasks.size
            tasks.forEach { t ->
                if (t.id in done) {
                    checked++
                    minutes += store.effectiveMinutes(t, overrides)
                }
            }
        }
        val stage = Plan.stageFor(reportDate)
        val nums = Regex("\\d+").findAll(stage.hoursPerWeek).map { it.value.toInt() }.toList()
        val minH = nums.getOrElse(0) { 0 }
        val maxH = nums.getOrElse(1) { minH }
        val targetText = if (minH <= 0) stage.hoursPerWeek
        else if (minH == maxH) "$minH 小时/周"
        else "$minH–$maxH 小时/周"
        return WeekSummary(
            minutes, checked, total, store.bottomLineStreak(reportDate), targetText, minH, maxH,
        )
    }
}
