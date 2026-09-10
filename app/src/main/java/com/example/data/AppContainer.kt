package com.example.data

import android.content.Context
import com.example.backup.BackupManager
import com.example.data.local.AppDatabase
import com.example.data.repository.AppRepository
import com.example.notification.AlarmScheduler
import com.example.notification.AndroidAlarmScheduler
import com.example.security.AppSecurityManager

import com.example.settings.VoiceSettingsManager

interface AppContainer {
    val appRepository: AppRepository
    val alarmScheduler: AlarmScheduler
    val securityManager: AppSecurityManager
    val backupManager: BackupManager
    val voiceSettingsManager: VoiceSettingsManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val database: AppDatabase by lazy { AppDatabase.getDatabase(context) }
    
    override val appRepository: AppRepository by lazy {
        AppRepository(database.partyDao(), database.transactionDao(), database.reminderDao())
    }

    override val alarmScheduler: AlarmScheduler by lazy {
        AndroidAlarmScheduler(context)
    }

    override val securityManager: AppSecurityManager by lazy {
        AppSecurityManager(context)
    }

    override val backupManager: BackupManager by lazy {
        BackupManager(context, appRepository, alarmScheduler)
    }

    override val voiceSettingsManager: VoiceSettingsManager by lazy {
        VoiceSettingsManager(context)
    }
}
