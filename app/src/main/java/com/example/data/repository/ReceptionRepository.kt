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
import com.example.domain.model.Hoster
import com.example.domain.model.LogResult
import com.example.domain.model.QueueEvent
import com.example.domain.model.Shift
import com.example.domain.model.ShiftPeriod
import com.example.domain.model.ShiftStatus
import com.example.domain.model.SystemLog
import com.example.domain.model.SystemLogActions
import com.example.domain.util.DateTimeUtils
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class ReceptionRepository(
    private val database: AppDatabase,
    private val syncManager: SyncManager,
    private val systemLogRepository: SystemLogRepository? = null
) {
    private val hosterDao = database.hosterDao()
    private val shiftDao = database.shiftDao()
    private val attendanceDao = database.attendanceDao()
    private val memberDao = database.attendanceMemberDao()
    private val auditDao = database.attendanceAuditDao()
    private val queueDao = database.queueEventDao()
    private val syncQueueDao = database.syncQueueDao()
    private val systemLogDao = database.systemLogDao()

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
            systemLogRepository?.recordLog(
                action = SystemLogActions.SHIFT_INVALID_OPEN,
                entityType = "SHIFT",
                entityId = existing.id,
                result = LogResult.FAILURE,
                description = "Tentativa de abrir turno ${period.displayName} com turno já aberto para $hosterName.",
                userId = hosterId,
                userName = hosterName
            )
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
        systemLogRepository?.recordLog(
            action = SystemLogActions.SHIFT_OPEN,
            entityType = "SHIFT",
            entityId = shiftId,
            result = LogResult.SUCCESS,
            description = "Turno ${period.displayName} aberto por $hosterName.",
            userId = hosterId,
            userName = hosterName
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
        systemLogRepository?.recordLog(
            action = SystemLogActions.SHIFT_CLOSE,
            entityType = "SHIFT",
            entityId = shiftId,
            result = LogResult.SUCCESS,
            description = "Turno ${shift.period} encerrado por ${shift.hosterName}.",
            userId = shift.hosterId,
            userName = shift.hosterName
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
        if (shift == null || shift.status != ShiftStatus.OPEN.name) {
            val reason = if (shift == null) "Turno inexistente." else "O turno já está encerrado."
            systemLogRepository?.recordLog(
                action = SystemLogActions.ATTENDANCE_SAVE_FAILURE,
                entityType = "ATTENDANCE",
                entityId = shiftId,
                result = LogResult.FAILURE,
                description = "Falha ao registrar atendimento: $reason",
                userId = hosterId,
                userName = hosterName
            )
            return@withContext Result.failure(Exception(reason))
        }

        val validationError = when {
            type == AttendanceType.SOLO && totalMembers != 1 -> "Solo deve ser exatamente 1 pessoa."
            type == AttendanceType.DUPLA && totalMembers != 2 -> "Dupla deve ser exatamente 2 pessoas."
            type == AttendanceType.GRUPO && totalMembers < 3 -> "Grupo deve ser de no mínimo 3 pessoas."
            newMembers + returningMembers != totalMembers -> "A soma de novos e recorrentes deve ser igual ao total."
            membersList.size != totalMembers -> "Classificação incompleta dos integrantes."
            membersList.any { it.classification == ClientClassification.NOVO && it.origin.isNullOrBlank() } ->
                "Todos os clientes novos precisam de uma origem."
            else -> null
        }

        if (validationError != null) {
            systemLogRepository?.recordLog(
                action = SystemLogActions.ATTENDANCE_SAVE_FAILURE,
                entityType = "ATTENDANCE",
                entityId = shiftId,
                result = LogResult.FAILURE,
                description = "Falha de validação ao registrar atendimento (${type.displayName}): $validationError",
                userId = hosterId,
                userName = hosterName
            )
            return@withContext Result.failure(Exception(validationError))
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
            isDeleted = false,
            deletedAt = null,
            deletedBy = null,
            deletionReason = null,
            syncStatus = "PENDENTE"
        )
        attendanceDao.insertOrUpdateAttendance(attendanceEntity)

        val memberEntities = membersList.mapIndexed { index, m ->
            val cleanOrigin = if (m.classification == ClientClassification.NOVO) {
                m.origin?.trim()?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            AttendanceMemberEntity(
                id = UUID.randomUUID().toString(),
                attendanceId = attendanceId,
                memberNumber = index + 1,
                classification = m.classification.name,
                origin = cleanOrigin,
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
            isPreferential = isPreferential,
            isDeleted = false
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
        val membersJson = SupabaseClient.moshi.adapter<List<SupabaseMemberDto>>(membersType).serializeNulls().toJson(memberDtos)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "ATTENDANCE_MEMBERS",
                entityId = attendanceId,
                operation = "INSERT",
                payloadJson = membersJson,
                status = "PENDING"
            )
        )

        val prefSuffix = if (isPreferential) " [PREF]" else ""
        systemLogRepository?.recordLog(
            action = SystemLogActions.ATTENDANCE_CREATE,
            entityType = "ATTENDANCE",
            entityId = attendanceId,
            result = LogResult.SUCCESS,
            description = "${type.displayName}$prefSuffix com $totalMembers p. ($newMembers novos, $returningMembers rec.) registrado por $hosterName.",
            userId = hosterId,
            userName = hosterName
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
            val cleanOrigin = if (m.classification == ClientClassification.NOVO) {
                m.origin?.trim()?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            AttendanceMemberEntity(
                id = UUID.randomUUID().toString(),
                attendanceId = attendanceId,
                memberNumber = index + 1,
                classification = m.classification.name,
                origin = cleanOrigin,
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
        val membersJson = SupabaseClient.moshi.adapter<List<SupabaseMemberDto>>(membersType).serializeNulls().toJson(memberDtos)
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

        systemLogRepository?.recordLog(
            action = SystemLogActions.ATTENDANCE_EDIT,
            entityType = "ATTENDANCE",
            entityId = attendanceId,
            result = LogResult.SUCCESS,
            description = "Atendimento corrigido por $editorHosterName: $prevDataSummary -> $newDataSummary.",
            userId = editorHosterId,
            userName = editorHosterName
        )

        syncManager.triggerSync()
        Result.success(Unit)
    }

    suspend fun softDeleteAttendance(
        attendanceId: String,
        actorHosterId: String,
        actorHosterName: String,
        isActorAdmin: Boolean,
        reason: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val attendance = attendanceDao.getAttendanceById(attendanceId)
        if (attendance == null || attendance.isDeleted) {
            val msg = if (attendance == null) "Atendimento não encontrado." else "Este atendimento já foi excluído."
            systemLogRepository?.recordLog(
                action = SystemLogActions.ATTENDANCE_DELETE_FAILURE,
                entityType = "ATTENDANCE",
                entityId = attendanceId,
                result = LogResult.FAILURE,
                description = "Falha ao excluir atendimento: $msg",
                userId = actorHosterId,
                userName = actorHosterName
            )
            return@withContext Result.failure(Exception(msg))
        }

        val shift = shiftDao.getShiftById(attendance.shiftId)
        if (shift?.status == ShiftStatus.CLOSED.name && !isActorAdmin) {
            val msg = "Não é permitido excluir atendimentos de um turno encerrado sem autorização administrativa."
            systemLogRepository?.recordLog(
                action = SystemLogActions.ATTENDANCE_DELETE_FAILURE,
                entityType = "ATTENDANCE",
                entityId = attendanceId,
                result = LogResult.FAILURE,
                description = "Exclusão bloqueada em turno encerrado (${shift.id}) para $actorHosterName.",
                userId = actorHosterId,
                userName = actorHosterName
            )
            return@withContext Result.failure(Exception(msg))
        }

        if (!isActorAdmin && attendance.hosterId != actorHosterId) {
            val msg = "Você só pode excluir atendimentos vinculados ao seu usuário."
            systemLogRepository?.recordLog(
                action = SystemLogActions.ATTENDANCE_DELETE_FAILURE,
                entityType = "ATTENDANCE",
                entityId = attendanceId,
                result = LogResult.FAILURE,
                description = "Exclusão negada: $actorHosterName tentou excluir atendimento de ${attendance.hosterName}.",
                userId = actorHosterId,
                userName = actorHosterName
            )
            return@withContext Result.failure(Exception(msg))
        }

        val now = System.currentTimeMillis()
        val cleanReason = reason?.trim()?.takeIf { it.isNotBlank() }
        val updated = attendance.copy(
            isDeleted = true,
            deletedAt = now,
            deletedBy = actorHosterName,
            deletionReason = cleanReason,
            updatedAt = now,
            syncStatus = "PENDENTE"
        )
        // Soft delete: attendance and linked attendance_members remain preserved in DB
        attendanceDao.insertOrUpdateAttendance(updated)

        val prevSummary = "${attendance.type} (${attendance.totalMembers}p: ${attendance.newMembers} novos, ${attendance.returningMembers} rec)"
        val newSummary = "EXCLUÍDO LOGICAMENTE por $actorHosterName" + (cleanReason?.let { " | Motivo: $it" } ?: "")

        val auditId = UUID.randomUUID().toString()
        auditDao.insertAudit(
            AttendanceAuditEntity(
                id = auditId,
                attendanceId = attendanceId,
                hosterId = actorHosterId,
                hosterName = actorHosterName,
                previousData = prevSummary,
                newData = newSummary,
                changedAt = now,
                syncStatus = "PENDENTE"
            )
        )

        val attDto = SupabaseAttendanceDto(
            id = updated.id,
            shiftId = updated.shiftId,
            hosterId = updated.hosterId,
            hosterName = updated.hosterName,
            type = updated.type,
            totalMembers = updated.totalMembers,
            newMembers = updated.newMembers,
            returningMembers = updated.returningMembers,
            createdAt = DateTimeUtils.toIsoUtc(updated.createdAt),
            updatedAt = DateTimeUtils.toIsoUtc(now),
            isPreferential = updated.isPreferential,
            isDeleted = true,
            deletedAt = DateTimeUtils.toIsoUtc(now),
            deletedBy = actorHosterName,
            deletionReason = cleanReason
        )
        val attJson = SupabaseClient.moshi
            .adapter(SupabaseAttendanceDto::class.java)
            .serializeNulls()
            .toJson(attDto)

        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "ATTENDANCE",
                entityId = attendanceId,
                operation = "UPDATE",
                payloadJson = attJson,
                status = "PENDING"
            )
        )

        val auditDto = SupabaseAuditDto(
            id = auditId,
            attendanceId = attendanceId,
            hosterId = actorHosterId,
            hosterName = actorHosterName,
            previousData = prevSummary,
            newData = newSummary,
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

        systemLogRepository?.recordLog(
            action = SystemLogActions.ATTENDANCE_DELETE,
            entityType = "ATTENDANCE",
            entityId = attendanceId,
            result = LogResult.SUCCESS,
            description = "Atendimento ${attendance.type} (${attendance.totalMembers}p) excluído logicamente por $actorHosterName." +
                (cleanReason?.let { " Motivo: $it" } ?: ""),
            userId = actorHosterId,
            userName = actorHosterName
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
            systemLogRepository?.recordLog(
                action = SystemLogActions.QUEUE_UPDATE_FAILURE,
                entityType = "QUEUE_EVENT",
                entityId = shiftId,
                result = LogResult.FAILURE,
                description = "Valores inválidos ao atualizar fila (duplas=$waitingDuos, grupos=$waitingGroups).",
                userId = hosterId
            )
            return@withContext Result.failure(Exception("Os valores da fila devem ser maiores ou iguais a zero."))
        }

        val shift = shiftDao.getShiftById(shiftId)
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

        systemLogRepository?.recordLog(
            action = SystemLogActions.QUEUE_UPDATE,
            entityType = "QUEUE_EVENT",
            entityId = eventId,
            result = LogResult.SUCCESS,
            description = "Fila atualizada: $waitingDuos dupla(s) e $waitingGroups grupo(s).",
            userId = hosterId,
            userName = shift?.hosterName
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

    fun getAllMembersFlow(): Flow<List<AttendanceMember>> {
        return memberDao.getAllMembersFlow().map { members ->
            members.map { it.toDomain() }
        }
    }

    fun getActiveHostersFlow(): Flow<List<Hoster>> {
        return hosterDao.getAllActiveHostersFlow().map { list ->
            list.filter { !it.isAdmin }.map { entity ->
                Hoster(
                    id = entity.id,
                    name = entity.name,
                    authUserId = entity.authUserId,
                    pinHash = entity.pinHash,
                    pinSalt = entity.pinSalt,
                    isActivated = entity.isActivated,
                    active = entity.active,
                    isAdmin = entity.isAdmin,
                    createdAt = entity.createdAt
                )
            }
        }
    }

    fun getSystemLogsFlow(): Flow<List<SystemLog>> {
        return systemLogDao.getAllLogsFlow().map { list -> list.map { it.toDomain() } }
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
    syncStatus = syncStatus,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    deletedBy = deletedBy,
    deletionReason = deletionReason
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
