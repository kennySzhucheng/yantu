package com.monesy.kaoyan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val store = remember { Store(context) }
            val themeMode by store.themeMode.collectAsState(initial = "light")
            val darkStart by store.themeDarkStart.collectAsState(initial = "22:00")
            val darkEnd by store.themeDarkEnd.collectAsState(initial = "07:00")
            val dark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> scheduledDarkNow(darkStart, darkEnd)
            }
            KaoyanTheme(dark) {
                AppRoot()
            }
        }
    }
}

/** 当前生效的深色状态（应用外观设置的结果） */
val LocalIsDark = staticCompositionLocalOf { false }

@Composable
fun KaoyanTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            content = content,
        )
    }
}

/** 定时模式：按 HH:mm 时段计算当前是否深色（支持跨夜，如 22:00–07:00） */
private fun computeScheduledDark(start: String, end: String): Boolean {
    val s = runCatching { LocalTime.parse(start) }.getOrNull() ?: return false
    val e = runCatching { LocalTime.parse(end) }.getOrNull() ?: return false
    val n = LocalTime.now()
    return if (!s.isAfter(e)) (n >= s && n < e) else (n >= s || n < e)
}

/** 到下一个切换点还有多少毫秒（用于定时自动切换） */
private fun millisUntilNextThemeBoundary(start: String, end: String): Long {
    val s = runCatching { LocalTime.parse(start) }.getOrNull() ?: return 60_000L
    val e = runCatching { LocalTime.parse(end) }.getOrNull() ?: return 60_000L
    val now = LocalTime.now()
    fun nextMs(t: LocalTime): Long {
        var d = java.time.Duration.between(now, t).toMillis()
        if (d <= 0) d += 24L * 3600_000L
        return d
    }
    return minOf(nextMs(s), nextMs(e))
}

/** 定时深色状态（跨过切换点时自动重组刷新） */
@Composable
private fun scheduledDarkNow(start: String, end: String): Boolean {
    var dark by remember { mutableStateOf(computeScheduledDark(start, end)) }
    LaunchedEffect(start, end) {
        while (true) {
            dark = computeScheduledDark(start, end)
            delay(millisUntilNextThemeBoundary(start, end).coerceAtLeast(5_000L) + 500L)
        }
    }
    return dark
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A4F8B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF1F9),
    onPrimaryContainer = Color(0xFF0F3355),
    secondary = Color(0xFF1E7A4D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F4EE),
    onSecondaryContainer = Color(0xFF14502F),
    tertiary = Color(0xFF9A6A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFDF4E2),
    onTertiaryContainer = Color(0xFF5C4000),
    error = Color(0xFFC0392B),
    errorContainer = Color(0xFFFDECEB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC3F0),
    onPrimary = Color(0xFF0E3049),
    primaryContainer = Color(0xFF1E3A5C),
    onPrimaryContainer = Color(0xFFD3E4F7),
    secondary = Color(0xFF8FD3B0),
    onSecondary = Color(0xFF0E3D26),
    secondaryContainer = Color(0xFF1E4A34),
    onSecondaryContainer = Color(0xFFCFEEDC),
    tertiary = Color(0xFFD9B36A),
    onTertiary = Color(0xFF4A3410),
    tertiaryContainer = Color(0xFF4A3A18),
    onTertiaryContainer = Color(0xFFF4E3C2),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF5C2B28),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF17191E),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF33363B),
    onSurfaceVariant = Color(0xFFC4C6CC),
)

@Composable
fun AppRoot() {
    val context = LocalContext.current

    // 配置加载失败：显示可读错误而不是白屏/崩溃
    if (Config.loadError != null) {
        Box(Modifier.padding(24.dp)) {
            Text(
                "配置加载失败：${Config.loadError}\n\n请检查 assets/config 下的 JSON 配置文件。",
                color = MaterialTheme.colorScheme.error,
            )
        }
        return
    }

    val store = remember { Store(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 引导分两套：全新用户（向导完成）看完整教程；升级用户（有数据、教程版本旧）只看「更新内容」速览
    val onboardedInitial = remember { runBlocking { store.onboarded.first() } }
    val onboarded by store.onboarded.collectAsState(initial = onboardedInitial)
    val tutVerInitial = remember { runBlocking { store.tutorialVersion.first() } }
    var showTutorial by remember { mutableStateOf(false) }
    var showWhatsNew by remember { mutableStateOf(onboardedInitial && tutVerInitial < TUTORIAL_VERSION) }
    var wizardRerun by remember { mutableStateOf(false) }
    if (!onboarded) {
        OnboardingScreen(store = store, isRerun = wizardRerun) {
            if (!wizardRerun) showTutorial = true
            scope.launch {
                ConfigLoader.load(context)
                store.setOnboarded(true)
            }
        }
        return
    }
    if (showWhatsNew) {
        WhatsNewScreen(onDone = {
            showWhatsNew = false
            scope.launch { store.setTutorialVersion(TUTORIAL_VERSION) }
        })
        return
    }
    if (showTutorial) {
        TutorialScreen(onDone = {
            showTutorial = false
            scope.launch { store.setTutorialVersion(TUTORIAL_VERSION) }
        })
        return
    }

    // 子页面路由（配置管理 / 课表管理 / 导入预览）+ 文件选择器
    var subScreen by remember { mutableStateOf<String?>(null) }
    var parsedCourses by remember { mutableStateOf<List<TimetableParser.Course>>(emptyList()) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var parseUnparsed by remember { mutableStateOf<List<TimetableParser.UnparsedCell>>(emptyList()) }
    var ocrBusy by remember { mutableStateOf(false) }

    // 子页面支持系统返回键逐级返回，而不是直接退出应用
    BackHandler(enabled = subScreen != null) {
        subScreen = when (subScreen) {
            "importPreview" -> "timetable"
            "timetable", "planEditor" -> "config"
            else -> null
        }
    }

    fun reloadAfterConfigChange() {
        ConfigLoader.load(context)
        scope.launch { Notify.scheduleNodeReminders(context) }
        (context as? android.app.Activity)?.recreate()
    }

    val excelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        TimetableParser.parse(ExcelReader.readGrid(context, uri))
                    }.getOrElse { e ->
                        TimetableParser.Result(mutableListOf(), "解析失败：${e.message ?: "未知错误"}")
                    }
                }
                parsedCourses = result.courses
                parseError = result.error
                parseUnparsed = result.unparsed
                subScreen = "importPreview"
            }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                ocrBusy = true
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        TimetableParser.parse(OcrReader.readGrid(context, uri))
                    }.getOrElse { e ->
                        TimetableParser.Result(mutableListOf(), "识别失败：${e.message ?: "未知错误"}")
                    }
                }
                ocrBusy = false
                parsedCourses = result.courses
                parseError = result.error
                parseUnparsed = result.unparsed
                subScreen = "importPreview"
            }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(
                                UserConfig.exportMerged(context, store).toString(2)
                                    .toByteArray(Charsets.UTF_8)
                            )
                        }
                    }
                }
            }
        }
    }
    val templateExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(
                                PlanTemplate.buildBlankTemplate().toString(2)
                                    .toByteArray(Charsets.UTF_8)
                            )
                        }
                    }
                }
            }
        }
    }
    val backupExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(store.exportBackup().toString(2).toByteArray(Charsets.UTF_8))
                        }
                    }
                }
            }
        }
    }
    val backupImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val n = withContext(Dispatchers.IO) {
                    runCatching {
                        val text = context.contentResolver.openInputStream(uri)
                            ?.use { it.readBytes().toString(Charsets.UTF_8) }
                            ?: return@runCatching -1
                        store.importBackup(org.json.JSONObject(text))
                    }.getOrElse { -1 }
                }
                if (n >= 0) reloadAfterConfigChange()
            }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val text = context.contentResolver.openInputStream(uri)
                            ?.use { it.readBytes().toString(Charsets.UTF_8) }
                            ?: return@runCatching false
                        val json = org.json.JSONObject(text)
                        // 按段合并导入：只含 plan 的模板文件也能直接回填
                        val sections = listOf("app", "plan", "timetable", "holidays").filter { json.has(it) }
                        if (sections.isEmpty()) return@runCatching false
                        sections.forEach { s -> UserConfig.putSection(store, s, json.getJSONObject(s)) }
                        true
                    }.getOrElse { false }
                }
                if (ok) reloadAfterConfigChange()
            }
        }
    }

    // 首次启动申请通知权限（Android 13+）
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val settings by store.settings.collectAsState(initial = Settings())
    val themeMode by store.themeMode.collectAsState(initial = "light")
    val themeDarkStart by store.themeDarkStart.collectAsState(initial = "22:00")
    val themeDarkEnd by store.themeDarkEnd.collectAsState(initial = "07:00")
    val today = remember { LocalDate.now() }
    val checked by store.checkinFor(today).collectAsState(initial = emptySet())
    val yesterday = remember { today.minusDays(1) }
    val dayBefore = remember { today.minusDays(2) }
    val checkedYesterday by store.checkinFor(yesterday).collectAsState(initial = emptySet())
    val checkedDayBefore by store.checkinFor(dayBefore).collectAsState(initial = emptySet())
    val durations by store.durationOverrides.collectAsState(initial = emptyMap())
    val rewardJournal by store.rewardJournal.collectAsState(initial = emptyList())

    // 日历权限：用户在「今日日程」卡片里主动授权
    var calendarGranted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        )
    }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        calendarGranted = it
    }
    val calendarEvents by produceState<List<DayEvent>?>(initialValue = null, today, calendarGranted) {
        value = if (calendarGranted) runCatching { readDayEvents(context, today) }.getOrDefault(emptyList()) else null
    }

    var streak by remember { mutableIntStateOf(0) }
    var totalDays by remember { mutableIntStateOf(0) }
    var weekSummary by remember { mutableStateOf<Stats.WeekSummary?>(null) }
    // 上次看到的达标状态：奖励只在「未达标 → 达标」的边沿记录（首次进入对缺失记录补一次），
    // 用户手动删掉的今日奖励不会因继续勾选任务而"复活"
    var prevMet by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(checked) {
        streak = store.bottomLineStreak(today)
        totalDays = store.bottomTotalDays()
        weekSummary = Stats.weekSummary(store, today)

        // 双层奖励·日常：当天达标（底线任务 + 其余过半）记一条小奖励；跌破达标线同步撤回
        val stage = Plan.stageFor(today)
        val tasks = Plan.tasksFor(stage, today.dayOfWeek)
        val metNow = isDayMet(tasks, checked)
        val prev = prevMet
        if (prev == null || prev != metNow) {
            val hasReward = store.hasReward(today, 'D')
            if (metNow && !hasReward && Config.rewardDaily.isNotEmpty() && !store.isRewardSkipped(today, 'D')) {
                val pool = Config.rewardDaily
                val text = pool[store.rewardJournal.first().count { it.kind == 'D' } % pool.size]
                store.appendReward(today, 'D', text)
            } else if (!metNow && hasReward) {
                store.removeReward(today, 'D')
            }
        }
        prevMet = metNow
    }

    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val tabs = listOf(
                    "今日" to Icons.Default.Home,
                    "节点" to Icons.Default.DateRange,
                    "阶段" to Icons.Default.List,
                    "设置" to Icons.Default.Settings,
                )
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { subScreen = null; tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (subScreen) {
                "config" -> ConfigManageScreen(
                    store = store,
                    onBack = { subScreen = null },
                    onOpenTimetable = { subScreen = "timetable" },
                    onImportJson = { importPicker.launch(arrayOf("application/json", "*/*")) },
                    onExportJson = { exportPicker.launch("config.json") },
                    onRerunWizard = {
                        wizardRerun = true
                        scope.launch { store.setOnboarded(false) }
                    },
                    onUseExample = {
                        scope.launch {
                            UserConfig.removeSection(store, UserConfig.SECTION_PLAN)
                            reloadAfterConfigChange()
                        }
                    },
                    onExportTemplate = { templateExportPicker.launch("计划模板.json") },
                    onEditPlan = { subScreen = "planEditor" },
                    onExportBackup = { backupExportPicker.launch("日拱一卒数据备份.json") },
                    onImportBackup = { backupImportPicker.launch(arrayOf("application/json", "*/*")) },
                    onReset = {
                        scope.launch {
                            UserConfig.clear(store)
                            reloadAfterConfigChange()
                        }
                    },
                    onWriteSection = { section, json ->
                        scope.launch {
                            UserConfig.putSection(store, section, json)
                            reloadAfterConfigChange()
                        }
                    },
                )
                "timetable" -> TimetableManageScreen(
                    onBack = { subScreen = "config" },
                    onImportExcel = {
                        excelPicker.launch(
                            arrayOf(
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/octet-stream",
                                "*/*",
                            )
                        )
                    },
                    onImportImage = { imagePicker.launch(arrayOf("image/*")) },
                    onImportClipboard = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    ClipboardImporter.readCourses(context)
                                }.getOrElse { e ->
                                    TimetableParser.Result(mutableListOf(), "导入失败：${e.message ?: "未知错误"}")
                                }
                            }
                            parsedCourses = result.courses
                            parseError = result.error
                            parseUnparsed = result.unparsed
                            subScreen = "importPreview"
                        }
                    },
                    onSave = { start, list ->
                        scope.launch {
                            UserConfig.putSection(store, UserConfig.SECTION_TIMETABLE, buildTimetableJson(start, list))
                            reloadAfterConfigChange()
                        }
                    },
                )
                "importPreview" -> ImportPreviewScreen(
                    initialCourses = parsedCourses,
                    parseError = parseError,
                    unparsed = parseUnparsed,
                    onBack = { subScreen = "timetable" },
                    onConfirm = { start, list ->
                        scope.launch {
                            UserConfig.putSection(store, UserConfig.SECTION_TIMETABLE, buildTimetableJson(start, list))
                            ConfigLoader.load(context)
                            subScreen = null
                            (context as? android.app.Activity)?.recreate()
                        }
                    },
                )
                "planEditor" -> PlanEditorScreen(
                    onBack = { subScreen = "config" },
                    onSave = { json ->
                        scope.launch {
                            UserConfig.putSection(store, UserConfig.SECTION_PLAN, json)
                            reloadAfterConfigChange()
                        }
                    },
                )
                "tutorial" -> TutorialScreen(onDone = { subScreen = null; scope.launch { store.setTutorialVersion(TUTORIAL_VERSION) } })
                "whatsnew" -> WhatsNewScreen(onDone = { subScreen = null; scope.launch { store.setTutorialVersion(TUTORIAL_VERSION) } })
                "faq" -> FaqScreen(onBack = { subScreen = null })
                else -> when (tab) {
                0 -> TodayScreen(
                    today = today,
                    settings = settings,
                    checked = checked,
                    streak = streak,
                    totalDays = totalDays,
                    weekSummary = weekSummary,
                    durations = durations,
                    yesterday = yesterday,
                    checkedYesterday = checkedYesterday,
                    dayBefore = dayBefore,
                    checkedDayBefore = checkedDayBefore,
                    rewardJournal = rewardJournal,
                    onToggle = { taskId -> scope.launch { store.toggleCheckin(today, taskId) } },
                    onDurationChange = { taskId, minutes -> scope.launch { store.setDuration(taskId, minutes) } },
                    onToggleDate = { date, taskId ->
                        scope.launch {
                            store.toggleCheckin(date, taskId)
                            if ("makeup" !in store.checkinFor(date).first()) store.toggleCheckin(date, "makeup")
                        }
                    },
                    onRewardDelete = { e -> scope.launch { store.removeRewardManual(e.date, e.kind) } },
                    onRewardClear = { scope.launch { store.clearRewards() } },
                )
                1 -> MilestonesScreen(
                    today = today,
                    store = store,
                    calendarGranted = calendarGranted,
                    calendarEvents = calendarEvents,
                    rewardJournal = rewardJournal,
                    onRequestCalendar = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) },
                )
                2 -> StagesScreen(today = today, store = store, onSaveStageTasks = { stageId, wd, sat, sun ->
                    scope.launch {
                        // 写回用户计划层；当前若还在用内置示例计划，先把内置内容落到用户层再改
                        val plan = UserConfig.readSection(store, UserConfig.SECTION_PLAN)
                            ?: org.json.JSONObject(
                                context.assets.open("config/plan.json").bufferedReader().use { it.readText() }
                            )
                        val arr = plan.optJSONArray("stages")
                        if (arr != null) {
                            fun tasksJson(list: List<TaskEdit>): org.json.JSONArray {
                                val a = org.json.JSONArray()
                                list.forEach { t ->
                                    a.put(
                                        org.json.JSONObject()
                                            .put("id", t.id)
                                            .put("title", t.title)
                                            .put("minutes", t.minutes)
                                            .apply {
                                                if (t.minMinutes > 0 && t.maxMinutes > 0) {
                                                    put("minMinutes", t.minMinutes)
                                                    put("maxMinutes", t.maxMinutes)
                                                }
                                                if (t.bottomLine) put("bottomLine", true)
                                            },
                                    )
                                }
                                return a
                            }
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                if (o.optInt("id") == stageId) {
                                    o.put("weekdayTasks", tasksJson(wd))
                                    o.put("saturdayTasks", tasksJson(sat))
                                    o.put("sundayTasks", tasksJson(sun))
                                }
                            }
                            UserConfig.putSection(store, UserConfig.SECTION_PLAN, plan)
                            ConfigLoader.load(context)
                            (context as? android.app.Activity)?.recreate()
                        }
                    }
                })
                else -> SettingsScreen(
                    store = store, settings = settings,
                    themeMode = themeMode,
                    themeDarkStart = themeDarkStart,
                    themeDarkEnd = themeDarkEnd,
                    onThemeChange = { mode -> scope.launch { store.setThemeMode(mode) } },
                    onDarkStartChange = { t -> scope.launch { store.setThemeDarkStart(t) } },
                    onDarkEndChange = { t -> scope.launch { store.setThemeDarkEnd(t) } },
                    onOpenConfig = { subScreen = "config" },
                    onOpenTutorial = { subScreen = "tutorial" },
                    onOpenWhatsNew = { subScreen = "whatsnew" },
                    onOpenFaq = { subScreen = "faq" },
                )
            }
            }
            if (ocrBusy) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("正在识别图片…") },
                    text = { Text("离线识别课表文字并解析，一般几秒钟，请稍候。") },
                    confirmButton = {},
                )
            }
        }
    }
}
