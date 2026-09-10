package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ReminderApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(NotificationHelper.EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) return

        // Always dismiss the active notification
        NotificationHelper.cancelNotification(context, reminderId)

        val app = context.applicationContext as? ReminderApplication
        val repository = app?.container?.appRepository

        when (intent.action) {
            ACTION_MARK_COMPLETE -> {
                if (repository != null) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val reminder = repository.getReminderById(reminderId)
                            if (reminder != null) {
                                repository.updateReminder(reminder.copy(status = "Completed", updatedAt = System.currentTimeMillis()))
                            }
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            ACTION_SNOOZE -> {
                val title = intent.getStringExtra(NotificationHelper.EXTRA_REMINDER_TITLE) ?: "Reminder"
                val category = intent.getStringExtra(NotificationHelper.EXTRA_REMINDER_CATEGORY) ?: "Reminder"
                val snoozeMillis = System.currentTimeMillis() + 10 * 60 * 1000L // 10 minutes later

                val scheduler = AndroidAlarmScheduler(context)
                scheduler.schedule(
                    reminderId = reminderId,
                    title = "$title (Snoozed)",
                    category = category,
                    triggerAtMillis = snoozeMillis,
                    notes = "Snoozed for 10 minutes"
                )
            }
        }
    }

    companion object {
        const val ACTION_MARK_COMPLETE = "com.example.reminder.ACTION_MARK_COMPLETE"
        const val ACTION_SNOOZE = "com.example.reminder.ACTION_SNOOZE"
    }
}
