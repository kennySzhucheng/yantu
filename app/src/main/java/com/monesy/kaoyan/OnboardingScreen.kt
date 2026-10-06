package com.monesy.kaoyan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
 * 完成时写入用户配置层（向导本身不重载配置，由调用方在 onDone 中处理）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(store: Store, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }

    // 步骤 1 考试信息
    var examName by remember { mutableStateOf("28考研") }
    var examDate by remember { mutableStateOf(Config.examDate) }
    var targetScore by remember { mutableStateOf(if (Config.targetScore > 0) Config.targetScore.toString() else "380") }
    var planStart by remember { mutableStateOf(LocalDate.now()) }

    // 步骤 2 目标院校
    var schoolFrom by remember { mutableStateOf("") }
    var schoolTo by remember { mutableStateOf("") }
    var major by remember { mutableStateOf("") }
    var subjects by remember { mutableStateOf("") }

    // 步骤 3 计划
    var planChoice by remember { mutableIntStateOf(0) } // 0 生成个性化计划 1 稍后导入
    var planOptions by remember { mutableStateOf(PlanOptions()) }

    // 步骤 4 提醒时间与学期
    var morningTime by remember { mutableStateOf(Config.morningTime) }
    var eveningTime by remember { mutableStateOf(Config.eveningTime) }
    var semesterStart by remember { mutableStateOf(Timetable.SEMESTER_START) }
    var pickDate by remember { mutableStateOf<String?>(null) }
    var pickTime by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("欢迎使用「${Config.appTitle}」", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("花两分钟把它变成你自己的软件；全程可跳过，之后在设置里随时改。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth())
        Text("第 ${step + 1} / 4 步", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        when (step) {
            0 -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("① 考试信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = examName, onValueChange = { examName = it }, label = { Text("考试名称（如 28考研）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { pickDate = "exam" }) { Text("初试日期：${examDate.year}年${examDate.monthValue}月${examDate.dayOfMonth}日") }
                    OutlinedTextField(value = targetScore, onValueChange = { targetScore = it.filter { c -> c.isDigit() } }, label = { Text("目标分（可留空）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { pickDate = "start" }) { Text("备考起始日：${planStart.year}年${planStart.monthValue}月${planStart.dayOfMonth}日") }
                }
            }
            1 -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("② 目标院校（用于生成文案与关于页）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = schoolFrom, onValueChange = { schoolFrom = it }, label = { Text("就读学校（可留空）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = schoolTo, onValueChange = { schoolTo = it }, label = { Text("报考院校（可留空）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = major, onValueChange = { major = it }, label = { Text("报考专业（可留空）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = subjects, onValueChange = { subjects = it }, label = { Text("考试科目（如 101政治 / 201英语一 / 301数学一 / …）") }, modifier = Modifier.fillMaxWidth())
                }
            }
            2 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Button(onClick = { step++ }, modifier = Modifier.weight(1f)) { Text("下一步") }
            } else {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val name = examName.trim().ifBlank { "考研" }
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
                                    subjects = subjects.trim(),
                                    morningTime = "%02d:%02d".format(morningTime.hour, morningTime.minute),
                                    eveningTime = "%02d:%02d".format(eveningTime.hour, eveningTime.minute),
                                    planSource = if (planChoice == 0) "个性化计划（向导生成）" else "待导入空模板",
                                ),
                            )
                            if (planChoice == 0) {
                                UserConfig.putSection(
                                    store, UserConfig.SECTION_PLAN,
                                    PlanTemplate.buildPlan(
                                        examDate,
                                        planStart,
                                        planOptions.copy(
                                            wordCount = planOptions.wordCount.coerceAtLeast(1),
                                            mainMinutes = planOptions.mainMinutes.coerceAtLeast(30),
                                        ),
                                    ),
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
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("跳过（保持当前配置）", fontSize = 13.sp)
        }
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
                            "exam" -> examDate = pickerMillisToDate(m)
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
