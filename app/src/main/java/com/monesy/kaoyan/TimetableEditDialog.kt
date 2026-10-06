package com.monesy.kaoyan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 课程添加/编辑对话框（课表管理与导入预览页共用） */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CourseEditDialog(
    initial: TimetableParser.Course?,
    onDismiss: () -> Unit,
    onConfirm: (TimetableParser.Course) -> Unit,
) {
    var day by remember { mutableIntStateOf(initial?.day ?: 1) }
    var slot by remember { mutableIntStateOf(initial?.slot ?: 0) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var room by remember { mutableStateOf(initial?.room ?: "") }
    var weeks by remember { mutableStateOf(initial?.weeks ?: "1-16") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加课程" else "编辑课程", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("星期", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekDayNames.forEachIndexed { i, n ->
                        FilterChip(selected = day == i + 1, onClick = { day = i + 1 }, label = { Text(n, fontSize = 12.sp) })
                    }
                }
                Text("节次", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Timetable.slotLabels.forEachIndexed { i, l ->
                        FilterChip(selected = slot == i, onClick = { slot = i }, label = { Text(l, fontSize = 12.sp) })
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("课程名") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = room, onValueChange = { room = it },
                    label = { Text("教室（可留空）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = weeks, onValueChange = { weeks = it },
                    label = { Text("周次（如 1-16 或 1-4,6-15）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = weeks.isBlank() || TimetableParser.normalizeWeeks(weeks).isBlank(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && TimetableParser.normalizeWeeks(weeks).isNotBlank(),
                onClick = {
                    onConfirm(
                        TimetableParser.Course(
                            day = day, slot = slot,
                            name = name.trim(),
                            room = room.trim(),
                            weeks = TimetableParser.normalizeWeeks(weeks),
                        )
                    )
                },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 课表数据（List<Course>）→ timetable 段 JSON */
fun buildTimetableJson(semesterStart: java.time.LocalDate, courses: List<TimetableParser.Course>): org.json.JSONObject {
    val byDay = courses.groupBy { it.day }
    val entries = org.json.JSONObject()
    for (d in 1..7) {
        val list = byDay[d] ?: continue
        val arr = org.json.JSONArray()
        list.sortedBy { it.slot }.forEach { c ->
            arr.put(
                org.json.JSONObject()
                    .put("slot", c.slot).put("name", c.name)
                    .put("room", c.room).put("weeks", c.weeks)
            )
        }
        entries.put(dayOfWeekName(d), arr)
    }
    return org.json.JSONObject()
        .put("semesterStart", semesterStart.toString())
        .put("slotLabels", org.json.JSONArray(Timetable.slotLabels))
        .put("entries", entries)
}

private fun dayOfWeekName(day: Int): String = listOf(
    "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY",
).getOrElse(day - 1) { "MONDAY" }
