package com.monesy.kaoyan

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 界面通用小工具（日期选择器互转、显示名、引导气泡） */

fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun pickerMillisToDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

val weekDayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun dayName(day: Int): String = if (day == 0) "未定位" else weekDayNames.getOrElse(day - 1) { "?$day" }

fun dateText(d: LocalDate): String = "${d.monthValue}月${d.dayOfMonth}日"

/**
 * 向导气泡提示：轻微脉动的小气泡，用于首次引导时解释"这一步是干什么的"。
 * 不打断输入，只是视觉提示；深浅色主题都走 tertiaryContainer。
 */
@Composable
fun BubbleTip(text: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bubble")
    val alpha by transition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bubbleAlpha",
    )
    Surface(
        modifier = modifier.alpha(alpha),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("💡", fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Text(text, fontSize = 11.5.sp, lineHeight = 15.sp)
        }
    }
}
