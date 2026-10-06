package com.monesy.kaoyan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val zhDay = DateTimeFormatter.ofPattern("M月d日 EEE", java.util.Locale.CHINA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    today: LocalDate,
    settings: Settings,
    checked: Set<String>,
    streak: Int,
    totalDays: Int,
    weekSummary: Stats.WeekSummary?,
    durations: Map<String, Int>,
    yesterday: LocalDate,
    checkedYesterday: Set<String>,
    dayBefore: LocalDate,
    checkedDayBefore: Set<String>,
    onToggle: (String) -> Unit,
    onDurationChange: (String, Int) -> Unit,
    onToggleDate: (LocalDate, String) -> Unit,
) {
    val stage = Plan.stageFor(today)
    val tasks = Plan.tasksFor(stage, today.dayOfWeek)
    val days = Plan.daysUntil(settings.examDate, today)
    val progress = Plan.journeyProgress(today, settings.examDate)
    val done = tasks.count { it.id in checked }
    var makeupDate by remember { mutableStateOf<LocalDate?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---- 倒计时 ----
        val heroBg = if (isSystemInDarkTheme()) Color(0xFF1E3A5C) else MaterialTheme.colorScheme.primary
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = heroBg,
                contentColor = Color.White,
            ),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(Config.countdownLabel, fontSize = 14.sp)
                when {
                    days > 0 -> {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "$days",
                                fontSize = 64.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 68.sp,
                            )
                            Text("天", fontSize = 20.sp, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
                        }
                    }
                    days == 0L -> Text("今天就是初试日！", fontSize = 40.sp, fontWeight = FontWeight.Black)
                    else -> Text("初试已结束，祝你上岸！", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${settings.examDate.year}年${settings.examDate.monthValue}月${settings.examDate.dayOfMonth}日（预计）· 目标 ${Config.targetScore} 分",
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                val animJourney by animateFloatAsState(targetValue = progress, label = "journey")
                LinearProgressIndicator(
                    progress = { animJourney },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
                Text(
                    "备考已走过 %.0f%%（自 ${Plan.PLAN_START.year}年${Plan.PLAN_START.monthValue}月${Plan.PLAN_START.dayOfMonth}日）".format(progress * 100),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // ---- 底线警告 ----
        val now = remember { LocalTime.now() }
        if ("words" !in checked && now >= LocalTime.of(21, 0) && days >= 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(
                    "⚠️ 底线要破了：今天的 40 个单词还没背！病假、考试周、过年都不破例。",
                    Modifier.padding(14.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // ---- 当前阶段 ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "阶段${"①②③④⑤⑥"[stage.id - 1]} ${stage.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (stage.isDecisive) {
                        Spacer(Modifier.size(8.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Text("★ 决定性", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(stage.hoursPerWeek, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                }
                Text(stage.dateText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(stage.goal, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                stage.coreTasks.take(2).forEach {
                    Text("· $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ---- 节假日 / 周末提示 ----
        val holiday = remember(today) { Holidays.holidayFor(today) }
        val weekend = remember(today) { Holidays.isWeekend(today) }
        val classes = remember(today) { Timetable.classesFor(today) }
        val weekNum = remember(today) { Timetable.weekOf(today) }
        if (holiday != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎉", fontSize = 22.sp)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "${holiday.name} · 假期第 ${holiday.dayOf(today)} 天（共 ${holiday.lengthDays()} 天）",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        if (holiday.isOfficial) "假期没有课，是赶进度的好窗口；底线任务照旧。"
                        else "按往年惯例推算的预计安排（官方通知一般11月前后发布），以学校校历为准。",
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        } else if (weekend) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Text(
                    when {
                        today.dayOfWeek == java.time.DayOfWeek.SATURDAY && classes.isNotEmpty() ->
                            "🗓 今天周六有 ${classes.size} 节课：课余见缝插针，底线任务照旧。"
                        today.dayOfWeek == java.time.DayOfWeek.SATURDAY ->
                            "🗓 今天周六没课：进度可以推快一点；底线任务照旧。"
                        classes.isNotEmpty() ->
                            "🗓 今天周日有 ${classes.size} 节课，先上课；下午整块休息是硬性安排 😄"
                        else ->
                            "🗓 今天周日：下午整块休息是硬性安排，不是偷懒 😄"
                    },
                    Modifier.padding(14.dp),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // ---- 今日课程（折叠显示节数与教学周，展开看时间/教室） ----
        Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
            Column(Modifier.padding(16.dp)) {
                var coursesExpanded by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { coursesExpanded = !coursesExpanded },
                ) {
                    Text(
                        if (weekNum in 1..ClassTimes.SEMESTER_WEEKS) "今日课程 · 第 $weekNum/${ClassTimes.SEMESTER_WEEKS} 教学周"
                        else "今日课程",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (holiday == null && classes.isNotEmpty()) {
                        Text(
                            "${classes.size} 节",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Icon(
                        if (coursesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                    )
                }
                Spacer(Modifier.height(6.dp))
                when {
                    holiday != null -> Text(
                        "假期中，课表暂停；调补课安排以学校通知为准。",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    classes.isEmpty() -> Text(
                        "今天没有课——完整的自习日 🎯",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium,
                    )
                    else -> {
                        AnimatedVisibility(visible = !coursesExpanded) {
                            Text(
                                "点击展开上课时间和教室",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AnimatedVisibility(visible = coursesExpanded) {
                            Column {
                                classes.sortedBy { it.slot }.forEach { c ->
                                    val bell = ClassTimes.bellFor(c.slot, c.room)
                                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.width(68.dp)) {
                                            Text(
                                                Timetable.slotLabels[c.slot],
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(c.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                                            if (c.room.isNotBlank()) {
                                                Text(
                                                    c.room,
                                                    fontSize = 11.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        if (bell != null) {
                                            Text(
                                                "${bell.start}–${bell.end}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                                if (classes.size >= 4) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "今天课多（${classes.size} 节）：晚自习可降级，只保单词底线。",
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- 今日任务 ----
        Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "今日任务 · ${today.format(zhDay)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "已完成 $done/${tasks.size}" + if (isDayMet(tasks, checked)) " · 已达标" else "",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(4.dp))
                tasks.forEachIndexed { i, task ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(checked = task.id in checked, onCheckedChange = { onToggle(task.id) })
                        Column(Modifier.padding(start = 4.dp).weight(1f)) {
                            Text(task.title, fontSize = 15.sp)
                            if (task.isBottomLine) {
                                Text(
                                    "底线任务 · 状态再差也不能断",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                        DurationControl(task, durations, onDurationChange)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "达标线：底线任务完成 + 其余任务过半 → 记奖励",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- 连续打卡 ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔥", fontSize = 30.sp)
                Column(Modifier.padding(start = 12.dp)) {
                    Row {
                        Text(
                            "连续背单词 $streak 天",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Text("累计 $totalDays 天 · 断一周要花两周找回手感，不断卡比每天多久更重要", fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    val bottomLineDone = "words" in checked
                    Text(
                        when {
                            bottomLineDone -> "今日底线：✅ 已完成"
                            now >= LocalTime.of(21, 0) -> "今日底线：⚠️ 已破 —— 现在背还来得及"
                            else -> "今日底线：⏳ 请在 21:00 前完成"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            bottomLineDone -> MaterialTheme.colorScheme.secondary
                            now >= LocalTime.of(21, 0) -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.tertiary
                        },
                    )
                }
            }
        }

        // ---- 补卡（最近两天） ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("补卡（最近两天）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "生病 / 忘带手机时，可补记错过的打卡（会标注“已补记”）",
                    fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(yesterday to checkedYesterday, dayBefore to checkedDayBefore).forEach { (d, set) ->
                    val dayTasks = Plan.tasksFor(Plan.stageFor(d), d.dayOfWeek)
                    val met = isDayMet(dayTasks, set)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${dateText(d)} ${dayName(d.dayOfWeek.value)}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                when {
                                    met -> "已达标 ✓" + if ("makeup" in set) "（已补记）" else ""
                                    "makeup" in set -> "已补记（未达标）"
                                    else -> "未达标，可补记"
                                },
                                fontSize = 11.5.sp,
                                color = if (met) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!met) TextButton(onClick = { makeupDate = d }) { Text("补记") }
                    }
                }
            }
        }

        // ---- 本周投入 ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "本周投入",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("周一至今", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                val ws = weekSummary
                if (ws == null) {
                    Text("统计中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            ws.hoursText,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 32.sp,
                        )
                        Text(
                            " 估算 · 目标 ${ws.targetText}",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val animWeek by animateFloatAsState(
                        targetValue = if (ws.targetMaxHours > 0) (ws.totalMinutes / 60f / ws.targetMaxHours).coerceIn(0f, 1f) else 0f,
                        label = "week",
                    )
                    LinearProgressIndicator(
                        progress = { animWeek },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "按打卡任务时长估算 · 打卡 ${ws.checkedCount}/${ws.taskCount} 次 · 连续背单词 ${ws.wordStreak} 天",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        // ---- 页脚 ----
        Text(
            "${Config.schoolFrom} → ${Config.schoolTo}\n${Config.subjects}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val md = makeupDate
    if (md != null) {
        val dayTasks = Plan.tasksFor(Plan.stageFor(md), md.dayOfWeek)
        val set = if (md == yesterday) checkedYesterday else checkedDayBefore
        AlertDialog(
            onDismissRequest = { makeupDate = null },
            title = { Text("补记 ${dateText(md)}") },
            text = {
                Column {
                    dayTasks.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = t.id in set, onCheckedChange = { onToggleDate(md, t.id) })
                            Text(t.title, fontSize = 13.5.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { makeupDate = null }) { Text("完成") } },
        )
    }
}

/** 任务时长控件：固定任务只显示时长；可调任务显示 −/+ 步进器（15 分钟步进，范围来自任务定义） */
@Composable
private fun DurationControl(task: PlanTask, durations: Map<String, Int>, onChange: (String, Int) -> Unit) {
    val m = durations[task.id] ?: task.minutes
    if (task.maxMinutes <= 0 || task.minMinutes <= 0) {
        Text(
            fmtMinutes(m),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", enabled = m - 15 >= task.minMinutes) { onChange(task.id, m - 15) }
            Text(
                fmtMinutes(m),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 5.dp),
            )
            StepButton("＋", enabled = m + 15 <= task.maxMinutes) { onChange(task.id, m + 15) }
        }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}
