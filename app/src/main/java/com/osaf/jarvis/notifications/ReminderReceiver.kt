package com.osaf.jarvis.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val title = intent?.getStringExtra(NotificationHelper.EXTRA_REMINDER_TITLE)
            ?: "Scheduled reminder"
        val id = intent?.getLongExtra(NotificationHelper.EXTRA_REMINDER_ID, 0L)?.toInt() ?: 0
        NotificationHelper.showReminderNotification(context, id, title)
    }
}
