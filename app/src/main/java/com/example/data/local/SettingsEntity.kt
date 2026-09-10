package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val appTheme: String = "System Default",
    val dateFormat: String = "dd/MM/yyyy",
    val currency: String = "₹",
    val language: String = "English",
    val notificationsEnabled: Boolean = true,
    val defaultReminderTime: String = "09:00",
    val voiceControlEnabled: Boolean = true
)
