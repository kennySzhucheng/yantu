package com.monesy.kaoyan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 奖励日志：完整列表，可逐条删除、可一键清空（今日、节点页共用）。
 * 新建的条目仍在 MainActivity 里按「达标边沿」自动记录；删除只是移除记录，不影响打卡。
 */
@Composable
fun RewardLogDialog(
    entries: List<RewardEntry>,
    onDelete: (RewardEntry) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("奖励日志 · ${entries.size} 条") },
        text = {
            if (entries.isEmpty()) {
                Text(
                    "还没有奖励记录。今日任务达标（底线完成 + 其余过半）会自动记一条小奖励 🎁",
                    fontSize = 13.5.sp,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    // 最新在前
                    items(entries.asReversed(), key = { "${it.date}|${it.kind}" }) { e ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (e.kind == 'W') "🍽" else "🎁", fontSize = 17.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "${e.date.monthValue}月${e.date.dayOfMonth}日 · ${if (e.kind == 'W') "周达标大奖励" else "日常小奖励"}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onDelete(e) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除这条奖励",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        dismissButton = {
            if (entries.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Text("清空全部", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空奖励日志？") },
            text = { Text("将删除全部 ${entries.size} 条奖励记录，不可恢复（打卡记录不受影响）。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearAll()
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}
