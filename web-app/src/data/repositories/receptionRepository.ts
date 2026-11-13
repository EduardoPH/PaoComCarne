import { localDb } from '../local/database';
import { syncManager } from '../sync/syncManager';
import { systemLogRepository } from './systemLogRepository';
import type { ActionResult } from './authRepository';
import {
  AttendanceTypeDisplay,
  ShiftPeriodDisplay,
  SystemLogActions,
  type Attendance,
  type AttendanceMember,
  type AttendanceType,
  type AttendanceWithMembers,
  type QueueEvent,
  type Shift,
  type ShiftPeriod,
  type SystemLog,
} from '../../domain/models';
import { toIsoUtc } from '../../domain/utils/dateTime';

function genId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export const receptionRepository = {
  async getActiveShift(hosterId: string): Promise<Shift | null> {
    return localDb.getActiveShiftForHoster(hosterId);
  },

  async openShift(
    hosterId: string,
    hosterName: string,
    period: ShiftPeriod
  ): Promise<ActionResult<Shift>> {
    const existing = await localDb.getActiveShiftForHoster(hosterId);
    if (existing) {
      systemLogRepository.logAsync({
        action: SystemLogActions.SHIFT_INVALID_OPEN,
        entityType: 'SHIFT',
        entityId: existing.id,
        userId: hosterId,
        userName: hosterName,
        result: 'FAILURE',
        description: `Tentativa de abrir turno ${ShiftPeriodDisplay[period]} com turno ativo existente.`,
      });
      return { ok: false, error: 'Já existe um turno ativo para este hoster.' };
    }

    const shiftId = genId();
    const now = Date.now();
    const entity: Shift = {
      id: shiftId,
      hosterId,
      hosterName,
      period,
      openedAt: now,
      closedAt: null,
      status: 'OPEN',
      deviceCreatedAt: now,
      syncedAt: null,
      syncStatus: 'PENDENTE',
    };

    await localDb.insertOrUpdateShift(entity);

    await localDb.enqueueSyncOperation({
      entityType: 'SHIFT',
      entityId: shiftId,
      operation: 'INSERT',
      payloadJson: JSON.stringify({
        id: shiftId,
        hoster_id: hosterId,
        hoster_name: hosterName,
        period,
        opened_at: toIsoUtc(now),
        closed_at: null,
        status: 'OPEN',
        device_created_at: toIsoUtc(now),
      }),
      status: 'PENDING',
    });

    systemLogRepository.logAsync({
      action: SystemLogActions.SHIFT_OPEN,
      entityType: 'SHIFT',
      entityId: shiftId,
      userId: hosterId,
      userName: hosterName,
      result: 'SUCCESS',
      description: `Turno ${ShiftPeriodDisplay[period]} aberto por ${hosterName}.`,
    });

    void syncManager.triggerSync();
    return { ok: true, value: entity };
  },

  async closeShift(shiftId: string): Promise<ActionResult<void>> {
    const shift = await localDb.getShiftById(shiftId);
    if (!shift) return { ok: false, error: 'Turno não encontrado.' };

    const now = Date.now();
    await localDb.insertOrUpdateShift({
      ...shift,
      closedAt: now,
      status: 'CLOSED',
      syncStatus: 'PENDENTE',
    });

    await localDb.enqueueSyncOperation({
      entityType: 'SHIFT_CLOSE',
      entityId: shiftId,
      operation: 'UPDATE',
      payloadJson: JSON.stringify({
        closed_at: toIsoUtc(now),
        status: 'CLOSED',
      }),
      status: 'PENDING',
    });

    systemLogRepository.logAsync({
      action: SystemLogActions.SHIFT_CLOSE,
      entityType: 'SHIFT',
      entityId: shiftId,
      userId: shift.hosterId,
      userName: shift.hosterName,
      result: 'SUCCESS',
      description: `Turno ${ShiftPeriodDisplay[shift.period]} encerrado por ${shift.hosterName}.`,
    });

    void syncManager.triggerSync();
    return { ok: true, value: undefined };
  },

  async registerAttendance(params: {
    shiftId: string;
    hosterId: string;
    hosterName: string;
    type: AttendanceType;
    totalMembers: number;
    newMembers: number;
    returningMembers: number;
    membersList: AttendanceMember[];
    isPreferential?: boolean;
  }): Promise<ActionResult<Attendance>> {
    const {
      shiftId,
      hosterId,
      hosterName,
      type,
      totalMembers,
      newMembers,
      returningMembers,
      membersList,
      isPreferential = false,
    } = params;

    const failWithLog = (msg: string): ActionResult<Attendance> => {
      systemLogRepository.logAsync({
        action: SystemLogActions.ATTENDANCE_SAVE_FAILURE,
        entityType: 'ATTENDANCE',
        userId: hosterId,
        userName: hosterName,
        result: 'FAILURE',
        description: `Falha ao registrar atendimento: ${msg}`,
      });
      return { ok: false, error: msg };
    };

    const shift = await localDb.getShiftById(shiftId);
    if (!shift) return failWithLog('Turno inexistente.');
    if (shift.status !== 'OPEN') {
      return failWithLog('O turno já está encerrado.');
    }

    if (type === 'SOLO' && totalMembers !== 1) {
      return failWithLog('Solo deve ser exatamente 1 pessoa.');
    }
    if (type === 'DUPLA' && totalMembers !== 2) {
      return failWithLog('Dupla deve ser exatamente 2 pessoas.');
    }
    if (type === 'GRUPO' && totalMembers < 3) {
      return failWithLog('Grupo deve ser de no mínimo 3 pessoas.');
    }

    if (newMembers + returningMembers !== totalMembers) {
      return failWithLog('A soma de novos e recorrentes deve ser igual ao total.');
    }

    if (membersList.length !== totalMembers) {
      return failWithLog('Classificação incompleta dos integrantes.');
    }

    for (const m of membersList) {
      if (m.classification === 'NOVO' && (!m.origin || !m.origin.trim())) {
        return failWithLog('Todos os clientes novos precisam de uma origem.');
      }
    }

    const attendanceId = genId();
    const now = Date.now();

    const attendanceEntity: Attendance = {
      id: attendanceId,
      shiftId,
      hosterId,
      hosterName,
      type,
      totalMembers,
      newMembers,
      returningMembers,
      createdAt: now,
      updatedAt: now,
      isEdited: false,
      isPreferential,
      syncStatus: 'PENDENTE',
      isDeleted: false,
      deletedAt: null,
      deletedBy: null,
      deletionReason: null,
    };

    await localDb.insertOrUpdateAttendance(attendanceEntity);

    const memberEntities: AttendanceMember[] = membersList.map((m, idx) => ({
      id: genId(),
      attendanceId,
      memberNumber: idx + 1,
      classification: m.classification,
      origin: m.classification === 'NOVO' && m.origin?.trim() ? m.origin.trim() : null,
      createdAt: now,
    }));

    await localDb.insertMembers(memberEntities);

    await localDb.enqueueSyncOperation({
      entityType: 'ATTENDANCE',
      entityId: attendanceId,
      operation: 'INSERT',
      payloadJson: JSON.stringify({
        id: attendanceId,
        shift_id: shiftId,
        hoster_id: hosterId,
        hoster_name: hosterName,
        type,
        total_members: totalMembers,
        new_members: newMembers,
        returning_members: returningMembers,
        is_preferential: isPreferential,
        is_deleted: false,
        deleted_at: null,
        deleted_by: null,
        deletion_reason: null,
        created_at: toIsoUtc(now),
        updated_at: toIsoUtc(now),
      }),
      status: 'PENDING',
    });

    await localDb.enqueueSyncOperation({
      entityType: 'ATTENDANCE_MEMBERS',
      entityId: attendanceId,
      operation: 'INSERT',
      payloadJson: JSON.stringify(
        memberEntities.map((me) => ({
          id: me.id,
          attendance_id: me.attendanceId,
          member_number: me.memberNumber,
          classification: me.classification,
          origin: me.origin,
          created_at: toIsoUtc(me.createdAt),
        }))
      ),
      status: 'PENDING',
    });

    systemLogRepository.logAsync({
      action: SystemLogActions.ATTENDANCE_CREATE,
      entityType: 'ATTENDANCE',
      entityId: attendanceId,
      userId: hosterId,
      userName: hosterName,
      result: 'SUCCESS',
      description: `Atendimento ${AttendanceTypeDisplay[type]} registrado (${totalMembers} pessoa(s): ${newMembers} novo(s), ${returningMembers} recorrente(s))${isPreferential ? ' [PREFERENCIAL]' : ''}.`,
    });

    void syncManager.triggerSync();
    return { ok: true, value: attendanceEntity };
  },

  async correctLastAttendance(params: {
    shiftId: string;
    attendanceId: string;
    editorHosterId: string;
    editorHosterName: string;
    newType: AttendanceType;
    newTotal: number;
    newNewMembers: number;
    newReturningMembers: number;
    newMembersList: AttendanceMember[];
    newIsPreferential?: boolean;
  }): Promise<ActionResult<void>> {
    const {
      shiftId,
      attendanceId,
      editorHosterId,
      editorHosterName,
      newType,
      newTotal,
      newNewMembers,
      newReturningMembers,
      newMembersList,
      newIsPreferential = false,
    } = params;

    const shift = await localDb.getShiftById(shiftId);
    if (!shift) return { ok: false, error: 'Turno não encontrado.' };
    if (shift.status !== 'OPEN') {
      return { ok: false, error: 'Não é possível corrigir atendimento em turno encerrado.' };
    }

    const lastAtt = await localDb.getLastAttendanceForShift(shiftId);
    if (!lastAtt) return { ok: false, error: 'Nenhum atendimento encontrado.' };
    if (lastAtt.id !== attendanceId) {
      return { ok: false, error: 'Apenas o último atendimento registrado pode ser corrigido.' };
    }

    if (newNewMembers + newReturningMembers !== newTotal) {
      return { ok: false, error: 'A soma de novos e recorrentes deve ser igual ao total.' };
    }

    for (const m of newMembersList) {
      if (m.classification === 'NOVO' && (!m.origin || !m.origin.trim())) {
        return { ok: false, error: 'Todos os clientes novos precisam de uma origem.' };
      }
    }

    const prefTagOld = lastAtt.isPreferential ? ' [PREF]' : '';
    const prefTagNew = newIsPreferential ? ' [PREF]' : '';
    const prevDataSummary = `${lastAtt.type}${prefTagOld} (${lastAtt.totalMembers}p: ${lastAtt.newMembers} novos, ${lastAtt.returningMembers} rec)`;

    const now = Date.now();
    await localDb.insertOrUpdateAttendance({
      ...lastAtt,
      type: newType,
      totalMembers: newTotal,
      newMembers: newNewMembers,
      returningMembers: newReturningMembers,
      updatedAt: now,
      isEdited: true,
      isPreferential: newIsPreferential,
      syncStatus: 'PENDENTE',
    });

    await localDb.deleteMembersForAttendance(attendanceId);

    const newMemberEntities: AttendanceMember[] = newMembersList.map((m, idx) => ({
      id: genId(),
      attendanceId,
      memberNumber: idx + 1,
      classification: m.classification,
      origin: m.classification === 'NOVO' && m.origin?.trim() ? m.origin.trim() : null,
      createdAt: now,
    }));
    await localDb.insertMembers(newMemberEntities);

    const newDataSummary = `${newType}${prefTagNew} (${newTotal}p: ${newNewMembers} novos, ${newReturningMembers} rec)`;
    const auditId = genId();

    await localDb.insertAudit({
      id: auditId,
      attendanceId,
      hosterId: editorHosterId,
      hosterName: editorHosterName,
      previousData: prevDataSummary,
      newData: newDataSummary,
      changedAt: now,
      syncStatus: 'PENDENTE',
    });

    await localDb.enqueueSyncOperation({
      entityType: 'ATTENDANCE',
      entityId: attendanceId,
      operation: 'UPDATE',
      payloadJson: JSON.stringify({
        id: attendanceId,
        shift_id: shiftId,
        hoster_id: lastAtt.hosterId,
        hoster_name: lastAtt.hosterName,
        type: newType,
        total_members: newTotal,
        new_members: newNewMembers,
        returning_members: newReturningMembers,
        is_preferential: newIsPreferential,
        is_deleted: Boolean(lastAtt.isDeleted),
        deleted_at: lastAtt.deletedAt ? toIsoUtc(lastAtt.deletedAt) : null,
        deleted_by: lastAtt.deletedBy ?? null,
        deletion_reason: lastAtt.deletionReason ?? null,
        created_at: toIsoUtc(lastAtt.createdAt),
        updated_at: toIsoUtc(now),
      }),
      status: 'PENDING',
    });

    await localDb.enqueueSyncOperation({
      entityType: 'ATTENDANCE_MEMBERS',
      entityId: attendanceId,
      operation: 'UPDATE',
      payloadJson: JSON.stringify(
        newMemberEntities.map((me) => ({
          id: me.id,
          attendance_id: me.attendanceId,
          member_number: me.memberNumber,
          classification: me.classification,
          origin: me.origin,
          created_at: toIsoUtc(me.createdAt),
        }))
      ),
      status: 'PENDING',
    });

    await localDb.enqueueSyncOperation({
      entityType: 'ATTENDANCE_AUDIT',
      entityId: auditId,
      operation: 'INSERT',
      payloadJson: JSON.stringify({
        id: auditId,
        attendance_id: attendanceId,
        hoster_id: editorHosterId,
        hoster_name: editorHosterName,
        previous_data: prevDataSummary,
        new_data: newDataSummary,
        changed_at: toIsoUtc(now),
      }),
      status: 'PENDING',
    });

    systemLogRepository.logAsync({
      action: SystemLogActions.ATTENDANCE_EDIT,
      entityType: 'ATTENDANCE',
      entityId: attendanceId,
      userId: editorHosterId,
      userName: editorHosterName,
      result: 'SUCCESS',
      description: `Atendimento editado: ${prevDataSummary} → ${newDataSummary}.`,
    });

    void syncManager.triggerSync();
    return { ok: true, value: undefined };
  },

  async softDeleteAttendance(params: {
    attendanceId: string;
    actorHosterId: string;
    actorHosterName: string;
    actorIsAdmin: boolean;
    deletionReason?: string | null;
  }): Promise<ActionResult<void>> {
    const {
      attendanceId,
      actorHosterId,
      actorHosterName,
      actorIsAdmin,
      deletionReason = null,
    } = params;

    const failDelete = (msg: string): ActionResult<void> => {
      systemLogRepository.logAsync({
        action: SystemLogActions.ATTENDANCE_DELETE_FAILURE,
        entityType: 'ATTENDANCE',
        entityId: attendanceId,
        userId: actorHosterId,
        userName: actorHosterName,
        result: 'FAILURE',
        description: `Falha ao excluir atendimento ${attendanceId.slice(0, 8)}: ${msg}`,
      });
      return { ok: false, error: msg };
    };

    const existing = await localDb.getAttendanceById(attendanceId);
    if (!existing) return failDelete('Atendimento não encontrado.');
    if (existing.isDeleted) return failDelete('Este atendimento já foi excluído.');

    const shift = await localDb.getShiftById(existing.shiftId);
    if (!shift) return failDelete('Turno vinculado ao atendimento não encontrado.');

    if (shift.status === 'CLOSED' && !actorIsAdmin) {
      return failDelete(
        'Não é permitido excluir atendimento de um turno já encerrado sem autorização administrativa.'
      );
    }

    if (!actorIsAdmin && existing.hosterId !== actorHosterId) {
      return failDelete(
        'Apenas o hoster responsável pelo registro ou um administrador pode excluir este atendimento.'
      );
    }

    const now = Date.now();
    const cleanReason = deletionReason && deletionReason.trim() ? deletionReason.trim() : null;

    await localDb.insertOrUpdateAttendance({
      ...existing,
      isDeleted: true,
      deletedAt: now,
      deletedBy: actorHosterName,
      deletionReason: cleanReason,
      updatedAt: now,
      syncStatus: 'PENDENTE',
    });

    const summary = `${existing.type} (${existing.totalMembers}p: ${existing.newMembers} novos, ${existing.returningMembers} rec)`;
    const auditId = genId();
    await localDb.insertAudit({
      id: auditId,
      attendanceId,
      hosterId: actorHosterId,
      hosterName: actorHosterName,
      previousData: summary,
      newData: `EXCLUÍDO por ${actorHosterName}${cleanReason ? ` • Motivo: ${cleanReason}` : ''}`,
      changedAt: now,
      syncStatus: 'PENDENTE',
    });

    await localDb.enqueueSyncOperation({
      entityType: 'ATTENDANCE',
      entityId: attendanceId,
      operation: 'DELETE',
      payloadJson: JSON.stringify({
        is_deleted: true,
        deleted_at: toIsoUtc(now),
        deleted_by: actorHosterName,
        deletion_reason: cleanReason,
        updated_at: toIsoUtc(now),
      }),
      status: 'PENDING',
    });

    await localDb.enqueueSyncOperation({
      entityType: 'ATTENDANCE_AUDIT',
      entityId: auditId,
      operation: 'INSERT',
      payloadJson: JSON.stringify({
        id: auditId,
        attendance_id: attendanceId,
        hoster_id: actorHosterId,
        hoster_name: actorHosterName,
        previous_data: summary,
        new_data: `EXCLUÍDO por ${actorHosterName}${cleanReason ? ` • Motivo: ${cleanReason}` : ''}`,
        changed_at: toIsoUtc(now),
      }),
      status: 'PENDING',
    });

    systemLogRepository.logAsync({
      action: SystemLogActions.ATTENDANCE_DELETE,
      entityType: 'ATTENDANCE',
      entityId: attendanceId,
      userId: actorHosterId,
      userName: actorHosterName,
      result: 'SUCCESS',
      description: `Atendimento ${summary} excluído logicamente por ${actorHosterName}${cleanReason ? ` (Motivo: ${cleanReason})` : ''}.`,
    });

    void syncManager.triggerSync();
    return { ok: true, value: undefined };
  },

  async updateQueue(
    shiftId: string,
    hosterId: string,
    waitingDuos: number,
    waitingGroups: number,
    hosterName?: string
  ): Promise<ActionResult<void>> {
    if (waitingDuos < 0 || waitingGroups < 0) {
      systemLogRepository.logAsync({
        action: SystemLogActions.QUEUE_UPDATE_FAILURE,
        entityType: 'QUEUE_EVENT',
        entityId: shiftId,
        userId: hosterId,
        userName: hosterName ?? null,
        result: 'FAILURE',
        description: 'Tentativa de atualizar fila com valores negativos.',
      });
      return { ok: false, error: 'Os valores da fila devem ser maiores ou iguais a zero.' };
    }

    const eventId = genId();
    const now = Date.now();
    await localDb.insertQueueEvent({
      id: eventId,
      shiftId,
      hosterId,
      waitingDuos,
      waitingGroups,
      createdAt: now,
      syncStatus: 'PENDENTE',
    });

    await localDb.enqueueSyncOperation({
      entityType: 'QUEUE_EVENT',
      entityId: eventId,
      operation: 'INSERT',
      payloadJson: JSON.stringify({
        id: eventId,
        shift_id: shiftId,
        hoster_id: hosterId,
        waiting_duos: waitingDuos,
        waiting_groups: waitingGroups,
        created_at: toIsoUtc(now),
      }),
      status: 'PENDING',
    });

    systemLogRepository.logAsync({
      action: SystemLogActions.QUEUE_UPDATE,
      entityType: 'QUEUE_EVENT',
      entityId: eventId,
      userId: hosterId,
      userName: hosterName ?? null,
      result: 'SUCCESS',
      description: `Fila atualizada: ${waitingDuos} dupla(s), ${waitingGroups} grupo(s).`,
    });

    void syncManager.triggerSync();
    return { ok: true, value: undefined };
  },

  async getLatestQueueForShift(shiftId: string): Promise<QueueEvent | null> {
    return localDb.getLatestQueueEventForShift(shiftId);
  },

  async getAttendancesWithMembersForShift(shiftId: string): Promise<AttendanceWithMembers[]> {
    const attendances = await localDb.getAttendancesForShift(shiftId);
    const allMembers = await localDb.getAllMembers();
    const byAtt = new Map<string, AttendanceMember[]>();
    for (const m of allMembers) {
      const list = byAtt.get(m.attendanceId) ?? [];
      list.push(m);
      byAtt.set(m.attendanceId, list);
    }
    return attendances.map((attendance) => ({
      attendance,
      members: (byAtt.get(attendance.id) ?? []).sort((a, b) => a.memberNumber - b.memberNumber),
    }));
  },

  async getAllShifts(): Promise<Shift[]> {
    return localDb.getAllShifts();
  },

  async getAllAttendances(): Promise<Attendance[]> {
    return localDb.getAllAttendances();
  },

  async getAllMembers(): Promise<AttendanceMember[]> {
    return localDb.getAllMembers();
  },

  async getAllSystemLogs(): Promise<SystemLog[]> {
    return localDb.getAllSystemLogs();
  },
};
