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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import java.util.UUID

/** 计划编辑器：阶段 / 日任务模板 / 重大节点的表单化增删改；JSON 导入导出通道仍然保留。 */

data class TaskEdit(
    val id: String, val title: String,
    val minutes: Int, val minMinutes: Int, val maxMinutes: Int,
    val bottomLine: Boolean,
)

data class StageEdit(
    val id: Int, val name: String,
    val start: LocalDate, val end: LocalDate,
    val hoursPerWeek: String, val goal: String,
    val cores: List<String>, val acceptance: List<String>,
    val decisive: Boolean,
    val weekday: List<TaskEdit>, val saturday: List<TaskEdit>, val sunday: List<TaskEdit>,
)

data class NodeEdit(
    val date: LocalDate, val title: String, val detail: String,
    val kind: NodeKind, val soft: Boolean = false,
)

private fun PlanTask.toEdit() = TaskEdit(id, title, minutes, minMinutes, maxMinutes, isBottomLine)

private fun Stage.toEdit() = StageEdit(
    id, name, start, end, hoursPerWeek, goal, coreTasks, acceptance, isDecisive,
    weekdayTasks.map { it.toEdit() }, saturdayTasks.map { it.toEdit() }, sundayTasks.map { it.toEdit() },
)

private fun KeyNode.toEdit() = NodeEdit(date, title, detail, kind, soft)

private fun kindName(k: NodeKind) = when (k) {
    NodeKind.STUDY -> "学习"; NodeKind.INFO -> "信息"; NodeKind.EXAM -> "考试"
}

fun buildPlanJson(stages: List<StageEdit>, nodes: List<NodeEdit>): JSONObject {
    fun taskJson(t: TaskEdit) = JSONObject().put("id", t.id).put("title", t.title).put("minutes", t.minutes).apply {
        if (t.minMinutes > 0 && t.maxMinutes > 0) { put("minMinutes", t.minMinutes); put("maxMinutes", t.maxMinutes) }
        if (t.bottomLine) put("bottomLine", true)
    }
    val sArr = JSONArray()
    stages.forEach { s ->
        sArr.put(
            JSONObject()
                .put("id", s.id).put("name", s.name)
                .put("dateText", "${s.start} – ${s.end}")
                .put("start", s.start.toString()).put("end", s.end.toString())
                .put("hoursPerWeek", s.hoursPerWeek).put("goal", s.goal)
                .put("coreTasks", JSONArray(s.cores)).put("acceptance", JSONArray(s.acceptance))
                .put("decisive", s.decisive)
                .put("weekdayTasks", JSONArray(s.weekday.map { taskJson(it) }))
                .put("saturdayTasks", JSONArray(s.saturday.map { taskJson(it) }))
                .put("sundayTasks", JSONArray(s.sunday.map { taskJson(it) })),
        )
    }
    val nArr = JSONArray()
    nodes.forEach { n ->
        nArr.put(
            JSONObject().put("date", n.date.toString()).put("title", n.title).put("detail", n.detail)
                .put("kind", n.kind.name)
                .apply { if (n.soft) put("soft", true) }
        )
    }
    return JSONObject().put("stages", sArr).put("keyNodes", nArr)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorScreen(onBack: () -> Unit, onSave: (JSONObject) -> Unit) {
    val stages = remember { mutableStateListOf<StageEdit>().apply { Plan.stages.forEach { add(it.toEdit()) } } }
    val nodes = remember { mutableStateListOf<NodeEdit>().apply { Plan.keyNodes.forEach { add(it.toEdit()) } } }
    var editingStage by remember { mutableIntStateOf(-1) }
    var editingNode by remember { mutableIntStateOf(-1) }
    var addingStage by remember { mutableStateOf(false) }
    var addingNode by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
            Text("计划编辑器", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = { onSave(buildPlanJson(stages, nodes)) }) { Text("保存") }
        }
        Text(
            "阶段、日任务模板与重大节点都可编辑，保存后立即生效（只影响计划，不动课表）。需要批量交换时可继续用 JSON 导入/导出。",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("阶段", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { addingStage = true }) { Text("+ 添加阶段") }
                }
            }
            itemsIndexed(stages) { i, s ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${s.id}. ${s.name}", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${s.start} – ${s.end} · 平日任务 ${s.weekday.size} 个",
                                fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { editingStage = i }) { Icon(Icons.Default.Edit, contentDescription = "编辑") }
                        IconButton(onClick = { stages.removeAt(i) }) {
                            Icon(Icons.Default.Close, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("重大节点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { addingNode = true }) { Text("+ 添加节点") }
                }
            }
            itemsIndexed(nodes) { i, n ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(n.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${n.date} · ${kindName(n.kind)}" + if (n.soft) " · 时间待定" else "",
                                fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { editingNode = i }) { Icon(Icons.Default.Edit, contentDescription = "编辑") }
                        IconButton(onClick = { nodes.removeAt(i) }) {
                            Icon(Icons.Default.Close, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (addingStage) {
        StageEditDialog(initial = null, onDismiss = { addingStage = false }) {
            stages.add(it); addingStage = false
        }
    }
    if (editingStage >= 0) {
        val idx = editingStage
        StageEditDialog(initial = stages[idx], onDismiss = { editingStage = -1 }) {
            stages[idx] = it; editingStage = -1
        }
    }
    if (addingNode) {
        NodeEditDialog(initial = null, onDismiss = { addingNode = false }) {
            nodes.add(it); addingNode = false
        }
    }
    if (editingNode >= 0) {
        val idx = editingNode
        NodeEditDialog(initial = nodes[idx], onDismiss = { editingNode = -1 }) {
            nodes[idx] = it; editingNode = -1
        }
    }
}

// ---------- 阶段编辑 ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StageEditDialog(initial: StageEdit?, onDismiss: () -> Unit, onConfirm: (StageEdit) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var start by remember { mutableStateOf(initial?.start ?: LocalDate.now()) }
    var end by remember { mutableStateOf(initial?.end ?: LocalDate.now().plusMonths(3)) }
    var hours by remember { mutableStateOf(initial?.hoursPerWeek ?: "15–25 小时/周") }
    var goal by remember { mutableStateOf(initial?.goal ?: "") }
    var decisive by remember { mutableStateOf(initial?.decisive ?: false) }
    var cores by remember { mutableStateOf(initial?.cores ?: listOf("")) }
    var acc by remember { mutableStateOf(initial?.acceptance ?: listOf("")) }
    var weekday by remember { mutableStateOf(initial?.weekday ?: emptyList()) }
    var saturday by remember { mutableStateOf(initial?.saturday ?: emptyList()) }
    var sunday by remember { mutableStateOf(initial?.sunday ?: emptyList()) }
    var pickDate by remember { mutableStateOf<String?>(null) }
    var taskGroup by remember { mutableIntStateOf(-1) }
    var taskIdx by remember { mutableIntStateOf(-1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加阶段" else "编辑阶段") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("阶段名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { pickDate = "s" }) { Text("开始：$start") }
                TextButton(onClick = { pickDate = "e" }) { Text("结束：$end") }
                OutlinedTextField(value = hours, onValueChange = { hours = it }, label = { Text("每周投入（如 15–25 小时/周）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = goal, onValueChange = { goal = it }, label = { Text("本阶段唯一目标") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("决定性阶段", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = decisive, onCheckedChange = { decisive = it })
                }

                Text("核心任务", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                cores.forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = c,
                            onValueChange = { v -> cores = cores.toMutableList().also { it[i] = v } },
                            modifier = Modifier.weight(1f), singleLine = true,
                        )
                        IconButton(onClick = { cores = cores.toMutableList().also { it.removeAt(i) } }) {
                            Icon(Icons.Default.Close, contentDescription = "删除")
                        }
                    }
                }
                TextButton(onClick = { cores = cores + "" }) { Text("+ 添加核心任务") }

                Text("验收标准", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary)
                acc.forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = c,
                            onValueChange = { v -> acc = acc.toMutableList().also { it[i] = v } },
                            modifier = Modifier.weight(1f), singleLine = true,
                        )
                        IconButton(onClick = { acc = acc.toMutableList().also { it.removeAt(i) } }) {
                            Icon(Icons.Default.Close, contentDescription = "删除")
                        }
                    }
                }
                TextButton(onClick = { acc = acc + "" }) { Text("+ 添加验收标准") }

                TaskGroupEditor("平日任务（周一至周五）", weekday,
                    onEdit = { i -> taskGroup = 0; taskIdx = i },
                    onDelete = { i -> weekday = weekday.toMutableList().also { it.removeAt(i) } },
                    onAdd = { taskGroup = 0; taskIdx = -1 })
                TaskGroupEditor("周六任务", saturday,
                    onEdit = { i -> taskGroup = 1; taskIdx = i },
                    onDelete = { i -> saturday = saturday.toMutableList().also { it.removeAt(i) } },
                    onAdd = { taskGroup = 1; taskIdx = -1 })
                TaskGroupEditor("周日任务", sunday,
                    onEdit = { i -> taskGroup = 2; taskIdx = i },
                    onDelete = { i -> sunday = sunday.toMutableList().also { it.removeAt(i) } },
                    onAdd = { taskGroup = 2; taskIdx = -1 })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !end.isBefore(start),
                onClick = {
                    val nextId = initial?.id ?: ((Plan.stages.maxOfOrNull { it.id } ?: 0) + 1)
                    onConfirm(
                        StageEdit(
                            id = nextId, name = name.trim(), start = start, end = end,
                            hoursPerWeek = hours.trim(), goal = goal.trim(),
                            cores = cores.filter { it.isNotBlank() }, acceptance = acc.filter { it.isNotBlank() },
                            decisive = decisive,
                            weekday = weekday, saturday = saturday, sunday = sunday,
                        )
                    )
                },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (pickDate != null) {
        val initialDate = if (pickDate == "s") start else end
        val st = rememberDatePickerState(initialSelectedDateMillis = initialDate.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { pickDate = null },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { m ->
                        if (pickDate == "s") start = pickerMillisToDate(m) else end = pickerMillisToDate(m)
                    }
                    pickDate = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { pickDate = null }) { Text("取消") } },
        ) { DatePicker(state = st) }
    }

    if (taskGroup >= 0) {
        val list = when (taskGroup) { 0 -> weekday; 1 -> saturday; else -> sunday }
        TaskEditDialog(
            initial = if (taskIdx >= 0) list[taskIdx] else null,
            onDismiss = { taskGroup = -1; taskIdx = -1 },
        ) { t ->
            val nl = list.toMutableList()
            if (taskIdx >= 0) nl[taskIdx] = t else nl.add(t)
            when (taskGroup) { 0 -> weekday = nl; 1 -> saturday = nl; else -> sunday = nl }
            taskGroup = -1; taskIdx = -1
        }
    }
}

@Composable
private fun TaskGroupEditor(
    label: String,
    tasks: List<TaskEdit>,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.tertiary)
    tasks.forEachIndexed { i, t ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.title, fontSize = 13.sp)
                Text(
                    fmtMinutes(t.minutes) +
                        if (t.maxMinutes > 0) "（可调 ${fmtMinutes(t.minMinutes)}–${fmtMinutes(t.maxMinutes)}）" else "（固定）" +
                        if (t.bottomLine) " · 底线" else "",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onEdit(i) }) { Icon(Icons.Default.Edit, contentDescription = "编辑") }
            IconButton(onClick = { onDelete(i) }) { Icon(Icons.Default.Close, contentDescription = "删除") }
        }
    }
    TextButton(onClick = onAdd) { Text("+ 添加任务") }
}

// ---------- 任务编辑 ----------

@Composable
private fun TaskEditDialog(initial: TaskEdit?, onDismiss: () -> Unit, onConfirm: (TaskEdit) -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var minutes by remember { mutableStateOf((initial?.minutes ?: 30).toString()) }
    var adjustable by remember { mutableStateOf((initial?.maxMinutes ?: 0) > 0) }
    var lo by remember { mutableStateOf((initial?.minMinutes ?: 60).toString()) }
    var hi by remember { mutableStateOf((initial?.maxMinutes ?: 150).toString()) }
    var bottom by remember { mutableStateOf(initial?.bottomLine ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加任务" else "编辑任务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("任务标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                    label = { Text("默认时长（分钟）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("允许用户调时长", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = adjustable, onCheckedChange = { adjustable = it })
                }
                if (adjustable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lo, onValueChange = { lo = it.filter { c -> c.isDigit() } },
                            label = { Text("最小(分)") }, singleLine = true, modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = hi, onValueChange = { hi = it.filter { c -> c.isDigit() } },
                            label = { Text("最大(分)") }, singleLine = true, modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("底线任务（不可断）", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = bottom, onCheckedChange = { bottom = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val m = minutes.toIntOrNull() ?: 30
                    val loV = if (adjustable) (lo.toIntOrNull() ?: 60) else 0
                    val hiV = if (adjustable) (hi.toIntOrNull() ?: 150) else 0
                    onConfirm(
                        TaskEdit(
                            id = initial?.id ?: "t" + UUID.randomUUID().toString().take(8),
                            title = title.trim(), minutes = m,
                            minMinutes = if (adjustable) minOf(loV, m) else 0,
                            maxMinutes = if (adjustable) maxOf(hiV, m) else 0,
                            bottomLine = bottom,
                        )
                    )
                },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ---------- 节点编辑 ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeEditDialog(initial: NodeEdit?, onDismiss: () -> Unit, onConfirm: (NodeEdit) -> Unit) {
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var detail by remember { mutableStateOf(initial?.detail ?: "") }
    var kind by remember { mutableStateOf(initial?.kind ?: NodeKind.INFO) }
    var soft by remember { mutableStateOf(initial?.soft ?: false) }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加节点" else "编辑节点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showPicker = true }) { Text("日期：$date") }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("节点标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = detail, onValueChange = { detail = it }, label = { Text("说明") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NodeKind.values().forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(kindName(k), fontSize = 12.sp) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("时间待定", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "适用于复试等由院校决定时间的节点：只做范围提醒，不显示硬倒计时",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = soft, onCheckedChange = { soft = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onConfirm(NodeEdit(date, title.trim(), detail.trim(), kind, soft)) },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (showPicker) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { date = pickerMillisToDate(it) }
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
        ) { DatePicker(state = st) }
    }
}
