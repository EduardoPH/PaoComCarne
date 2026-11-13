import type {
  Attendance,
  AttendanceMember,
  LogResult,
  Shift,
  ShiftPeriod,
  SystemLog,
} from '../models';
import { formatDate } from './dateTime';

export type DateFilterOption =
  | 'HOJE'
  | 'ONTEM'
  | 'ULTIMOS_7_DIAS'
  | 'ULTIMOS_30_DIAS'
  | 'MES_ATUAL'
  | 'MES_ANTERIOR'
  | 'INTERVALO_PERSONALIZADO'
  | 'TODOS';

export const DATE_OPTIONS: { id: DateFilterOption; label: string }[] = [
  { id: 'HOJE', label: 'Hoje' },
  { id: 'ONTEM', label: 'Ontem' },
  { id: 'ULTIMOS_7_DIAS', label: 'Últimos 7 dias' },
  { id: 'ULTIMOS_30_DIAS', label: 'Últimos 30 dias' },
  { id: 'MES_ATUAL', label: 'Este mês' },
  { id: 'MES_ANTERIOR', label: 'Mês anterior' },
  { id: 'INTERVALO_PERSONALIZADO', label: 'Personalizado' },
  { id: 'TODOS', label: 'Todo o período' },
];

export interface AdminFilterState {
  dateOption: DateFilterOption;
  draftStartMillis: number;
  draftEndMillis: number;
  appliedStartMillis: number;
  appliedEndMillis: number;
  selectedHosterId: string | null;
  selectedPeriod: ShiftPeriod | null;
  selectedOrigin: string | null;
  selectedClassification: 'NOVO' | 'RECORRENTE' | null;
  preferentialOnly: boolean;
}

export interface AdminMetrics {
  totalPeople: number;
  totalNewClients: number;
  totalReturningClients: number;
  totalAttendances: number;
  preferentialCount: number;
  morningClients: number;
  nightClients: number;
  originDistribution: Record<string, number>;
  hosterPerformance: Record<string, number>;
}

export interface SystemLogsFilterState {
  startMillis: number | null;
  endMillis: number | null;
  selectedUser: string | null;
  selectedAction: string | null;
  selectedDevice: string | null;
  selectedResult: LogResult | null;
}

export function startOfDay(millis: number): number {
  const d = new Date(millis);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate(), 0, 0, 0, 0).getTime();
}

export function endOfDay(millis: number): number {
  const d = new Date(millis);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate(), 23, 59, 59, 999).getTime();
}

export function validateDateRange(startMillis: number, endMillis: number): string | null {
  if (startOfDay(startMillis) > startOfDay(endMillis)) {
    return 'A data inicial não pode ser posterior à data final.';
  }
  return null;
}

export function rangeForOption(
  option: DateFilterOption,
  customStart: number,
  customEnd: number,
  referenceMillis = Date.now()
): [number, number] {
  const ref = new Date(referenceMillis);
  switch (option) {
    case 'HOJE':
      return [startOfDay(referenceMillis), endOfDay(referenceMillis)];
    case 'ONTEM': {
      const y = new Date(ref);
      y.setDate(y.getDate() - 1);
      return [startOfDay(y.getTime()), endOfDay(y.getTime())];
    }
    case 'ULTIMOS_7_DIAS': {
      const s = new Date(ref);
      s.setDate(s.getDate() - 6);
      return [startOfDay(s.getTime()), endOfDay(referenceMillis)];
    }
    case 'ULTIMOS_30_DIAS': {
      const s = new Date(ref);
      s.setDate(s.getDate() - 29);
      return [startOfDay(s.getTime()), endOfDay(referenceMillis)];
    }
    case 'MES_ATUAL': {
      const first = new Date(ref.getFullYear(), ref.getMonth(), 1, 0, 0, 0, 0);
      const last = new Date(ref.getFullYear(), ref.getMonth() + 1, 0, 23, 59, 59, 999);
      return [first.getTime(), last.getTime()];
    }
    case 'MES_ANTERIOR': {
      const first = new Date(ref.getFullYear(), ref.getMonth() - 1, 1, 0, 0, 0, 0);
      const last = new Date(ref.getFullYear(), ref.getMonth(), 0, 23, 59, 59, 999);
      return [first.getTime(), last.getTime()];
    }
    case 'INTERVALO_PERSONALIZADO':
      return [startOfDay(customStart), endOfDay(customEnd)];
    case 'TODOS':
      return [0, Number.MAX_SAFE_INTEGER];
  }
}

export function getPeriodDisplayLabel(filter: AdminFilterState): string {
  const periodStr =
    filter.selectedPeriod === 'MANHA'
      ? ' • Turno Manhã'
      : filter.selectedPeriod === 'NOITE'
        ? ' • Turno Noite'
        : '';

  let base: string;
  switch (filter.dateOption) {
    case 'HOJE':
      base = `Hoje (${formatDate(filter.appliedStartMillis)})`;
      break;
    case 'ONTEM':
      base = `Ontem (${formatDate(filter.appliedStartMillis)})`;
      break;
    case 'ULTIMOS_7_DIAS':
      base = `Últimos 7 dias (${formatDate(filter.appliedStartMillis)} - ${formatDate(filter.appliedEndMillis)})`;
      break;
    case 'ULTIMOS_30_DIAS':
      base = `Últimos 30 dias (${formatDate(filter.appliedStartMillis)} - ${formatDate(filter.appliedEndMillis)})`;
      break;
    case 'MES_ATUAL':
      base = `Este mês (${formatDate(filter.appliedStartMillis)} - ${formatDate(filter.appliedEndMillis)})`;
      break;
    case 'MES_ANTERIOR':
      base = `Mês anterior (${formatDate(filter.appliedStartMillis)} - ${formatDate(filter.appliedEndMillis)})`;
      break;
    case 'INTERVALO_PERSONALIZADO':
      base = `${formatDate(filter.appliedStartMillis)} até ${formatDate(filter.appliedEndMillis)}`;
      break;
    case 'TODOS':
      base = 'Todo o período';
      break;
  }
  return `${base}${periodStr}`;
}

export function computeAdminMetrics(
  shifts: Shift[],
  attendances: Attendance[],
  allMembers: AttendanceMember[],
  filter: AdminFilterState
): AdminMetrics {
  const [startMillis, endMillis] = rangeForOption(
    filter.dateOption,
    filter.appliedStartMillis,
    filter.appliedEndMillis
  );
  const shiftsById = new Map(shifts.map((s) => [s.id, s]));
  const membersByAtt = new Map<string, AttendanceMember[]>();
  for (const m of allMembers) {
    const list = membersByAtt.get(m.attendanceId) ?? [];
    list.push(m);
    membersByAtt.set(m.attendanceId, list);
  }

  const filtered = attendances.filter((att) => {
    if (att.isDeleted) return false;
    const matchDate = att.createdAt >= startMillis && att.createdAt <= endMillis;
    const matchHoster =
      filter.selectedHosterId === null || att.hosterId === filter.selectedHosterId;
    const shift = shiftsById.get(att.shiftId);
    const matchPeriod =
      filter.selectedPeriod === null || shift?.period === filter.selectedPeriod;
    const matchClass =
      filter.selectedClassification === null
        ? true
        : filter.selectedClassification === 'NOVO'
          ? att.newMembers > 0
          : att.returningMembers > 0;
    const matchPref = !filter.preferentialOnly || att.isPreferential;
    const members = membersByAtt.get(att.id) ?? [];
    const matchOrigin =
      filter.selectedOrigin === null ||
      members.some(
        (m) =>
          m.classification === 'NOVO' &&
          m.origin?.trim().toLowerCase() === filter.selectedOrigin?.trim().toLowerCase()
      );
    return matchDate && matchHoster && matchPeriod && matchClass && matchPref && matchOrigin;
  });

  const originDist: Record<string, number> = {};
  const hosterPerf: Record<string, number> = {};
  let morningClients = 0;
  let nightClients = 0;
  let prefCount = 0;

  for (const att of filtered) {
    hosterPerf[att.hosterName] = (hosterPerf[att.hosterName] ?? 0) + att.totalMembers;
    const shift = shiftsById.get(att.shiftId);
    if (shift?.period === 'MANHA') morningClients += att.totalMembers;
    if (shift?.period === 'NOITE') nightClients += att.totalMembers;
    if (att.isPreferential) prefCount += 1;

    const members = membersByAtt.get(att.id) ?? [];
    for (const m of members) {
      if (m.classification === 'NOVO' && m.origin && m.origin.trim()) {
        if (
          filter.selectedOrigin === null ||
          m.origin.trim().toLowerCase() === filter.selectedOrigin.trim().toLowerCase()
        ) {
          originDist[m.origin] = (originDist[m.origin] ?? 0) + 1;
        }
      }
    }
  }

  return {
    totalPeople: filtered.reduce((s, a) => s + a.totalMembers, 0),
    totalNewClients: filtered.reduce((s, a) => s + a.newMembers, 0),
    totalReturningClients: filtered.reduce((s, a) => s + a.returningMembers, 0),
    totalAttendances: filtered.length,
    preferentialCount: prefCount,
    morningClients,
    nightClients,
    originDistribution: originDist,
    hosterPerformance: hosterPerf,
  };
}

export function filterSystemLogs(logs: SystemLog[], filter: SystemLogsFilterState): SystemLog[] {
  return logs.filter((item) => {
    if (filter.startMillis !== null && item.createdAt < filter.startMillis) return false;
    if (filter.endMillis !== null && item.createdAt > filter.endMillis) return false;
    if (filter.selectedUser !== null && item.userName !== filter.selectedUser) return false;
    if (filter.selectedAction !== null && item.action !== filter.selectedAction) return false;
    if (filter.selectedDevice !== null && item.deviceName !== filter.selectedDevice) return false;
    if (filter.selectedResult !== null && item.result !== filter.selectedResult) return false;
    return true;
  });
}
