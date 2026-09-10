package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.example.data.local.ReminderEntity

interface AlarmScheduler {
    fun schedule(
        reminderId: Long,
        title: String,
        category: String,
        triggerAtMillis: Long,
        notes: String = "",
        priority: String = "High",
        amount: Double? = null
    )

    fun scheduleReminder(reminder: ReminderEntity)
    fun cancel(reminderId: Long)
    fun canScheduleExactAlarms(): Boolean
}

class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    override fun schedule(
        reminderId: Long,
        title: String,
        category: String,
        triggerAtMillis: Long,
        notes: String,
        priority: String,
        amount: Double?
    ) {
        val now = System.currentTimeMillis()
        // If the scheduled time is in the past, do not trigger immediately unless within recent 1 minute
        val finalTriggerMillis = if (triggerAtMillis < now) now + 5000L else triggerAtMillis

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_TRIGGER_REMINDER
            putExtra(NotificationHelper.EXTRA_REMINDER_ID, reminderId)
            putExtra(NotificationHelper.EXTRA_REMINDER_TITLE, title)
            putExtra(NotificationHelper.EXTRA_REMINDER_CATEGORY, category)
            putExtra(EXTRA_NOTES, notes)
            putExtra(EXTRA_PRIORITY, priority)
            if (amount != null) putExtra(EXTRA_AMOUNT, amount)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (canScheduleExactAlarms()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        finalTriggerMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        finalTriggerMillis,
                        pendingIntent
                    )
                }
            } else {
                // Fallback for devices where user or OEM restricted exact alarms
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        finalTriggerMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        finalTriggerMillis,
                        pendingIntent
                    )
                }
            }
        } catch (_: SecurityException) {
            // Fallback in case exact alarm was revoked between check and schedule
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    finalTriggerMillis,
                    pendingIntent
                )
            }
        }
    }

    override fun scheduleReminder(reminder: ReminderEntity) {
        val triggerTime = reminder.dueTime ?: reminder.dueDate
        schedule(
            reminderId = reminder.id,
            title = reminder.title,
            category = reminder.category,
            triggerAtMillis = triggerTime,
            notes = reminder.notes,
            priority = reminder.priority,
            amount = reminder.amount
        )
    }

    override fun cancel(reminderId: Long) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        NotificationHelper.cancelNotification(context, reminderId)
    }

    companion object {
        const val EXTRA_NOTES = "extra_notes"
        const val EXTRA_PRIORITY = "extra_priority"
        const val EXTRA_AMOUNT = "extra_amount"
    }
}
