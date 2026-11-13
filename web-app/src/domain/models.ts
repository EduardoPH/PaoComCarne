export type ShiftPeriod = 'MANHA' | 'NOITE';

export const ShiftPeriodDisplay: Record<ShiftPeriod, string> = {
  MANHA: 'Manhã',
  NOITE: 'Noite',
};

export type ShiftStatus = 'OPEN' | 'CLOSED';

export const ShiftStatusDisplay: Record<ShiftStatus, string> = {
  OPEN: 'Aberto',
  CLOSED: 'Encerrado',
};

export type AttendanceType = 'SOLO' | 'DUPLA' | 'GRUPO';

export const AttendanceTypeDisplay: Record<AttendanceType, string> = {
  SOLO: 'Solo',
  DUPLA: 'Dupla',
  GRUPO: 'Grupo',
};

export type ClientClassification = 'NOVO' | 'RECORRENTE';

export const ClientClassificationDisplay: Record<ClientClassification, string> = {
  NOVO: 'Novo',
  RECORRENTE: 'Recorrente',
};

export const ClientOrigins = [
  'Instagram',
  'TikTok',
  'Indicação',
  'iFood',
  'Google',
  'Já foi na antiga loja',
] as const;

export type SyncState =
  | 'SINCRONIZADO'
  | 'OFFLINE'
  | 'PENDENTE'
  | 'SINCRONIZANDO'
  | 'ERRO';

export interface Hoster {
  id: string;
  name: string;
  authUserId?: string | null;
  pinHash: string;
  pinSalt: string;
  isActivated: boolean;
  active: boolean;
  isAdmin: boolean;
  createdAt: number;
}

export interface Shift {
  id: string;
  hosterId: string;
  hosterName: string;
  period: ShiftPeriod;
  openedAt: number;
  closedAt?: number | null;
  status: ShiftStatus;
  deviceCreatedAt: number;
  syncedAt?: number | null;
  syncStatus: string;
}

export interface Attendance {
  id: string;
  shiftId: string;
  hosterId: string;
  hosterName: string;
  type: AttendanceType;
  totalMembers: number;
  newMembers: number;
  returningMembers: number;
  createdAt: number;
  updatedAt: number;
  isEdited: boolean;
  isPreferential: boolean;
  syncStatus: string;
}

export interface AttendanceMember {
  id: string;
  attendanceId: string;
  memberNumber: number;
  classification: ClientClassification;
  origin: string | null;
  createdAt: number;
}

export interface AttendanceWithMembers {
  attendance: Attendance;
  members: AttendanceMember[];
}

export interface AttendanceAudit {
  id: string;
  attendanceId: string;
  hosterId: string;
  hosterName: string;
  previousData: string;
  newData: string;
  changedAt: number;
  syncStatus: string;
}

export interface QueueEvent {
  id: string;
  shiftId: string;
  hosterId: string;
  waitingDuos: number;
  waitingGroups: number;
  createdAt: number;
  syncStatus: string;
}

export type SyncOperationStatus = 'PENDING' | 'SYNCING' | 'FAILED';

export interface SyncQueueItem {
  id: number;
  entityType:
    | 'SHIFT'
    | 'SHIFT_CLOSE'
    | 'ATTENDANCE'
    | 'ATTENDANCE_MEMBERS'
    | 'ATTENDANCE_AUDIT'
    | 'QUEUE_EVENT'
    | 'HOSTER_ACTIVATION'
    | 'HOSTER_INSERT'
    | 'HOSTER_UPDATE'
    | 'HOSTER_DELETE';
  entityId: string;
  operation: 'INSERT' | 'UPDATE' | 'DELETE';
  payloadJson: string;
  status: SyncOperationStatus;
  attempts: number;
  lastError: string | null;
  createdAt: number;
  updatedAt: number;
}

export interface SyncLogEntry {
  id: string;
  timestamp: number;
  message: string;
  isError: boolean;
}

export type AppTab = 'RECEPCAO' | 'INSPECAO' | 'ADMINISTRACAO' | 'TURNO';

export interface MemberDraft {
  memberNumber: number;
  classification: ClientClassification;
  origin: string | null;
}
