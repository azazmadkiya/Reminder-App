package com.example.notification

import android.Manifest
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
import java.text.NumberFormat
import java.util.Locale

object NotificationHelper {

    const val CHANNEL_ID = "compliance_reminders_channel"
    const val CHANNEL_NAME = "Compliance & Payment Reminders"
    const val CHANNEL_DESC = "Timely alerts for GST filing, Income Tax deadlines, and payment due dates"

    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
    const val EXTRA_REMINDER_CATEGORY = "extra_reminder_category"

    const val CHANNEL_BACKUP_ID = "backup_reminders_channel"
    const val CHANNEL_BACKUP_NAME = "Backup & Data Safety Alerts"
    const val CHANNEL_BACKUP_DESC = "Recurring reminders to back up business records and prevent data loss"
    const val BACKUP_REMINDER_NOTIFICATION_ID = 99999

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val backupChannel = NotificationChannel(CHANNEL_BACKUP_ID, CHANNEL_BACKUP_NAME, importance).apply {
                description = CHANNEL_BACKUP_DESC
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(backupChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun showReminderNotification(
        context: Context,
        reminderId: Long,
        title: String,
        category: String,
        notes: String = "",
        priority: String = "High",
        amount: Double? = null
    ) {
        if (!hasNotificationPermission(context)) {
            return
        }

        createNotificationChannel(context)

        // Main Tap Action: Open MainActivity
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Mark Complete
        val completeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_MARK_COMPLETE
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId * 10 + 1).toInt(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Snooze (10 minutes)
        val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_REMINDER_TITLE, title)
            putExtra(EXTRA_REMINDER_CATEGORY, category)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId * 10 + 2).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = buildString {
            if (amount != null && amount > 0) {
                val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
                append("Amount: ${format.format(amount)}. ")
            }
            if (notes.isNotBlank()) {
                append(notes)
            } else {
                append("Due for action today. Tap to view details.")
            }
        }

        val notificationPriority = when (priority.lowercase()) {
            "high" -> NotificationCompat.PRIORITY_MAX
            "low" -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_HIGH
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle("[$category] $title")
            .setContentText(bodyText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("[$category] $title")
                    .bigText(bodyText)
                    .setSummaryText(category)
            )
            .setPriority(notificationPriority)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_notification_bell, "Mark Done", completePendingIntent)
            .addAction(R.drawable.ic_notification_bell, "Snooze (10m)", snoozePendingIntent)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(reminderId.toInt(), notification)
        } catch (_: SecurityException) {
            // Gracefully ignore if revoked at runtime
        }
    }

    fun cancelNotification(context: Context, reminderId: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(reminderId.toInt())
    }

    fun showBackupReminderNotification(context: Context) {
        if (!hasNotificationPermission(context)) {
            return
        }

        createNotificationChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "settings")
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            BACKUP_REMINDER_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BACKUP_ID)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle("Data Backup Recommended")
            .setContentText("Keep your business ledgers, vouchers, and tax reminders safe. Tap to export a backup.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("Data Backup Recommended")
                    .bigText("Safeguard your important business records, parties, and transaction ledgers. Tap here to export or share a secure backup file.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_notification_bell, "Backup Now", openPendingIntent)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(BACKUP_REMINDER_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Gracefully ignore if revoked at runtime
        }
    }

    fun cancelBackupNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(BACKUP_REMINDER_NOTIFICATION_ID)
    }
}
