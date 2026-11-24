package com.osaf.jarvis.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {
    const val CHANNEL_VOICE_SERVICE = "jarvis_voice_service"
    const val CHANNEL_REMINDERS = "jarvis_reminders"
    const val FGS_NOTIFICATION_ID = 1001
    const val EXTRA_REMINDER_TITLE = "reminder_title"
    const val EXTRA_REMINDER_ID = "reminder_id"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val voiceChannel = NotificationChannel(
            CHANNEL_VOICE_SERVICE,
            context.getString(R.string.fgs_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = context.getString(R.string.fgs_channel_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.reminder_channel_desc)
        }

        nm.createNotificationChannel(voiceChannel)
        nm.createNotificationChannel(reminderChannel)
    }

    fun buildVoiceForegroundNotification(
        context: Context,
        wakePhrase: String,
        stopIntent: PendingIntent
    ): Notification {
        ensureChannels(context)
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_VOICE_SERVICE)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(context.getString(R.string.fgs_notification_title))
            .setContentText(context.getString(R.string.fgs_notification_body, wakePhrase))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                context.getString(R.string.fgs_action_stop),
                stopIntent
            )
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    fun scheduleLocalReminder(context: Context, reminderId: Long, title: String, triggerAtMillis: Long) {
        ensureChannels(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_TITLE, title)
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
    }

    fun showReminderNotification(context: Context, reminderId: Int, title: String) {
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val openIntent = PendingIntent.getActivity(
            context,
            reminderId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("OSAF JARVIS Reminder")
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(2000 + reminderId, notification)
    }
}
