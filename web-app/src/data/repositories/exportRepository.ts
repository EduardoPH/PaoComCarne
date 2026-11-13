import { localDb } from '../local/database';
import type { AttendanceMember, Shift } from '../../domain/models';
import { formatDate, formatDateTime, formatFileDate } from '../../domain/utils/dateTime';
import {
  generateWorkbookBlob,
  type AttendanceRow,
  type ClientRow,
  type DailySummary,
  type ReportConsolidatedSummary,
} from '../../domain/utils/xlsxGenerator';

export type ExportResult =
  | {
      status: 'SUCCESS';
      bytes: Uint8Array;
      fileName: string;
      rowCount: number;
      totalClients: number;
    }
  | {
      status: 'BLOCKED';
      reason: string;
      openShiftsCount?: number;
      pendingSyncCount?: number;
    }
  | {
      status: 'FAILURE';
      message: string;
    };

export async function exportReport(params: {
  startMillis: number;
  endMillis: number;
  selectedHosterId?: string | null;
  selectedShiftPeriod?: string | null;
}): Promise<ExportResult> {
  const { startMillis, endMillis, selectedHosterId = null, selectedShiftPeriod = null } = params;

  try {
    // Rule 1: Check pending sync items
    const pendingCount = await localDb.getPendingCount();
    if (pendingCount > 0) {
      return {
        status: 'BLOCKED',
        reason: `Existem ${pendingCount} registros pendentes de sincronização com o servidor. Sincronize antes de exportar.`,
        pendingSyncCount: pendingCount,
      };
    }

    // Rule 2: Check open shifts in selected range
    const openShifts = await localDb.getOpenShiftsInRange(startMillis, endMillis);
    if (openShifts.length > 0) {
      const hosterNames = openShifts.map((s) => `${s.hosterName} (${s.period})`).join(', ');
      return {
        status: 'BLOCKED',
        reason: `Existem turnos abertos no período selecionado que impedem o fechamento e a exportação do relatório: ${hosterNames}. Encerre os turnos primeiro.`,
        openShiftsCount: openShifts.length,
      };
    }

    const shiftsList = await localDb.getAllShifts();
    const shifts = new Map<string, Shift>(shiftsList.map((s) => [s.id, s]));

    const allAttendances = await localDb.getAllAttendances();
    const filteredAttendances = allAttendances
      .filter((att) => {
        const matchDate = att.createdAt >= startMillis && att.createdAt <= endMillis;
        const matchHoster = selectedHosterId === null || att.hosterId === selectedHosterId;
        const shift = shifts.get(att.shiftId);
        const matchPeriod =
          selectedShiftPeriod === null || shift?.period === selectedShiftPeriod;
        return matchDate && matchHoster && matchPeriod;
      })
      .sort((a, b) => a.createdAt - b.createdAt);

    if (filteredAttendances.length === 0) {
      return {
        status: 'FAILURE',
        message: 'Nenhum atendimento encontrado para os critérios e período selecionados.',
      };
    }

    const attendanceIds = new Set(filteredAttendances.map((a) => a.id));
    const allMembers = (await localDb.getAllMembers()).filter((m) =>
      attendanceIds.has(m.attendanceId)
    );
    const memberMap = new Map<string, AttendanceMember[]>();
    for (const m of allMembers) {
      const list = memberMap.get(m.attendanceId) ?? [];
      list.push(m);
      memberMap.set(m.attendanceId, list);
    }

    // Aba 1: Atendimentos
    const attendanceRows: AttendanceRow[] = filteredAttendances.map((att) => {
      const shift = shifts.get(att.shiftId);
      const periodDisplay = shift ? (shift.period === 'MANHA' ? 'Manhã' : 'Noite') : 'N/D';
      return {
        id: att.id,
        date: formatDateTime(att.createdAt),
        shift: periodDisplay,
        hoster: att.hosterName,
        type: att.type,
        isPreferential: att.isPreferential ? 'Sim' : 'Não',
        totalMembers: att.totalMembers,
        newMembers: att.newMembers,
        returningMembers: att.returningMembers,
      };
    });

    // Aba 2: Clientes individuais
    const clientRows: ClientRow[] = [];
    for (const att of filteredAttendances) {
      const members = (memberMap.get(att.id) ?? []).sort(
        (a, b) => a.memberNumber - b.memberNumber
      );
      for (const m of members) {
        clientRows.push({
          attendanceId: att.id,
          hoster: att.hosterName,
          memberNumber: m.memberNumber,
          classification: m.classification === 'NOVO' ? 'Novo' : 'Recorrente',
          origin: m.origin || '—',
          isPreferential: att.isPreferential ? 'Sim' : 'Não',
        });
      }
    }

    // Aba 3: Resumo Diário e Consolidado Geral
    const byDay = new Map<string, typeof filteredAttendances>();
    for (const att of filteredAttendances) {
      const dateLabel = formatDate(att.createdAt);
      const list = byDay.get(dateLabel) ?? [];
      list.push(att);
      byDay.set(dateLabel, list);
    }

    const dailySummaries: DailySummary[] = [];
    byDay.forEach((dayAtts, dateLabel) => {
      const dayTotalClients = dayAtts.reduce((acc, a) => acc + a.totalMembers, 0);
      const dayMorning = dayAtts
        .filter((a) => shifts.get(a.shiftId)?.period === 'MANHA')
        .reduce((acc, a) => acc + a.totalMembers, 0);
      const dayNight = dayAtts
        .filter((a) => shifts.get(a.shiftId)?.period === 'NOITE')
        .reduce((acc, a) => acc + a.totalMembers, 0);
      const dayNew = dayAtts.reduce((acc, a) => acc + a.newMembers, 0);
      const dayRet = dayAtts.reduce((acc, a) => acc + a.returningMembers, 0);
      const dayPref = dayAtts.filter((a) => a.isPreferential).length;

      const dayOrigins: Record<string, number> = {};
      for (const att of dayAtts) {
        const members = memberMap.get(att.id) ?? [];
        for (const m of members) {
          if (m.classification === 'NOVO') {
            const orig = m.origin && m.origin.trim() ? m.origin.trim() : 'Outros';
            dayOrigins[orig] = (dayOrigins[orig] ?? 0) + 1;
          }
        }
      }

      dailySummaries.push({
        dateLabel,
        totalClients: dayTotalClients,
        morningClients: dayMorning,
        nightClients: dayNight,
        newClients: dayNew,
        returningClients: dayRet,
        preferentialCount: dayPref,
        originsBreakdown: dayOrigins,
      });
    });

    const grandOrigins: Record<string, number> = {};
    for (const att of filteredAttendances) {
      const members = memberMap.get(att.id) ?? [];
      for (const m of members) {
        if (m.classification === 'NOVO') {
          const orig = m.origin && m.origin.trim() ? m.origin.trim() : 'Outros';
          grandOrigins[orig] = (grandOrigins[orig] ?? 0) + 1;
        }
      }
    }

    const grandTotalClients = filteredAttendances.reduce((acc, a) => acc + a.totalMembers, 0);
    const grandMorningClients = filteredAttendances
      .filter((a) => shifts.get(a.shiftId)?.period === 'MANHA')
      .reduce((acc, a) => acc + a.totalMembers, 0);
    const grandNightClients = filteredAttendances
      .filter((a) => shifts.get(a.shiftId)?.period === 'NOITE')
      .reduce((acc, a) => acc + a.totalMembers, 0);
    const grandNewClients = filteredAttendances.reduce((acc, a) => acc + a.newMembers, 0);
    const grandReturningClients = filteredAttendances.reduce(
      (acc, a) => acc + a.returningMembers,
      0
    );
    const grandPreferentialCount = filteredAttendances.filter((a) => a.isPreferential).length;

    const consolidatedSummary: ReportConsolidatedSummary = {
      dailySummaries,
      grandTotalClients,
      grandMorningClients,
      grandNightClients,
      grandNewClients,
      grandReturningClients,
      grandPreferentialCount,
      grandOriginsBreakdown: grandOrigins,
    };

    const startDateStr = formatFileDate(startMillis);
    const endDateStr = formatFileDate(endMillis);
    const periodSuffix = selectedShiftPeriod ? `_${selectedShiftPeriod}` : '';
    const fileName = `PaoComCarne_Relatorio_${startDateStr}_a_${endDateStr}${periodSuffix}.xlsx`;

    const bytes = await generateWorkbookBlob(attendanceRows, clientRows, consolidatedSummary);

    return {
      status: 'SUCCESS',
      bytes,
      fileName,
      rowCount: attendanceRows.length,
      totalClients: grandTotalClients,
    };
  } catch (err) {
    return {
      status: 'FAILURE',
      message: err instanceof Error ? err.message : 'Erro desconhecido ao gerar arquivo Excel.',
    };
  }
}

export function downloadXlsxBytes(bytes: Uint8Array, fileName: string): void {
  const blob = new Blob([bytes.buffer as ArrayBuffer], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}

export async function shareOrDownloadXlsx(bytes: Uint8Array, fileName: string): Promise<void> {
  const mime = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
  const file = new File([bytes.buffer as ArrayBuffer], fileName, { type: mime });

  if (
    typeof navigator !== 'undefined' &&
    typeof navigator.share === 'function' &&
    typeof navigator.canShare === 'function' &&
    navigator.canShare({ files: [file] })
  ) {
    try {
      await navigator.share({
        files: [file],
        title: fileName,
        text: 'Relatório Gerencial — Pão com Carne',
      });
      return;
    } catch (err) {
      // User cancelled share sheet -> do not force-download if aborted
      if (err instanceof DOMException && err.name === 'AbortError') {
        return;
      }
    }
  }

  downloadXlsxBytes(bytes, fileName);
}
