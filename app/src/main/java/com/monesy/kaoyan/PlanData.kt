package com.monesy.kaoyan

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 计划数据模型与查询 API。
 * 数据来源：assets/config/plan.json（由 ConfigLoader 在应用启动时注入）。
 * 普适化：换计划只改 plan.json，本文件与 UI 无需改动。
 */

data class PlanTask(
    val id: String,
    val title: String,
    val isBottomLine: Boolean = false,
    /** 默认时长（分钟） */
    val minutes: Int = 60,
    /** 可调下限；与 maxMinutes 同时为 0 表示固定不可调 */
    val minMinutes: Int = 0,
    /** 可调上限 */
    val maxMinutes: Int = 0,
)

data class Stage(
    val id: Int,
    val name: String,
    val dateText: String,
    val start: LocalDate,
    val end: LocalDate,
    val hoursPerWeek: String,
    val goal: String,
    val coreTasks: List<String>,
    val acceptance: List<String>,
    val isDecisive: Boolean = false,
    val weekdayTasks: List<PlanTask>,
    val saturdayTasks: List<PlanTask>,
    val sundayTasks: List<PlanTask>,
)

enum class NodeKind { STUDY, INFO, EXAM }

data class KeyNode(
    val date: LocalDate,
    val title: String,
    val detail: String,
    val kind: NodeKind,
    /** 软节点：具体时间由外部决定（如复试，各校不同），只做范围提醒，不显示硬倒计时 */
    val soft: Boolean = false,
) {
    /** 稳定唯一 ID，用于节点完成打卡的存储键 */
    val id: String get() = "node_${date.toEpochDay()}_$title"
}

object Plan {

    var PLAN_START: LocalDate = LocalDate.of(2026, 1, 1)
        private set
    var DEFAULT_EXAM_DATE: LocalDate = LocalDate.of(2027, 12, 18)
        private set

    var stages: List<Stage> = emptyList()
        private set
    var keyNodes: List<KeyNode> = emptyList()
        private set

    private val fallbackStage = Stage(
        id = 1, name = "未配置", dateText = "请在 assets/config/plan.json 中配置备考计划",
        start = LocalDate.MIN, end = LocalDate.MAX, hoursPerWeek = "",
        goal = "配置文件缺失或格式有误", coreTasks = emptyList(), acceptance = emptyList(),
        weekdayTasks = emptyList(), saturdayTasks = emptyList(), sundayTasks = emptyList(),
    )

    fun init(json: JSONObject) {
        PLAN_START = Config.planStart
        DEFAULT_EXAM_DATE = Config.examDate

        val stagesArr = json.optJSONArray("stages") ?: JSONArray()
        stages = (0 until stagesArr.length()).mapNotNull { i ->
            val o = stagesArr.optJSONObject(i) ?: return@mapNotNull null
            val start = parseDate(o.optString("start")) ?: return@mapNotNull null
            val end = parseDate(o.optString("end")) ?: return@mapNotNull null
            Stage(
                id = o.optInt("id", i + 1),
                name = o.optString("name"),
                dateText = o.optString("dateText"),
                start = start,
                end = end,
                hoursPerWeek = o.optString("hoursPerWeek"),
                goal = o.optString("goal"),
                coreTasks = readList(o.optJSONArray("coreTasks")),
                acceptance = readList(o.optJSONArray("acceptance")),
                isDecisive = o.optBoolean("decisive", false),
                weekdayTasks = parseTasks(o.optJSONArray("weekdayTasks")),
                saturdayTasks = parseTasks(o.optJSONArray("saturdayTasks")),
                sundayTasks = parseTasks(o.optJSONArray("sundayTasks")),
            )
        }

        val nodesArr = json.optJSONArray("keyNodes") ?: JSONArray()
        keyNodes = (0 until nodesArr.length()).mapNotNull { i ->
            val o = nodesArr.optJSONObject(i) ?: return@mapNotNull null
            val date = parseDate(o.optString("date")) ?: return@mapNotNull null
            val kind = runCatching { NodeKind.valueOf(o.optString("kind", "INFO")) }.getOrDefault(NodeKind.INFO)
            KeyNode(date, o.optString("title"), o.optString("detail"), kind, o.optBoolean("soft", false))
        }
    }

    private fun parseTasks(arr: JSONArray?): List<PlanTask> =
        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            PlanTask(
                id = o.optString("id"),
                title = o.optString("title"),
                isBottomLine = o.optBoolean("bottomLine", false),
                minutes = o.optInt("minutes", 60),
                minMinutes = o.optInt("minMinutes", 0),
                maxMinutes = o.optInt("maxMinutes", 0),
            )
        }

    private fun readList(arr: JSONArray?): List<String> =
        if (arr == null) emptyList() else (0 until arr.length()).map { arr.optString(it) }

    private fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()

    // ---------- 查询 API（UI 与 Worker 共用，语义与旧版一致） ----------

    fun stageFor(today: LocalDate): Stage {
        if (stages.isEmpty()) return fallbackStage
        return stages.firstOrNull { !today.isBefore(it.start) && !today.isAfter(it.end) }
            ?: if (today.isBefore(stages.first().start)) stages.first() else stages.last()
    }

    fun tasksFor(stage: Stage, day: DayOfWeek): List<PlanTask> = when (day) {
        DayOfWeek.SATURDAY -> stage.saturdayTasks
        DayOfWeek.SUNDAY -> stage.sundayTasks
        else -> stage.weekdayTasks
    }

    fun daysUntil(examDate: LocalDate, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, examDate)

    fun journeyProgress(today: LocalDate, examDate: LocalDate): Float {
        val total = ChronoUnit.DAYS.between(PLAN_START, examDate).toFloat()
        val done = ChronoUnit.DAYS.between(PLAN_START, today).toFloat()
        return if (total <= 0f) 1f else (done / total).coerceIn(0f, 1f)
    }

    /** 未来的节点按剩余天数排序 */
    fun upcomingNodes(today: LocalDate): List<Pair<KeyNode, Long>> =
        keyNodes.filter { !it.date.isBefore(today) }
            .sortedBy { it.date }
            .map { it to ChronoUnit.DAYS.between(today, it.date) }

    fun nearestNodeLine(today: LocalDate): String? =
        upcomingNodes(today).firstOrNull { !it.first.soft }?.let { (node, days) ->
            when {
                days == 0L -> "今天：「${node.title}」"
                else -> "距「${node.title}」还有 $days 天"
            }
        }
}

/** 达标判定：底线任务全部完成 + 其余任务完成过半（向上取整）。任务≤2 时与"全部完成"等价。 */
fun isDayMet(tasks: List<PlanTask>, checked: Set<String>): Boolean {
    if (tasks.isEmpty()) return false
    val bottomOk = tasks.filter { it.isBottomLine }.all { it.id in checked }
    val others = tasks.filter { !it.isBottomLine }
    val need = (others.size + 1) / 2
    val done = others.count { it.id in checked }
    return bottomOk && done >= need
}

/** 全部完成（荣誉档） */
fun isDayFull(tasks: List<PlanTask>, checked: Set<String>): Boolean =
    tasks.isNotEmpty() && tasks.all { it.id in checked }
