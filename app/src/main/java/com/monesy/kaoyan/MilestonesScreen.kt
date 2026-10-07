package com.monesy.kaoyan

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    // 重大节点默认收起为摘要（最近节点 + 圆点缩略条），展开才显示完整卡片列表
    var majorExpanded by remember { mutableStateOf(false) }
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
        // 折叠摘要：最近未完成 + 圆点缩略条，日常节点不再被长列表挤出屏幕
        AnimatedVisibility(visible = !majorExpanded) {
            Column {
                val next = Plan.upcomingNodes(today).firstOrNull { it.first.id !in done && !it.first.soft }
                Text(
                    text = next?.let { (n, d) -> if (d == 0L) "今天：「${n.title}」" else "最近未完成：「${n.title}」· 还有 $d 天" }
                        ?: "全部节点已完成 🎉",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    nodes.forEach { n ->
                        val d = ChronoUnit.DAYS.between(today, n.date)
                        val dot = when {
                            n.id in done -> MaterialTheme.colorScheme.secondary
                            n.soft -> Color.Transparent
                            d < 0 -> MaterialTheme.colorScheme.surfaceVariant
                            d <= 30 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(dot)
                                .then(
                                    if (n.soft) Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, CircleShape)
                                    else Modifier
                                ),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "共 ${nodes.size} 项",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "绿=已完成 红=30天内 蓝=未来 灰=已过 空心=时间待定 · 点击标题展开全部",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        AnimatedVisibility(visible = majorExpanded) {
            Column {
                Text(
                    "完成一项勾一项：✓ 已完成后卡片变绿",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
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

                    // ---- 信息渠道清单（全离线备忘：考研情报该去哪搜集） ----
                    Spacer(Modifier.padding(vertical = 10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(Modifier.padding(vertical = 6.dp))
                    Text(
                        "信息渠道 · 情报搜集指引",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "考研信息战的一半是「知道去哪看」。把下面的渠道收藏好，按节奏定期查看。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                    )
                    InfoSources.all.forEach { s ->
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(
                                "· ${s.name}",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text("看什么：${s.what}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("何时看：${s.whenText}", fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }
    }
}

/** 内置信息渠道清单：考研情报的搜集路径备忘（通用，不绑定具体院校） */
private data class InfoSource(val name: String, val what: String, val whenText: String)

private object InfoSources {
    val all = listOf(
        InfoSource("研招网（yz.chsi.com.cn）", "预报名 / 正式报名 / 网上确认 / 成绩 / 调剂，一切以这里为准", "报名季每周一次；出分当天"),
        InfoSource("目标院校研究生院官网", "招生简章、专业目录、考试科目与参考书、历年分数线", "每年 9 月简章期必查；出分后查院线"),
        InfoSource("报考学院官网", "复试方案、复试名单、拟录取公示、导师名录", "初试后每两周一次；复试季（3-4月）每周一次"),
        InfoSource("学信网 / 中国教育在线", "考试大纲发布、全国性政策变化", "大纲发布期（6-9 月）留意"),
        InfoSource("考研交流群 / 上岸学长学姐", "真题、笔记、经验帖、导师风评（最快的信息渠道）", "随时积累；真题主要来源"),
        InfoSource("目标导师主页 / 学院公众号", "导师研究方向、近年论文、招生动态", "复试前 1-2 个月重点了解"),
    )
}

@Composable
private fun NodeCard(node: KeyNode, isDone: Boolean, today: LocalDate, onToggle: () -> Unit) {
    val days = ChronoUnit.DAYS.between(today, node.date)
    // 软节点（复试等）：时间由院校决定，不参与已过/紧急判断，也不显示硬倒计时
    val passed = !node.soft && days < 0 && !isDone
    val urgent = !node.soft && days in 0..30 && !isDone
    val dateText = if (node.soft) {
        "时间以报考院校通知为准（一般在初试次年 2–4 月）"
    } else {
        node.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
    }
    val badge = when {
        isDone -> "已完成"
        node.soft -> "时间待定"
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
