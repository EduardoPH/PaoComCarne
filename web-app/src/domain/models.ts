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
  isDeleted?: boolean;
  deletedAt?: number | null;
  deletedBy?: string | null;
  deletionReason?: string | null;
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

export type LogResult = 'SUCCESS' | 'FAILURE';

export const LogResultDisplay: Record<LogResult, string> = {
  SUCCESS: 'Sucesso',
  FAILURE: 'Falha',
};

export const SystemLogActions = {
  LOGIN_SUCCESS: 'LOGIN_SUCESSO',
  LOGIN_FAILURE: 'LOGIN_FALHA',
  LOGOUT: 'LOGOUT',
  FIRST_ACCESS_ACTIVATION: 'ATIVACAO_PRIMEIRO_ACESSO',

  SHIFT_OPEN: 'TURNO_ABERTO',
  SHIFT_CLOSE: 'TURNO_ENCERRADO',
  SHIFT_INVALID_OPEN: 'TURNO_ABERTURA_INVALIDA',
  SHIFT_RESUME: 'TURNO_RETOMADO',

  ATTENDANCE_CREATE: 'ATENDIMENTO_REGISTRADO',
  ATTENDANCE_EDIT: 'ATENDIMENTO_EDITADO',
  ATTENDANCE_DELETE: 'ATENDIMENTO_EXCLUIDO',
  ATTENDANCE_SAVE_FAILURE: 'ATENDIMENTO_FALHA_SALVAR',
  ATTENDANCE_DELETE_FAILURE: 'ATENDIMENTO_FALHA_EXCLUIR',

  QUEUE_UPDATE: 'FILA_ATUALIZADA',
  QUEUE_UPDATE_FAILURE: 'FILA_FALHA_ATUALIZAR',

  ADMIN_ACCESS: 'ADMIN_ACESSO',
  ADMIN_FILTER_APPLIED: 'ADMIN_FILTRO_APLICADO',
  REPORT_EXPORTED: 'RELATORIO_EXPORTADO',
  REPORT_EXPORT_FAILED: 'RELATORIO_FALHA_EXPORTAR',

  SYNC_STARTED: 'SYNC_INICIADA',
  SYNC_FINISHED: 'SYNC_CONCLUIDA',
  SYNC_FAILED: 'SYNC_FALHOU',
  RECONNECT: 'RECONEXAO_REDE',
  REMOTE_UPDATE_RECEIVED: 'ATUALIZACAO_REMOTA_RECEBIDA',
  CONFLICT_DETECTED: 'CONFLITO_SINCRONIZACAO',

  VERSION_CHECK: 'VERSAO_VERIFICADA',
  UPDATE_AVAILABLE: 'ATUALIZACAO_DISPONIVEL',
  MANDATORY_UPDATE_DETECTED: 'ATUALIZACAO_OBRIGATORIA_DETECTADA',
  UPDATE_DOWNLOAD_STARTED: 'ATUALIZACAO_DOWNLOAD_INICIADO',
  UPDATE_DOWNLOAD_COMPLETED: 'ATUALIZACAO_DOWNLOAD_CONCLUIDO',
  UPDATE_DOWNLOAD_FAILED: 'ATUALIZACAO_DOWNLOAD_FALHOU',
  UPDATE_INSTALL_REQUESTED: 'ATUALIZACAO_INSTALACAO_SOLICITADA',
} as const;

export interface SystemLog {
  id: string;
  userId: string | null;
  userName: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  deviceName: string;
  result: LogResult;
  description: string;
  createdAt: number;
  syncedAt?: number | null;
}

export interface AppReleasePolicy {
  id: string;
  versionName: string;
  versionCode: number;
  minSupportedCode: number;
  mandatory: boolean;
  apkPath: string;
  sha256: string;
  changelog: string;
  releaseReason: string;
  active: boolean;
  createdAt: number;
  confirmedAt: number;
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
    | 'HOSTER_DELETE'
    | 'SYSTEM_LOG';
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
