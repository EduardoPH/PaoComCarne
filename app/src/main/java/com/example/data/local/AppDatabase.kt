package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AppReleaseDao
import com.example.data.local.dao.AttendanceAuditDao
import com.example.data.local.dao.AttendanceDao
import com.example.data.local.dao.AttendanceMemberDao
import com.example.data.local.dao.HosterDao
import com.example.data.local.dao.QueueEventDao
import com.example.data.local.dao.ShiftDao
import com.example.data.local.dao.SyncQueueDao
import com.example.data.local.dao.SystemLogDao
import com.example.data.local.entity.AppReleaseEntity
import com.example.data.local.entity.AttendanceAuditEntity
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.AttendanceMemberEntity
import com.example.data.local.entity.HosterEntity
import com.example.data.local.entity.QueueEventEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.SyncQueueEntity
import com.example.data.local.entity.SystemLogEntity

@Database(
    entities = [
        HosterEntity::class,
        ShiftEntity::class,
        AttendanceEntity::class,
        AttendanceMemberEntity::class,
        AttendanceAuditEntity::class,
        QueueEventEntity::class,
        SyncQueueEntity::class,
        SystemLogEntity::class,
        AppReleaseEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hosterDao(): HosterDao
    abstract fun shiftDao(): ShiftDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun attendanceMemberDao(): AttendanceMemberDao
    abstract fun attendanceAuditDao(): AttendanceAuditDao
    abstract fun queueEventDao(): QueueEventDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun systemLogDao(): SystemLogDao
    abstract fun appReleaseDao(): AppReleaseDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attendances ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE attendances ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE attendances ADD COLUMN deletedBy TEXT")
                db.execSQL("ALTER TABLE attendances ADD COLUMN deletionReason TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_attendances_isDeleted ON attendances(isDeleted)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS system_logs (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT,
                        userName TEXT,
                        action TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT,
                        deviceName TEXT NOT NULL,
                        result TEXT NOT NULL,
                        description TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        syncedAt INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_system_logs_createdAt ON system_logs(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_system_logs_action ON system_logs(action)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_system_logs_userId ON system_logs(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_system_logs_result ON system_logs(result)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_system_logs_deviceName ON system_logs(deviceName)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_releases (
                        id TEXT NOT NULL PRIMARY KEY,
                        versionName TEXT NOT NULL,
                        versionCode INTEGER NOT NULL,
                        minSupportedCode INTEGER NOT NULL,
                        mandatory INTEGER NOT NULL,
                        apkPath TEXT NOT NULL,
                        sha256 TEXT NOT NULL,
                        changelog TEXT NOT NULL,
                        releaseReason TEXT NOT NULL,
                        active INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        confirmedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_releases_active ON app_releases(active)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_releases_versionCode ON app_releases(versionCode)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pao_com_carne_local.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
