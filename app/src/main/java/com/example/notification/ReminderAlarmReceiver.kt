package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TRIGGER_REMINDER) {
            val reminderId = intent.getLongExtra(NotificationHelper.EXTRA_REMINDER_ID, -1L)
            if (reminderId == -1L) return

            val title = intent.getStringExtra(NotificationHelper.EXTRA_REMINDER_TITLE) ?: "Reminder"
            val category = intent.getStringExtra(NotificationHelper.EXTRA_REMINDER_CATEGORY) ?: "Compliance"
            val notes = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_NOTES) ?: ""
            val priority = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_PRIORITY) ?: "High"
            val amount = if (intent.hasExtra(AndroidAlarmScheduler.EXTRA_AMOUNT)) {
                intent.getDoubleExtra(AndroidAlarmScheduler.EXTRA_AMOUNT, 0.0)
            } else null

            NotificationHelper.showReminderNotification(
                context = context,
                reminderId = reminderId,
                title = title,
                category = category,
                notes = notes,
                priority = priority,
                amount = amount
            )
        }
    }

    companion object {
        const val ACTION_TRIGGER_REMINDER = "com.example.reminder.ACTION_TRIGGER_REMINDER"
    }
}
