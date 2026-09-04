package com.example.personalassistant.core.utils

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * 通知帮助类
 *
 * 渠道由 PersonalAssistantApp.onCreate() 注册，这里只负责构建与发送
 */
object NotificationHelper {

    const val CHANNEL_COURSE = "channel_course"
    const val CHANNEL_TASK = "channel_task"
    const val CHANNEL_EVENT = "channel_event"
    const val CHANNEL_FINANCE = "channel_finance"

    /**
     * 发送一条通知
     *
     * @param context Context
     * @param channelId 渠道 ID（见 CHANNEL_* 常量）
     * @param notificationId 通知 ID（同一渠道下唯一）
     * @param title 标题
     * @param content 内容
     * @param intent 点击通知后触发的意图（可选）
     */
    fun showNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        content: String,
        intent: Intent? = null,
        autoCancel: Boolean = true
    ) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(autoCancel)

        if (intent != null) {
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, builder.build())
    }

    /** 取消通知 */
    fun cancel(context: Context, notificationId: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId)
    }
}
