package com.example.personalassistant.service.reminder

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.personalassistant.core.utils.NotificationHelper
import com.example.personalassistant.navigation.DeepLinks
import java.time.LocalDate

/**
 * 课程提醒接收器：AlarmManager 触发后弹通知
 */
class CourseReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CourseReminderScheduler.ACTION_COURSE_REMINDER) return

        val courseId = intent.getLongExtra(CourseReminderScheduler.EXTRA_COURSE_ID, -1L)
        val name = intent.getStringExtra(CourseReminderScheduler.EXTRA_COURSE_NAME).orEmpty()
        val teacher = intent.getStringExtra(CourseReminderScheduler.EXTRA_COURSE_TEACHER).orEmpty()
        val location = intent.getStringExtra(CourseReminderScheduler.EXTRA_COURSE_LOCATION).orEmpty()
        val reminderMinutes = intent.getIntExtra(CourseReminderScheduler.EXTRA_REMINDER_MINUTES, 15)
        val targetDateEpoch = intent.getLongExtra(CourseReminderScheduler.EXTRA_TARGET_DATE, LocalDate.now().toEpochDay())
        val targetDate = LocalDate.ofEpochDay(targetDateEpoch)

        if (courseId <= 0L || name.isBlank()) return

        // Android 13+ 需要运行时通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val title = "课程提醒：$name"
        val content = buildString {
            append("${reminderMinutes} 分钟后开始")
            if (teacher.isNotBlank()) append(" · $teacher")
            if (location.isNotBlank()) append(" · $location")
        }

        val notificationId = CourseReminderScheduler.notificationIdFor(courseId, targetDate)
        val deepLinkIntent = DeepLinks.courseEditIntent(context, courseId)
        NotificationHelper.showNotification(
            context = context,
            channelId = NotificationHelper.CHANNEL_COURSE,
            notificationId = notificationId,
            title = title,
            content = content,
            intent = deepLinkIntent,
        )
    }
}