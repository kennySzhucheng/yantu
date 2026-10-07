package com.monesy.kaoyan

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 计划生成器 v2：按「科目 + 老师 + 已完成进度」生成个性化阶段计划与重大节点。
 * - 老师/资料预设来自 PlanPresets，代入各阶段核心任务与每日任务标题
 * - 进度感知：基础已完成的用户自动跳过基础期；单词已过一遍则标注第 2 遍
 */
object PlanTemplate {

    private data class StageDef(
        val name: String,
        val hours: String,
        val goal: String,
        val cores: List<String>,
        val acceptance: List<String>,
    )

    fun buildPlan(examDate: LocalDate, planStart: LocalDate, o: PlanOptions): JSONObject {
        val hasMath = o.math != "不考"
        val hasEng = o.english != "不考"
        val hasMajor = o.major.isNotBlank()
        val hasPol = o.hasPolitics
        val mt = PlanPresets.mathTeachers.firstOrNull { it.name == o.mathTeacher }
            ?: PlanPresets.mathTeachers.first()

        // 按剩余时长分配阶段。从零开始：基础≈40% / 强化≈30% / 真题≈18% / 冲刺≈12%，
        // 各档有下限（冲刺≥4周、真题≥6周、强化≥8周）；总时长不足时按 冲刺>真题>强化 优先级压缩。
        // 中途/后期开始（startFrom>0）：丢弃之前的阶段，剩余时间按比例重分、不设下限——
        // 真题期起 = 真题60/冲刺40；强化期起 = 强化55/真题27/冲刺18；冲刺起 = 全部给冲刺。
        val startIdx = o.startFrom.coerceIn(0, 3)
        val total = ChronoUnit.DAYS.between(planStart, examDate).coerceAtLeast(0)
        val stageCount = 4 - startIdx
        val starts = ArrayList<LocalDate>(stageCount)
        val ends = ArrayList<LocalDate>(stageCount)
        if (startIdx == 0) {
            var sprintLen = (total * 0.12).toLong().coerceIn(28, 42)
            var realLen = (total * 0.18).toLong().coerceIn(42, 70)
            var strongLen = (total * 0.30).toLong().coerceIn(56, 112)
            if (sprintLen + realLen + strongLen > total) {
                sprintLen = minOf(sprintLen, total)
                realLen = minOf(realLen, (total - sprintLen).coerceAtLeast(0))
                strongLen = minOf(strongLen, (total - sprintLen - realLen).coerceAtLeast(0))
            }
            val baseLen = (total - sprintLen - realLen - strongLen).coerceAtLeast(0)

            val strongStart = planStart.plusDays(baseLen)
            val realStart = strongStart.plusDays(strongLen)
            val sprintStart = realStart.plusDays(realLen)
            starts.addAll(listOf(planStart, strongStart, realStart, sprintStart))
            ends.addAll(
                listOf(
                    strongStart.minusDays(1),
                    realStart.minusDays(1),
                    sprintStart.minusDays(1),
                    examDate.minusDays(1),
                )
            )
        } else {
            val weights = when (startIdx) {
                1 -> doubleArrayOf(0.55, 0.27, 0.18)
                2 -> doubleArrayOf(0.60, 0.40)
                else -> doubleArrayOf(1.0)
            }
            var cursor = planStart
            for (k in 0 until stageCount) {
                val last = k == stageCount - 1
                val len = if (last) {
                    ChronoUnit.DAYS.between(cursor, examDate).coerceAtLeast(1)
                } else {
                    (total * weights[k]).toLong().coerceAtLeast(1)
                }
                starts.add(cursor)
                ends.add(if (last) examDate.minusDays(1) else cursor.plusDays(len).minusDays(1))
                cursor = cursor.plusDays(len)
            }
        }

        fun task(id: String, title: String, minutes: Int, lo: Int = 0, hi: Int = 0, bottom: Boolean = false): JSONObject =
            JSONObject().put("id", id).put("title", title).put("minutes", minutes).apply {
                if (lo > 0 && hi > 0) { put("minMinutes", lo); put("maxMinutes", hi) }
                if (bottom) put("bottomLine", true)
            }

        fun wordsTitle(): String =
            "早晨：背单词 ${o.wordCount} 个" + (if (o.wordsDone) "（第 2 遍巩固）" else "")

        fun words() = task("words", wordsTitle(), o.wordMinutes, bottom = true)
        val m = o.mainMinutes
        val lo = maxOf(60, m - 30)
        val hi = m + 60

        // ---- 各阶段内容（科目/老师/进度感知） ----

        val baseCores = buildList {
            add("单词：${o.vocabApp} 每天 ${o.wordCount} 词" + if (o.wordsDone) "（已过一遍，进入巩固）" else "")
            if (hasEng && !o.grammarDone) add("英语（${o.english}）：语法长难句（${o.grammarTeacher}）每周 2–3 节")
            if (hasMath && !o.mathBaseDone) add("数学（${o.math}）：${mt.base}")
            if (hasMajor && !o.majorRound1Done) add("专业课：${o.major} 教材通读一遍")
        }
        val baseAccept = buildList {
            if (o.wordsDone) add("单词进入第 2 遍巩固") else add("单词完成第 1 遍")
            if (hasEng && !o.grammarDone) add("语法能独立拆分复杂句")
            if (hasMath && !o.mathBaseDone) add("数学基础课过完一遍")
            if (hasMajor && !o.majorRound1Done) add("专业课教材通读一遍")
            add("没有连续超过 3 天完全不学")
        }
        val baseTasks = buildList {
            add(words())
            if (hasMath && !o.mathBaseDone) add(task("g_base_math", "数学基础推进（${mt.name}）", m, lo, hi))
            if (hasEng && !o.grammarDone) add(task("g_base_eng", "英语：语法 / 长难句（${o.grammarTeacher}）", 60, 30, 90))
            if (hasMajor && !o.majorRound1Done) add(task("g_base_major", "专业课：${o.major} 通读", 90, 60, 120))
            if (size == 1) add(task("g_base_main", "主科推进", m, lo, hi))
        }

        val strongNotes = buildList {
            if (hasMath) {
                if (o.linearDone) add("线代已过基础 → 直接刷题")
                if (o.probDone) add("概率已过基础 → 直接刷题")
            }
        }
        val strongCores = buildList {
            add("单词：${o.vocabApp} 每天 ${o.wordCount} 词")
            if (hasMath) add("数学（${o.math}）：${mt.strong}" + if (strongNotes.isNotEmpty()) "（${strongNotes.joinToString("；")}）" else "")
            if (hasEng) add("英语：真题阅读精做（${o.readingTeacher}，近 10 年）")
            if (hasMajor) add(
                if (o.majorRound1Done) "专业课：${o.major} 二轮 + 真题分类"
                else "专业课：${o.major} 教材精读 + 课后题"
            )
            if (hasPol) add(if (o.politicsStarted) "政治：继续推进（${o.politicsTeacher}）" else "政治：暑期启动（${o.politicsTeacher}）")
        }
        val strongAccept = buildList {
            if (hasMath) add("数学强化完成一遍")
            if (hasEng) add("真题阅读一刷过半")
            if (hasMajor) add("专业课过完一轮（或完成二轮）")
            if (hasPol) add("政治强化过一遍")
        }
        val strongTasks = buildList {
            add(words())
            if (hasMath) add(task("g_str_math", "数学强化：${mt.name}", m + 30, lo, hi + 60))
            if (hasEng) add(task("g_str_eng", "英语：真题阅读（${o.readingTeacher}）", 90, 60, 150))
            if (hasMajor) add(task("g_str_major", "专业课：${o.major}", 120, 60, 180))
            if (hasPol && o.politicsStarted) add(task("g_str_pol", "政治：${o.politicsTeacher}", 60, 30, 90))
        }

        val realAccept = buildList {
            if (hasMath) add("数学真题一刷完成")
            if (hasMajor) add("专业课真题过完一遍")
            if (hasPol) add("政治 1000 题一刷")
            if (hasEng) add("英语真题二刷过半")
        }
        val realTasks = buildList {
            add(words())
            if (hasMath) add(task("g_real_math", "数学真题限时（一套）", 180))
            if (hasMajor) add(task("g_real_major", "${o.major} 真题分类 / 限时", 150, 90, 180))
            add(task("g_real_review", "错题整理 / 回顾", 90, 60, 150))
            if (hasPol) add(task("g_real_pol", "政治：1000 题 / 强化课", 90))
        }

        val sprintAccept = buildList {
            add("大题背诵完成（政治 / 专业课）")
            add("全科模考完成 2 次以上")
            add("准考证打印、证件文具备齐")
        }
        val sprintTasks = buildList {
            add(words())
            add(task("g_sprint_mock", "模考 / 套卷限时", 180))
            if (hasPol) add(task("g_sprint_memo", "政治大题背诵（肖四 / 腿姐）", 120))
            if (hasMajor) add(task("g_sprint_major", "${o.major} 背诵 / 真题回顾", 120))
            if (!hasPol && !hasMajor) add(task("g_sprint_review", "错题 / 背诵回顾", 120))
        }

        val defs = listOf(
            StageDef("基础期", "15–25 小时/周", "把每日学习变成习惯，主科基础过一遍", baseCores, baseAccept),
            StageDef("强化期", "25–35 小时/周", "主科强化 + 真题起步", strongCores, strongAccept),
            StageDef("真题期", "30–40 小时/周", "真题限时训练 + 错题整理",
                buildList {
                    if (hasMath) add("数学：真题按套卷限时")
                    if (hasMajor) add("专业课：真题分类 + 教材回顾")
                    if (hasEng) add("英语：真题二刷 + 作文素材")
                    if (hasPol) add("政治：强化 + 1000 题")
                },
                realAccept),
            StageDef("冲刺期", "35–45 小时/周", "背诵 + 模考，稳住心态",
                buildList {
                    if (hasPol) add("政治：大题背诵滚动 2–3 遍（肖四 / 腿姐）")
                    if (hasMajor) add("专业课：背诵 + 真题回顾")
                    add("全科：按真实时间模考 2–3 次")
                    add("回归错题本，停刷新题")
                },
                sprintAccept),
        )
        val taskSets = listOf(baseTasks, strongTasks, realTasks, sprintTasks)

        // 进度起点：基础全完成（或无此科）→ 跳过基础期（仅对从零开始的计划生效）
        val baseDone = (!hasMath || o.mathBaseDone) && (!hasEng || o.grammarDone) &&
            (!hasMajor || o.majorRound1Done)
        val skipBase = startIdx == 0 && baseDone

        val keptDefs = defs.drop(startIdx)
        val keptSets = taskSets.drop(startIdx)
        val stages = JSONArray()
        var stageId = 1
        keptDefs.forEachIndexed { k, def ->
            val s = starts.getOrNull(k)
            val e = ends.getOrNull(k)
            if (s == null || e == null) return@forEachIndexed
            if (e < s) return@forEachIndexed
            val satTasks = buildList {
                add(words())
                add(task("g_${k}_sat_am", "上午：主科推进", 120, 60, 180))
                add(task("g_${k}_sat_pm", "下午：英语 / 专业课", 120, 60, 150))
            }
            val sunTasks = buildList {
                add(words())
                add(task("g_${k}_sun_am", "上午：主科推进", 120, 60, 180))
            }
            stages.put(
                JSONObject()
                    .put("id", stageId++)
                    .put("name", def.name)
                    .put("dateText", "$s – $e")
                    .put("start", s.toString())
                    .put("end", e.toString())
                    .put("hoursPerWeek", def.hours)
                    .put("goal", def.goal)
                    .put("coreTasks", JSONArray(def.cores))
                    .put("acceptance", JSONArray(def.acceptance))
                    .put("weekdayTasks", JSONArray(keptSets[k]))
                    .put("saturdayTasks", JSONArray(satTasks))
                    .put("sundayTasks", JSONArray(sunTasks)),
            )
        }

        // 复试期
        val reStart = examDate.plusDays(1)
        val reEnd = examDate.plusDays(100)
        stages.put(
            JSONObject()
                .put("id", stageId)
                .put("name", "复试期")
                .put("dateText", "$reStart – $reEnd（预计）")
                .put("start", reStart.toString())
                .put("end", reEnd.toString())
                .put("hoursPerWeek", "初试后休息不超过一周即启动")
                .put("goal", "复试准备：笔试 + 面试素材 + 英语口语")
                .put("coreTasks", JSONArray(listOf(
                    if (hasMajor) "复试笔试：${o.major}" else "复试笔试科目复习",
                    "英语：自我介绍 + 常见问答",
                    "面试素材：课程设计 / 竞赛 / 项目梳理",
                    "了解目标导师方向与论文",
                )))
                .put("acceptance", JSONArray(listOf("笔试科目过完一轮", "面试能讲 3–5 分钟的项目素材至少一件")))
                .put("weekdayTasks", JSONArray(listOf(
                    task("g_re_written", "复试笔试复习", 120),
                    task("g_re_eng", "英语口语：自我介绍 + 问答", 45),
                    task("g_re_quiz", "面试素材梳理", 60),
                )))
                .put("saturdayTasks", JSONArray(listOf(
                    task("g_re_written_sat", "复试笔试复习", 120),
                    task("g_re_eng_sat", "英语口语练习", 45),
                    task("g_re_quiz_sat", "面试素材梳理", 60),
                )))
                .put("sundayTasks", JSONArray(listOf(
                    task("g_re_written_sun", "复试笔试复习", 120),
                    task("g_re_eng_sun", "英语口语练习", 45),
                    task("g_re_quiz_sun", "面试素材梳理", 60),
                ))),
        )

        val nodes = JSONArray()
        fun node(offset: Long, title: String, detail: String, kind: String, soft: Boolean = false) {
            nodes.put(
                JSONObject()
                    .put("date", examDate.plusDays(offset).toString())
                    .put("title", title).put("detail", detail).put("kind", kind)
                    .apply { if (soft) put("soft", true) }
            )
        }
        node(-100, "招生简章与专业目录公布", "核对考试科目与参考书是否变动", "INFO")
        node(-70, "预报名开始", "关注研招网通知，按官方时间执行", "INFO")
        node(-55, "正式报名截止", "最终确定院校与方向，逾期不补", "INFO")
        node(-45, "网上确认", "按要求上传照片与材料", "INFO")
        node(-30, "背诵冲刺期", "政治大题 / 专业课滚动背诵", "STUDY")
        node(-14, "考前全科模考", "按真实考试时间模考 2–3 次", "STUDY")
        node(-10, "打印准考证", "多打几份，核对考场信息；备齐证件文具", "INFO")
        node(0, "初试 · 第一天", "保持状态，正常发挥", "EXAM")
        node(1, "初试 · 第二天", "考完即放下，准备复试节奏", "EXAM")
        // 复试时间由各校决定，作软节点只做范围提醒，不显示硬倒计时
        node(60, "复试（时间以报考院校通知为准）",
            "一般在初试次年 2月下旬–4月上旬，各校不同；出分后立刻准备笔试与面试，持续关注报考院校研究生院通知",
            "EXAM", soft = true)

        return JSONObject().put("stages", stages).put("keyNodes", nodes)
    }

    /** 空白计划模板（带字段说明与示例），用户导出填写后经"导入配置"回填。 */
    fun buildBlankTemplate(): JSONObject = JSONObject().apply {
        put(
            "_说明",
            "这是空白的计划模板：把 stages 与 keyNodes 改成你自己的内容后，" +
                "到 设置 → 配置管理 → 导入配置 选择本文件即可回填。" +
                "任务字段：id 唯一英文标识；title 显示文本；minutes 默认时长(分钟)；" +
                "minMinutes/maxMinutes 可调范围(0 表示固定不可调)；bottomLine 是否底线任务。",
        )
        put(
            "plan",
            JSONObject()
                .put(
                    "_说明",
                    "stages 按时间顺序排列，覆盖从备考开始到考试前一天；kind 可选 STUDY / INFO / EXAM。" +
                        "示例中的文字请全部替换为你自己的内容。",
                )
                .put(
                    "stages",
                    JSONArray().put(
                        JSONObject()
                            .put("id", 1)
                            .put("name", "阶段名（如：基础期）")
                            .put("dateText", "2026.9 – 2026.12")
                            .put("start", "2026-09-01")
                            .put("end", "2026-12-31")
                            .put("hoursPerWeek", "15–25 小时/周")
                            .put("goal", "本阶段唯一目标（写一句）")
                            .put("coreTasks", JSONArray(listOf("核心任务 1", "核心任务 2")))
                            .put("acceptance", JSONArray(listOf("验收标准 1", "验收标准 2")))
                            .put("decisive", false)
                            .put("weekdayTasks", JSONArray(listOf(
                                JSONObject().put("id", "my_words").put("title", "背单词 40 个").put("minutes", 30).put("bottomLine", true),
                                JSONObject().put("id", "my_main").put("title", "主科学习 1.5 小时").put("minutes", 90).put("minMinutes", 60).put("maxMinutes", 150),
                            )))
                            .put("saturdayTasks", JSONArray(listOf(
                                JSONObject().put("id", "my_sat").put("title", "周六上午：主科 2 小时").put("minutes", 120),
                            )))
                            .put("sundayTasks", JSONArray()),
                    ),
                )
                .put(
                    "keyNodes",
                    JSONArray().put(
                        JSONObject()
                            .put("date", "2026-12-20")
                            .put("title", "节点名（如：初试）")
                            .put("detail", "说明文字")
                            .put("kind", "EXAM"),
                    ),
                ),
        )
    }

    /** app 段补丁（考试信息 / 目标院校 / 提醒时间 / 关于页文案）。 */
    fun buildAppPatch(
        examName: String,
        examDate: LocalDate,
        planStart: LocalDate,
        targetScore: Int,
        schoolFrom: String,
        schoolTo: String,
        major: String,
        subjects: String,
        morningTime: String,
        eveningTime: String,
        planSource: String,
    ): JSONObject = JSONObject().apply {
        put("countdownLabel", "距 $examName 初试")
        put("examName", examName)
        put("examDate", examDate.toString())
        put("planStart", planStart.toString())
        if (targetScore > 0) put("targetScore", targetScore)
        put("schoolFrom", schoolFrom.ifBlank { "我的本科" })
        if (schoolTo.isNotBlank()) put("schoolTo", schoolTo)
        if (major.isNotBlank()) put("major", major)
        if (subjects.isNotBlank()) put("subjects", subjects)
        put("aboutGoal", "目标：${schoolTo.ifBlank { "待定院校" }} ${major}".trim())
        if (subjects.isNotBlank()) put("aboutSubjects", "科目：$subjects")
        if (targetScore > 0) put("aboutTarget", "目标分：$targetScore")
        put("aboutSource", "计划来源：$planSource")
        put("morningTime", morningTime)
        put("eveningTime", eveningTime)
    }
}
