package com.monesy.kaoyan

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 节点页：重大节点（默认展开，逐项打勾）+ 日常节点（默认折叠，含手机日历日程与奖励日志）。
 */
@Composable
fun MilestonesScreen(
    today: LocalDate,
    store: Store,
    calendarGranted: Boolean,
    calendarEvents: List<DayEvent>?,
    rewardJournal: List<RewardEntry>,
    onRequestCalendar: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val done by store.doneNodes.collectAsState(initial = emptySet())
    val nodes = Plan.keyNodes.sortedBy { it.date }
    var majorExpanded by remember { mutableStateOf(true) }
    var dailyExpanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ---- 重大节点 ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { majorExpanded = !majorExpanded },
        ) {
            Text(
                "重大节点",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${done.size}/${nodes.size} 已完成",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Icon(if (majorExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        AnimatedVisibility(visible = majorExpanded) {
            Column {
                val next = Plan.upcomingNodes(today).firstOrNull { it.first.id !in done }
                Text(
                    text = next?.let { (n, d) -> if (d == 0L) "今天：「${n.title}」" else "最近未完成：「${n.title}」· 还有 $d 天" }
                        ?: "全部节点已完成 🎉",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                Text(
                    "完成一项勾一项：✓ 已完成后卡片变绿",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                nodes.forEach { node ->
                    NodeCard(
                        node = node,
                        isDone = node.id in done,
                        today = today,
                        onToggle = { scope.launch { store.toggleNode(node.id) } },
                    )
                    Spacer(Modifier.padding(bottom = 10.dp))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(10.dp))

        // ---- 日常节点 ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { dailyExpanded = !dailyExpanded },
        ) {
            Text(
                "日常节点",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "日程 · 奖励",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Icon(if (dailyExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        AnimatedVisibility(visible = dailyExpanded) {
            Column {
                // 今日日程
                Text(
                    "今日日程 · 来自手机日历",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                )
                when {
                    !calendarGranted -> {
                        Text(
                            "授权读取手机日历后，这里每天自动显示你当天的日程（上课、会议、约定等）。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.padding(bottom = 8.dp))
                        Button(onClick = onRequestCalendar) { Text("授权日历权限") }
                    }
                    calendarEvents == null -> Text(
                        "读取中…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    calendarEvents.isEmpty() -> Text(
                        "日历上今天没有日程 📭",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> calendarEvents.forEach { e ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                e.timeText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(56.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                e.location?.let {
                                    Text(it, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.padding(vertical = 10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.padding(vertical = 10.dp))
                // 奖励日志
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "奖励日志",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${rewardJournal.size} 条",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.padding(bottom = 6.dp))
                if (rewardJournal.isEmpty()) {
                    Text(
                        "还没有奖励——今日任务全绿解锁第一条小奖励 🎁",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    rewardJournal.takeLast(3).reversed().forEach { e ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (e.kind == 'W') "🍽" else "🎁", fontSize = 17.sp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(e.text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "${e.date.monthValue}月${e.date.dayOfMonth}日 · ${if (e.kind == 'W') "周达标大奖励" else "日常小奖励"}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (rewardJournal.size > 3) {
                        Text(
                            "…共 ${rewardJournal.size} 条",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeCard(node: KeyNode, isDone: Boolean, today: LocalDate, onToggle: () -> Unit) {
    val days = ChronoUnit.DAYS.between(today, node.date)
    val passed = days < 0 && !isDone
    val urgent = days in 0..30 && !isDone
    val dateText = node.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
    val badge = when {
        isDone -> "已完成"
        days == 0L -> "今天"
        passed -> "已过 ${-days} 天"
        else -> "还有 $days 天"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDone -> MaterialTheme.colorScheme.secondaryContainer
                urgent -> MaterialTheme.colorScheme.errorContainer
                passed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(Modifier.padding(start = 6.dp, top = 10.dp, bottom = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isDone,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.secondary),
            )
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    node.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = when {
                        isDone -> MaterialTheme.colorScheme.onSecondaryContainer
                        passed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    node.detail,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    dateText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when {
                    isDone -> MaterialTheme.colorScheme.secondary
                    urgent -> MaterialTheme.colorScheme.error
                    passed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
            ) {
                Text(
                    badge,
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 11.5.sp,
                    color = when {
                        isDone -> MaterialTheme.colorScheme.onSecondary
                        urgent -> MaterialTheme.colorScheme.onError
                        passed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
        }
    }
}
