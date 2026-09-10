package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ReminderApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val app = context.applicationContext as? ReminderApplication ?: return
            val repository = app.container.appRepository
            val scheduler = AndroidAlarmScheduler(context)

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val now = System.currentTimeMillis()
                    val pendingReminders = repository.getPendingRemindersList()
                    for (reminder in pendingReminders) {
                        // If reminder trigger time is in the future, reschedule it
                        val triggerTime = reminder.dueTime ?: reminder.dueDate
                        if (triggerTime > now) {
                            scheduler.scheduleReminder(reminder)
                        }
                    }

                    // Reschedule recurring backup reminder if enabled
                    val backupManager = app.container.backupManager
                    if (backupManager.isBackupReminderEnabled()) {
                        backupManager.scheduleNextBackupReminder()
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
