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

/** 课表管理：查看/增删改课程 + 学期起始日 + Excel/剪贴板/图片导入入口；保存后写用户配置层并重载 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableManageScreen(
    onBack: () -> Unit,
    onImportExcel: () -> Unit,
    onImportImage: () -> Unit,
    onImportClipboard: () -> Unit,
    onSave: (semesterStart: LocalDate, courses: List<TimetableParser.Course>) -> Unit,
) {
    val courses = remember {
        mutableStateListOf<TimetableParser.Course>().apply {
            for (day in DayOfWeek.values()) {
                Timetable.allEntries()[day]?.forEach { e ->
                    add(
                        TimetableParser.Course(
                            day = day.value, slot = e.slot, name = e.name,
                            room = e.room, weeks = Timetable.formatWeeks(e.weeks),
                        )
                    )
                }
            }
        }
    }

    var semesterStart by remember { mutableStateOf(Timetable.SEMESTER_START) }
    var showStartPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TimetableParser.Course?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showImageTip by remember { mutableStateOf(false) }
    var showClipboardTip by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
            Text("课表管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("学期第 1 周周一：", fontSize = 14.sp)
            TextButton(onClick = { showStartPicker = true }) {
                Text("${semesterStart.year}年${semesterStart.monthValue}月${semesterStart.dayOfMonth}日 · 修改")
            }
        }
        Text(
            "教学周数按此日期推算（各校校历不同，请按你学校的实际开学周设置）",
            fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("共 ${courses.size} 门次课程；改动点下方「保存」后生效", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(courses) { i, c ->
                Card(Modifier.fillMaxWidth().clickable { editing = c }) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${dayName(c.day)} ${Timetable.slotLabels.getOrElse(c.slot) { "?" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text(c.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                            Text(
                                listOfNotNull(c.room.ifBlank { null }, c.weeks.takeIf { it.isNotBlank() }?.let { "第 $it 周" }).joinToString(" · "),
                                fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { courses.removeAt(i) }) {
                            Icon(Icons.Default.Close, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Text(
            "导入方式",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onImportExcel, modifier = Modifier.weight(1f)) { Text("Excel", fontSize = 13.sp, maxLines = 1) }
            OutlinedButton(onClick = { showClipboardTip = true }, modifier = Modifier.weight(1f)) { Text("剪贴板", fontSize = 13.sp, maxLines = 1) }
            OutlinedButton(onClick = { showImageTip = true }, modifier = Modifier.weight(1f)) { Text("图片", fontSize = 13.sp, maxLines = 1) }
        }
        Text(
            "推荐「剪贴板」：浏览器打开课表页，长按表格 → 全选 → 复制，最准；Excel 适合教务能导出的场景；图片适合小程序 / 截图。结果都在预览页逐条核对",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("添加")
            }
            Button(onClick = { onSave(semesterStart, courses) }, modifier = Modifier.weight(1f)) { Text("保存") }
        }
    }

    if (showClipboardTip) {
        AlertDialog(
            onDismissRequest = { showClipboardTip = false },
            title = { Text("从剪贴板导入课表") },
            text = {
                Text(
                    "最准的导入方式（推荐），三步：\n\n" +
                        "① 手机浏览器登录教务系统，打开能看见整张课表的页面\n" +
                        "② 手指按住课表表格里的文字不放，弹出菜单里点「全选」\n" +
                        "③ 点「复制」\n\n" +
                        "⚠️ 复制的是屏幕上那张课表表格，不是网页地址。\n" +
                        "不确定复制对没有？先粘贴到微信输入框看一眼：出现课名、教室等课表文字就对了。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showClipboardTip = false; onImportClipboard() }) { Text("读取剪贴板") }
            },
            dismissButton = {
                TextButton(onClick = { showClipboardTip = false }) { Text("取消") }
            },
        )
    }
    if (showImageTip) {
        AlertDialog(
            onDismissRequest = { showImageTip = false },
            title = { Text("导入图片课表") },
            text = {
                Text(
                    "请使用学校原始课表的完整截图，识别效果最好：\n" +
                        "· 教务系统 / 课表小程序 / 课表 App 的正屏截图（含星期表头和节次列）\n" +
                        "· 清晰、不拍照、不斜截、不要只截一半\n" +
                        "· 截图里没有周次信息的课程会默认按全学期导入\n\n" +
                        "识别在手机本地完成，图片不会上传。导入后请在预览页逐条核对修改。",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showImageTip = false; onImportImage() }) { Text("选择图片") }
            },
            dismissButton = {
                TextButton(onClick = { showImageTip = false }) { Text("取消") }
            },
        )
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
        CourseEditDialog(initial = null, onDismiss = { adding = false }, onConfirm = { courses.add(it); adding = false })
    }
}
