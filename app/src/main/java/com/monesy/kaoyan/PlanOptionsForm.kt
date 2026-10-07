package com.monesy.kaoyan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/** 计划生成选项表单（向导第 3 步与设置里的"生成个性化计划"共用） */

private val startFromLabels = listOf("从零开始", "已过基础期", "已进真题期", "冲刺起步")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlanOptionsForm(options: PlanOptions, onChange: (PlanOptions) -> Unit) {
    val o = options
    val hasMath = o.math != "不考"
    val hasEng = o.english != "不考"
    val hasMajor = o.major.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("考试科目")
        ChipRow(PlanPresets.englishTypes, o.english) { onChange(o.copy(english = it)) }
        ChipRow(PlanPresets.mathTypes, o.math) { onChange(o.copy(math = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("考政治", fontSize = 13.5.sp, modifier = Modifier.weight(1f))
            Switch(checked = o.hasPolitics, onCheckedChange = { onChange(o.copy(hasPolitics = it)) })
        }
        OutlinedTextField(
            value = o.major, onValueChange = { onChange(o.copy(major = it)) },
            label = { Text("专业课名称（如 807材料力学，可留空）") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (hasMath) {
            Label("数学老师")
            ChipRow(PlanPresets.mathTeachers.map { it.name }, o.mathTeacher) { onChange(o.copy(mathTeacher = it)) }
        }
        if (hasEng) {
            Label("语法 / 长难句")
            ChipRow(PlanPresets.grammarTeachers, o.grammarTeacher) { onChange(o.copy(grammarTeacher = it)) }
            Label("真题阅读")
            ChipRow(PlanPresets.readingTeachers, o.readingTeacher) { onChange(o.copy(readingTeacher = it)) }
            Label("单词工具")
            ChipRow(PlanPresets.vocabApps, o.vocabApp) { onChange(o.copy(vocabApp = it)) }
        }
        if (o.hasPolitics) {
            Label("政治")
            ChipRow(PlanPresets.politicsTeachers, o.politicsTeacher) { onChange(o.copy(politicsTeacher = it)) }
        }

        Label("每日节奏")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = o.wordCount.toString(),
                onValueChange = { v -> onChange(o.copy(wordCount = v.filter { it.isDigit() }.toIntOrNull() ?: 0)) },
                label = { Text("每天单词数") }, singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = o.mainMinutes.toString(),
                onValueChange = { v -> onChange(o.copy(mainMinutes = v.filter { it.isDigit() }.toIntOrNull() ?: 0)) },
                label = { Text("主科基准(分钟)") }, singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Label("复习起点（中后期开始直接从对应阶段生成）")
        ChipRow(startFromLabels, startFromLabels[o.startFrom.coerceIn(0, 3)]) { label ->
            val idx = startFromLabels.indexOf(label).coerceIn(0, 3)
            // 起点联动：起点之前的进度视为已完成
            val o2 = when (idx) {
                1 -> o.copy(startFrom = 1, wordsDone = true, grammarDone = true, mathBaseDone = true)
                2 -> o.copy(
                    startFrom = 2, wordsDone = true, grammarDone = true, mathBaseDone = true,
                    linearDone = true, probDone = true, majorRound1Done = true, politicsStarted = true,
                )
                3 -> o.copy(
                    startFrom = 3, wordsDone = true, grammarDone = true, mathBaseDone = true,
                    linearDone = true, probDone = true, majorRound1Done = true, politicsStarted = true,
                )
                else -> o.copy(startFrom = 0)
            }
            onChange(o2)
        }

        Label("已完成的进度（计划会从你所在的位置开始）")
        val doneItems = buildList {
            if (hasEng) add(Triple("单词已过一遍", o.wordsDone) { v: Boolean -> onChange(o.copy(wordsDone = v)) })
            if (hasMath) {
                add(Triple("高数基础完成", o.mathBaseDone) { v: Boolean -> onChange(o.copy(mathBaseDone = v)) })
                add(Triple("线代基础完成", o.linearDone) { v: Boolean -> onChange(o.copy(linearDone = v)) })
                add(Triple("概率基础完成", o.probDone) { v: Boolean -> onChange(o.copy(probDone = v)) })
            }
            if (hasEng) add(Triple("语法长难句已过", o.grammarDone) { v: Boolean -> onChange(o.copy(grammarDone = v)) })
            if (hasMajor) add(Triple("专业课过了一轮", o.majorRound1Done) { v: Boolean -> onChange(o.copy(majorRound1Done = v)) })
            if (o.hasPolitics) add(Triple("政治已启动", o.politicsStarted) { v: Boolean -> onChange(o.copy(politicsStarted = v)) })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            doneItems.forEach { (label, selected, toggle) ->
                FilterChip(selected = selected, onClick = { toggle(!selected) }, label = { Text(label, fontSize = 12.sp) })
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            FilterChip(selected = selected == item, onClick = { onSelect(item) }, label = { Text(item, fontSize = 12.sp) })
        }
    }
}

/** 设置页的"生成个性化计划"弹窗（可滚动表单） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanOptionsDialog(onDismiss: () -> Unit, onConfirm: (PlanOptions) -> Unit) {
    var options by remember { mutableStateOf(PlanOptions()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生成个性化计划") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "按你的初试日期与起始日倒推生成阶段计划（覆盖当前计划，课表不受影响）。",
                    fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(top = 4.dp))
                PlanOptionsForm(options) { options = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(options) }) { Text("生成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
