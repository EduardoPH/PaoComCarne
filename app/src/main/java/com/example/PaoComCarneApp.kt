package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.AuthRepository
import com.example.data.repository.ExportRepository
import com.example.data.repository.ReceptionRepository
import com.example.data.sync.SyncManager

class PaoComCarneApp : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var syncManager: SyncManager
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var receptionRepository: ReceptionRepository
        private set
    lateinit var exportRepository: ExportRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        syncManager = SyncManager(this)
        authRepository = AuthRepository(this, database, syncManager)
        receptionRepository = ReceptionRepository(database, syncManager)
        exportRepository = ExportRepository(this, database)
    }

    companion object {
        lateinit var instance: PaoComCarneApp
            private set
    }
}
