import { localDb } from '../local/database';
import {
  supabaseClient,
  type SupabaseAttendanceDto,
  type SupabaseAuditDto,
  type SupabaseHosterDto,
  type SupabaseMemberDto,
  type SupabaseQueueDto,
  type SupabaseShiftDto,
} from '../remote/supabaseClient';
import type {
  Attendance,
  AttendanceMember,
  AttendanceType,
  ClientClassification,
  Hoster,
  QueueEvent,
  Shift,
  ShiftPeriod,
  ShiftStatus,
  SyncLogEntry,
  SyncQueueItem,
  SyncState,
} from '../../domain/models';
import { fromIsoUtc } from '../../domain/utils/dateTime';

export const ADMIN_VALID_SALT = 'c6bca501f28b49e6';
export const ADMIN_VALID_HASH =
  'e1ef788d028b746c3594f232fd4a5213c3901c45acfc5ff3f552119d89d554f9';

export interface SyncSnapshot {
  syncState: SyncState;
  pendingCount: number;
  lastSyncTime: number | null;
  syncLogs: SyncLogEntry[];
  queueItems: SyncQueueItem[];
  isOnline: boolean;
}

class SyncManager {
  private snapshot: SyncSnapshot = {
    syncState: typeof navigator !== 'undefined' && navigator.onLine ? 'SINCRONIZADO' : 'OFFLINE',
    pendingCount: 0,
    lastSyncTime: null,
    syncLogs: [],
    queueItems: [],
    isOnline: typeof navigator !== 'undefined' ? navigator.onLine : false,
  };

  private isSyncInProgress = false;
  private initialized = false;
  private listeners = new Set<() => void>();

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  getSnapshot(): SyncSnapshot {
    return this.snapshot;
  }

  private emit(partial: Partial<SyncSnapshot>): void {
    this.snapshot = { ...this.snapshot, ...partial };
    this.listeners.forEach((fn) => fn());
  }

  log(message: string, isError = false): void {
    const entry: SyncLogEntry = {
      id: `${Date.now()}-${Math.floor(Math.random() * 100000)}`,
      timestamp: Date.now(),
      message,
      isError,
    };
    const nextLogs = [entry, ...this.snapshot.syncLogs].slice(0, 100);
    this.emit({ syncLogs: nextLogs });
  }

  async init(): Promise<void> {
    if (this.initialized) return;
    this.initialized = true;

    localDb.subscribe(() => {
      void this.refreshQueueSnapshot();
    });

    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => {
        this.emit({ isOnline: true });
        this.log('Rede online restabelecida. Disparando sincronização...');
        void this.triggerSync();
      });

      window.addEventListener('offline', () => {
        this.emit({ isOnline: false });
        this.log('Rede perdida. Dados mantidos em cache local.', true);
        void this.refreshQueueSnapshot();
      });
    }

    await this.seedInitialHostersIfEmpty();
    await this.refreshQueueSnapshot();

    const online = typeof navigator !== 'undefined' ? navigator.onLine : false;
    this.emit({ isOnline: online });

    if (online) {
      this.log('Conexão detectada. Inicializando sincronização e cache...');
      await this.fetchAllRemoteData();
      await this.syncPendingQueue();
    } else {
      this.log('Dispositivo offline. Operando em modo cache local.');
    }
  }

  private async refreshQueueSnapshot(): Promise<void> {
    const queueItems = await localDb.getAllSyncQueue();
    const pendingCount = queueItems.length;
    const isOnline = typeof navigator !== 'undefined' ? navigator.onLine : this.snapshot.isOnline;
    const hasFailed = queueItems.some((q) => q.status === 'FAILED');

    let nextState = this.snapshot.syncState;
    if (!this.isSyncInProgress) {
      if (!isOnline) {
        nextState = pendingCount > 0 ? 'PENDENTE' : 'OFFLINE';
      } else if (hasFailed) {
        nextState = 'ERRO';
      } else if (pendingCount > 0) {
        nextState = 'PENDENTE';
      } else {
        nextState = 'SINCRONIZADO';
      }
    }

    this.emit({ queueItems, pendingCount, isOnline, syncState: nextState });
  }

  async seedInitialHostersIfEmpty(): Promise<void> {
    const legacyDummyIds = [
      'h-001-eduardo',
      'h-002-mariana',
      'h-003-lucas',
      'h-004-beatriz',
      'h-005-gabriel',
      'h-006-camila',
    ];
    for (const id of legacyDummyIds) {
      await localDb.deleteHosterById(id);
    }

    const admin = await localDb.getHosterById('admin');
    if (!admin) {
      await localDb.insertOrUpdateHoster({
        id: 'admin',
        name: 'Administrador',
        authUserId: null,
        pinHash: ADMIN_VALID_HASH,
        pinSalt: ADMIN_VALID_SALT,
        isActivated: true,
        active: true,
        isAdmin: true,
        createdAt: Date.now(),
      });
    } else if (!admin.isActivated || !admin.isAdmin || admin.pinHash !== ADMIN_VALID_HASH) {
      await localDb.insertOrUpdateHoster({
        ...admin,
        name: 'Administrador',
        pinHash: ADMIN_VALID_HASH,
        pinSalt: ADMIN_VALID_SALT,
        isActivated: true,
        active: true,
        isAdmin: true,
      });
    }
  }

  async fetchAllRemoteData(): Promise<void> {
    if (!this.snapshot.isOnline) return;
    try {
      await this.fetchHostersFromRemote();
      await this.fetchShiftsFromRemote();
      await this.fetchAttendancesFromRemote();
      await this.fetchAttendanceMembersFromRemote();
      await this.fetchQueueEventsFromRemote();
      this.emit({ lastSyncTime: Date.now() });
    } catch (err) {
      this.log(
        `Erro ao atualizar cache remoto: ${err instanceof Error ? err.message : 'desconhecido'}`,
        true
      );
    }
  }

  async fetchHostersFromRemote(): Promise<void> {
    const res = await supabaseClient.getHosters();
    if (!res.ok || !res.data || res.data.length === 0) return;

    const entities: Hoster[] = [];
    for (const dto of res.data) {
      const local = await localDb.getHosterById(dto.id);
      const isAdmin = dto.is_admin || dto.id === 'admin' || local?.isAdmin === true;
      const pinHash = dto.id === 'admin' ? ADMIN_VALID_HASH : dto.pin_hash || local?.pinHash || '';
      const pinSalt = dto.id === 'admin' ? ADMIN_VALID_SALT : dto.pin_salt || local?.pinSalt || '';
      const isActivated =
        dto.id === 'admin' ? true : dto.is_activated || local?.isActivated === true;

      entities.push({
        id: dto.id,
        name: dto.name,
        authUserId: dto.auth_user_id ?? null,
        pinHash,
        pinSalt,
        isActivated,
        active: dto.active,
        isAdmin,
        createdAt: dto.created_at ? fromIsoUtc(dto.created_at) : Date.now(),
      });
    }

    await localDb.insertOrUpdateHosters(entities);
    this.log(`Hosters sincronizados do Supabase (${entities.length} membros da equipe).`);
  }

  async fetchShiftsFromRemote(): Promise<void> {
    const res = await supabaseClient.getShifts();
    if (!res.ok || !res.data || res.data.length === 0) return;

    const entities: Shift[] = [];
    for (const dto of res.data) {
      const local = await localDb.getShiftById(dto.id);
      entities.push({
        id: dto.id,
        hosterId: dto.hoster_id,
        hosterName: dto.hoster_name,
        period: (dto.period as ShiftPeriod) || 'MANHA',
        openedAt: fromIsoUtc(dto.opened_at),
        closedAt: dto.closed_at ? fromIsoUtc(dto.closed_at) : null,
        status: (dto.status as ShiftStatus) || 'OPEN',
        deviceCreatedAt: fromIsoUtc(dto.device_created_at),
        syncedAt: Date.now(),
        syncStatus: local?.syncStatus ?? 'SINCRONIZADO',
      });
    }

    await localDb.insertOrUpdateShifts(entities);
    this.log(`Turnos sincronizados e guardados em cache (${entities.length} turnos).`);
  }

  async fetchAttendancesFromRemote(): Promise<void> {
    const res = await supabaseClient.getAttendances();
    if (!res.ok || !res.data || res.data.length === 0) return;

    const entities: Attendance[] = [];
    for (const dto of res.data) {
      const local = await localDb.getAttendanceById(dto.id);
      entities.push({
        id: dto.id,
        shiftId: dto.shift_id,
        hosterId: dto.hoster_id,
        hosterName: dto.hoster_name,
        type: (dto.type as AttendanceType) || 'SOLO',
        totalMembers: dto.total_members,
        newMembers: dto.new_members,
        returningMembers: dto.returning_members,
        createdAt: fromIsoUtc(dto.created_at),
        updatedAt: fromIsoUtc(dto.updated_at),
        isEdited: local?.isEdited ?? false,
        isPreferential: Boolean(dto.is_preferential),
        syncStatus: local?.syncStatus ?? 'SINCRONIZADO',
      });
    }

    await localDb.insertOrUpdateAttendances(entities);
    this.log(`Atendimentos sincronizados em cache (${entities.length} registros).`);
  }

  async fetchAttendanceMembersFromRemote(): Promise<void> {
    const res = await supabaseClient.getAttendanceMembers();
    if (!res.ok || !res.data || res.data.length === 0) return;

    const entities: AttendanceMember[] = res.data.map((dto) => ({
      id: dto.id,
      attendanceId: dto.attendance_id,
      memberNumber: dto.member_number,
      classification: (dto.classification as ClientClassification) || 'RECORRENTE',
      origin: dto.origin ?? null,
      createdAt: fromIsoUtc(dto.created_at),
    }));

    await localDb.insertMembers(entities);
    this.log(`Integrantes individuais carregados em cache (${entities.length} pessoas).`);
  }

  async fetchQueueEventsFromRemote(): Promise<void> {
    const res = await supabaseClient.getQueueEvents();
    if (!res.ok || !res.data || res.data.length === 0) return;

    const entities: QueueEvent[] = res.data.map((dto) => ({
      id: dto.id,
      shiftId: dto.shift_id,
      hosterId: dto.hoster_id,
      waitingDuos: dto.waiting_duos,
      waitingGroups: dto.waiting_groups,
      createdAt: fromIsoUtc(dto.created_at),
      syncStatus: 'SINCRONIZADO',
    }));

    await localDb.insertQueueEvents(entities);
  }

  async triggerSync(): Promise<void> {
    const online = typeof navigator !== 'undefined' ? navigator.onLine : this.snapshot.isOnline;
    this.emit({ isOnline: online });
    this.log('Sincronização manual solicitada...');
    await this.fetchAllRemoteData();
    await this.syncPendingQueue();
  }

  async retryFailedOperations(): Promise<void> {
    await localDb.resetFailedToPending();
    this.log('Reenviando operações com falha...');
    await this.refreshQueueSnapshot();
    await this.syncPendingQueue();
  }

  async syncPendingQueue(): Promise<void> {
    const online = typeof navigator !== 'undefined' ? navigator.onLine : this.snapshot.isOnline;
    this.emit({ isOnline: online });

    if (!online) {
      await this.refreshQueueSnapshot();
      this.log(`Não há conexão. ${this.snapshot.pendingCount} itens aguardando na fila.`);
      return;
    }

    if (this.isSyncInProgress) return;
    this.isSyncInProgress = true;

    try {
      const pendingList = await localDb.getPendingOperations();
      const totalQueue = await localDb.getPendingCount();

      if (pendingList.length === 0) {
        this.isSyncInProgress = false;
        this.emit({
          pendingCount: totalQueue,
          lastSyncTime: Date.now(),
          syncState: totalQueue > 0 ? 'ERRO' : 'SINCRONIZADO',
        });
        if (totalQueue === 0) {
          this.log('Fila vazia. Todos os dados sincronizados.');
        }
        return;
      }

      this.emit({ syncState: 'SINCRONIZANDO', pendingCount: pendingList.length });
      this.log(`Enviando ${pendingList.length} operações pendentes para o Supabase...`);

      let hasError = false;

      for (const op of pendingList) {
        try {
          await localDb.updateSyncOperation({ ...op, status: 'SYNCING' });
          const result = await this.processQueueItem(op);
          if (result.ok) {
            await localDb.deleteSyncOperation(op.id);
            this.log(`Operação ${op.entityType} [${op.entityId.slice(0, 8)}] enviada com sucesso!`);
          } else {
            hasError = true;
            const errMsg = result.error || 'Resposta com código de erro do servidor';
            this.log(
              `Falha ao enviar ${op.entityType} [${op.entityId.slice(0, 8)}]: ${errMsg}`,
              true
            );
            await localDb.updateSyncOperation({
              ...op,
              status: 'FAILED',
              attempts: op.attempts + 1,
              lastError: errMsg,
              updatedAt: Date.now(),
            });
          }
        } catch (err) {
          hasError = true;
          const errMsg = err instanceof Error ? err.message : 'Erro de rede';
          this.log(`Erro na operação ${op.entityType} [${op.entityId.slice(0, 8)}]: ${errMsg}`, true);
          await localDb.updateSyncOperation({
            ...op,
            status: 'FAILED',
            attempts: op.attempts + 1,
            lastError: errMsg,
            updatedAt: Date.now(),
          });
        }
      }

      const remaining = await localDb.getPendingCount();
      this.isSyncInProgress = false;
      this.emit({
        pendingCount: remaining,
        syncState: hasError && remaining > 0 ? 'ERRO' : remaining > 0 ? 'PENDENTE' : 'SINCRONIZADO',
        lastSyncTime: remaining === 0 ? Date.now() : this.snapshot.lastSyncTime,
      });

      if (!hasError) {
        await this.fetchAllRemoteData();
      }
    } catch (err) {
      this.isSyncInProgress = false;
      this.log(
        `Erro no loop de sincronização: ${err instanceof Error ? err.message : 'desconhecido'}`,
        true
      );
      this.emit({ syncState: 'ERRO' });
    } finally {
      this.isSyncInProgress = false;
      await this.refreshQueueSnapshot();
    }
  }

  private async processQueueItem(op: SyncQueueItem): Promise<{ ok: boolean; error?: string }> {
    switch (op.entityType) {
      case 'SHIFT': {
        const dto = JSON.parse(op.payloadJson) as SupabaseShiftDto;
        const res = await supabaseClient.upsertShift(dto);
        return { ok: res.ok, error: res.errorText };
      }
      case 'SHIFT_CLOSE': {
        const body = JSON.parse(op.payloadJson) as Record<string, unknown>;
        const res = await supabaseClient.patchShift(op.entityId, body);
        return { ok: res.ok, error: res.errorText };
      }
      case 'ATTENDANCE': {
        const dto = JSON.parse(op.payloadJson) as SupabaseAttendanceDto;
        const res = await supabaseClient.upsertAttendance(dto);
        return { ok: res.ok, error: res.errorText };
      }
      case 'ATTENDANCE_MEMBERS': {
        const list = JSON.parse(op.payloadJson) as SupabaseMemberDto[];
        const sanitizedList: SupabaseMemberDto[] = list.map((m) => ({
          ...m,
          origin:
            m.classification === 'NOVO' && m.origin && m.origin.trim() ? m.origin.trim() : null,
        }));

        try {
          await supabaseClient.deleteMembersForAttendance(op.entityId);
        } catch {
          // ignore if none existed yet
        }

        const bulkRes = await supabaseClient.upsertMembers(sanitizedList);
        if (bulkRes.ok) {
          return { ok: true };
        }

        this.log(
          `Aviso no envio em lote de integrantes (${bulkRes.errorText}). Tentando envio individual...`,
          true
        );

        let allOk = true;
        let lastErr = bulkRes.errorText;
        for (const member of sanitizedList) {
          const singleRes = await supabaseClient.upsertMember(member);
          if (!singleRes.ok) {
            allOk = false;
            lastErr = singleRes.errorText;
            this.log(
              `Falha ao enviar integrante ${member.member_number}: ${singleRes.errorText}`,
              true
            );
          }
        }
        return { ok: allOk, error: lastErr };
      }
      case 'ATTENDANCE_AUDIT': {
        const dto = JSON.parse(op.payloadJson) as SupabaseAuditDto;
        const res = await supabaseClient.insertAudit(dto);
        return { ok: res.ok, error: res.errorText };
      }
      case 'QUEUE_EVENT': {
        const dto = JSON.parse(op.payloadJson) as SupabaseQueueDto;
        const res = await supabaseClient.insertQueueEvent(dto);
        return { ok: res.ok, error: res.errorText };
      }
      case 'HOSTER_ACTIVATION':
      case 'HOSTER_UPDATE': {
        const body = JSON.parse(op.payloadJson) as Record<string, unknown>;
        const res = await supabaseClient.updateHoster(op.entityId, body);
        return { ok: res.ok, error: res.errorText };
      }
      case 'HOSTER_INSERT': {
        const dto = JSON.parse(op.payloadJson) as SupabaseHosterDto;
        const res = await supabaseClient.upsertHoster(dto);
        return { ok: res.ok, error: res.errorText };
      }
      case 'HOSTER_DELETE': {
        const res = await supabaseClient.deleteHoster(op.entityId);
        return { ok: res.ok, error: res.errorText };
      }
      default:
        return { ok: true };
    }
  }
}

export const syncManager = new SyncManager();
