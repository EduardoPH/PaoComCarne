import { useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  Calendar,
  CheckCircle,
  Download,
  Filter,
  Share2,
  User,
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import {
  downloadXlsxBytes,
  exportReport,
  shareOrDownloadXlsx,
} from '../../data/repositories/exportRepository';
import { syncManager } from '../../data/sync/syncManager';
import {
  ClientOrigins,
  ShiftPeriodDisplay,
  type ShiftPeriod,
} from '../../domain/models';
import {
  formatDate,
  formatTime,
  fromInputDateValue,
  toInputDateValue,
} from '../../domain/utils/dateTime';
import { PaoPrimaryButton, SyncStatusPill } from '../components/CommonComponents';

type DateFilterOption =
  | 'HOJE'
  | 'ONTEM'
  | 'ULTIMOS_7_DIAS'
  | 'ULTIMOS_30_DIAS'
  | 'MES_ATUAL'
  | 'DIA_ESPECIFICO'
  | 'INTERVALO_PERSONALIZADO'
  | 'TODOS';

const DATE_OPTIONS: { id: DateFilterOption; label: string }[] = [
  { id: 'HOJE', label: 'Hoje' },
  { id: 'ONTEM', label: 'Ontem' },
  { id: 'ULTIMOS_7_DIAS', label: 'Últimos 7 dias' },
  { id: 'ULTIMOS_30_DIAS', label: 'Últimos 30 dias' },
  { id: 'MES_ATUAL', label: 'Mês atual' },
  { id: 'DIA_ESPECIFICO', label: 'Dia específico' },
  { id: 'INTERVALO_PERSONALIZADO', label: 'Início e Fim' },
  { id: 'TODOS', label: 'Todo o período' },
];

interface ExportSuccessData {
  bytes: Uint8Array;
  fileName: string;
  rowCount: number;
  totalClients: number;
  periodLabel: string;
}

function startOfDay(d: Date): number {
  const c = new Date(d.getFullYear(), d.getMonth(), d.getDate(), 0, 0, 0, 0);
  return c.getTime();
}

function endOfDay(d: Date): number {
  const c = new Date(d.getFullYear(), d.getMonth(), d.getDate(), 23, 59, 59, 999);
  return c.getTime();
}

function computeDateRange(
  dateOption: DateFilterOption,
  specificDateMillis: number,
  customStartMillis: number,
  customEndMillis: number
): [number, number] {
  const now = new Date();
  switch (dateOption) {
    case 'HOJE':
      return [startOfDay(now), endOfDay(now)];
    case 'ONTEM': {
      const y = new Date(now);
      y.setDate(y.getDate() - 1);
      return [startOfDay(y), endOfDay(y)];
    }
    case 'ULTIMOS_7_DIAS': {
      const start = new Date(now);
      start.setDate(start.getDate() - 6);
      return [startOfDay(start), endOfDay(now)];
    }
    case 'ULTIMOS_30_DIAS': {
      const start = new Date(now);
      start.setDate(start.getDate() - 29);
      return [startOfDay(start), endOfDay(now)];
    }
    case 'MES_ATUAL': {
      const first = new Date(now.getFullYear(), now.getMonth(), 1, 0, 0, 0, 0);
      const last = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59, 999);
      return [first.getTime(), last.getTime()];
    }
    case 'DIA_ESPECIFICO': {
      const d = new Date(specificDateMillis);
      return [startOfDay(d), endOfDay(d)];
    }
    case 'INTERVALO_PERSONALIZADO': {
      const s = new Date(customStartMillis);
      const e = new Date(customEndMillis);
      return [startOfDay(s), endOfDay(e)];
    }
    case 'TODOS':
      return [0, Number.MAX_SAFE_INTEGER];
  }
}

function getPeriodLabel(
  dateOption: DateFilterOption,
  specificDateMillis: number,
  customStartMillis: number,
  customEndMillis: number,
  selectedPeriod: ShiftPeriod | null
): string {
  const periodStr =
    selectedPeriod === 'MANHA'
      ? ' (Turno Manhã)'
      : selectedPeriod === 'NOITE'
        ? ' (Turno Noite)'
        : '';

  switch (dateOption) {
    case 'HOJE':
      return `Hoje${periodStr}`;
    case 'ONTEM':
      return `Ontem${periodStr}`;
    case 'ULTIMOS_7_DIAS':
      return `Últimos 7 dias${periodStr}`;
    case 'ULTIMOS_30_DIAS':
      return `Últimos 30 dias${periodStr}`;
    case 'MES_ATUAL':
      return `Mês atual${periodStr}`;
    case 'DIA_ESPECIFICO':
      return `${formatDate(specificDateMillis)}${periodStr}`;
    case 'INTERVALO_PERSONALIZADO':
      return `${formatDate(customStartMillis)} até ${formatDate(customEndMillis)}${periodStr}`;
    case 'TODOS':
      return `Todo o período${periodStr}`;
  }
}

export function AdminScreen() {
  const { allShifts, allAttendances, allMembers, sync, setShowSyncLogsModal } = useApp();

  const [dateOption, setDateOption] = useState<DateFilterOption>('HOJE');
  const [specificDateMillis, setSpecificDateMillis] = useState<number>(() => Date.now());
  const [customStartMillis, setCustomStartMillis] = useState<number>(
    () => Date.now() - 7 * 86400000
  );
  const [customEndMillis, setCustomEndMillis] = useState<number>(() => Date.now());
  const [selectedPeriod, setSelectedPeriod] = useState<ShiftPeriod | null>(null);
  const [preferentialOnly, setPreferentialOnly] = useState(false);

  const [isExporting, setIsExporting] = useState(false);
  const [exportSuccessData, setExportSuccessData] = useState<ExportSuccessData | null>(null);
  const [exportErrorMessage, setExportErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    void syncManager.triggerSync();
  }, []);

  const [startMillis, endMillis] = useMemo(
    () => computeDateRange(dateOption, specificDateMillis, customStartMillis, customEndMillis),
    [dateOption, specificDateMillis, customStartMillis, customEndMillis]
  );

  const metrics = useMemo(() => {
    const shiftsById = new Map(allShifts.map((s) => [s.id, s]));
    const filtered = allAttendances.filter((att) => {
      const matchDate = att.createdAt >= startMillis && att.createdAt <= endMillis;
      const shift = shiftsById.get(att.shiftId);
      const matchPeriod = selectedPeriod === null || shift?.period === selectedPeriod;
      const matchPref = !preferentialOnly || att.isPreferential;
      return matchDate && matchPeriod && matchPref;
    });

    const membersByAtt = new Map<string, typeof allMembers>();
    for (const m of allMembers) {
      const list = membersByAtt.get(m.attendanceId) ?? [];
      list.push(m);
      membersByAtt.set(m.attendanceId, list);
    }

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
          originDist[m.origin] = (originDist[m.origin] ?? 0) + 1;
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
  }, [allShifts, allAttendances, allMembers, startMillis, endMillis, selectedPeriod, preferentialOnly]);

  const openShifts = useMemo(
    () => allShifts.filter((s) => s.status === 'OPEN'),
    [allShifts]
  );

  const totalOriginNew = Math.max(
    1,
    Object.values(metrics.originDistribution).reduce((a, b) => a + b, 0)
  );

  const handleExport = async () => {
    setIsExporting(true);
    setExportSuccessData(null);
    setExportErrorMessage(null);

    const periodLabel = getPeriodLabel(
      dateOption,
      specificDateMillis,
      customStartMillis,
      customEndMillis,
      selectedPeriod
    );

    const result = await exportReport({
      startMillis,
      endMillis,
      selectedHosterId: null,
      selectedShiftPeriod: selectedPeriod,
    });

    setIsExporting(false);

    if (result.status === 'SUCCESS') {
      setExportSuccessData({
        bytes: result.bytes,
        fileName: result.fileName,
        rowCount: result.rowCount,
        totalClients: result.totalClients,
        periodLabel,
      });
    } else if (result.status === 'BLOCKED') {
      setExportErrorMessage(result.reason);
    } else {
      setExportErrorMessage(result.message);
    }
  };

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <div
        className="scroll-area"
        style={{
          flex: 1,
          padding: '0 20px 24px',
          display: 'flex',
          flexDirection: 'column',
          gap: '16px',
        }}
      >
        {/* Header */}
        <div
          style={{
            paddingTop: '16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '10px',
          }}
        >
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: '20px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
              Administração
            </div>
            <div style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
              Métricas operacionais e exportação de relatórios
            </div>
          </div>

          <SyncStatusPill syncState={sync.syncState} pendingCount={sync.pendingCount} />
        </div>

        {/* Outdated / Cache Warning Banner */}
        {(sync.syncState !== 'SINCRONIZADO' || sync.pendingCount > 0) && (
          <button
            type="button"
            onClick={() => setShowSyncLogsModal(true)}
            className="tap-feedback"
            style={{
              width: '100%',
              backgroundColor: 'rgba(217, 119, 6, 0.12)',
              border: '1px solid rgba(217, 119, 6, 0.4)',
              borderRadius: '12px',
              padding: '10px 14px',
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
              textAlign: 'left',
            }}
          >
            <AlertTriangle size={20} color="#D97706" style={{ flexShrink: 0 }} />
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
                As informações podem estar desatualizadas, sincronize-as
              </div>
              <div style={{ fontSize: '11px', color: 'var(--pao-anthracite-muted)' }}>
                Somando registros em cache com alterações locais.
              </div>
            </div>
            <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--pao-red)' }}>
              Ver Logs
            </span>
          </button>
        )}

        {/* Filter Bar Card */}
        <div
          style={{
            backgroundColor: '#FFFFFF',
            border: '1px solid var(--pao-border)',
            borderRadius: '16px',
            padding: '14px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Filter size={18} color="#9C1819" />
            <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
              Período do Relatório e Filtros
            </span>
          </div>

          {/* Date Preset Chips */}
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: '6px',
              marginTop: '10px',
            }}
          >
            {DATE_OPTIONS.map((opt) => {
              const selected = dateOption === opt.id;
              return (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => setDateOption(opt.id)}
                  data-testid={`filter_date_${opt.id.toLowerCase()}`}
                  className="tap-feedback"
                  style={{
                    border: 'none',
                    borderRadius: '14px',
                    padding: '6px 10px',
                    backgroundColor: selected ? 'var(--pao-red)' : 'var(--pao-cream-light)',
                    color: selected ? '#FFFFFF' : 'var(--pao-anthracite)',
                    fontSize: '11px',
                    fontWeight: selected ? 700 : 500,
                  }}
                >
                  {opt.label}
                </button>
              );
            })}
          </div>

          {/* Specific Day Picker */}
          {dateOption === 'DIA_ESPECIFICO' && (
            <label
              style={{
                marginTop: '10px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: '8px',
                backgroundColor: 'var(--pao-cream-light)',
                border: '1px solid var(--pao-border)',
                borderRadius: '10px',
                padding: '8px 12px',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Calendar size={16} color="#9C1819" />
                <span style={{ fontSize: '12px', fontWeight: 500, color: 'var(--pao-anthracite)' }}>
                  Dia selecionado:
                </span>
              </div>
              <input
                type="date"
                value={toInputDateValue(specificDateMillis)}
                onChange={(e) => {
                  if (e.target.value) {
                    setSpecificDateMillis(fromInputDateValue(e.target.value));
                  }
                }}
                style={{
                  border: '1px solid var(--pao-border)',
                  borderRadius: '8px',
                  backgroundColor: '#FFFFFF',
                  padding: '4px 8px',
                  fontSize: '12px',
                  fontWeight: 700,
                  color: 'var(--pao-anthracite)',
                }}
              />
            </label>
          )}

          {/* Custom Range Start & End Pickers */}
          {dateOption === 'INTERVALO_PERSONALIZADO' && (
            <div style={{ display: 'flex', gap: '8px', marginTop: '10px' }}>
              <label
                style={{
                  flex: 1,
                  backgroundColor: 'var(--pao-cream-light)',
                  border: '1px solid var(--pao-border)',
                  borderRadius: '10px',
                  padding: '8px',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '4px',
                }}
              >
                <span style={{ fontSize: '10px', color: 'var(--pao-anthracite-muted)' }}>
                  Data Início
                </span>
                <input
                  type="date"
                  value={toInputDateValue(customStartMillis)}
                  onChange={(e) => {
                    if (e.target.value) {
                      setCustomStartMillis(fromInputDateValue(e.target.value));
                    }
                  }}
                  style={{
                    width: '100%',
                    border: '1px solid var(--pao-border)',
                    borderRadius: '6px',
                    backgroundColor: '#FFFFFF',
                    padding: '4px 6px',
                    fontSize: '12px',
                    fontWeight: 700,
                    color: 'var(--pao-anthracite)',
                  }}
                />
              </label>

              <label
                style={{
                  flex: 1,
                  backgroundColor: 'var(--pao-cream-light)',
                  border: '1px solid var(--pao-border)',
                  borderRadius: '10px',
                  padding: '8px',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '4px',
                }}
              >
                <span style={{ fontSize: '10px', color: 'var(--pao-anthracite-muted)' }}>
                  Data Fim
                </span>
                <input
                  type="date"
                  value={toInputDateValue(customEndMillis)}
                  onChange={(e) => {
                    if (e.target.value) {
                      setCustomEndMillis(fromInputDateValue(e.target.value));
                    }
                  }}
                  style={{
                    width: '100%',
                    border: '1px solid var(--pao-border)',
                    borderRadius: '6px',
                    backgroundColor: '#FFFFFF',
                    padding: '4px 6px',
                    fontSize: '12px',
                    fontWeight: 700,
                    color: 'var(--pao-anthracite)',
                  }}
                />
              </label>
            </div>
          )}

          {/* Shift & Preferential Filter Chips */}
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginTop: '12px' }}>
            <PeriodChip
              title="Todos Turnos"
              isSelected={selectedPeriod === null}
              onClick={() => setSelectedPeriod(null)}
            />
            <PeriodChip
              title="Manhã"
              isSelected={selectedPeriod === 'MANHA'}
              onClick={() => setSelectedPeriod('MANHA')}
            />
            <PeriodChip
              title="Noite"
              isSelected={selectedPeriod === 'NOITE'}
              onClick={() => setSelectedPeriod('NOITE')}
            />
            <PeriodChip
              title="★ Preferencial"
              isSelected={preferentialOnly}
              onClick={() => setPreferentialOnly((p) => !p)}
            />
          </div>
        </div>

        {/* Key Metrics Title */}
        <div style={{ fontSize: '15px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
          Indicadores Principais
        </div>

        {/* 2x3 Metric Cards */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <div style={{ display: 'flex', gap: '10px' }}>
            <AdminMetricCard
              title="Pessoas Atendidas"
              value={String(metrics.totalPeople)}
              subtitle={`${metrics.totalAttendances} atendimentos`}
            />
            <AdminMetricCard
              title="Clientes Novos"
              value={String(metrics.totalNewClients)}
              valueColor="#1E824C"
              subtitle={`${
                metrics.totalPeople > 0
                  ? Math.floor((metrics.totalNewClients * 100) / metrics.totalPeople)
                  : 0
              }% do total`}
            />
          </div>

          <div style={{ display: 'flex', gap: '10px' }}>
            <AdminMetricCard
              title="Recorrentes"
              value={String(metrics.totalReturningClients)}
              subtitle="Clientes fiéis"
            />
            <AdminMetricCard
              title="Preferencial (★)"
              value={String(metrics.preferentialCount)}
              valueColor="#D97706"
              subtitle="Atendimentos prioritários"
            />
          </div>

          <div style={{ display: 'flex', gap: '10px' }}>
            <AdminMetricCard
              title="Clientes Manhã"
              value={String(metrics.morningClients)}
              subtitle="Turno Manhã"
            />
            <AdminMetricCard
              title="Clientes Noite"
              value={String(metrics.nightClients)}
              subtitle="Turno Noite"
            />
          </div>
        </div>

        {/* Distribution of New Clients by Origin */}
        <div
          style={{
            backgroundColor: '#FFFFFF',
            border: '1px solid var(--pao-border)',
            borderRadius: '16px',
            padding: '16px',
          }}
        >
          <div
            style={{
              fontSize: '14px',
              fontWeight: 700,
              color: 'var(--pao-anthracite)',
              marginBottom: '12px',
            }}
          >
            De Onde Conheceram (Clientes Novos)
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {ClientOrigins.map((origin) => {
              const count = metrics.originDistribution[origin] ?? 0;
              const pct = Math.floor((count * 100) / totalOriginNew);
              return (
                <div key={origin}>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      fontSize: '12px',
                    }}
                  >
                    <span style={{ color: 'var(--pao-anthracite)' }}>{origin}</span>
                    <span style={{ fontWeight: 700, color: 'var(--pao-anthracite)' }}>
                      {count} ({pct}%)
                    </span>
                  </div>
                  <div
                    style={{
                      marginTop: '4px',
                      width: '100%',
                      height: '6px',
                      borderRadius: '3px',
                      backgroundColor: 'var(--pao-border-light)',
                      overflow: 'hidden',
                    }}
                  >
                    <div
                      style={{
                        width: `${Math.min(100, Math.max(0, pct))}%`,
                        height: '100%',
                        borderRadius: '3px',
                        backgroundColor: count > 0 ? 'var(--pao-red)' : 'transparent',
                        transition: 'width 0.25s ease',
                      }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Hoster Performance */}
        <div
          style={{
            backgroundColor: '#FFFFFF',
            border: '1px solid var(--pao-border)',
            borderRadius: '16px',
            padding: '16px',
          }}
        >
          <div
            style={{
              fontSize: '14px',
              fontWeight: 700,
              color: 'var(--pao-anthracite)',
              marginBottom: '10px',
            }}
          >
            Desempenho por Hoster
          </div>

          {Object.keys(metrics.hosterPerformance).length === 0 ? (
            <div style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
              Nenhum atendimento registrado no período.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {Object.entries(metrics.hosterPerformance).map(([name, totalPpl]) => (
                <div
                  key={name}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '4px 0',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <User size={16} color="#9C1819" />
                    <span
                      style={{
                        fontSize: '13px',
                        fontWeight: 500,
                        color: 'var(--pao-anthracite)',
                      }}
                    >
                      {name}
                    </span>
                  </div>
                  <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
                    {totalPpl} pessoas
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Open Shifts Status */}
        <div
          style={{
            backgroundColor: '#FFFFFF',
            border: `1px solid ${openShifts.length > 0 ? '#D97706' : 'var(--pao-border)'}`,
            borderRadius: '16px',
            padding: '16px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{ fontSize: '14px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
              Turnos em Aberto ({openShifts.length})
            </span>
            {openShifts.length > 0 && (
              <span
                style={{
                  borderRadius: '4px',
                  backgroundColor: 'rgba(217, 119, 6, 0.15)',
                  padding: '2px 6px',
                  fontSize: '10px',
                  fontWeight: 700,
                  color: '#D97706',
                }}
              >
                Bloqueia Exportação
              </span>
            )}
          </div>

          <div style={{ marginTop: '10px' }}>
            {openShifts.length === 0 ? (
              <div style={{ fontSize: '12px', fontWeight: 500, color: '#1E824C' }}>
                Todos os turnos estão devidamente encerrados. Exportação liberada.
              </div>
            ) : (
              <>
                <div style={{ fontSize: '11px', color: 'var(--pao-anthracite-muted)' }}>
                  Turnos abertos que precisam ser encerrados antes do fechamento:
                </div>
                <div style={{ marginTop: '6px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  {openShifts.map((s) => (
                    <div
                      key={s.id}
                      style={{
                        backgroundColor: 'var(--pao-cream-light)',
                        borderRadius: '8px',
                        padding: '10px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                      }}
                    >
                      <div>
                        <div
                          style={{
                            fontSize: '12px',
                            fontWeight: 700,
                            color: 'var(--pao-anthracite)',
                          }}
                        >
                          {s.hosterName}
                        </div>
                        <div style={{ fontSize: '11px', color: 'var(--pao-anthracite-muted)' }}>
                          Turno {ShiftPeriodDisplay[s.period]} • Aberto {formatTime(s.openedAt)}
                        </div>
                      </div>
                      <span style={{ fontSize: '11px', fontWeight: 700, color: '#D97706' }}>
                        Aberto
                      </span>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        </div>

        {/* Export Section */}
        <div
          style={{
            backgroundColor: 'var(--pao-cream-light)',
            border: '1px solid var(--pao-border)',
            borderRadius: '16px',
            padding: '16px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Download size={20} color="#9C1819" />
            <span style={{ fontSize: '14px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
              Gerar Planilha Gerencial (.xlsx)
            </span>
          </div>
          <div
            style={{
              marginTop: '6px',
              marginBottom: '14px',
              fontSize: '12px',
              color: 'var(--pao-anthracite-muted)',
              lineHeight: 1.45,
            }}
          >
            Gera uma planilha Excel com 3 abas completas (Atendimentos, Clientes Detalhado e
            Resumo Diário & Origens com separação por dia e consolidação geral).
          </div>

          <PaoPrimaryButton
            text="Gerar Relatório XLSX"
            onClick={handleExport}
            isLoading={isExporting}
            testId="btn_exportar_excel"
          />
        </div>
      </div>

      {/* Export Success Confirmation & Share Popup */}
      {exportSuccessData && (
        <div className="dialog-modal-backdrop" onClick={() => setExportSuccessData(null)}>
          <div className="dialog-modal-card" onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <CheckCircle size={26} color="#1E824C" />
              <span style={{ fontSize: '18px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
                Relatório Gerado!
              </span>
            </div>

            <div
              style={{
                marginTop: '8px',
                fontSize: '13px',
                color: 'var(--pao-anthracite-muted)',
              }}
            >
              Planilha XLSX consolidada pronta para compartilhamento e análise.
            </div>

            <div
              style={{
                marginTop: '12px',
                backgroundColor: 'var(--pao-cream-light)',
                borderRadius: '12px',
                padding: '12px',
                display: 'flex',
                flexDirection: 'column',
                gap: '5px',
                fontSize: '12px',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: '8px' }}>
                <span style={{ color: 'var(--pao-anthracite-muted)' }}>Período:</span>
                <span style={{ fontWeight: 700, color: 'var(--pao-anthracite)', textAlign: 'right' }}>
                  {exportSuccessData.periodLabel}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: '8px' }}>
                <span style={{ color: 'var(--pao-anthracite-muted)' }}>Total de Clientes:</span>
                <span style={{ fontWeight: 700, color: 'var(--pao-red)' }}>
                  {exportSuccessData.totalClients} pessoas
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: '8px' }}>
                <span style={{ color: 'var(--pao-anthracite-muted)' }}>Atendimentos:</span>
                <span style={{ fontWeight: 700, color: 'var(--pao-anthracite)' }}>
                  {exportSuccessData.rowCount} registros
                </span>
              </div>
            </div>

            <div
              style={{
                marginTop: '10px',
                backgroundColor: '#FFFFFF',
                border: '1px solid var(--pao-border-light)',
                borderRadius: '10px',
                padding: '10px',
                fontSize: '11px',
                color: 'var(--pao-anthracite-muted)',
                lineHeight: 1.5,
              }}
            >
              <div style={{ fontWeight: 700, color: 'var(--pao-anthracite)', marginBottom: '3px' }}>
                Abas incluídas na planilha:
              </div>
              <div>1. Atendimentos (Turno, hoster, membros, tipo)</div>
              <div>2. Clientes (Individual, origem, classificação)</div>
              <div>3. Resumo Diário & Origens (Métricas por dia, turnos e canais)</div>
            </div>

            <div
              style={{
                marginTop: '16px',
                display: 'flex',
                justifyContent: 'flex-end',
                flexWrap: 'wrap',
                gap: '8px',
              }}
            >
              <button
                type="button"
                onClick={() => setExportSuccessData(null)}
                data-testid="btn_modal_fechar"
                style={{
                  border: 'none',
                  background: 'transparent',
                  padding: '8px 12px',
                  fontSize: '13px',
                  fontWeight: 600,
                  color: 'var(--pao-anthracite-muted)',
                }}
              >
                Fechar
              </button>
              <button
                type="button"
                onClick={() =>
                  downloadXlsxBytes(exportSuccessData.bytes, exportSuccessData.fileName)
                }
                data-testid="btn_modal_abrir"
                style={{
                  border: '1px solid var(--pao-border)',
                  borderRadius: '10px',
                  backgroundColor: '#FFFFFF',
                  padding: '8px 14px',
                  fontSize: '13px',
                  fontWeight: 600,
                  color: 'var(--pao-anthracite)',
                }}
              >
                Baixar
              </button>
              <button
                type="button"
                onClick={() =>
                  void shareOrDownloadXlsx(exportSuccessData.bytes, exportSuccessData.fileName)
                }
                data-testid="btn_modal_compartilhar"
                className="tap-feedback"
                style={{
                  border: 'none',
                  borderRadius: '10px',
                  backgroundColor: 'var(--pao-red)',
                  padding: '8px 14px',
                  fontSize: '13px',
                  fontWeight: 700,
                  color: '#FFFFFF',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                }}
              >
                <Share2 size={15} />
                <span>Compartilhar</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Export Error / Blocked Popup */}
      {exportErrorMessage && (
        <div className="dialog-modal-backdrop" onClick={() => setExportErrorMessage(null)}>
          <div className="dialog-modal-card" onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <AlertTriangle size={24} color="#9C1819" />
              <span style={{ fontSize: '17px', fontWeight: 700, color: '#9C1819' }}>
                Não Foi Possível Exportar
              </span>
            </div>
            <div
              style={{
                marginTop: '10px',
                fontSize: '13px',
                color: 'var(--pao-anthracite)',
                lineHeight: 1.5,
              }}
            >
              {exportErrorMessage}
            </div>
            <div style={{ marginTop: '16px', display: 'flex', justifyContent: 'flex-end' }}>
              <button
                type="button"
                onClick={() => setExportErrorMessage(null)}
                style={{
                  border: 'none',
                  borderRadius: '10px',
                  backgroundColor: 'var(--pao-anthracite)',
                  color: '#FFFFFF',
                  padding: '9px 18px',
                  fontSize: '13px',
                  fontWeight: 600,
                }}
              >
                Entendido
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function PeriodChip({
  title,
  isSelected,
  onClick,
}: {
  title: string;
  isSelected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="tap-feedback"
      style={{
        border: 'none',
        borderRadius: '12px',
        padding: '6px 10px',
        backgroundColor: isSelected ? 'var(--pao-anthracite)' : 'var(--pao-cream-light)',
        color: isSelected ? '#FFFFFF' : 'var(--pao-anthracite)',
        fontSize: '11px',
        fontWeight: isSelected ? 700 : 500,
      }}
    >
      {title}
    </button>
  );
}

function AdminMetricCard({
  title,
  value,
  subtitle,
  valueColor = 'var(--pao-anthracite)',
}: {
  title: string;
  value: string;
  subtitle: string;
  valueColor?: string;
}) {
  return (
    <div
      style={{
        flex: 1,
        backgroundColor: '#FFFFFF',
        border: '1px solid var(--pao-border)',
        borderRadius: '14px',
        padding: '14px',
      }}
    >
      <div style={{ fontSize: '11px', fontWeight: 500, color: 'var(--pao-anthracite-muted)' }}>
        {title}
      </div>
      <div style={{ marginTop: '4px', fontSize: '24px', fontWeight: 700, color: valueColor }}>
        {value}
      </div>
      <div style={{ fontSize: '10px', color: 'var(--pao-anthracite-muted)' }}>{subtitle}</div>
    </div>
  );
}
