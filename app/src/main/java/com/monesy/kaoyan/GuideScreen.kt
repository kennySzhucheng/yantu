package com.monesy.kaoyan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 使用教程：完成向导后首次自动弹出，设置 → 帮助 可随时重看。
 * 分页讲解每个页面的用法，页点指示当前位置。
 */

private data class TutorialPage(val emoji: String, val title: String, val points: List<String>)

private val tutorialPages = listOf(
    TutorialPage(
        "👋", "欢迎使用",
        listOf(
            "这是一款完全离线的考研打卡提醒应用：数据只存在你的手机里，不需要网络",
            "底部有四个标签：今日 · 节点 · 阶段 · 设置",
            "向导里填写的考试信息会生成你的专属计划与提醒",
        ),
    ),
    TutorialPage(
        "📅", "今日页",
        listOf(
            "顶部：距初试倒计时与备考进度",
            "今日课程：来自你的课表（设置里可从 Excel 或图片导入）",
            "今日任务：逐项打勾；⭐ 底线任务尽量别断",
            "任务时长可以 ± 微调；达标线 = 底线完成 + 其余过半",
            "忘记打卡？底部「补卡」可以补记前两天的任务",
        ),
    ),
    TutorialPage(
        "🔔", "通知提醒",
        listOf(
            "每天早晚定时提醒，通知上可以直接「打卡」或「晚点提醒」，不用打开应用",
            "收不到提醒：先检查通知权限，再到系统设置允许后台运行/自启动（设置页底部有各品牌指引）",
            "手机没开机错过了提醒？开机后会自动补发，不会累积轰炸",
        ),
    ),
    TutorialPage(
        "🎯", "节点页",
        listOf(
            "重大节点逐项打勾：报名、确认、模考、初试……完成后卡片变绿",
            "复试等时间由院校决定的节点只做范围提醒（「时间待定」），不显示硬倒计时",
            "日常节点：手机日历日程 + 奖励日志（日达标小奖励、周达标大奖励）",
        ),
    ),
    TutorialPage(
        "📊", "阶段页",
        listOf(
            "每个阶段一张卡片，当前阶段自动展开",
            "打卡格子是日历式：一行 = 一个教学周，从周一到周日；格内数字是日期，描边是今天",
            "颜色：深绿=全部完成 · 浅绿=达标 · 黄=部分 · 灰=没打卡 · 空框=还没到",
        ),
    ),
    TutorialPage(
        "⚙️", "设置",
        listOf(
            "外观：浅色 / 深色 / 定时切换（支持跨夜时段）",
            "配置管理：课表导入、计划编辑器、数据备份、配置导入导出",
            "本教程和常见问题随时可以从 设置 → 帮助 重看",
        ),
    ),
)

@Composable
fun TutorialScreen(onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val last = tutorialPages.size - 1

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { dir * it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -dir * it } + fadeOut())
            },
            label = "tutorialPage",
        ) { p ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(tutorialPages[p].emoji, fontSize = 52.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    tutorialPages[p].title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(14.dp))
                tutorialPages[p].points.forEach {
                    Text(
                        "· $it",
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tutorialPages.indices.forEach { i ->
                Box(
                    Modifier
                        .size(if (i == page) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == page) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (page > 0) {
                OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f)) { Text("上一步") }
            }
            Button(
                onClick = { if (page < last) page++ else onDone() },
                modifier = Modifier.weight(1f),
            ) { Text(if (page < last) "下一步" else "开始使用") }
        }
        if (page < last) {
            TextButton(onClick = onDone, modifier = Modifier.padding(top = 4.dp)) { Text("跳过教程") }
        }
    }
}

// ---------- 常见问题 Q&A ----------

private data class Faq(val q: String, val a: String)

private val faqList = listOf(
    Faq(
        "收不到定时提醒？",
        "① 设置 → 每日通知：确认开关已打开、通知权限已授予；\n" +
            "② 系统设置 → 电池 / 应用管理：允许本应用「后台运行 / 自启动」（vivo：电池→后台高耗电；小米：省电策略→无限制；华为：应用启动管理→手动允许）；\n" +
            "③ 最近任务里长按卡片加锁；\n" +
            "④ 手机没开机错过提醒的，开机后会自动补发。",
    ),
    Faq(
        "通知上的按钮是什么？",
        "「打卡」直接完成单词底线任务；「晚点」把这次提醒延后 60 分钟。都不需要解锁手机进入应用。",
    ),
    Faq(
        "忘了打卡怎么办？",
        "今日页底部有「补卡」卡片：可以补记昨天与前天的任务，补记会正常计入打卡格与统计。",
    ),
    Faq(
        "阶段页的格子颜色是什么意思？",
        "深绿 = 当天全部完成；浅绿 = 达标（底线任务完成 + 其余过半）；黄 = 部分完成；灰 = 没打卡；空框 = 还没到。\n" +
            "一行是一个教学周，列是周一到周日，格内数字是日期，描边是今天。",
    ),
    Faq(
        "课表导入 Excel 解析失败或不对？",
        "支持教务系统导出的 .xls / .xlsx（含共享公式填充的表）。解析成功后一定先在预览页逐条确认（可改、可删、可手动补录）；" +
            "认不出表头时会给出提示，此时可在课表管理里手动添加。导不出 Excel 的课表，可以用「从图片识别」导入课表截图。",
    ),
    Faq(
        "从图片识别课表要注意什么？",
        "课表管理 → 从图片识别：请用学校原始课表（教务系统 / 课表小程序 / 课表 App）的完整截图，" +
            "包含星期表头和节次列、清晰正屏（不要拍照或只截一半）。识别在手机本地完成（不联网、不上传图片）；\n" +
            "截图里没有周次信息的课程会默认按全学期导入，导入后在预览页逐条核对修改。竖排表头或贴边截图可能识别不全，失败时换 Excel 或手动添加。",
    ),
    Faq(
        "计划想改怎么办？",
        "设置 → 配置管理 → 计划编辑器：阶段、日任务、重大节点都能改。" +
            "批量调整可以导出 JSON 模板，改好后用「导入配置」回填；复试这类时间待定的节点，在编辑器里打开「时间待定」开关即可。",
    ),
    Faq(
        "换手机 / 重装，数据怎么办？",
        "设置 → 配置管理 → 导出数据备份（包含打卡历史、奖励日志、节点完成、配置），新设备上「导入数据备份」即可完整恢复。",
    ),
    Faq(
        "深色模式怎么设？",
        "设置 → 外观：浅色 / 深色 / 定时。定时模式可以设定每天自动切换的时段（支持跨夜，如 22:00 开、07:00 关）。",
    ),
    Faq(
        "为什么复试没有倒计时？",
        "复试的具体时间由各院校自行决定，应用不给它显示硬倒计时，只做范围提醒（卡片标「时间待定」）。" +
            "请以报考院校研究生院发布的通知为准。",
    ),
    Faq(
        "我的数据安全吗？",
        "应用不需要网络权限，所有数据只保存在手机本机；备份文件由你自己保管，不会上传到任何服务器。",
    ),
)

@Composable
fun FaqScreen(onBack: () -> Unit) {
    var open by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
            Text("常见问题", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            "点开问题查看解答；没找到答案可以到 GitHub 仓库提 issue",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
        )
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(faqList) { i, f ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { open = if (open == i) null else i },
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Q${i + 1}. ${f.q}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        AnimatedVisibility(visible = open == i) {
                            Text(
                                f.a,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
