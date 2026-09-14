package com.example.personalassistant.core.holiday.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personalassistant.MainActivity
import com.example.personalassistant.R
import com.example.personalassistant.core.holiday.model.HolidayType
import com.example.personalassistant.core.holiday.usecase.IsWorkdayOverrideUseCase
import com.example.personalassistant.core.holiday.usecase.ObserveUpcomingHolidaysUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Phase 8 Sub-step 7：每日 21:00 提前提醒 Worker
 *
 * 行为：
 * - 查询明天 + 后天两天的节假日
 * - 若明天是 HOLIDAY → 通知「明天是 X 节，休息愉快♪」
 * - 若明天是 WORKDAY → 通知「明天是调休工作日，别忘了课程表」
 * - 若后天有重大节日 → 加一句预告
 *
 * 触发时机：
 * - 由 [HolidayReminderScheduler] 调度为 PeriodicWorkRequest（24h 周期）
 * - 用户设置时间通过 initialDelay 控制首次执行（默认 21:00）
 *
 * 通知 ID：固定 7001，避免与其它模块冲突
 */
@HiltWorker
class HolidayEveningReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val observeUpcomingHolidays: ObserveUpcomingHolidaysUseCase,
    private val isWorkdayOverride: IsWorkdayOverrideUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val tomorrow = LocalDate.now().plusDays(1)
        val dayAfter = LocalDate.now().plusDays(2)

        val upcomingHolidays = observeUpcomingHolidays(
            from = LocalDate.now(),
            days = 3, // 覆盖明天和后天
        ).first()

        val tomorrowHoliday = upcomingHolidays.firstOrNull { it.dateEpochDay == tomorrow.toEpochDay() }
        val dayAfterHoliday = upcomingHolidays.firstOrNull { it.dateEpochDay == dayAfter.toEpochDay() }
        val tomorrowIsWorkday = isWorkdayOverride(tomorrow)

        val (title, body) = when {
            // 明天是 HOLIDAY
            tomorrowHoliday != null && tomorrowHoliday.type == HolidayType.HOLIDAY -> {
                val prefix = "♪ 明天是 ${tomorrowHoliday.name}"
                val suffix = when {
                    dayAfterHoliday != null && dayAfterHoliday.type == HolidayType.HOLIDAY ->
                            "后天还有 ${dayAfterHoliday.name}，连假开始♪"
                    else -> "记得调闹钟和家人分享♪"
                }
                prefix to suffix
            }
            // 明天是 WORKDAY
            tomorrowIsWorkday -> "⏰ 明天是调休工作日" to "别忘了查课程表，闹钟可能要调♪"
            // 后天有重大节日
            dayAfterHoliday != null && dayAfterHoliday.type == HolidayType.HOLIDAY ->
                    "📅 后天是 ${dayAfterHoliday.name}" to "提前安排好出行计划吧"
            // 没事
            else -> return Result.success()
        }

        postNotification(title, body)
        return Result.success()
    }

    private fun postNotification(title: String, body: String) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8.0+ 需要 Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "节假日提前提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "每天 21:00 提示明日 / 后日节假日"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 点击通知打开 MainActivity
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 7001, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "holiday_evening_reminder"
        private const val NOTIFICATION_ID = 7001
    }
}