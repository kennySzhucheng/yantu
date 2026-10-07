package com.monesy.kaoyan

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun StagesScreen(today: LocalDate, store: Store) {    val checkins by produceState<Map<LocalDate, Set<String>>>(emptyMap(), today) {
        value = store.readAllCheckins()
    }
    val currentId = Plan.stageFor(today).id
    val currentStage = Plan.stages.firstOrNull { it.id == currentId }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("备考阶段（共 ${Plan.stages.size} 个）", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            currentStage?.let { stageStrategy(it) } ?: "配置缺失：请在 设置 → 配置管理 检查计划。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Plan.stages.forEach { stage ->
            val isCurrent = stage.id == currentId
            var expanded by remember { mutableStateOf(isCurrent) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .then(
                        if (isCurrent) Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(12.dp),
                        ) else Modifier,
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded },
                    ) {
                        Text(
                            "阶段${"①②③④⑤⑥"[stage.id - 1]} ${stage.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.primary,
                        )
                        if (stage.isDecisive) {
                            Spacer(Modifier.size(8.dp))
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.errorContainer) {
                                Text("★ 决定性", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (isCurrent) {
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary) {
                                Text(
                                    "当前",
                                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                            Spacer(Modifier.size(8.dp))
                        }
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                        )
                    }
                    Text(stage.dateText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stage.hoursPerWeek, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)

                    if (expanded) {
                        Spacer(Modifier.size(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(Modifier.size(8.dp))
                        Text("唯一目标", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(stage.goal, fontSize = 13.5.sp)
                        Spacer(Modifier.size(8.dp))
                        Text("核心任务", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        stage.coreTasks.forEach { Text("· $it", fontSize = 13.5.sp) }
                        Spacer(Modifier.size(8.dp))
                        Text("验收标准", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        stage.acceptance.forEach { Text("✅ $it", fontSize = 13.5.sp) }
                        Spacer(Modifier.size(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(Modifier.size(10.dp))
                        StageCheckinGrid(stage, today, checkins)
                    }
                }
            }
        }
    }
}

/**
 * 阶段每日打卡表（日历式）：左侧一列是教学周序号，每行从周一排到周日，
 * 与平时翻日历的习惯一致；格内数字为"日"，今天描边高亮。
 * 当天任务全部打卡 = 深绿✓；达标 = 浅绿；部分打卡 = 黄；没打卡 = 灰；还没到 = 空框。
 */
@Composable
private fun StageCheckinGrid(stage: Stage, today: LocalDate, checkins: Map<LocalDate, Set<String>>) {
    val firstMonday = stage.start.minusDays((stage.start.dayOfWeek.value - 1).toLong())
    val spanDays = ChronoUnit.DAYS.between(firstMonday, stage.end) + 1
    val totalWeeks = ((spanDays + 6) / 7).toInt()

    Column(Modifier.fillMaxWidth()) {
        Text(
            "每日打卡",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "一行 = 一个教学周，从周一到周日；格内数字是日期，描边是今天",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(6.dp))
        // 表头：周数列 + 星期
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("周", Modifier.width(26.dp), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { d ->
                Text(
                    d,
                    Modifier.width(22.dp),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 每个教学周一行
        for (w in 0 until totalWeeks) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                Text(
                    "${w + 1}",
                    Modifier.width(26.dp),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (dow in 1..7) {
                    val date = firstMonday.plusDays((w * 7 + (dow - 1)).toLong())
                    val inStage = !date.isBefore(stage.start) && !date.isAfter(stage.end)
                    val isFuture = date.isAfter(today)
                    val isToday = date == today
                    var bg = androidx.compose.ui.graphics.Color.Transparent
                    var fg = MaterialTheme.colorScheme.onSurfaceVariant
                    if (inStage && !isFuture) {
                        val tasks = Plan.tasksFor(stage, date.dayOfWeek)
                        val doneSet = checkins[date] ?: emptySet()
                        val doneCount = doneSet.count { id -> tasks.any { it.id == id } }
                        when {
                            isDayFull(tasks, doneSet) -> {
                                // 全部完成：深绿
                                bg = MaterialTheme.colorScheme.secondary
                                fg = MaterialTheme.colorScheme.onSecondary
                            }
                            isDayMet(tasks, doneSet) -> {
                                // 达标：底线任务 + 其余过半 → 浅绿
                                bg = MaterialTheme.colorScheme.secondaryContainer
                                fg = MaterialTheme.colorScheme.onSecondaryContainer
                            }
                            doneCount > 0 -> {
                                bg = MaterialTheme.colorScheme.tertiaryContainer
                                fg = MaterialTheme.colorScheme.onTertiaryContainer
                            }
                            else -> {
                                bg = MaterialTheme.colorScheme.surfaceVariant
                                fg = MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        }
                    }
                    Box(
                        Modifier
                            .padding(horizontal = 1.dp)
                            .size(20.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(bg)
                            .then(
                                when {
                                    isToday -> Modifier.border(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(5.dp),
                                    )
                                    inStage && isFuture -> Modifier.border(
                                        0.5.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        RoundedCornerShape(5.dp),
                                    )
                                    else -> Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (inStage) {
                            Text(
                                "${date.dayOfMonth}",
                                fontSize = 8.sp,
                                color = fg,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.size(6.dp))
        // 图例
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            legendBox(MaterialTheme.colorScheme.secondary, "全部完成")
            legendBox(MaterialTheme.colorScheme.secondaryContainer, "达标")
            legendBox(MaterialTheme.colorScheme.tertiaryContainer, "部分")
            legendBox(MaterialTheme.colorScheme.surfaceVariant, "没打卡")
            Box(
                Modifier
                    .size(12.dp)
                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
            )
            Text("还没到", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "达标 = 底线任务完成 + 其余任务过半（记小奖励）",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun legendBox(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Text(" $label", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 阶段页顶部策略提示：跟随当前阶段变化（按阶段名关键字匹配，兼容内置示例与向导生成的计划） */
private fun stageStrategy(stage: Stage): String = when {
    stage.name.contains("基础") || stage.name.contains("缓") || stage.name.contains("起步") ->
        "基础期策略：习惯先成型，强度后跟上——节奏稳定比单日时长更重要。"
    stage.name.contains("强化") || stage.name.contains("爬坡") || stage.name.contains("稳推") ->
        "强化期策略：整块时间啃硬骨头，主科强化与真题起步并行。"
    stage.name.contains("真题") ->
        "真题期策略：一切以限时实练为核心，分数是从真题里长出来的。"
    stage.name.contains("冲刺") ->
        "冲刺期策略：背诵 + 模考，回归错题本，停刷新题，稳住心态。"
    stage.name.contains("复试") ->
        "复试期策略：初试只是入场券，笔试与面试素材两手抓，出分前就该启动。"
    else -> "按阶段目标推进；真正拉开差距的是假期，学期只是「不停机」。"
}
