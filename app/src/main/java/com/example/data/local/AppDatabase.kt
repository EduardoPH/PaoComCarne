package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.AttendanceAuditDao
import com.example.data.local.dao.AttendanceDao
import com.example.data.local.dao.AttendanceMemberDao
import com.example.data.local.dao.HosterDao
import com.example.data.local.dao.QueueEventDao
import com.example.data.local.dao.ShiftDao
import com.example.data.local.dao.SyncQueueDao
import com.example.data.local.entity.AttendanceAuditEntity
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.AttendanceMemberEntity
import com.example.data.local.entity.HosterEntity
import com.example.data.local.entity.QueueEventEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.SyncQueueEntity

@Database(
    entities = [
        HosterEntity::class,
        ShiftEntity::class,
        AttendanceEntity::class,
        AttendanceMemberEntity::class,
        AttendanceAuditEntity::class,
        QueueEventEntity::class,
        SyncQueueEntity::class
    ],
    version = 1,
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pao_com_carne_local.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
