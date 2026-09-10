package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String, // "GST", "Income Tax", "Payment Receive", "Payment Paid", "Custom"
    val tag: String = "General", // "Tax", "Payment", "General"
    val partyId: Long? = null,
    val amount: Double? = null,
    val dueDate: Long,
    val dueTime: Long? = null,
    val repeatType: String, // "One-time", "Daily", "Weekly", "Monthly", "Quarterly", "Yearly", "Custom"
    val notificationSettings: String, // e.g., "1 day before"
    val priority: String, // "High", "Medium", "Low"
    val status: String, // "Pending", "Completed", "Overdue", "Snoozed"
    val notes: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
