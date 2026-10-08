package com.monesy.kaoyan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 配置管理：考试信息 / 节假日 / 计划模板 / 导入导出 / 恢复内置 / 重跑向导 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigManageScreen(
    store: Store,
    onBack: () -> Unit,
    onOpenTimetable: () -> Unit,
    onImportJson: () -> Unit,
    onExportJson: () -> Unit,
    onRerunWizard: () -> Unit,
    onReset: () -> Unit,
    onUseExample: () -> Unit,
    onExportTemplate: () -> Unit,
    onEditPlan: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onWriteSection: (section: String, json: JSONObject) -> Unit,
) {
    var showExamEdit by remember { mutableStateOf(false) }
    var showHolidayAdd by remember { mutableStateOf(false) }
    var showGenPlan by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var holidays by remember { mutableStateOf(Holidays.list) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
            Text("配置管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        // 数据与分享
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("数据与分享", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("导出一份 config.json 发给同学，对方导入即可用你的全部配置（含课表）。", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onExportJson, modifier = Modifier.weight(1f)) { Text("导出配置") }
                    OutlinedButton(onClick = onImportJson, modifier = Modifier.weight(1f)) { Text("导入配置") }
                }
            }
        }

        // 数据备份
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("数据备份（换机迁移）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "打卡历史、连续天数、奖励日志都只存在本机；导出备份文件后可在新手机导入还原。",
                    fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onExportBackup, modifier = Modifier.weight(1f)) { Text("导出数据备份") }
                    OutlinedButton(onClick = onImportBackup, modifier = Modifier.weight(1f)) { Text("导入数据备份") }
                }
            }
        }

        // 课表
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("课表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("查看/编辑课程；支持 Excel / 剪贴板 / 图片三种导入方式", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onOpenTimetable) { Text("课表管理") }
            }
        }

        // 考试信息
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("考试信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(Config.countdownLabel, fontSize = 13.sp)
                Text(
                    "初试：${Config.examDate}" +
                        (if (Config.targetScore > 0) " · 目标分：${Config.targetScore}" else "") +
                        " · 起始：${Plan.PLAN_START}",
                    fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { showExamEdit = true }) { Text("修改考试信息") }
            }
        }

        // 计划模板
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("计划模板", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "按科目 / 老师 / 已完成进度生成个性化计划；或导出空模板自己填写后导入（都只影响计划，不动课表）。",
                    fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { showGenPlan = true }) { Text("生成个性化计划") }
                OutlinedButton(onClick = onEditPlan) { Text("编辑当前计划（表单）") }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onUseExample, modifier = Modifier.weight(1f)) { Text("应用内置示例") }
                    OutlinedButton(onClick = onExportTemplate, modifier = Modifier.weight(1f)) { Text("导出空模板") }
                }
            }
        }

        // 节假日
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("节假日", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                holidays.take(6).forEach { h ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${h.name}：${h.start.monthValue}月${h.start.dayOfMonth}日–${h.end.monthValue}月${h.end.dayOfMonth}日${if (h.isOfficial) "" else "（预计）"}",
                            fontSize = 13.sp, modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            holidays = holidays - h
                            onWriteSection(UserConfig.SECTION_HOLIDAYS, holidaysJson(holidays))
                        }) { Icon(Icons.Default.Close, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (holidays.size > 6) Text("…共 ${holidays.size} 个假期", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { showHolidayAdd = true }) { Text("添加假期区间") }
            }
        }

        // 其它
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("其它", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onRerunWizard, modifier = Modifier.weight(1f)) { Text("重新运行向导") }
                    OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.weight(1f)) { Text("恢复内置模板") }
                }
            }
        }
    }

    if (showExamEdit) {
        ExamEditDialog(
            onDismiss = { showExamEdit = false },
            onConfirm = { name, date, score, start ->
                onWriteSection(
                    UserConfig.SECTION_APP,
                    JSONObject()
                        .put("countdownLabel", "距 $name 初试")
                        .put("examDate", date.toString())
                        .put("planStart", start.toString())
                        .put("targetScore", score),
                )
                showExamEdit = false
            },
        )
    }
    if (showHolidayAdd) {
        HolidayAddDialog(
            onDismiss = { showHolidayAdd = false },
            onConfirm = { name, start, end, official ->
                holidays = (holidays + HolidayRange(start, end, name, official)).sortedBy { it.start }
                onWriteSection(UserConfig.SECTION_HOLIDAYS, holidaysJson(holidays))
                showHolidayAdd = false
            },
        )
    }
    if (showGenPlan) {
        PlanOptionsDialog(
            onDismiss = { showGenPlan = false },
            onConfirm = { options ->
                onWriteSection(
                    UserConfig.SECTION_PLAN,
                    PlanTemplate.buildPlan(Config.examDate, Plan.PLAN_START, options),
                )
                showGenPlan = false
            },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("恢复内置模板？") },
            text = { Text("将清除你的全部自定义配置（课表、计划、节假日、考试信息），恢复到内置示例。此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    confirmReset = false
                }) { Text("恢复") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } },
        )
    }
}

private fun holidaysJson(list: List<HolidayRange>): JSONObject {
    val arr = JSONArray()
    list.forEach { h ->
        arr.put(
            JSONObject().put("start", h.start.toString()).put("end", h.end.toString())
                .put("name", h.name).put("official", h.isOfficial)
        )
    }
    return JSONObject().put("holidays", arr)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamEditDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, LocalDate, Int, LocalDate) -> Unit,
) {
    // 倒计时标签形如「距 28考研 初试」/「距考试初试」：去头尾后的核心词即考试名称
    var name by remember {
        mutableStateOf(
            Config.countdownLabel.removePrefix("距").removeSuffix("初试").trim().ifBlank { Config.examName }
        )
    }
    var examDate by remember { mutableStateOf(Config.examDate) }
    var score by remember { mutableStateOf(if (Config.targetScore > 0) Config.targetScore.toString() else "") }
    var planStart by remember { mutableStateOf(Plan.PLAN_START) }
    var pickTarget by remember { mutableStateOf<String?>(null) } // "exam" | "start"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改考试信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("考试名称（如 28考研）") }, singleLine = true)
                TextButton(onClick = { pickTarget = "exam" }) { Text("初试日期：$examDate") }
                OutlinedTextField(value = score, onValueChange = { score = it.filter { c -> c.isDigit() } }, label = { Text("目标分") }, singleLine = true)
                TextButton(onClick = { pickTarget = "start" }) { Text("备考起始日：$planStart") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), examDate, score.toIntOrNull() ?: 0, planStart) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (pickTarget != null) {
        val initial = if (pickTarget == "exam") examDate else planStart
        val st = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { pickTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { m ->
                        if (pickTarget == "exam") examDate = pickerMillisToDate(m) else planStart = pickerMillisToDate(m)
                    }
                    pickTarget = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { pickTarget = null }) { Text("取消") } },
        ) { DatePicker(state = st) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HolidayAddDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, LocalDate, LocalDate, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var end by remember { mutableStateOf(LocalDate.now()) }
    var official by remember { mutableStateOf(true) }
    var pickerFor by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加假期") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("假期名称") }, singleLine = true)
                TextButton(onClick = { pickerFor = "s" }) { Text("开始：$start") }
                TextButton(onClick = { pickerFor = "e" }) { Text("结束：$end") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("官方通知日期", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(checked = official, onCheckedChange = { official = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !end.isBefore(start),
                onClick = { onConfirm(name.trim(), start, end, official) },
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (pickerFor != null) {
        val initial = if (pickerFor == "s") start else end
        val st = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { pickerFor = null },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { m ->
                        if (pickerFor == "s") start = pickerMillisToDate(m) else end = pickerMillisToDate(m)
                    }
                    pickerFor = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { pickerFor = null }) { Text("取消") } },
        ) { DatePicker(state = st) }
    }
}

