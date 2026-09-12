package com.example.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.PartyEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.TransactionEntity
import com.example.data.repository.AppRepository
import com.example.notification.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupPreview(
    val exportDate: Long,
    val partyCount: Int,
    val transactionCount: Int,
    val reminderCount: Int,
    val appName: String = "Compliance & Ledger"
)

data class RestoreResult(
    val success: Boolean,
    val partyCount: Int,
    val transactionCount: Int,
    val reminderCount: Int,
    val message: String
)

class BackupManager(
    private val context: Context,
    private val appRepository: AppRepository,
    private val alarmScheduler: AlarmScheduler
) {
    private val prefs = context.getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)

    fun getLastBackupTime(): Long {
        return prefs.getLong(KEY_LAST_BACKUP, 0L)
    }

    private fun updateLastBackupTime(time: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_BACKUP, time).apply()
    }

    suspend fun generateBackupJson(): String = withContext(Dispatchers.IO) {
        val parties = appRepository.getAllPartiesList()
        val transactions = appRepository.getAllTransactionsList()
        val reminders = appRepository.getAllRemindersList()

        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("appName", "Compliance & Ledger")
        root.put("exportTimestamp", System.currentTimeMillis())

        // 1. Parties
        val partiesArray = JSONArray()
        parties.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("businessName", p.businessName)
            obj.put("mobile", p.mobile)
            obj.put("email", p.email)
            obj.put("gstin", p.gstin)
            obj.put("pan", p.pan)
            obj.put("address", p.address)
            obj.put("openingBalance", p.openingBalance)
            obj.put("balanceType", p.balanceType)
            obj.put("notes", p.notes)
            obj.put("createdAt", p.createdAt)
            obj.put("updatedAt", p.updatedAt)
            partiesArray.put(obj)
        }
        root.put("parties", partiesArray)

        // 2. Transactions
        val transArray = JSONArray()
        transactions.forEach { t ->
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("partyId", t.partyId)
            obj.put("date", t.date)
            obj.put("type", t.type)
            obj.put("amount", t.amount)
            obj.put("paymentMode", t.paymentMode)
            obj.put("referenceNumber", t.referenceNumber)
            obj.put("description", t.description)
            obj.put("createdAt", t.createdAt)
            transArray.put(obj)
        }
        root.put("transactions", transArray)

        // 3. Reminders
        val remindersArray = JSONArray()
        reminders.forEach { r ->
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("title", r.title)
            obj.put("category", r.category)
            if (r.partyId != null) obj.put("partyId", r.partyId)
            obj.put("dueDate", r.dueDate)
            if (r.dueTime != null) obj.put("dueTime", r.dueTime)
            obj.put("repeatType", r.repeatType)
            obj.put("notificationSettings", r.notificationSettings)
            obj.put("priority", r.priority)
            obj.put("status", r.status)
            obj.put("notes", r.notes)
            if (r.amount != null) obj.put("amount", r.amount)
            remindersArray.put(obj)
        }
        root.put("reminders", remindersArray)

        root.toString(2)
    }

    suspend fun exportToFile(targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = generateBackupJson()
            context.contentResolver.openOutputStream(targetUri)?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            }
            updateLastBackupTime()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun shareBackupFile(): Intent? = withContext(Dispatchers.IO) {
        try {
            val json = generateBackupJson()
            val backupDir = File(context.cacheDir, "backups")
            if (!backupDir.exists()) backupDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(backupDir, "Reminder_Backup_$timestamp.json")
            FileOutputStream(file).use {
                it.write(json.toByteArray(Charsets.UTF_8))
            }

            val uri = FileProvider.getUriForFile(
                context,
                "com.reminder.app.azaz.fileprovider",
                file
            )

            updateLastBackupTime()

            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Compliance & Ledger App Backup ($timestamp)")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Attached is the database backup for Compliance & Ledger. Keep this file safe to restore your parties, transactions, and reminders."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parsePreview(jsonString: String): BackupPreview? {
        return try {
            val root = JSONObject(jsonString)
            val exportTimestamp = root.optLong("exportTimestamp", System.currentTimeMillis())
            val partiesCount = root.optJSONArray("parties")?.length() ?: 0
            val transactionsCount = root.optJSONArray("transactions")?.length() ?: 0
            val remindersCount = root.optJSONArray("reminders")?.length() ?: 0
            val appName = root.optString("appName", "Compliance & Ledger")
            BackupPreview(exportTimestamp, partiesCount, transactionsCount, remindersCount, appName)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun restoreFromJson(
        jsonString: String,
        replaceExisting: Boolean
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val partiesArray = root.optJSONArray("parties") ?: JSONArray()
            val transArray = root.optJSONArray("transactions") ?: JSONArray()
            val remindersArray = root.optJSONArray("reminders") ?: JSONArray()

            val parties = mutableListOf<PartyEntity>()
            for (i in 0 until partiesArray.length()) {
                val obj = partiesArray.getJSONObject(i)
                parties.add(
                    PartyEntity(
                        id = obj.optLong("id", 0L),
                        name = obj.getString("name"),
                        businessName = obj.optString("businessName", ""),
                        mobile = obj.optString("mobile", ""),
                        email = obj.optString("email", ""),
                        gstin = obj.optString("gstin", ""),
                        pan = obj.optString("pan", ""),
                        address = obj.optString("address", ""),
                        openingBalance = obj.optDouble("openingBalance", 0.0),
                        balanceType = obj.optString("balanceType", "Receivable"),
                        notes = obj.optString("notes", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            val transactions = mutableListOf<TransactionEntity>()
            for (i in 0 until transArray.length()) {
                val obj = transArray.getJSONObject(i)
                transactions.add(
                    TransactionEntity(
                        id = obj.optLong("id", 0L),
                        partyId = obj.getLong("partyId"),
                        date = obj.getLong("date"),
                        type = obj.getString("type"),
                        amount = obj.getDouble("amount"),
                        paymentMode = obj.optString("paymentMode", "Cash"),
                        referenceNumber = obj.optString("referenceNumber", ""),
                        description = obj.optString("description", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            val reminders = mutableListOf<ReminderEntity>()
            for (i in 0 until remindersArray.length()) {
                val obj = remindersArray.getJSONObject(i)
                reminders.add(
                    ReminderEntity(
                        id = obj.optLong("id", 0L),
                        title = obj.getString("title"),
                        category = obj.optString("category", "General"),
                        partyId = if (obj.has("partyId") && !obj.isNull("partyId")) obj.getLong("partyId") else null,
                        dueDate = obj.getLong("dueDate"),
                        dueTime = if (obj.has("dueTime") && !obj.isNull("dueTime")) obj.getLong("dueTime") else null,
                        repeatType = obj.optString("repeatType", "One-time"),
                        notificationSettings = obj.optString("notificationSettings", ""),
                        priority = obj.optString("priority", "Medium"),
                        status = obj.optString("status", "Pending"),
                        notes = obj.optString("notes", ""),
                        amount = if (obj.has("amount") && !obj.isNull("amount")) obj.getDouble("amount") else null
                    )
                )
            }

            // Perform DB Restore
            appRepository.restoreData(parties, transactions, reminders, replaceExisting)

            // Reschedule pending reminders with alarm scheduler
            val activeReminders = reminders.filter { it.status == "Pending" }
            activeReminders.forEach { reminder ->
                alarmScheduler.scheduleReminder(reminder)
            }

            RestoreResult(
                success = true,
                partyCount = parties.size,
                transactionCount = transactions.size,
                reminderCount = reminders.size,
                message = "Successfully restored ${parties.size} parties, ${transactions.size} transactions, and ${reminders.size} reminders."
            )
        } catch (e: Exception) {
            e.printStackTrace()
            RestoreResult(
                success = false,
                partyCount = 0,
                transactionCount = 0,
                reminderCount = 0,
                message = "Restore failed: ${e.localizedMessage ?: "Invalid file format"}"
            )
        }
    }

    suspend fun readTextFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        private const val KEY_LAST_BACKUP = "key_last_backup_timestamp"
        private const val KEY_BACKUP_REMINDER_ENABLED = "key_backup_reminder_enabled"
        private const val KEY_BACKUP_REMINDER_FREQUENCY = "key_backup_reminder_frequency"
        private const val KEY_NEXT_BACKUP_REMINDER_TIME = "key_next_backup_reminder_time"
        const val BACKUP_REMINDER_REQUEST_CODE = 998877
    }

    fun isBackupReminderEnabled(): Boolean {
        return prefs.getBoolean(KEY_BACKUP_REMINDER_ENABLED, false)
    }

    fun setBackupReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKUP_REMINDER_ENABLED, enabled).apply()
        if (enabled) {
            scheduleNextBackupReminder()
        } else {
            cancelBackupReminder()
        }
    }

    fun getBackupReminderFrequency(): String {
        return prefs.getString(KEY_BACKUP_REMINDER_FREQUENCY, "Weekly") ?: "Weekly"
    }

    fun setBackupReminderFrequency(frequency: String) {
        prefs.edit().putString(KEY_BACKUP_REMINDER_FREQUENCY, frequency).apply()
        if (isBackupReminderEnabled()) {
            scheduleNextBackupReminder()
        }
    }

    fun getNextBackupReminderTime(): Long {
        return prefs.getLong(KEY_NEXT_BACKUP_REMINDER_TIME, 0L)
    }

    fun calculateNextReminderTime(frequency: String): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 20)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)

        val now = System.currentTimeMillis()
        if (calendar.timeInMillis <= now) {
            when (frequency.lowercase(Locale.ROOT)) {
                "daily" -> calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                "weekly" -> calendar.add(java.util.Calendar.DAY_OF_YEAR, 7)
                "monthly" -> calendar.add(java.util.Calendar.MONTH, 1)
                else -> calendar.add(java.util.Calendar.DAY_OF_YEAR, 7)
            }
        }
        return calendar.timeInMillis
    }

    fun scheduleNextBackupReminder() {
        val frequency = getBackupReminderFrequency()
        val nextTime = calculateNextReminderTime(frequency)
        prefs.edit().putLong(KEY_NEXT_BACKUP_REMINDER_TIME, nextTime).apply()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
        val intent = Intent(context, com.example.notification.BackupReminderReceiver::class.java).apply {
            action = com.example.notification.BackupReminderReceiver.ACTION_BACKUP_REMINDER
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            BACKUP_REMINDER_REQUEST_CODE,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    nextTime,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    android.app.AlarmManager.RTC_WAKEUP,
                    nextTime,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelBackupReminder() {
        prefs.edit().remove(KEY_NEXT_BACKUP_REMINDER_TIME).apply()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
        val intent = Intent(context, com.example.notification.BackupReminderReceiver::class.java).apply {
            action = com.example.notification.BackupReminderReceiver.ACTION_BACKUP_REMINDER
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            BACKUP_REMINDER_REQUEST_CODE,
            intent,
            android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
