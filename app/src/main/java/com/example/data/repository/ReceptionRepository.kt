package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entity.AttendanceAuditEntity
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.AttendanceMemberEntity
import com.example.data.local.entity.QueueEventEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.SyncQueueEntity
import com.example.data.remote.SupabaseAttendanceDto
import com.example.data.remote.SupabaseAuditDto
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseMemberDto
import com.example.data.remote.SupabaseQueueDto
import com.example.data.remote.SupabaseShiftDto
import com.example.data.sync.SyncManager
import com.example.domain.model.Attendance
import com.example.domain.model.AttendanceMember
import com.example.domain.model.AttendanceType
import com.example.domain.model.AttendanceWithMembers
import com.example.domain.model.ClientClassification
import com.example.domain.model.QueueEvent
import com.example.domain.model.Shift
import com.example.domain.model.ShiftPeriod
import com.example.domain.model.ShiftStatus
import com.example.domain.util.DateTimeUtils
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class ReceptionRepository(
    private val database: AppDatabase,
    private val syncManager: SyncManager
) {
    private val shiftDao = database.shiftDao()
    private val attendanceDao = database.attendanceDao()
    private val memberDao = database.attendanceMemberDao()
    private val auditDao = database.attendanceAuditDao()
    private val queueDao = database.queueEventDao()
    private val syncQueueDao = database.syncQueueDao()

    // --- Shift Operations ---

    fun getActiveShiftFlow(hosterId: String): Flow<Shift?> {
        return shiftDao.getActiveShiftForHosterFlow(hosterId).map { entity ->
            entity?.toDomain()
        }
    }

    suspend fun getActiveShift(hosterId: String): Shift? {
        return shiftDao.getActiveShiftForHoster(hosterId)?.toDomain()
    }

    suspend fun openShift(
        hosterId: String,
        hosterName: String,
        period: ShiftPeriod
    ): Result<Shift> = withContext(Dispatchers.IO) {
        val existing = shiftDao.getActiveShiftForHoster(hosterId)
        if (existing != null) {
            return@withContext Result.failure(Exception("Já existe um turno ativo para este hoster."))
        }

        val shiftId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entity = ShiftEntity(
            id = shiftId,
            hosterId = hosterId,
            hosterName = hosterName,
            period = period.name,
            openedAt = now,
            closedAt = null,
            status = ShiftStatus.OPEN.name,
            deviceCreatedAt = now,
            syncedAt = null,
            syncStatus = "PENDENTE"
        )
        shiftDao.insertOrUpdateShift(entity)

        // Enqueue shift sync
        val dto = SupabaseShiftDto(
            id = shiftId,
            hosterId = hosterId,
            hosterName = hosterName,
            period = period.name,
            openedAt = DateTimeUtils.toIsoUtc(now),
            closedAt = null,
            status = ShiftStatus.OPEN.name,
            deviceCreatedAt = DateTimeUtils.toIsoUtc(now)
        )
        val json = SupabaseClient.moshi.adapter(SupabaseShiftDto::class.java).toJson(dto)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "SHIFT",
                entityId = shiftId,
                operation = "INSERT",
                payloadJson = json,
                status = "PENDING"
            )
        )
        syncManager.triggerSync()

        Result.success(entity.toDomain())
    }

    suspend fun closeShift(shiftId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val shift = shiftDao.getShiftById(shiftId)
            ?: return@withContext Result.failure(Exception("Turno não encontrado."))

        val now = System.currentTimeMillis()
        val updated = shift.copy(
            closedAt = now,
            status = ShiftStatus.CLOSED.name,
            syncStatus = "PENDENTE"
        )
        shiftDao.updateShift(updated)

        val map = mapOf(
            "closed_at" to DateTimeUtils.toIsoUtc(now),
            "status" to ShiftStatus.CLOSED.name
        )
        val json = SupabaseClient.moshi.adapter(Map::class.java).toJson(map)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "SHIFT_CLOSE",
                entityId = shiftId,
                operation = "UPDATE",
                payloadJson = json,
                status = "PENDING"
            )
        )
        syncManager.triggerSync()

        Result.success(Unit)
    }

    // --- Attendances Operations ---

    suspend fun registerAttendance(
        shiftId: String,
        hosterId: String,
        hosterName: String,
        type: AttendanceType,
        totalMembers: Int,
        newMembers: Int,
        returningMembers: Int,
        membersList: List<AttendanceMember>,
        isPreferential: Boolean = false
    ): Result<Attendance> = withContext(Dispatchers.IO) {
        // Strict Business Rule Validations
        val shift = shiftDao.getShiftById(shiftId)
            ?: return@withContext Result.failure(Exception("Turno inexistente."))
        if (shift.status != ShiftStatus.OPEN.name) {
            return@withContext Result.failure(Exception("O turno já está encerrado."))
        }

        when (type) {
            AttendanceType.SOLO -> if (totalMembers != 1) return@withContext Result.failure(Exception("Solo deve ser exatamente 1 pessoa."))
            AttendanceType.DUPLA -> if (totalMembers != 2) return@withContext Result.failure(Exception("Dupla deve ser exatamente 2 pessoas."))
            AttendanceType.GRUPO -> if (totalMembers < 3) return@withContext Result.failure(Exception("Grupo deve ser de no mínimo 3 pessoas."))
        }

        if (newMembers + returningMembers != totalMembers) {
            return@withContext Result.failure(Exception("A soma de novos e recorrentes deve ser igual ao total."))
        }

        if (membersList.size != totalMembers) {
            return@withContext Result.failure(Exception("Classificação incompleta dos integrantes."))
        }

        // Verify each new member has origin
        for (m in membersList) {
            if (m.classification == ClientClassification.NOVO && m.origin.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Todos os clientes novos precisam de uma origem."))
            }
        }

        val attendanceId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val attendanceEntity = AttendanceEntity(
            id = attendanceId,
            shiftId = shiftId,
            hosterId = hosterId,
            hosterName = hosterName,
            type = type.name,
            totalMembers = totalMembers,
            newMembers = newMembers,
            returningMembers = returningMembers,
            createdAt = now,
            updatedAt = now,
            isEdited = false,
            isPreferential = isPreferential,
            syncStatus = "PENDENTE"
        )
        attendanceDao.insertOrUpdateAttendance(attendanceEntity)

        val memberEntities = membersList.mapIndexed { index, m ->
            AttendanceMemberEntity(
                id = UUID.randomUUID().toString(),
                attendanceId = attendanceId,
                memberNumber = index + 1,
                classification = m.classification.name,
                origin = if (m.classification == ClientClassification.NOVO) m.origin else null,
                createdAt = now
            )
        }
        memberDao.insertMembers(memberEntities)

        // Queue for sync
        val attDto = SupabaseAttendanceDto(
            id = attendanceId,
            shiftId = shiftId,
            hosterId = hosterId,
            hosterName = hosterName,
            type = type.name,
            totalMembers = totalMembers,
            newMembers = newMembers,
            returningMembers = returningMembers,
            createdAt = DateTimeUtils.toIsoUtc(now),
            updatedAt = DateTimeUtils.toIsoUtc(now),
            isPreferential = isPreferential
        )
        val attJson = SupabaseClient.moshi.adapter(SupabaseAttendanceDto::class.java).toJson(attDto)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "ATTENDANCE",
                entityId = attendanceId,
                operation = "INSERT",
                payloadJson = attJson,
                status = "PENDING"
            )
        )

        val memberDtos = memberEntities.map { me ->
            SupabaseMemberDto(
                id = me.id,
                attendanceId = me.attendanceId,
                memberNumber = me.memberNumber,
                classification = me.classification,
                origin = me.origin,
                createdAt = DateTimeUtils.toIsoUtc(me.createdAt)
            )
        }
        val membersType = Types.newParameterizedType(List::class.java, SupabaseMemberDto::class.java)
        val membersJson = SupabaseClient.moshi.adapter<List<SupabaseMemberDto>>(membersType).toJson(memberDtos)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "ATTENDANCE_MEMBERS",
                entityId = attendanceId,
                operation = "INSERT",
                payloadJson = membersJson,
                status = "PENDING"
            )
        )

        syncManager.triggerSync()
        Result.success(attendanceEntity.toDomain())
    }

    suspend fun getLastAttendance(shiftId: String): AttendanceWithMembers? = withContext(Dispatchers.IO) {
        val att = attendanceDao.getLastAttendanceForShift(shiftId) ?: return@withContext null
        val members = memberDao.getMembersForAttendance(att.id)
        AttendanceWithMembers(
            attendance = att.toDomain(),
            members = members.map { it.toDomain() }
        )
    }

    suspend fun correctLastAttendance(
        shiftId: String,
        attendanceId: String,
        editorHosterId: String,
        editorHosterName: String,
        newType: AttendanceType,
        newTotal: Int,
        newNewMembers: Int,
        newReturningMembers: Int,
        newMembersList: List<AttendanceMember>,
        newIsPreferential: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // Rule: Only the last attendance of the current OPEN shift can be corrected
        val shift = shiftDao.getShiftById(shiftId)
            ?: return@withContext Result.failure(Exception("Turno não encontrado."))
        if (shift.status != ShiftStatus.OPEN.name) {
            return@withContext Result.failure(Exception("Não é possível corrigir atendimento em turno encerrado."))
        }

        val lastAtt = attendanceDao.getLastAttendanceForShift(shiftId)
            ?: return@withContext Result.failure(Exception("Nenhum atendimento encontrado."))

        if (lastAtt.id != attendanceId) {
            return@withContext Result.failure(Exception("Apenas o último atendimento registrado pode ser corrigido."))
        }

        if (newNewMembers + newReturningMembers != newTotal) {
            return@withContext Result.failure(Exception("A soma de novos e recorrentes deve ser igual ao total."))
        }

        for (m in newMembersList) {
            if (m.classification == ClientClassification.NOVO && m.origin.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Todos os clientes novos precisam de uma origem."))
            }
        }

        val previousMembers = memberDao.getMembersForAttendance(attendanceId)
        val prefTagOld = if (lastAtt.isPreferential) " [PREF]" else ""
        val prefTagNew = if (newIsPreferential) " [PREF]" else ""
        val prevDataSummary = "${lastAtt.type}$prefTagOld (${lastAtt.totalMembers}p: ${lastAtt.newMembers} novos, ${lastAtt.returningMembers} rec)"

        val now = System.currentTimeMillis()
        val updatedAttendance = lastAtt.copy(
            type = newType.name,
            totalMembers = newTotal,
            newMembers = newNewMembers,
            returningMembers = newReturningMembers,
            updatedAt = now,
            isEdited = true,
            isPreferential = newIsPreferential,
            syncStatus = "PENDENTE"
        )
        attendanceDao.insertOrUpdateAttendance(updatedAttendance)

        // Replace members
        memberDao.deleteMembersForAttendance(attendanceId)
        val newMemberEntities = newMembersList.mapIndexed { index, m ->
            AttendanceMemberEntity(
                id = UUID.randomUUID().toString(),
                attendanceId = attendanceId,
                memberNumber = index + 1,
                classification = m.classification.name,
                origin = if (m.classification == ClientClassification.NOVO) m.origin else null,
                createdAt = now
            )
        }
        memberDao.insertMembers(newMemberEntities)

        val newDataSummary = "${newType.name}$prefTagNew (${newTotal}p: ${newNewMembers} novos, ${newReturningMembers} rec)"

        // Audit Trail
        val auditId = UUID.randomUUID().toString()
        val auditEntity = AttendanceAuditEntity(
            id = auditId,
            attendanceId = attendanceId,
            hosterId = editorHosterId,
            hosterName = editorHosterName,
            previousData = prevDataSummary,
            newData = newDataSummary,
            changedAt = now,
            syncStatus = "PENDENTE"
        )
        auditDao.insertAudit(auditEntity)

        // Enqueue update to Supabase
        val attDto = SupabaseAttendanceDto(
            id = attendanceId,
            shiftId = shiftId,
            hosterId = lastAtt.hosterId,
            hosterName = lastAtt.hosterName,
            type = newType.name,
            totalMembers = newTotal,
            newMembers = newNewMembers,
            returningMembers = newReturningMembers,
            createdAt = DateTimeUtils.toIsoUtc(lastAtt.createdAt),
            updatedAt = DateTimeUtils.toIsoUtc(now),
            isPreferential = newIsPreferential
        )
        val attJson = SupabaseClient.moshi.adapter(SupabaseAttendanceDto::class.java).toJson(attDto)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "ATTENDANCE",
                entityId = attendanceId,
                operation = "UPDATE",
                payloadJson = attJson,
                status = "PENDING"
            )
        )

        val memberDtos = newMemberEntities.map { me ->
            SupabaseMemberDto(
                id = me.id,
                attendanceId = me.attendanceId,
                memberNumber = me.memberNumber,
                classification = me.classification,
                origin = me.origin,
                createdAt = DateTimeUtils.toIsoUtc(me.createdAt)
            )
        }
        val membersType = Types.newParameterizedType(List::class.java, SupabaseMemberDto::class.java)
        val membersJson = SupabaseClient.moshi.adapter<List<SupabaseMemberDto>>(membersType).toJson(memberDtos)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "ATTENDANCE_MEMBERS",
                entityId = attendanceId,
                operation = "UPDATE",
                payloadJson = membersJson,
                status = "PENDING"
            )
        )

        val auditDto = SupabaseAuditDto(
            id = auditId,
            attendanceId = attendanceId,
            hosterId = editorHosterId,
            hosterName = editorHosterName,
            previousData = prevDataSummary,
            newData = newDataSummary,
            changedAt = DateTimeUtils.toIsoUtc(now)
        )
        val auditJson = SupabaseClient.moshi.adapter(SupabaseAuditDto::class.java).toJson(auditDto)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "ATTENDANCE_AUDIT",
                entityId = auditId,
                operation = "INSERT",
                payloadJson = auditJson,
                status = "PENDING"
            )
        )

        syncManager.triggerSync()
        Result.success(Unit)
    }

    // --- Waitlist Queue ---

    fun getLatestQueueFlow(shiftId: String): Flow<QueueEvent?> {
        return queueDao.getLatestQueueEventFlow(shiftId).map { it?.toDomain() }
    }

    suspend fun updateQueue(
        shiftId: String,
        hosterId: String,
        waitingDuos: Int,
        waitingGroups: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (waitingDuos < 0 || waitingGroups < 0) {
            return@withContext Result.failure(Exception("Os valores da fila devem ser maiores ou iguais a zero."))
        }

        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entity = QueueEventEntity(
            id = eventId,
            shiftId = shiftId,
            hosterId = hosterId,
            waitingDuos = waitingDuos,
            waitingGroups = waitingGroups,
            createdAt = now,
            syncStatus = "PENDENTE"
        )
        queueDao.insertQueueEvent(entity)

        val dto = SupabaseQueueDto(
            id = eventId,
            shiftId = shiftId,
            hosterId = hosterId,
            waitingDuos = waitingDuos,
            waitingGroups = waitingGroups,
            createdAt = DateTimeUtils.toIsoUtc(now)
        )
        val json = SupabaseClient.moshi.adapter(SupabaseQueueDto::class.java).toJson(dto)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "QUEUE_EVENT",
                entityId = eventId,
                operation = "INSERT",
                payloadJson = json,
                status = "PENDING"
            )
        )

        syncManager.triggerSync()
        Result.success(Unit)
    }

    // --- Inspection & Admin queries ---

    fun getAttendancesWithMembersForShiftFlow(shiftId: String): Flow<List<AttendanceWithMembers>> {
        return attendanceDao.getAttendancesForShiftFlow(shiftId).map { attendances ->
            attendances.map { att ->
                val members = memberDao.getMembersForAttendance(att.id)
                AttendanceWithMembers(
                    attendance = att.toDomain(),
                    members = members.map { it.toDomain() }
                )
            }
        }
    }

    fun getAllShiftsFlow(): Flow<List<Shift>> {
        return shiftDao.getAllShiftsFlow().map { list -> list.map { it.toDomain() } }
    }

    fun getAllAttendancesFlow(): Flow<List<Attendance>> {
        return attendanceDao.getAllAttendancesFlow().map { list -> list.map { it.toDomain() } }
    }

    suspend fun getAllAttendances(): List<Attendance> = withContext(Dispatchers.IO) {
        attendanceDao.getAllAttendances().map { it.toDomain() }
    }

    suspend fun getAllMembers(): List<AttendanceMember> = withContext(Dispatchers.IO) {
        memberDao.getAllMembers().map { it.toDomain() }
    }

    suspend fun getOpenShifts(): List<Shift> = withContext(Dispatchers.IO) {
        shiftDao.getOpenShifts().map { it.toDomain() }
    }

    suspend fun getOpenShiftsInRange(startMillis: Long, endMillis: Long): List<Shift> = withContext(Dispatchers.IO) {
        shiftDao.getOpenShiftsInRange(startMillis, endMillis).map { it.toDomain() }
    }

    suspend fun getPendingSyncCount(): Int = withContext(Dispatchers.IO) {
        syncQueueDao.getPendingCount()
    }
}

// Extension mapper functions
private fun ShiftEntity.toDomain() = Shift(
    id = id,
    hosterId = hosterId,
    hosterName = hosterName,
    period = ShiftPeriod.valueOf(period),
    openedAt = openedAt,
    closedAt = closedAt,
    status = ShiftStatus.valueOf(status),
    deviceCreatedAt = deviceCreatedAt,
    syncedAt = syncedAt,
    syncStatus = syncStatus
)

private fun AttendanceEntity.toDomain() = Attendance(
    id = id,
    shiftId = shiftId,
    hosterId = hosterId,
    hosterName = hosterName,
    type = AttendanceType.valueOf(type),
    totalMembers = totalMembers,
    newMembers = newMembers,
    returningMembers = returningMembers,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isEdited = isEdited,
    isPreferential = isPreferential,
    syncStatus = syncStatus
)

private fun AttendanceMemberEntity.toDomain() = AttendanceMember(
    id = id,
    attendanceId = attendanceId,
    memberNumber = memberNumber,
    classification = ClientClassification.valueOf(classification),
    origin = origin,
    createdAt = createdAt
)

private fun QueueEventEntity.toDomain() = QueueEvent(
    id = id,
    shiftId = shiftId,
    hosterId = hosterId,
    waitingDuos = waitingDuos,
    waitingGroups = waitingGroups,
    createdAt = createdAt,
    syncStatus = syncStatus
)
