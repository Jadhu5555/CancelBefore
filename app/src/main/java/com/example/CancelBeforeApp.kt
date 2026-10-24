package com.example

import android.app.Application
import com.example.data.CancelBeforeDatabase
import com.example.data.PreferencesRepository
import com.example.service.ReminderScheduler

class CancelBeforeApp : Application() {

    lateinit var database: CancelBeforeDatabase
        private set

    lateinit var preferencesRepository: PreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = CancelBeforeDatabase.getDatabase(this)
        preferencesRepository = PreferencesRepository(this)
        ReminderScheduler.createNotificationChannel(this)
    }

    companion object {
        lateinit var instance: CancelBeforeApp
            private set
    }
}
