package com.monesy.kaoyan

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
fun StagesScreen(today: LocalDate, store: Store) {
    val checkins by produceState<Map<LocalDate, Set<String>>>(emptyMap(), today) {
        value = store.readAllCheckins()
    }
    val currentId = Plan.stageFor(today).id

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("六个阶段 · 18 个月", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "学期缓推 + 假期冲刺。真正拉开差距的是假期，学期只是「不停机」。",
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
 * 阶段每日打卡表：列为教学周序号（对齐周一）、行为星期。
 * 当天任务全部打卡 = 绿色✓；部分打卡 = 黄；没打卡 = 灰；还没到 = 空框。
 */
@Composable
private fun StageCheckinGrid(stage: Stage, today: LocalDate, checkins: Map<LocalDate, Set<String>>) {
    val firstMonday = stage.start.minusDays((stage.start.dayOfWeek.value - 1).toLong())
    val spanDays = ChronoUnit.DAYS.between(firstMonday, stage.end) + 1
    val totalWeeks = ((spanDays + 6) / 7).toInt()

    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        Text(
            "每日打卡（第 ${Plan.stages.indexOf(stage) + 1} 阶段）",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.size(6.dp))
        // 周次表头
        Row {
            Text("周", Modifier.width(16.dp), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            repeat(totalWeeks) { w ->
                Text(
                    "${w + 1}",
                    Modifier.width(17.dp),
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 每个星期一行
        for (dow in 1..7) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                Text(
                    "一二三四五六日"[dow - 1].toString(),
                    Modifier.width(16.dp),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (w in 0 until totalWeeks) {
                    val date = firstMonday.plusDays((w * 7 + (dow - 1)).toLong())
                    val inStage = !date.isBefore(stage.start) && !date.isAfter(stage.end)
                    val isFuture = date.isAfter(today)
                    var bg = androidx.compose.ui.graphics.Color.Transparent
                    var fg = MaterialTheme.colorScheme.onSurfaceVariant
                    var showCheck = false
                    var isFutureCell = false
                    if (inStage) {
                        if (isFuture) {
                            isFutureCell = true
                        } else {
                            val tasks = Plan.tasksFor(stage, date.dayOfWeek)
                            val doneSet = checkins[date] ?: emptySet()
                            val doneCount = doneSet.count { id -> tasks.any { it.id == id } }
                            when {
                                isDayFull(tasks, doneSet) -> {
                                    // 全部完成：深绿
                                    bg = MaterialTheme.colorScheme.secondary
                                }
                                isDayMet(tasks, doneSet) -> {
                                    // 达标：底线任务 + 其余过半 → 浅绿
                                    bg = MaterialTheme.colorScheme.secondaryContainer
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
                    }
                    Box(
                        Modifier
                            .padding(horizontal = 0.5.dp)
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(bg)
                            .then(
                                if (isFutureCell) Modifier.border(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    RoundedCornerShape(4.dp),
                                ) else Modifier,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showCheck) Text("✓", fontSize = 9.sp, color = fg, fontWeight = FontWeight.Bold)
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
