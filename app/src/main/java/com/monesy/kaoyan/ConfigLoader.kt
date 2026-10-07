package com.monesy.kaoyan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * 全局配置（来自 assets/config/app.json）。
 * 这是 App 全部个性化参数的唯一入口——普适化时用户只需改 JSON，不用碰代码。
 */
object Config {
    var appTitle: String = "日拱一卒"
    var countdownLabel: String = "距考试初试"
    var examName: String = ""
    var targetScore: Int = 0
    var planStart: LocalDate = LocalDate.of(2026, 1, 1)
    var examDate: LocalDate = LocalDate.of(2027, 12, 18)
    var morningTime: LocalTime = LocalTime.of(8, 0)
    var eveningTime: LocalTime = LocalTime.of(19, 30)
    var semesterWeeks: Int = 18
    var schoolFrom: String = ""
    var schoolTo: String = ""
    var major: String = ""
    var subjects: String = ""
    var aboutGoal: String = ""
    var aboutSubjects: String = ""
    var aboutTarget: String = ""
    var aboutSource: String = ""
    var rewardDaily: List<String> = listOf("奖励自己一下")
    var rewardWeekly: List<String> = listOf("好好犒劳自己一次")

    /** 用户反馈入口（GitHub Issues），关于页可跳转 */
    var feedbackUrl: String = "https://github.com/kennySzhucheng/yantu/issues"

    /** 作者邮箱（反馈备选渠道；未装邮件客户端时点击会复制到剪贴板） */
    var feedbackEmail: String = "kennySli@163.com"

    /** 非空表示配置加载失败，界面应提示用户检查 assets/config */
    var loadError: String? = null
}

object ConfigLoader {

    /** 在 Application.onCreate 中最先调用；任何异常都转为可读错误而不是崩溃。 */
    fun load(context: Context) {
        try {
            val store = Store(context)
            val user = UserConfig.readAll(store)

            val app = UserConfig.merge(read(context, "config/app.json"), user.optJSONObject(UserConfig.SECTION_APP))
            // 旧版应用名"研途"与第三方商标重名：读取时自动迁移为"日拱一卒"（含老用户配置层旧值）
            Config.appTitle = app.optString("appTitle", Config.appTitle)
                .let { if (it == "研途" || it.isBlank()) "日拱一卒" else it }
            Config.countdownLabel = app.optString("countdownLabel", Config.countdownLabel)
            Config.examName = app.optString("examName", Config.examName)
            Config.targetScore = app.optInt("targetScore", Config.targetScore)
            Config.planStart = parseDate(app.optString("planStart")) ?: Config.planStart
            Config.examDate = parseDate(app.optString("examDate")) ?: Config.examDate
            Config.morningTime = parseTime(app.optString("morningTime")) ?: Config.morningTime
            Config.eveningTime = parseTime(app.optString("eveningTime")) ?: Config.eveningTime
            Config.semesterWeeks = app.optInt("semesterWeeks", Config.semesterWeeks)
            Config.schoolFrom = app.optString("schoolFrom", Config.schoolFrom)
            Config.schoolTo = app.optString("schoolTo", Config.schoolTo)
            Config.major = app.optString("major", Config.major)
            Config.subjects = app.optString("subjects", Config.subjects)
            Config.aboutGoal = app.optString("aboutGoal", Config.aboutGoal)
            Config.aboutSubjects = app.optString("aboutSubjects", Config.aboutSubjects)
            Config.aboutTarget = app.optString("aboutTarget", Config.aboutTarget)
            Config.aboutSource = app.optString("aboutSource", Config.aboutSource)
            Config.rewardDaily = readList(app.optJSONArray("rewardsDaily")).ifEmpty { Config.rewardDaily }
            Config.rewardWeekly = readList(app.optJSONArray("rewardsWeekly")).ifEmpty { Config.rewardWeekly }
            Config.feedbackUrl = app.optString("feedbackUrl", Config.feedbackUrl)
            Config.feedbackEmail = app.optString("feedbackEmail", Config.feedbackEmail)

            Plan.init(UserConfig.merge(read(context, "config/plan.json"), user.optJSONObject(UserConfig.SECTION_PLAN)))
            Timetable.init(UserConfig.merge(read(context, "config/timetable.json"), user.optJSONObject(UserConfig.SECTION_TIMETABLE)))
            Holidays.init(
                UserConfig
                    .merge(read(context, "config/holidays.json"), user.optJSONObject(UserConfig.SECTION_HOLIDAYS))
                    .optJSONArray("holidays")
            )
            ClassTimes.init(app.optJSONArray("bells"))

            Config.loadError = null
        } catch (t: Throwable) {
            Config.loadError = t.message ?: t.toString()
        }
    }

    private fun read(context: Context, path: String): JSONObject =
        JSONObject(context.assets.open(path).bufferedReader().use { it.readText() })

    private fun readList(arr: JSONArray?): List<String> =
        if (arr == null) emptyList() else (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }

    private fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()

    /** 宽松解析："8:00" 与 "08:00" 都接受（手写 JSON 常见一位小时） */
    private fun parseTime(s: String): LocalTime? =
        runCatching { LocalTime.parse(s) }.getOrNull()
            ?: runCatching { LocalTime.parse(s.padStart(5, '0')) }.getOrNull()
}
