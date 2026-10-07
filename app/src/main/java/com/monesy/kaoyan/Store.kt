package com.monesy.kaoyan

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val Context.dataStore by preferencesDataStore(name = "kaoyan")

/** 奖励日志条目：kind 'D' = 日常小奖励，'W' = 周达标大奖励 */
data class RewardEntry(val date: LocalDate, val kind: Char, val text: String)

data class Settings(
    val examDate: LocalDate = Plan.DEFAULT_EXAM_DATE,
    val morningEnabled: Boolean = true,
    val morningTime: LocalTime = Config.morningTime,
    val eveningEnabled: Boolean = true,
    val eveningTime: LocalTime = Config.eveningTime,
)

class Store(private val context: Context) {

    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private val KEY_EXAM_DATE = stringPreferencesKey("exam_date")
    private val KEY_MORNING_ON = booleanPreferencesKey("notif_morning_on")
    private val KEY_MORNING_TIME = stringPreferencesKey("notif_morning_time")
    private val KEY_EVENING_ON = booleanPreferencesKey("notif_evening_on")
    private val KEY_EVENING_TIME = stringPreferencesKey("notif_evening_time")
    private val KEY_DONE_NODES = stringSetPreferencesKey("done_nodes")
    private val KEY_REWARD_JOURNAL = stringPreferencesKey("reward_journal")
    private val KEY_USER_CONFIG = stringPreferencesKey("user_config")
    private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
    private val KEY_THEME = stringPreferencesKey("theme_mode")
    private val KEY_THEME_DARK_START = stringPreferencesKey("theme_dark_start")
    private val KEY_THEME_DARK_END = stringPreferencesKey("theme_dark_end")
    private val KEY_NODE_REMIND = booleanPreferencesKey("node_reminders_enabled")
    private val KEY_TUTORIAL_VERSION = intPreferencesKey("tutorial_version")

    // ---------- 外观（light / dark / scheduled + 定时时段） ----------

    /** 兼容旧值：历史 "system" 迁移为 "light" */
    val themeMode: Flow<String> = context.dataStore.data.map {
        when (val v = it[KEY_THEME] ?: "light") {
            "system" -> "light"
            else -> v
        }
    }

    /** 定时模式下：几点开深色 / 几点关深色（HH:mm，支持跨夜） */
    val themeDarkStart: Flow<String> = context.dataStore.data.map { it[KEY_THEME_DARK_START] ?: "22:00" }
    val themeDarkEnd: Flow<String> = context.dataStore.data.map { it[KEY_THEME_DARK_END] ?: "07:00" }

    suspend fun setThemeMode(mode: String) =
        context.dataStore.edit { it[KEY_THEME] = mode }

    suspend fun setThemeDarkStart(t: String) =
        context.dataStore.edit { it[KEY_THEME_DARK_START] = t }

    suspend fun setThemeDarkEnd(t: String) =
        context.dataStore.edit { it[KEY_THEME_DARK_END] = t }

    // ---------- 用户配置层（向导/编辑/导入 都写这里） ----------

    val userConfigRaw: Flow<String?> = context.dataStore.data.map { it[KEY_USER_CONFIG] }

    suspend fun setUserConfigRaw(json: String) =
        context.dataStore.edit { it[KEY_USER_CONFIG] = json }

    suspend fun clearUserConfig() =
        context.dataStore.edit { it.remove(KEY_USER_CONFIG) }

    // ---------- 数据备份（换机迁移：打卡历史 / 奖励日志 / 节点完成 / 时长覆盖 / 设置 / 用户配置） ----------

    suspend fun exportBackup(): org.json.JSONObject {
        val prefs = context.dataStore.data.first().asMap()
        val arr = org.json.JSONArray()
        for ((key, value) in prefs) {
            val t: String
            val v: Any
            when (value) {
                is Boolean -> { t = "b"; v = value }
                is Int -> { t = "i"; v = value }
                is Long -> { t = "l"; v = value }
                is String -> { t = "s"; v = value }
                is Set<*> -> { t = "ss"; v = org.json.JSONArray(value.map { it.toString() }) }
                else -> continue
            }
            arr.put(org.json.JSONObject().put("k", key.name).put("t", t).put("v", v))
        }
        return org.json.JSONObject()
            .put("_type", "studyreminder_backup")
            .put("version", 1)
            .put("exported", LocalDate.now().toString())
            .put("prefs", arr)
    }

    /** 导入数据备份（覆盖同名键）；返回导入条数，-1 表示不是有效备份文件 */
    suspend fun importBackup(root: org.json.JSONObject): Int {
        if (root.optString("_type") != "studyreminder_backup") return -1
        val arr = root.optJSONArray("prefs") ?: return 0
        var n = 0
        context.dataStore.edit { p ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val k = o.optString("k")
                if (k.isBlank()) continue
                when (o.optString("t")) {
                    "b" -> p[booleanPreferencesKey(k)] = o.optBoolean("v")
                    "i" -> p[intPreferencesKey(k)] = o.optInt("v")
                    "l" -> p[longPreferencesKey(k)] = o.optLong("v")
                    "s" -> p[stringPreferencesKey(k)] = o.optString("v")
                    "ss" -> {
                        val vs = o.optJSONArray("v") ?: continue
                        p[stringSetPreferencesKey(k)] = (0 until vs.length()).map { vs.optString(it) }.toSet()
                    }
                    else -> continue
                }
                n++
            }
        }
        return n
    }

    // ---------- 首次引导标记 ----------

    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDED] ?: false }

    suspend fun setOnboarded(v: Boolean) =
        context.dataStore.edit { it[KEY_ONBOARDED] = v }

    // ---------- 节点临近提醒开关 ----------

    val nodeRemindersEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_NODE_REMIND] ?: true }

    suspend fun setNodeRemindersEnabled(v: Boolean) =
        context.dataStore.edit { it[KEY_NODE_REMIND] = v }

    // ---------- 使用教程版本（教程内容更新后老用户启动时自动重看一次） ----------

    val tutorialVersion: Flow<Int> = context.dataStore.data.map { it[KEY_TUTORIAL_VERSION] ?: 0 }

    suspend fun setTutorialVersion(v: Int) =
        context.dataStore.edit { it[KEY_TUTORIAL_VERSION] = v }

    // ---------- 任务时长覆盖（仅可调任务会写入） ----------

    val durationOverrides: Flow<Map<String, Int>> = context.dataStore.data.map { p ->
        val out = mutableMapOf<String, Int>()
        for ((key, value) in p.asMap()) {
            if (!key.name.startsWith("dur_")) continue
            // intPreferencesKey 存回的是 Int；此前误判成 Long 导致覆盖永远读不出来
            val m = (value as? Int) ?: continue
            out[key.name.removePrefix("dur_")] = m
        }
        out
    }

    suspend fun setDuration(taskId: String, minutes: Int) {
        context.dataStore.edit { it[intPreferencesKey("dur_$taskId")] = minutes }
    }

    /** 任务实际时长：用户调过的优先，否则用默认值 */
    fun effectiveMinutes(task: PlanTask, overrides: Map<String, Int>): Int =
        overrides[task.id] ?: task.minutes

    // ---------- 奖励日志 ----------

    val rewardJournal: Flow<List<RewardEntry>> = context.dataStore.data.map { p ->
        val out = mutableListOf<RewardEntry>()
        for (line in (p[KEY_REWARD_JOURNAL] ?: "").split('\n')) {
            if (line.isBlank()) continue
            val parts = line.split('|', limit = 3)
            if (parts.size < 3) continue
            val d = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: continue
            out.add(RewardEntry(d, parts[1].firstOrNull() ?: 'D', parts[2]))
        }
        out
    }

    suspend fun appendReward(date: LocalDate, kind: Char, text: String) {
        context.dataStore.edit { p ->
            val cur = p[KEY_REWARD_JOURNAL] ?: ""
            val line = "${date}|${kind}|${text}"
            p[KEY_REWARD_JOURNAL] = if (cur.isBlank()) line else "$cur\n$line"
        }
    }

    /** 当天是否已记过某类奖励（防止重复） */
    suspend fun hasReward(date: LocalDate, kind: Char): Boolean =
        context.dataStore.data.first()[KEY_REWARD_JOURNAL]
            ?.split('\n')
            ?.any { it.startsWith("${date}|${kind}|") } == true

    /** 撤回某天某类奖励（打卡被取消时同步移除） */
    suspend fun removeReward(date: LocalDate, kind: Char) {
        context.dataStore.edit { p ->
            val prefix = "${date}|$kind|"
            val cur = p[KEY_REWARD_JOURNAL] ?: ""
            p[KEY_REWARD_JOURNAL] = cur.split('\n')
                .filter { it.isNotBlank() && !it.startsWith(prefix) }
                .joinToString("\n")
        }
    }

    /** 已打勾完成的重大节点（KeyNode.id 集合） */
    val doneNodes: Flow<Set<String>> = context.dataStore.data.map { p ->
        p[KEY_DONE_NODES] ?: emptySet()
    }

    /** 原子切换节点完成状态，返回切换后是否为已完成 */
    suspend fun toggleNode(id: String): Boolean {
        var added = false
        context.dataStore.edit { p ->
            val cur = p[KEY_DONE_NODES] ?: emptySet()
            added = id !in cur
            p[KEY_DONE_NODES] = if (added) cur + id else cur - id
        }
        return added
    }

    /** 一次性读取全部打卡记录：日期 → 已勾选任务ID集合 */
    suspend fun readAllCheckins(): Map<LocalDate, Set<String>> {
        val prefs = context.dataStore.data.first()
        val result = mutableMapOf<LocalDate, Set<String>>()
        for ((key, value) in prefs.asMap()) {
            if (!key.name.startsWith("checkin_")) continue
            val d = runCatching { LocalDate.parse(key.name.removePrefix("checkin_")) }.getOrNull() ?: continue
            val ids = (value as? String)?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            result[d] = ids
        }
        return result
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            examDate = p[KEY_EXAM_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: Plan.DEFAULT_EXAM_DATE,
            morningEnabled = p[KEY_MORNING_ON] ?: true,
            morningTime = p[KEY_MORNING_TIME]?.let { runCatching { LocalTime.parse(it, timeFmt) }.getOrNull() }
                ?: Config.morningTime,
            eveningEnabled = p[KEY_EVENING_ON] ?: true,
            eveningTime = p[KEY_EVENING_TIME]?.let { runCatching { LocalTime.parse(it, timeFmt) }.getOrNull() }
                ?: Config.eveningTime,
        )
    }

    suspend fun setExamDate(date: LocalDate) =
        context.dataStore.edit { it[KEY_EXAM_DATE] = date.format(dateFmt) }

    suspend fun setMorningEnabled(on: Boolean) =
        context.dataStore.edit { it[KEY_MORNING_ON] = on }

    suspend fun setMorningTime(time: LocalTime) =
        context.dataStore.edit { it[KEY_MORNING_TIME] = time.format(timeFmt) }

    suspend fun setEveningEnabled(on: Boolean) =
        context.dataStore.edit { it[KEY_EVENING_ON] = on }

    suspend fun setEveningTime(time: LocalTime) =
        context.dataStore.edit { it[KEY_EVENING_TIME] = time.format(timeFmt) }

    // ---------- 打卡 ----------

    private fun checkinKey(date: LocalDate) = stringPreferencesKey("checkin_$date")

    fun checkinFor(date: LocalDate): Flow<Set<String>> = context.dataStore.data.map { p ->
        p[checkinKey(date)]?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    /** 原子地切换某天某任务的勾选状态，返回切换后的新状态。 */
    suspend fun toggleCheckin(date: LocalDate, taskId: String): Boolean {
        var nowChecked = false
        context.dataStore.edit { p ->
            val cur = p[checkinKey(date)]?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            nowChecked = taskId !in cur
            val next = if (nowChecked) cur + taskId else cur - taskId
            p[checkinKey(date)] = next.joinToString(",")
        }
        return nowChecked
    }

    /** 连续背单词天数：若今天还没打卡，从昨天往前数（连续性不被"还没到晚上"打断）。 */
    suspend fun wordStreak(today: LocalDate): Int {
        val p = context.dataStore.data.first()
        var day = today
        if (!isChecked(p, day, "words")) day = today.minusDays(1)
        var n = 0
        while (isChecked(p, day, "words")) {
            n++
            day = day.minusDays(1)
        }
        return n
    }

    // ---------- 底线通用统计（不绑定具体任务 id，自定义计划同样适用） ----------

    /** 当天底线任务是否全部完成（当天没有底线任务返回 false） */
    private fun bottomLineMet(day: LocalDate, all: Map<LocalDate, Set<String>>): Boolean {
        val bottoms = Plan.tasksFor(Plan.stageFor(day), day.dayOfWeek).filter { it.isBottomLine }
        if (bottoms.isEmpty()) return false
        val ids = all[day] ?: emptySet()
        return bottoms.all { it.id in ids }
    }

    /** 底线连续天数：今天还没完成不打断连续（从昨天往前数） */
    suspend fun bottomLineStreak(today: LocalDate): Int {
        val all = readAllCheckins()
        var day = today
        if (!bottomLineMet(day, all)) day = day.minusDays(1)
        var n = 0
        while (bottomLineMet(day, all)) {
            n++
            day = day.minusDays(1)
        }
        return n
    }

    /** 累计"底线任务全完成"天数 */
    suspend fun bottomTotalDays(): Int =
        readAllCheckins().count { (d, ids) ->
            val bottoms = Plan.tasksFor(Plan.stageFor(d), d.dayOfWeek).filter { it.isBottomLine }
            bottoms.isNotEmpty() && bottoms.all { it.id in ids }
        }

    /** 累计背单词天数。 */
    suspend fun wordTotalDays(): Int {
        val p = context.dataStore.data.first()
        return p.asMap().count { (key, value) ->
            key.name.startsWith("checkin_") && (value as? String)?.split(',')?.contains("words") == true
        }
    }

    private fun isChecked(p: androidx.datastore.preferences.core.Preferences, day: LocalDate, taskId: String): Boolean =
        p[checkinKey(day)]?.split(',')?.contains(taskId) == true
}
