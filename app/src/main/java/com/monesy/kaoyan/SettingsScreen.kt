package com.monesy.kaoyan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    store: Store,
    settings: Settings,
    themeMode: String,
    themeDarkStart: String,
    themeDarkEnd: String,
    onThemeChange: (String) -> Unit,
    onDarkStartChange: (String) -> Unit,
    onDarkEndChange: (String) -> Unit,
    onOpenConfig: () -> Unit,
    onOpenTutorial: () -> Unit = {},
    onOpenWhatsNew: () -> Unit = {},
    onOpenFaq: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var showMorningTime by remember { mutableStateOf(false) }
    var showEveningTime by remember { mutableStateOf(false) }
    var showDarkStart by remember { mutableStateOf(false) }
    var showDarkEnd by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var nodeRemindOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { nodeRemindOn = store.nodeRemindersEnabled.first() }

    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = it
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifGranted = Build.VERSION.SDK_INT < 33 ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun change(block: suspend () -> Unit) {
        scope.launch {
            block()
            Notify.scheduleAll(context)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // ---- 外观 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("外观", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("light" to "浅色", "dark" to "深色", "scheduled" to "定时").forEach { (value, label) ->
                        FilterChip(
                            selected = themeMode == value,
                            onClick = { onThemeChange(value) },
                            label = { Text(label) },
                        )
                    }
                }
                if (themeMode == "scheduled") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { showDarkStart = true }) { Text("开深色：$themeDarkStart") }
                        TextButton(onClick = { showDarkEnd = true }) { Text("关深色：$themeDarkEnd") }
                    }
                    Text(
                        "每天到点自动切换（支持跨夜，如 22:00 开、07:00 关）",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- 通知提醒 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("每日通知", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "通知内容会自动附上最近的重大节点倒计时",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("早晨 · 单词提醒", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "每天 ${settings.morningTime.format(DateTimeFormatter.ofPattern("HH:mm"))} · 点击修改时间",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clickable { showMorningTime = true },
                        )
                    }
                    Switch(
                        checked = settings.morningEnabled,
                        onCheckedChange = { on -> change { store.setMorningEnabled(on) } },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("晚上 · 自习提醒", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "每天 ${settings.eveningTime.format(DateTimeFormatter.ofPattern("HH:mm"))} · 点击修改时间",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clickable { showEveningTime = true },
                        )
                    }
                    Switch(
                        checked = settings.eveningEnabled,
                        onCheckedChange = { on -> change { store.setEveningEnabled(on) } },
                    )
                }

                Spacer(Modifier.padding(top = 8.dp))
                if (notifGranted) {
                    Text("✅ 通知权限已授予", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                } else {
                    Button(onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("授予通知权限（必需）")
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("重大节点临近提醒", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "报名、模考、初试等节点提前 7 天 / 3 天 / 当天上午 9 点推送",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = nodeRemindOn,
                        onCheckedChange = { on ->
                            nodeRemindOn = on
                            scope.launch {
                                store.setNodeRemindersEnabled(on)
                                Notify.scheduleNodeReminders(context)
                            }
                        },
                    )
                }
            }
        }

        // ---- 考试日期 ----
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("初试日期（预计）", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${Config.examDate.year}年${Config.examDate.monthValue}月${Config.examDate.dayOfMonth}日 · 官方目录公布后如有变动请在此修改（与计划、倒计时同步）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showDatePicker = true }) { Text("修改") }
            }
        }

        // ---- 配置管理入口 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "课表导入（Excel/手动）、计划模板、考试信息、导入导出、节假日",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenConfig) { Text("打开配置管理") }
            }
        }

        // ---- 帮助 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("帮助", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "功能教程与常见问题，随时可以重看",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(onClick = onOpenTutorial) { Text("使用教程") }
                    OutlinedButton(onClick = onOpenWhatsNew) { Text("更新内容") }
                    OutlinedButton(onClick = onOpenFaq) { Text("常见问题") }
                }
            }
        }

        // ---- 关于 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("关于", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(Config.aboutGoal, fontSize = 13.sp)
                Text(Config.aboutSubjects, fontSize = 13.sp)
                Text(Config.aboutTarget, fontSize = 13.sp)
                Text(Config.aboutSource, fontSize = 13.sp)
                Text("${Config.appTitle} v1.5.0 · 全离线运行，数据只存在本机", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.feedbackUrl)))
                        }
                    }) { Text("GitHub Issues") }
                    TextButton(onClick = {
                        // 优先拉起邮件客户端；没有则复制邮箱到剪贴板
                        val ok = runCatching {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Config.feedbackEmail}")))
                        }.isSuccess
                        if (!ok) {
                            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("email", Config.feedbackEmail))
                            android.widget.Toast.makeText(context, "已复制邮箱 ${Config.feedbackEmail}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("✉ 邮件反馈") }
                }
            }
        }

        // ---- 通知保活提示（通用，放最后） ----
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("📌 收不到定时提醒？", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                Text(
                    "多数安卓系统默认限制应用在后台运行。请在 系统设置 → 电池 / 应用管理 中允许本应用「后台运行 / 自启动」" +
                        "（vivo/OPPO 一般在「电池」里，小米在「省电与电池」，华为在「应用启动管理」），并在最近任务里给卡片加锁，" +
                        "否则通知可能被延迟。",
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    // ---- 时间选择弹窗 ----
    if (showMorningTime) {
        val st = rememberTimePickerState(settings.morningTime.hour, settings.morningTime.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showMorningTime = false },
            title = { Text("早晨提醒时间") },
            text = { TimePicker(state = st) },
            confirmButton = {
                TextButton(onClick = {
                    change { store.setMorningTime(LocalTime.of(st.hour, st.minute)) }
                    showMorningTime = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showMorningTime = false }) { Text("取消") } },
        )
    }
    if (showEveningTime) {
        val st = rememberTimePickerState(settings.eveningTime.hour, settings.eveningTime.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showEveningTime = false },
            title = { Text("晚上提醒时间") },
            text = { TimePicker(state = st) },
            confirmButton = {
                TextButton(onClick = {
                    change { store.setEveningTime(LocalTime.of(st.hour, st.minute)) }
                    showEveningTime = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showEveningTime = false }) { Text("取消") } },
        )
    }
    if (showDarkStart) {
        val init = runCatching { LocalTime.parse(themeDarkStart) }.getOrDefault(LocalTime.of(22, 0))
        val st = rememberTimePickerState(init.hour, init.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showDarkStart = false },
            title = { Text("每天开深色的时间") },
            text = { TimePicker(state = st) },
            confirmButton = {
                TextButton(onClick = {
                    onDarkStartChange("%02d:%02d".format(st.hour, st.minute))
                    showDarkStart = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDarkStart = false }) { Text("取消") } },
        )
    }
    if (showDarkEnd) {
        val init = runCatching { LocalTime.parse(themeDarkEnd) }.getOrDefault(LocalTime.of(7, 0))
        val st = rememberTimePickerState(init.hour, init.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showDarkEnd = false },
            title = { Text("每天关深色的时间") },
            text = { TimePicker(state = st) },
            confirmButton = {
                TextButton(onClick = {
                    onDarkEndChange("%02d:%02d".format(st.hour, st.minute))
                    showDarkEnd = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDarkEnd = false }) { Text("取消") } },
        )
    }
    if (showDatePicker) {
        val st = rememberDatePickerState(
            initialSelectedDateMillis = Config.examDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { millis ->
                        val d = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        // 初试日期单一真源：写用户配置层并重载，倒计时/计划/节点提醒全部同步
                        scope.launch {
                            val app = UserConfig.readSection(store, UserConfig.SECTION_APP) ?: org.json.JSONObject()
                            app.put("examDate", d.toString())
                            UserConfig.putSection(store, UserConfig.SECTION_APP, app)
                            ConfigLoader.load(context)
                            Notify.scheduleNodeReminders(context)
                            (context as? android.app.Activity)?.recreate()
                        }
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = st)
        }
    }
}
