package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AttendanceAuditEntity
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.AttendanceMemberEntity
import com.example.data.local.entity.HosterEntity
import com.example.data.local.entity.QueueEventEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HosterDao {
    @Query("SELECT * FROM hosters WHERE active = 1 ORDER BY name ASC")
    fun getAllActiveHostersFlow(): Flow<List<HosterEntity>>

    @Query("SELECT * FROM hosters WHERE active = 1 ORDER BY name ASC")
    suspend fun getAllActiveHosters(): List<HosterEntity>

    @Query("SELECT * FROM hosters WHERE id = :id LIMIT 1")
    suspend fun getHosterById(id: String): HosterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateHosters(hosters: List<HosterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateHoster(hoster: HosterEntity)

    @Query("DELETE FROM hosters WHERE id = :id")
    suspend fun deleteHosterById(id: String)

    @Query("SELECT COUNT(*) FROM hosters")
    suspend fun countHosters(): Int
}

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE hosterId = :hosterId AND status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
    fun getActiveShiftForHosterFlow(hosterId: String): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts WHERE hosterId = :hosterId AND status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
    suspend fun getActiveShiftForHoster(hosterId: String): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE id = :id LIMIT 1")
    suspend fun getShiftById(id: String): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE id = :id LIMIT 1")
    fun getShiftByIdFlow(id: String): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts ORDER BY openedAt DESC")
    fun getAllShiftsFlow(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts ORDER BY openedAt DESC")
    suspend fun getAllShifts(): List<ShiftEntity>

    @Query("SELECT * FROM shifts WHERE status = 'OPEN' ORDER BY openedAt DESC")
    fun getOpenShiftsFlow(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE status = 'OPEN' ORDER BY openedAt DESC")
    suspend fun getOpenShifts(): List<ShiftEntity>

    @Query("SELECT * FROM shifts WHERE status = 'OPEN' AND openedAt >= :startMillis AND openedAt <= :endMillis")
    suspend fun getOpenShiftsInRange(startMillis: Long, endMillis: Long): List<ShiftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateShift(shift: ShiftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateShifts(shifts: List<ShiftEntity>)

    @Update
    suspend fun updateShift(shift: ShiftEntity)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendances WHERE shiftId = :shiftId ORDER BY createdAt ASC")
    fun getAttendancesForShiftFlow(shiftId: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendances WHERE shiftId = :shiftId ORDER BY createdAt ASC")
    suspend fun getAttendancesForShift(shiftId: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendances WHERE shiftId = :shiftId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLastAttendanceForShift(shiftId: String): AttendanceEntity?

    @Query("SELECT * FROM attendances WHERE shiftId = :shiftId ORDER BY createdAt DESC LIMIT 1")
    fun getLastAttendanceForShiftFlow(shiftId: String): Flow<AttendanceEntity?>

    @Query("SELECT * FROM attendances WHERE id = :id LIMIT 1")
    suspend fun getAttendanceById(id: String): AttendanceEntity?

    @Query("SELECT * FROM attendances ORDER BY createdAt DESC")
    fun getAllAttendancesFlow(): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendances ORDER BY createdAt DESC")
    suspend fun getAllAttendances(): List<AttendanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAttendance(attendance: AttendanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAttendances(attendances: List<AttendanceEntity>)

    @Query("SELECT COUNT(*) FROM attendances WHERE shiftId = :shiftId")
    suspend fun countAttendancesInShift(shiftId: String): Int
}

@Dao
interface AttendanceMemberDao {
    @Query("SELECT * FROM attendance_members WHERE attendanceId = :attendanceId ORDER BY memberNumber ASC")
    suspend fun getMembersForAttendance(attendanceId: String): List<AttendanceMemberEntity>

    @Query("SELECT * FROM attendance_members WHERE attendanceId = :attendanceId ORDER BY memberNumber ASC")
    fun getMembersForAttendanceFlow(attendanceId: String): Flow<List<AttendanceMemberEntity>>

    @Query("SELECT * FROM attendance_members")
    suspend fun getAllMembers(): List<AttendanceMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<AttendanceMemberEntity>)

    @Query("DELETE FROM attendance_members WHERE attendanceId = :attendanceId")
    suspend fun deleteMembersForAttendance(attendanceId: String)
}

@Dao
interface AttendanceAuditDao {
    @Query("SELECT * FROM attendance_audit WHERE attendanceId = :attendanceId ORDER BY changedAt DESC")
    suspend fun getAuditsForAttendance(attendanceId: String): List<AttendanceAuditEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(audit: AttendanceAuditEntity)
}

@Dao
interface QueueEventDao {
    @Query("SELECT * FROM queue_events WHERE shiftId = :shiftId ORDER BY createdAt DESC LIMIT 1")
    fun getLatestQueueEventFlow(shiftId: String): Flow<QueueEventEntity?>

    @Query("SELECT * FROM queue_events WHERE shiftId = :shiftId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestQueueEvent(shiftId: String): QueueEventEntity?

    @Query("SELECT * FROM queue_events WHERE shiftId = :shiftId ORDER BY createdAt DESC")
    fun getAllQueueEventsForShiftFlow(shiftId: String): Flow<List<QueueEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueEvent(event: QueueEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueEvents(events: List<QueueEventEntity>)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status IN ('PENDING', 'FAILED', 'SYNCING') ORDER BY createdAt ASC")
    suspend fun getPendingOperations(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue ORDER BY createdAt DESC")
    fun getAllQueueFlow(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue ORDER BY createdAt DESC")
    suspend fun getAllQueue(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('PENDING', 'FAILED', 'SYNCING')")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('PENDING', 'FAILED', 'SYNCING')")
    suspend fun getPendingCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Update
    suspend fun update(item: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sync_queue SET status = :newStatus WHERE id = :id")
    suspend fun updateStatus(id: Long, newStatus: String)

    @Query("UPDATE sync_queue SET status = 'PENDING', attempts = 0 WHERE status = 'FAILED'")
    suspend fun resetFailedToPending()
}
