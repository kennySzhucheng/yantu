package com.monesy.kaoyan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Notify {

    const val CHANNEL_ID = "kaoyan_reminders"

    const val KIND_MORNING = "morning"
    const val KIND_EVENING = "evening"
    const val KIND_BOTTOMLINE = "bottomline"
    const val KIND_WEEKLY = "weekly"
    private val ALL_KINDS = listOf(KIND_MORNING, KIND_EVENING, KIND_BOTTOMLINE, KIND_WEEKLY)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "备考提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "${Config.appTitle}每日提醒"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * 应用启动 / 开机时调用：只补「断掉的链」，绝不取消已排定的任务。
     * 之前用 REPLACE 全量重排，会把「关机期间过期、等开机补发」的任务静默跳过——已修复。
     */
    suspend fun healChains(context: Context) {
        for (kind in ALL_KINDS) {
            if (!hasPendingWork(context, kind)) {
                enqueueNext(context, kind)
            }
        }
    }

    /** 设置变更时调用：显式重排（REPLACE），属于用户主动行为。 */
    suspend fun scheduleAll(context: Context) {
        for (kind in ALL_KINDS) {
            enqueueNext(context, kind)
        }
    }

    /**
     * 稍后提醒：60 分钟后以独立唯一名再跑一次（snooze 标记让它按"准时"处理，不再走补发窗口逻辑）
     */
    fun enqueueSnooze(context: Context, kind: String) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.ofMinutes(60))
            .setInputData(workDataOf("kind" to kind, "snooze" to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("snooze_$kind", ExistingWorkPolicy.REPLACE, request)
    }

    private fun uniqueName(kind: String): String =
        if (kind == KIND_WEEKLY) "weekly_report" else "daily_$kind"

    private fun hasPendingWork(context: Context, kind: String): Boolean = runCatching {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(uniqueName(kind)).get()
            .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
    }.getOrDefault(false)

    /**
     * 排定某类提醒的下一次触发（唯一名 REPLACE，重复调用安全）。
     * 链式调度：worker 触发完毕后自行调用本方法排下一次，按目标钟点重算，偏差不累积。
     */
    suspend fun enqueueNext(context: Context, kind: String) {
        val settings = Store(context).settings.first()
        val wm = WorkManager.getInstance(context)
        val enabled = when (kind) {
            KIND_MORNING -> settings.morningEnabled
            KIND_EVENING -> settings.eveningEnabled
            else -> true
        }
        if (!enabled) {
            wm.cancelUniqueWork(uniqueName(kind))
            return
        }
        val time = when (kind) {
            KIND_MORNING -> settings.morningTime
            KIND_EVENING -> settings.eveningTime
            KIND_BOTTOMLINE -> LocalTime.of(21, 0)
            else -> LocalTime.of(21, 30) // 周报：周日 21:30
        }
        val now = LocalDateTime.now()
        var candidate = now.toLocalDate().atTime(time)
        if (kind == KIND_WEEKLY) {
            while (candidate.dayOfWeek != DayOfWeek.SUNDAY) candidate = candidate.plusDays(1)
        }
        if (!candidate.isAfter(now)) {
            candidate = if (kind == KIND_WEEKLY) candidate.plusWeeks(1) else candidate.plusDays(1)
        }
        val fireAt = candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, candidate))
            .setInputData(workDataOf("kind" to kind, "fireAt" to fireAt))
            .build()
        wm.enqueueUniqueWork(uniqueName(kind), ExistingWorkPolicy.REPLACE, request)
    }
}

class ReminderWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString("kind") ?: Notify.KIND_MORNING
        val today = LocalDate.now()
        val nearest = Plan.nearestNodeLine(today)
        val store = Store(ctx)

        // 与计划触发时刻的偏差：15 分钟内算准时；超窗口则静默跳过（只重排）
        val fireAt = inputData.getLong("fireAt", 0L)
        val lateMinutes = if (fireAt > 0) ((System.currentTimeMillis() - fireAt) / 60000L).coerceAtLeast(0) else 0
        val windowMinutes = when (kind) {
            Notify.KIND_MORNING -> 240L        // 早晨提醒：12 点前补发有效
            Notify.KIND_EVENING -> 210L        // 晚自习提醒：23 点前补发有效
            Notify.KIND_BOTTOMLINE -> 120L     // 底线警戒：23 点前补发有效
            else -> 720L                       // 周报：12 小时内补发有效
        }
        val snooze = inputData.getBoolean("snooze", false)
        val missed = !snooze && fireAt > 0 && lateMinutes > 15
        val tooLate = !snooze && fireAt > 0 && lateMinutes > windowMinutes
        val checked = store.checkinFor(today).first()

        if (!tooLate) {
            when (kind) {
                Notify.KIND_MORNING -> if ("words" !in checked) {
                    if (missed) push(
                        id = 1001,
                        title = "⏰ 补提醒：今天的 40 个单词还没背",
                        text = "刚才没能按时提醒你。现在补上，别断卡。" + (nearest?.let { " $it。" } ?: ""),
                        kind = Notify.KIND_MORNING, wordsAction = true, snoozeAction = true,
                    ) else push(
                        id = 1001,
                        title = "🌅 底线任务：今天的 40 个单词",
                        text = "起床后立刻背，别等「有空的时候」。" + (nearest?.let { " $it。" } ?: ""),
                        kind = Notify.KIND_MORNING, wordsAction = true, snoozeAction = true,
                    )
                }
                Notify.KIND_EVENING -> {
                    val stage = Plan.stageFor(today)
                    val evening = Plan.tasksFor(stage, today.dayOfWeek).firstOrNull { !it.isBottomLine }
                    val done = evening != null && evening.id in checked
                    if (!done) {
                        val body = evening?.title ?: "按当前阶段任务推进"
                        if (missed) push(
                            id = 1002,
                            title = "⏰ 补提醒：晚自习（${stage.name}阶段）",
                            text = "刚才没能按时提醒你。$body。别断卡。",
                            kind = Notify.KIND_EVENING, snoozeAction = true,
                        ) else push(
                            id = 1002,
                            title = "🌙 晚自习时间（${stage.name}阶段）",
                            text = "$body。" + (nearest?.let { " $it。" } ?: ""),
                            kind = Notify.KIND_EVENING, snoozeAction = true,
                        )
                    }
                }
                Notify.KIND_BOTTOMLINE -> if ("words" !in checked) {
                    push(
                        id = 1003,
                        title = "⚠️ 底线要破了：今天的 40 个单词还没背",
                        text = "病假、考试周、过年都不破例。现在背，还来得及。",
                        kind = Notify.KIND_BOTTOMLINE, wordsAction = true, snoozeAction = true,
                    )
                }
                else -> {
                    val s = Stats.weekSummary(store, today)
                    var rewardLine = ""
                    if (s.targetMinHours > 0 && s.totalMinutes >= s.targetMinHours * 60) {
                        val rewardText = if (store.hasReward(today, 'W')) {
                            store.rewardJournal.first().lastOrNull { it.kind == 'W' }?.text ?: ""
                        } else {
                            val pool = Config.rewardWeekly
                            if (pool.isEmpty()) "" else {
                                val text = pool[store.rewardJournal.first().count { it.kind == 'W' } % pool.size]
                                store.appendReward(today, 'W', text)
                                text
                            }
                        }
                        if (rewardText.isNotBlank()) rewardLine = "\n🎉 已记入奖励日志：$rewardText"
                    }
                    push(
                        id = 1004,
                        title = "📊 本周备考总结",
                        text = "打卡 ${s.checkedCount}/${s.taskCount} 次 · 估算投入约 ${s.hoursText}" +
                            "（目标 ${s.targetText}）\n连续背单词 ${s.wordStreak} 天$rewardLine",
                    )
                }
            }
        }

        // 触发（或静默跳过）后链接下一次；若期间设置被关闭，enqueueNext 内部会改为取消
        Notify.enqueueNext(ctx, kind)
        return Result.success()
    }

    private fun push(
        id: Int,
        title: String,
        text: String,
        kind: String = "",
        wordsAction: Boolean = false,
        snoozeAction: Boolean = false,
    ) {
        val manager = NotificationManagerCompat.from(ctx)
        if (!manager.areNotificationsEnabled()) return
        val tapIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(ctx, Notify.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kaoyan)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
        if (wordsAction) {
            builder.addAction(
                0, "✅ 已背单词",
                PendingIntent.getBroadcast(
                    ctx, id * 10 + 1,
                    Intent(ctx, CheckinReceiver::class.java).putExtra("notifId", id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        if (snoozeAction && kind.isNotBlank()) {
            builder.addAction(
                0, "⏰ 稍后提醒",
                PendingIntent.getBroadcast(
                    ctx, id * 10 + 2,
                    Intent(ctx, SnoozeReceiver::class.java)
                        .putExtra("notifId", id)
                        .putExtra("kind", kind),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        runCatching { manager.notify(id, builder.build()) }
    }
}

/** 通知按钮：一键完成单词打卡 */
class CheckinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("notifId", 1001)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val today = LocalDate.now()
                val store = Store(context)
                if ("words" !in store.checkinFor(today).first()) {
                    store.toggleCheckin(today, "words")
                }
                NotificationManagerCompat.from(context).cancel(id)
            } finally {
                pending.finish()
            }
        }
    }
}

/** 通知按钮：1 小时后再次提醒 */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: Notify.KIND_MORNING
        val id = intent.getIntExtra("notifId", 1001)
        NotificationManagerCompat.from(context).cancel(id)
        Notify.enqueueSnooze(context, kind)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                Notify.createChannel(context)
                Notify.healChains(context)
            } finally {
                pending.finish()
            }
        }
    }
}
