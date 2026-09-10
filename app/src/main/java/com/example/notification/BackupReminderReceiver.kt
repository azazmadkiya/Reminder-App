package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ReminderApplication

class BackupReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_BACKUP_REMINDER) {
            val app = context.applicationContext as? ReminderApplication
            val backupManager = app?.container?.backupManager

            if (backupManager?.isBackupReminderEnabled() == true) {
                NotificationHelper.showBackupReminderNotification(context)
                // Schedule next occurrence
                backupManager.scheduleNextBackupReminder()
            }
        }
    }

    companion object {
        const val ACTION_BACKUP_REMINDER = "com.example.reminder.ACTION_BACKUP_REMINDER"
    }
}
