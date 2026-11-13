package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.DeviceIdentityManager
import com.example.data.repository.AuthRepository
import com.example.data.repository.ExportRepository
import com.example.data.repository.ReceptionRepository
import com.example.data.repository.SystemLogRepository
import com.example.data.sync.SyncManager
import com.example.data.update.AppUpdateManager

class PaoComCarneApp : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var deviceIdentityManager: DeviceIdentityManager
        private set
    lateinit var systemLogRepository: SystemLogRepository
        private set
    lateinit var syncManager: SyncManager
        private set
    lateinit var appUpdateManager: AppUpdateManager
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
        deviceIdentityManager = DeviceIdentityManager(this)
        systemLogRepository = SystemLogRepository(database, deviceIdentityManager)
        syncManager = SyncManager(this)
        appUpdateManager = AppUpdateManager(this, database, systemLogRepository)

        systemLogRepository.setOnEnqueueCallback {
            syncManager.flushPendingQueueNow()
        }

        authRepository = AuthRepository(this, database, syncManager, systemLogRepository)
        receptionRepository = ReceptionRepository(database, syncManager, systemLogRepository)
        exportRepository = ExportRepository(this, database, systemLogRepository)

        syncManager.attachDependencies(systemLogRepository) { isOnline ->
            val current = authRepository.currentHoster.value
            appUpdateManager.checkVersionPolicy(
                isOnline = isOnline,
                userId = current?.id,
                userName = current?.name
            )
        }
    }

    companion object {
        lateinit var instance: PaoComCarneApp
            private set
    }
}
