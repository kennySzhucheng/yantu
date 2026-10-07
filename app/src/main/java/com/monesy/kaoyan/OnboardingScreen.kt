package com.monesy.kaoyan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * 首次启动向导（4 步）：考试信息 → 目标院校 → 计划选择 → 每日节奏。
 * - 首次使用（isRerun=false）：必填项未填完不能跳过、不能进入下一步；完成后自动进入使用教程
 * - 重跑（isRerun=true，设置进入）：预填当前配置、可跳过、完成后不再弹教程
 * - 科目为四槽结构（政治/外语/数学/专业课），存储时以 " / " 拼接，保证格式统一
 */

/** 一键填充的示例档案（主要供测试与快速体验，真实用户也可拿来改） */
private data class WizardPreset(
    val label: String,
    val desc: String,
    val examName: String,
    val examDate: LocalDate,
    val target: String,
    val from: String,
    val to: String,
    val major: String,
    val pol: String,
    val lang: String,
    val math: String,
    val majorSubj: String,
    val startOffsetDays: Long,
    val options: PlanOptions,
)

private val wizardPresets = listOf(
    WizardPreset(
        "28考研 · 从零开始", "标准新考生：数一英一政治 + 材料力学，全程四阶段",
        "28考研", LocalDate.of(2027, 12, 18), "380",
        "东北林业大学", "华南理工大学", "机械（085500）",
        "101思想政治理论", "201英语（一）", "301数学（一）", "807材料力学",
        0,
        PlanOptions(major = "材料力学"),
    ),
    WizardPreset(
        "28考研 · 已过基础", "单词/语法/高数/线代/概率已完成 → 直接从强化期生成",
        "28考研", LocalDate.of(2027, 12, 18), "380",
        "东北林业大学", "华南理工大学", "机械（085500）",
        "101思想政治理论", "201英语（一）", "301数学（一）", "807材料力学",
        30,
        PlanOptions(
            major = "材料力学", startFrom = 1,
            wordsDone = true, grammarDone = true, mathBaseDone = true, linearDone = true, probDone = true,
        ),
    ),
    WizardPreset(
        "27考研 · 真题期起步", "2026年12月初试，现在才用 → 只生成真题期+冲刺期",
        "27考研", LocalDate.of(2026, 12, 19), "350",
        "某双非本科", "目标院校待定", "机械考研",
        "101思想政治理论", "201英语（一）", "301数学（一）", "807材料力学",
        90,
        PlanOptions(
            major = "材料力学", startFrom = 2,
            wordsDone = true, grammarDone = true, mathBaseDone = true,
            linearDone = true, probDone = true, majorRound1Done = true, politicsStarted = true,
        ),
    ),
    WizardPreset(
        "冲刺起步 · 英二数三", "只剩最后几周 → 只生成冲刺段（跨考专业课示例）",
        "27考研", LocalDate.of(2026, 12, 19), "360",
        "某本科", "某大学", "电子信息（085400）",
        "101思想政治理论", "204英语（二）", "302数学（二）", "908信号与系统",
        20,
        PlanOptions(
            english = "英语二", math = "数学二", major = "信号与系统", startFrom = 3,
            wordsDone = true, grammarDone = true, mathBaseDone = true,
            linearDone = true, probDone = true, majorRound1Done = true, politicsStarted = true,
        ),
    ),
)

/** 初试总在 12 月：届别 = 年份 + 1 的后两位（2026-12 → 27考研；非 12 月按下一个 12 月算） */
private fun suggestedExamName(d: LocalDate): String =
    "${if (d.monthValue == 12) d.year - 1999 else d.year - 1998}考研"

/** 科目存储格式 "A / B / C / D" 的拆分（兼容中英文斜杠、缺项补空） */
private fun splitSubjects(s: String): List<String> {
    val p = s.split('/', '／').map { it.trim() }.filter { it.isNotBlank() }
    return List(4) { p.getOrNull(it) ?: "" }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(store: Store, isRerun: Boolean, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }

    // 步骤 1 考试信息（重跑时预填当前配置）
    var examName by remember { mutableStateOf(if (isRerun) Config.examName.ifBlank { "28考研" } else "28考研") }
    var examNameTouched by remember { mutableStateOf(false) }
    var examDate by remember { mutableStateOf(Config.examDate) }
    var targetScore by remember { mutableStateOf(if (Config.targetScore > 0) Config.targetScore.toString() else "380") }
    var planStart by remember { mutableStateOf(if (isRerun) Config.planStart else LocalDate.now()) }

    // 步骤 2 目标院校 + 科目四槽
    var schoolFrom by remember {
        mutableStateOf(if (isRerun && Config.schoolFrom.isNotBlank() && Config.schoolFrom != "我的本科") Config.schoolFrom else "")
    }
    var schoolTo by remember { mutableStateOf(if (isRerun) Config.schoolTo else "") }
    var major by remember { mutableStateOf(if (isRerun) Config.major else "") }
    val prefillSubj = if (isRerun) splitSubjects(Config.subjects) else List(4) { "" }
    var subjPol by remember { mutableStateOf(prefillSubj[0].ifBlank { "101思想政治理论" }) }
    var subjLang by remember { mutableStateOf(prefillSubj[1].ifBlank { "201英语（一）" }) }
    var subjMath by remember { mutableStateOf(prefillSubj[2].ifBlank { "301数学（一）" }) }
    var subjMajor by remember { mutableStateOf(prefillSubj[3]) }

    // 步骤 3 计划
    var planChoice by remember { mutableIntStateOf(0) } // 0 生成个性化计划 1 稍后导入
    var planOptions by remember { mutableStateOf(PlanOptions(major = if (isRerun) Config.major else "")) }

    // 步骤 4 提醒时间与学期
    var morningTime by remember { mutableStateOf(Config.morningTime) }
    var eveningTime by remember { mutableStateOf(Config.eveningTime) }
    var semesterStart by remember { mutableStateOf(Timetable.SEMESTER_START) }
    var pickDate by remember { mutableStateOf<String?>(null) }
    var pickTime by remember { mutableStateOf<String?>(null) }
    var showPresets by remember { mutableStateOf(false) }

    // 前两步必填校验（目标分除外）：填全才放行，点了下一步才显示提示，避免一进来就满屏红
    var step0Tried by remember { mutableStateOf(false) }
    var step1Tried by remember { mutableStateOf(false) }
    val step0Valid = examName.isNotBlank()
    val step1Valid = listOf(schoolFrom, schoolTo, major, subjPol, subjLang, subjMath, subjMajor).all { it.isNotBlank() }

    fun pickExamDate(d: LocalDate) {
        examDate = d
        if (!examNameTouched) examName = suggestedExamName(d)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("欢迎使用「${Config.appTitle}」", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            if (isRerun) "修改你的配置：所有内容已按当前设置预填，改完保存即可。"
            else "花两分钟完成初始设置，每一步都有说明；填错了之后也能在设置里改。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { showPresets = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("📋 一键填充示例档案（体验 / 测试用）", fontSize = 13.sp)
        }
        LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth())
        Text("第 ${step + 1} / 4 步", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        when (step) {
            0 -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("① 考试信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    BubbleTip("考试名称与初试日期决定首页倒计时和所有提醒文案，务必准确")
                    OutlinedTextField(
                        value = examName,
                        onValueChange = { examName = it; examNameTouched = true },
                        label = { Text("考试名称＊（选日期后自动推断届别，可改）") },
                        singleLine = true,
                        isError = step0Tried && examName.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("初试日期＊（快捷选择，以官方公告为准）", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = examDate == LocalDate.of(2026, 12, 19),
                            onClick = { pickExamDate(LocalDate.of(2026, 12, 19)) },
                            label = { Text("27考研 · 约2026-12-19", fontSize = 12.sp) },
                        )
                        FilterChip(
                            selected = examDate == LocalDate.of(2027, 12, 18),
                            onClick = { pickExamDate(LocalDate.of(2027, 12, 18)) },
                            label = { Text("28考研 · 约2027-12-18", fontSize = 12.sp) },
                        )
                        TextButton(onClick = { pickDate = "exam" }) { Text("自选：${examDate.monthValue}月${examDate.dayOfMonth}日") }
                    }
                    OutlinedTextField(value = targetScore, onValueChange = { targetScore = it.filter { c -> c.isDigit() } }, label = { Text("目标分（选填）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("备考起始日＊（已经开始一段时间？选更早的起点）", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "今天" to 0L,
                            "1个月前" to 30L,
                            "3个月前" to 90L,
                            "半年前" to 182L,
                        ).forEach { (label, off) ->
                            val d = LocalDate.now().minusDays(off)
                            FilterChip(
                                selected = planStart == d,
                                onClick = { planStart = d },
                                label = { Text(label, fontSize = 12.sp) },
                            )
                        }
                        TextButton(onClick = { pickDate = "start" }) { Text("自选") }
                    }
                    if (step0Tried && !step0Valid) {
                        Text("请先填写考试名称", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            1 -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("② 目标院校", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    BubbleTip("院校与科目会写入关于页和通知文案；科目按固定格式分四格填写，是生成计划的依据")
                    OutlinedTextField(
                        value = schoolFrom, onValueChange = { schoolFrom = it },
                        label = { Text("就读学校＊") }, singleLine = true,
                        isError = step1Tried && schoolFrom.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = schoolTo, onValueChange = { schoolTo = it },
                        label = { Text("报考院校＊") }, singleLine = true,
                        isError = step1Tried && schoolTo.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = major, onValueChange = { major = it },
                        label = { Text("报考专业＊（如 机械（085500））") }, singleLine = true,
                        isError = step1Tried && major.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("考试科目＊（四科固定格式，以 / 拼接存储）", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = subjPol, onValueChange = { subjPol = it },
                        label = { Text("政治＊") }, singleLine = true,
                        isError = step1Tried && subjPol.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = subjLang, onValueChange = { subjLang = it },
                        label = { Text("外语＊") }, singleLine = true,
                        isError = step1Tried && subjLang.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("201英语（一）", "204英语（二）").forEach {
                            FilterChip(selected = subjLang == it, onClick = { subjLang = it }, label = { Text(it, fontSize = 11.5.sp) })
                        }
                    }
                    OutlinedTextField(
                        value = subjMath, onValueChange = { subjMath = it },
                        label = { Text("数学＊（不考填「不考」）") }, singleLine = true,
                        isError = step1Tried && subjMath.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("301数学（一）", "302数学（二）", "303数学（三）", "不考").forEach {
                            FilterChip(selected = subjMath == it, onClick = { subjMath = it }, label = { Text(it, fontSize = 11.5.sp) })
                        }
                    }
                    OutlinedTextField(
                        value = subjMajor, onValueChange = { subjMajor = it },
                        label = { Text("专业课＊（如 807材料力学）") }, singleLine = true,
                        isError = step1Tried && subjMajor.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (step1Tried && !step1Valid) {
                        Text(
                            "请填完院校信息与全部四科再继续（不确定的可以先填「待定」，之后在设置里改）",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            2 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BubbleTip("选科目和跟随的老师，再选复习起点、勾掉已完成的部分，会生成你的个性化任务")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("③ 备考计划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        listOf(
                            "生成个性化计划（选择科目、老师和已完成进度）",
                            "稍后导入：到 设置 → 配置管理 导出自带说明的空模板，填好后回填",
                        ).forEachIndexed { i, label ->
                            Row(
                                Modifier.fillMaxWidth().selectable(selected = planChoice == i, onClick = { planChoice = i }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = planChoice == i, onClick = { planChoice = i })
                                Text(label, fontSize = 13.5.sp)
                            }
                        }
                    }
                }
                if (planChoice == 0) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            PlanOptionsForm(planOptions) { planOptions = it }
                        }
                    }
                }
            }
            else -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("④ 提醒与学期", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    BubbleTip("这两个时间就是每天通知的时间；学期第 1 周用来计算「第几教学周」")
                    Text("每天定时提醒你背单词与晚自习（之后可在设置里改）", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { pickTime = "morning" }) { Text("早晨提醒：${morningTime}") }
                    TextButton(onClick = { pickTime = "evening" }) { Text("晚上提醒：${eveningTime}") }
                    Text(
                        "学期第 1 周周一（“第几教学周”从此算起，各校校历不同）",
                        fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    TextButton(onClick = { pickDate = "semester" }) { Text("当前：$semesterStart") }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) { Text("上一步") }
            }
            if (step < 3) {
                Button(
                    onClick = {
                        when (step) {
                            0 -> if (step0Valid) step++ else step0Tried = true
                            1 -> if (step1Valid) step++ else step1Tried = true
                            else -> step++
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("下一步") }
            } else {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val name = examName.trim().ifBlank { "考研" }
                        val subjectsText = listOf(subjPol, subjLang, subjMath, subjMajor)
                            .map { it.trim() }
                            .joinToString(" / ")
                        val opts = planOptions.copy(
                            wordCount = planOptions.wordCount.coerceAtLeast(1),
                            mainMinutes = planOptions.mainMinutes.coerceAtLeast(30),
                            major = planOptions.major.ifBlank {
                                subjMajor.trim().replace(Regex("^[0-9]{3}\\s*"), "")
                            },
                        )
                        scope.launch {
                            UserConfig.putSection(
                                store, UserConfig.SECTION_APP,
                                PlanTemplate.buildAppPatch(
                                    examName = name,
                                    examDate = examDate,
                                    planStart = planStart,
                                    targetScore = targetScore.toIntOrNull() ?: 0,
                                    schoolFrom = schoolFrom.trim(),
                                    schoolTo = schoolTo.trim(),
                                    major = major.trim(),
                                    subjects = subjectsText,
                                    morningTime = "%02d:%02d".format(morningTime.hour, morningTime.minute),
                                    eveningTime = "%02d:%02d".format(eveningTime.hour, eveningTime.minute),
                                    planSource = if (planChoice == 0) "个性化计划（向导生成）" else "待导入空模板",
                                ),
                            )
                            if (planChoice == 0) {
                                UserConfig.putSection(
                                    store, UserConfig.SECTION_PLAN,
                                    PlanTemplate.buildPlan(examDate, planStart, opts),
                                )
                            }
                            if (semesterStart != Timetable.SEMESTER_START) {
                                // 只覆盖学期起始日，保留已有课表条目
                                val existing = UserConfig.readSection(store, UserConfig.SECTION_TIMETABLE)
                                    ?: org.json.JSONObject()
                                existing.put("semesterStart", semesterStart.toString())
                                UserConfig.putSection(store, UserConfig.SECTION_TIMETABLE, existing)
                            }
                            onDone()
                        }
                    },
                ) { Text("完成，开始使用") }
            }
        }
        if (isRerun) {
            TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("跳过（保持当前配置）", fontSize = 13.sp)
            }
        }
    }

    // ---- 一键填充示例档案 ----
    if (showPresets) {
        AlertDialog(
            onDismissRequest = { showPresets = false },
            title = { Text("示例档案") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "选中后填充全部向导字段（可再手动调整）。",
                        fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    wizardPresets.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .selectable(
                                    selected = false,
                                    onClick = {
                                        examName = p.examName; examNameTouched = true
                                        examDate = p.examDate
                                        targetScore = p.target
                                        planStart = LocalDate.now().minusDays(p.startOffsetDays)
                                        schoolFrom = p.from; schoolTo = p.to; major = p.major
                                        subjPol = p.pol; subjLang = p.lang; subjMath = p.math; subjMajor = p.majorSubj
                                        planChoice = 0
                                        planOptions = p.options
                                        showPresets = false
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = false, onClick = null)
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(p.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(p.desc, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPresets = false }) { Text("关闭") }
            },
        )
    }

    if (pickDate != null) {
        val initial = when (pickDate) {
            "exam" -> examDate
            "semester" -> semesterStart
            else -> planStart
        }
        val st = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { pickDate = null },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { m ->
                        when (pickDate) {
                            "exam" -> pickExamDate(pickerMillisToDate(m))
                            "semester" -> semesterStart = pickerMillisToDate(m)
                            else -> planStart = pickerMillisToDate(m)
                        }
                    }
                    pickDate = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { pickDate = null }) { Text("取消") } },
        ) { DatePicker(state = st) }
    }
    if (pickTime != null) {
        val initial = if (pickTime == "morning") morningTime else eveningTime
        val st = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = null },
            title = { Text(if (pickTime == "morning") "早晨提醒时间" else "晚上提醒时间") },
            text = { TimePicker(state = st) },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalTime.of(st.hour, st.minute)
                    if (pickTime == "morning") morningTime = t else eveningTime = t
                    pickTime = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { pickTime = null }) { Text("取消") } },
        )
    }
}
