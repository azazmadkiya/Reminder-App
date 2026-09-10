package com.example

import android.app.Application
import com.example.data.AppContainer
import com.example.data.DefaultAppContainer
import com.example.notification.NotificationHelper

class ReminderApplication : Application() {
    lateinit var container: AppContainer
    
    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        NotificationHelper.createNotificationChannel(this)
    }
}
