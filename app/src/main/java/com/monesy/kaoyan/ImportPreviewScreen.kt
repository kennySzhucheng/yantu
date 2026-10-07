package com.monesy.kaoyan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate

/** 课表导入预览页：解析结果逐条确认，可编辑/删除/手动补录，未识别内容可一键补录，确认后写入用户配置 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewScreen(
    initialCourses: List<TimetableParser.Course>,
    parseError: String?,
    unparsed: List<TimetableParser.UnparsedCell> = emptyList(),
    onBack: () -> Unit,
    onConfirm: (semesterStart: LocalDate, courses: List<TimetableParser.Course>) -> Unit,
) {
    val courses = remember { mutableStateListOf<TimetableParser.Course>().apply { addAll(initialCourses) } }
    var semesterStart by remember {
        mutableStateOf(LocalDate.now().with(DayOfWeek.MONDAY))
    }
    var showStartPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TimetableParser.Course?>(null) }
    var adding by remember { mutableStateOf(false) }
    val unparsedList = remember { mutableStateListOf<TimetableParser.UnparsedCell>().apply { addAll(unparsed) } }
    var prefill by remember { mutableStateOf<Pair<Int, TimetableParser.Course>?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
            Text("导入预览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        if (parseError != null) {
            Card(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(parseError, Modifier.padding(12.dp), fontSize = 13.sp)
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("学期第 1 周周一：", fontSize = 14.sp)
            TextButton(onClick = { showStartPicker = true }) {
                Text("${semesterStart.year}年${semesterStart.monthValue}月${semesterStart.dayOfMonth}日 · 点击修改")
            }
        }
        Text(
            "各校校历不同，请设为你们学校第 1 周实际开始的周一",
            fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "识别到 ${courses.size} 门课程（勾选的要导入；点条目可修正；导入将覆盖当前课表）",
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val unlocatedCount = courses.count { it.day == 0 }
        if (unlocatedCount > 0) {
            Text(
                "⚠️ 有 $unlocatedCount 门未定位课程（缺星期信息）：点开条目选择星期后才会被保存",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(courses) { i, c ->
                Card(Modifier.fillMaxWidth().clickable { editing = c }) {
                    Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = c.checked, onCheckedChange = { c.checked = it })
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${dayName(c.day)} ${Timetable.slotLabels.getOrElse(c.slot) { "?" }}",
                                fontSize = 12.sp,
                                fontWeight = if (c.day == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (c.day == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                            Text(c.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                            Text(
                                listOfNotNull(
                                    c.room.ifBlank { null },
                                    c.weeks.takeIf { it.isNotBlank() }?.let { "第 $it 周" },
                                ).joinToString(" · "),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { courses.removeAt(i) }) {
                            Icon(Icons.Default.Close, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (unparsedList.isNotEmpty()) {
                item {
                    Text(
                        "以下 ${unparsedList.size} 段内容未能自动识别——点条目补录成课程，用不到就忽略（不会导入）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                itemsIndexed(unparsedList) { i, u ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                prefill = i to TimetableParser.Course(
                                    day = u.day, slot = u.slot,
                                    name = u.text.lineSequence().firstOrNull()?.take(40) ?: u.text.take(40),
                                    room = "", weeks = "1-25",
                                )
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${dayName(u.day)} ${Timetable.slotLabels.getOrElse(u.slot) { "?" }} · 点此补录",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(u.text.replace("\n", " ").take(80), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("手动补录")
            }
            Button(
                enabled = courses.any { it.checked },
                onClick = { onConfirm(semesterStart, courses.filter { it.checked }) },
                modifier = Modifier.weight(1f),
            ) {
                Text("导入 ${courses.count { it.checked }} 条")
            }
        }
    }

    if (showStartPicker) {
        val st = rememberDatePickerState(initialSelectedDateMillis = semesterStart.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { semesterStart = pickerMillisToDate(it) }
                    showStartPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("取消") } },
        ) { DatePicker(state = st) }
    }
    if (editing != null) {
        CourseEditDialog(
            initial = editing,
            onDismiss = { editing = null },
            onConfirm = { updated ->
                val idx = courses.indexOf(editing)
                if (idx >= 0) courses[idx] = updated
                editing = null
            },
        )
    }
    if (adding) {
        CourseEditDialog(
            initial = null,
            onDismiss = { adding = false },
            onConfirm = { courses.add(it.copy(checked = true)); adding = false },
        )
    }
    prefill?.let { (idx, preCourse) ->
        CourseEditDialog(
            initial = preCourse,
            onDismiss = { prefill = null },
            onConfirm = { updated ->
                courses.add(updated.copy(checked = true))
                if (idx in unparsedList.indices) unparsedList.removeAt(idx)
                prefill = null
            },
        )
    }
}
