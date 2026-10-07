package com.monesy.kaoyan

/**
 * 科目 / 老师 / 资料预设库（主流考研配置，用户可任意组合；不在列表里选"不选/自学"）。
 */
object PlanPresets {

    data class Teacher(val name: String, val base: String, val strong: String)

    val mathTeachers = listOf(
        Teacher("武忠祥", "《高等数学基础篇》听课 + 例题独立完成", "《高数辅导讲义》强化 + 《严选题》"),
        Teacher("张宇", "《基础30讲》+ 配套练习", "《36讲》+《1000题》"),
        Teacher("汤家凤", "基础班 + 《1800题》基础篇", "强化班 + 《1800题》提高篇"),
        Teacher("李永乐 + 余丙森", "李永乐线代基础 + 余丙森概率基础", "线代 / 概率辅导讲义强化"),
    )

    val grammarTeachers = listOf("田静（句句真研）", "刘晓艳", "不选 / 自学")
    val readingTeachers = listOf("唐迟（阅读的逻辑）", "颉斌斌", "不选 / 自学")
    val politicsTeachers = listOf("徐涛", "腿姐", "不选 / 自学")
    val vocabApps = listOf("不背单词", "墨墨背单词", "红宝书", "扇贝单词")

    val englishTypes = listOf("英语一", "英语二", "不考")
    val mathTypes = listOf("数学一", "数学二", "数学三", "不考")
}

/**
 * 计划生成选项：科目、老师、节奏、以及"用户已完成的进度"。
 * 生成器会按进度跳过已完成的阶段/条目——中途开始的用户不会被迫从零重来。
 */
data class PlanOptions(
    val english: String = "英语一",
    val math: String = "数学一",
    val hasPolitics: Boolean = true,
    val major: String = "",

    val mathTeacher: String = PlanPresets.mathTeachers[0].name,
    val grammarTeacher: String = PlanPresets.grammarTeachers[0],
    val readingTeacher: String = PlanPresets.readingTeachers[0],
    val politicsTeacher: String = PlanPresets.politicsTeachers[0],
    val vocabApp: String = PlanPresets.vocabApps[0],

    val wordCount: Int = 40,
    val wordMinutes: Int = 30,
    val mainMinutes: Int = 90,

    // ---- 已完成的进度 ----
    val wordsDone: Boolean = false,        // 单词已过一遍
    val mathBaseDone: Boolean = false,     // 高数基础完成
    val linearDone: Boolean = false,       // 线代基础完成
    val probDone: Boolean = false,         // 概率基础完成
    val grammarDone: Boolean = false,      // 语法长难句已过
    val majorRound1Done: Boolean = false,  // 专业课过了一轮
    val politicsStarted: Boolean = false,  // 政治已启动

    // ---- 复习起点：中途/后期开始的用户直接从对应阶段生成（0 基础 1 强化 2 真题 3 冲刺）----
    val startFrom: Int = 0,
)
