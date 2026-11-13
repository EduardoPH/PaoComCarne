import { useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  Calendar,
  CheckCircle,
  Download,
  Filter,
  History,
  Share2,
  User,
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import {
  downloadXlsxBytes,
  exportReport,
  shareOrDownloadXlsx,
} from '../../data/repositories/exportRepository';
import { systemLogRepository } from '../../data/repositories/systemLogRepository';
import { syncManager } from '../../data/sync/syncManager';
import {
  ClientOrigins,
  ShiftPeriodDisplay,
  SystemLogActions,
  type LogResult,
  type ShiftPeriod,
} from '../../domain/models';
import {
  computeAdminMetrics,
  DATE_OPTIONS,
  endOfDay,
  filterSystemLogs,
  getPeriodDisplayLabel,
  rangeForOption,
  startOfDay,
  validateDateRange,
  type AdminFilterState,
  type DateFilterOption,
  type SystemLogsFilterState,
} from '../../domain/utils/adminFilters';
import { formatTime, fromInputDateValue, toInputDateValue } from '../../domain/utils/dateTime';
import { PaoPrimaryButton, SyncStatusPill } from '../components/CommonComponents';
import { SystemLogItemCard, SystemLogsFilterCard } from '../components/SystemLogsSection';

interface ExportSuccessData {
  bytes: Uint8Array;
  fileName: string;
  rowCount: number;
  totalClients: number;
  periodLabel: string;
}

type AdminSubTab = 'INDICADORES' | 'LOGS';

export function AdminScreen() {
  const {
    currentHoster,
    hosters,
    allShifts,
    allAttendances,
    allMembers,
    systemLogs,
    sync,
    setShowSyncLogsModal,
  } = useApp();

  const [activeSubTab, setActiveSubTab] = useState<AdminSubTab>('INDICADORES');
  const [filter, setFilter] = useState<AdminFilterState>(() => {
    const now = Date.now();
    return {
      dateOption: 'HOJE',
      draftStartMillis: startOfDay(now),
      draftEndMillis: endOfDay(now),
      appliedStartMillis: startOfDay(now),
      appliedEndMillis: endOfDay(now),
      selectedHosterId: null,
      selectedPeriod: null,
      selectedOrigin: null,
      selectedClassification: null,
      preferentialOnly: false,
    };
  });
  const [dateValidationError, setDateValidationError] = useState<string | null>(null);

  const [logsFilter, setLogsFilter] = useState<SystemLogsFilterState>({
    startMillis: null,
    endMillis: null,
    selectedUser: null,
    selectedAction: null,
    selectedDevice: null,
    selectedResult: null,
  });

  const [isExporting, setIsExporting] = useState(false);
  const [exportSuccessData, setExportSuccessData] = useState<ExportSuccessData | null>(null);
  const [exportErrorMessage, setExportErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    void syncManager.triggerSync();
    systemLogRepository.logAsync({
      action: SystemLogActions.ADMIN_ACCESS,
      entityType: 'ADMIN',
      result: 'SUCCESS',
      description: 'Acesso ao painel Administrativo.',
      userId: currentHoster?.id ?? null,
      userName: currentHoster?.name ?? null,
    });
  }, []);

  const activePeriodSummary = useMemo(() => getPeriodDisplayLabel(filter), [filter]);

  const metrics = useMemo(
    () => computeAdminMetrics(allShifts, allAttendances, allMembers, filter),
    [allShifts, allAttendances, allMembers, filter]
  );

  const openShifts = useMemo(
    () => allShifts.filter((s) => s.status === 'OPEN'),
    [allShifts]
  );

  const availableNonAdminHosters = useMemo(
    () => hosters.filter((h) => !h.isAdmin),
    [hosters]
  );

  const totalOriginNew = Math.max(
    1,
    Object.values(metrics.originDistribution).reduce((a, b) => a + b, 0)
  );

  // System logs computed options & filtered list
  const availableLogUsers = useMemo(
    () =>
      Array.from(
        new Set(systemLogs.map((l) => l.userName).filter((n): n is string => Boolean(n && n.trim())))
      ).sort(),
    [systemLogs]
  );
  const availableLogActions = useMemo(
    () => Array.from(new Set(systemLogs.map((l) => l.action))).sort(),
    [systemLogs]
  );
  const availableLogDevices = useMemo(
    () => Array.from(new Set(systemLogs.map((l) => l.deviceName))).sort(),
    [systemLogs]
  );
  const filteredLogs = useMemo(
    () => filterSystemLogs(systemLogs, logsFilter),
    [systemLogs, logsFilter]
  );

  const logFilterChange = (nextFilter: AdminFilterState) => {
    systemLogRepository.logAsync({
      action: SystemLogActions.ADMIN_FILTER_APPLIED,
      entityType: 'ADMIN',
      result: 'SUCCESS',
      description: `Filtros aplicados: ${getPeriodDisplayLabel(nextFilter)}.`,
      userId: currentHoster?.id ?? null,
      userName: currentHoster?.name ?? null,
    });
  };

  const handleSelectDateOption = (opt: DateFilterOption) => {
    const [start, end] = rangeForOption(opt, filter.draftStartMillis, filter.draftEndMillis);
    setDateValidationError(null);
    const next: AdminFilterState =
      opt === 'TODOS'
        ? {
            ...filter,
            dateOption: opt,
            appliedStartMillis: 0,
            appliedEndMillis: Number.MAX_SAFE_INTEGER,
          }
        : {
            ...filter,
            dateOption: opt,
            draftStartMillis: start,
            draftEndMillis: end,
            appliedStartMillis: start,
            appliedEndMillis: end,
          };
    setFilter(next);
    logFilterChange(next);
  };

  const handleApplyDraftFilters = () => {
    const err = validateDateRange(filter.draftStartMillis, filter.draftEndMillis);
    if (err) {
      setDateValidationError(err);
      return;
    }
    setDateValidationError(null);
    const next: AdminFilterState = {
      ...filter,
      dateOption:
        filter.dateOption === 'TODOS' ? 'INTERVALO_PERSONALIZADO' : filter.dateOption,
      appliedStartMillis: startOfDay(filter.draftStartMillis),
      appliedEndMillis: endOfDay(filter.draftEndMillis),
    };
    setFilter(next);
    logFilterChange(next);
  };

  const handleClearFilters = () => {
    const now = Date.now();
    const start = startOfDay(now);
    const end = endOfDay(now);
    setDateValidationError(null);
    const next: AdminFilterState = {
      dateOption: 'TODOS',
      draftStartMillis: start,
      draftEndMillis: end,
      appliedStartMillis: 0,
      appliedEndMillis: Number.MAX_SAFE_INTEGER,
      selectedHosterId: null,
      selectedPeriod: null,
      selectedOrigin: null,
      selectedClassification: null,
      preferentialOnly: false,
    };
    setFilter(next);
    logFilterChange(next);
  };

  const handleExport = async () => {
    setIsExporting(true);
    setExportSuccessData(null);
    setExportErrorMessage(null);

    const [startMillis, endMillis] = rangeForOption(
      filter.dateOption,
      filter.appliedStartMillis,
      filter.appliedEndMillis
    );

    const result = await exportReport({
      startMillis,
      endMillis,
      selectedHosterId: filter.selectedHosterId,
      selectedShiftPeriod: filter.selectedPeriod,
      selectedOrigin: filter.selectedOrigin,
      selectedClassification: filter.selectedClassification,
      preferentialOnly: filter.preferentialOnly,
      actorUserId: currentHoster?.id ?? null,
      actorUserName: currentHoster?.name ?? null,
    });

    setIsExporting(false);

    if (result.status === 'SUCCESS') {
      setExportSuccessData({
        bytes: result.bytes,
        fileName: result.fileName,
        rowCount: result.rowCount,
        totalClients: result.totalClients,
        periodLabel: activePeriodSummary,
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
          gap: '14px',
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
              Métricas operacionais, relatórios e auditoria
            </div>
          </div>

          <SyncStatusPill syncState={sync.syncState} pendingCount={sync.pendingCount} />
        </div>

        {/* Sub-tabs: Indicadores vs Logs do Sistema */}
        <div
          style={{
            backgroundColor: '#FFFFFF',
            border: '1px solid var(--pao-border)',
            borderRadius: '12px',
            padding: '4px',
            display: 'flex',
            gap: '6px',
          }}
        >
          <button
            type="button"
            onClick={() => setActiveSubTab('INDICADORES')}
            data-testid="admin_tab_indicadores"
            className="tap-feedback"
            style={{
              flex: 1,
              height: '38px',
              borderRadius: '9px',
              border: 'none',
              backgroundColor:
                activeSubTab === 'INDICADORES' ? 'var(--pao-red)' : 'transparent',
              color:
                activeSubTab === 'INDICADORES' ? '#FFFFFF' : 'var(--pao-anthracite-muted)',
              fontSize: '12px',
              fontWeight: 700,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '6px',
            }}
          >
            <Filter size={14} />
            <span>Indicadores</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveSubTab('LOGS')}
            data-testid="admin_tab_logs"
            className="tap-feedback"
            style={{
              flex: 1,
              height: '38px',
              borderRadius: '9px',
              border: 'none',
              backgroundColor: activeSubTab === 'LOGS' ? 'var(--pao-red)' : 'transparent',
              color: activeSubTab === 'LOGS' ? '#FFFFFF' : 'var(--pao-anthracite-muted)',
              fontSize: '12px',
              fontWeight: 700,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '6px',
            }}
          >
            <History size={14} />
            <span>Logs do Sistema ({systemLogs.length})</span>
          </button>
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

        {activeSubTab === 'LOGS' ? (
          <div className="screen-transition" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <SystemLogsFilterCard
              filter={logsFilter}
              availableUsers={availableLogUsers}
              availableActions={availableLogActions}
              availableDevices={availableLogDevices}
              onStartDateChange={(v) => setLogsFilter((p) => ({ ...p, startMillis: v }))}
              onEndDateChange={(v) => setLogsFilter((p) => ({ ...p, endMillis: v }))}
              onUserChange={(v) => setLogsFilter((p) => ({ ...p, selectedUser: v }))}
              onActionChange={(v) => setLogsFilter((p) => ({ ...p, selectedAction: v }))}
              onDeviceChange={(v) => setLogsFilter((p) => ({ ...p, selectedDevice: v }))}
              onResultChange={(v: LogResult | null) =>
                setLogsFilter((p) => ({ ...p, selectedResult: v }))
              }
              onClearFilters={() =>
                setLogsFilter({
                  startMillis: null,
                  endMillis: null,
                  selectedUser: null,
                  selectedAction: null,
                  selectedDevice: null,
                  selectedResult: null,
                })
              }
            />

            <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
              Registros de Auditoria ({filteredLogs.length})
            </div>

            {filteredLogs.length === 0 ? (
              <div
                style={{
                  backgroundColor: '#FFFFFF',
                  border: '1px solid var(--pao-border)',
                  borderRadius: '14px',
                  padding: '20px',
                  textAlign: 'center',
                  fontSize: '12px',
                  color: 'var(--pao-anthracite-muted)',
                }}
              >
                Nenhum log encontrado para os filtros aplicados.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {filteredLogs.slice(0, 150).map((log) => (
                  <SystemLogItemCard key={log.id} log={log} />
                ))}
              </div>
            )}
          </div>
        ) : (
          <div className="screen-transition" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            {/* Prominent Período da Consulta Card */}
            <div
              data-testid="admin_period_filter_card"
              style={{
                backgroundColor: '#FFFFFF',
                border: '1.5px solid rgba(156, 24, 25, 0.35)',
                borderRadius: '18px',
                padding: '16px',
                boxShadow: '0 2px 10px rgba(156, 24, 25, 0.06)',
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: '8px',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Calendar size={18} color="#9C1819" />
                  <span
                    style={{
                      fontSize: '14px',
                      fontWeight: 700,
                      color: 'var(--pao-anthracite)',
                    }}
                  >
                    Período da consulta
                  </span>
                </div>

                <button
                  type="button"
                  onClick={handleClearFilters}
                  data-testid="btn_limpar_filtros"
                  style={{
                    border: 'none',
                    background: 'transparent',
                    fontSize: '12px',
                    fontWeight: 700,
                    color: 'var(--pao-red)',
                    padding: '4px 6px',
                  }}
                >
                  Limpar
                </button>
              </div>

              {/* Active period badge */}
              <div
                style={{
                  marginTop: '8px',
                  backgroundColor: 'var(--pao-cream-light)',
                  borderRadius: '8px',
                  padding: '6px 10px',
                  fontSize: '11px',
                  fontWeight: 700,
                  color: 'var(--pao-red)',
                }}
              >
                Consultando: {activePeriodSummary}
              </div>

              {/* Prominent Start & End Date Pickers */}
              <div style={{ display: 'flex', gap: '8px', marginTop: '12px' }}>
                <label
                  style={{
                    flex: 1,
                    backgroundColor: 'var(--pao-cream-light)',
                    border: '1px solid var(--pao-border)',
                    borderRadius: '10px',
                    padding: '8px 10px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '4px',
                  }}
                >
                  <span style={{ fontSize: '10px', color: 'var(--pao-anthracite-muted)' }}>
                    Data inicial
                  </span>
                  <input
                    type="date"
                    value={toInputDateValue(filter.draftStartMillis)}
                    onChange={(e) => {
                      if (!e.target.value) return;
                      const cleanStart = startOfDay(fromInputDateValue(e.target.value));
                      const err = validateDateRange(cleanStart, filter.draftEndMillis);
                      setDateValidationError(err);
                      setFilter((prev) => ({
                        ...prev,
                        dateOption: 'INTERVALO_PERSONALIZADO',
                        draftStartMillis: cleanStart,
                      }));
                    }}
                    data-testid="input_data_inicial"
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
                    padding: '8px 10px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '4px',
                  }}
                >
                  <span style={{ fontSize: '10px', color: 'var(--pao-anthracite-muted)' }}>
                    Data final
                  </span>
                  <input
                    type="date"
                    value={toInputDateValue(filter.draftEndMillis)}
                    onChange={(e) => {
                      if (!e.target.value) return;
                      const cleanEnd = endOfDay(fromInputDateValue(e.target.value));
                      const err = validateDateRange(filter.draftStartMillis, cleanEnd);
                      setDateValidationError(err);
                      setFilter((prev) => ({
                        ...prev,
                        dateOption: 'INTERVALO_PERSONALIZADO',
                        draftEndMillis: cleanEnd,
                      }));
                    }}
                    data-testid="input_data_final"
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

              {dateValidationError && (
                <div
                  data-testid="date_validation_error"
                  style={{
                    marginTop: '8px',
                    fontSize: '11px',
                    fontWeight: 600,
                    color: 'var(--status-red)',
                  }}
                >
                  {dateValidationError}
                </div>
              )}

              <button
                type="button"
                onClick={handleApplyDraftFilters}
                data-testid="btn_aplicar_filtros"
                className="tap-feedback"
                style={{
                  marginTop: '10px',
                  width: '100%',
                  height: '44px',
                  borderRadius: '10px',
                  border: 'none',
                  backgroundColor: 'var(--pao-red)',
                  color: '#FFFFFF',
                  fontSize: '13px',
                  fontWeight: 700,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '6px',
                }}
              >
                <Filter size={15} />
                <span>Aplicar filtros</span>
              </button>

              {/* Atalhos rápidos */}
              <div
                style={{
                  marginTop: '12px',
                  fontSize: '11px',
                  fontWeight: 600,
                  color: 'var(--pao-anthracite-muted)',
                }}
              >
                Atalhos de período
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginTop: '6px' }}>
                {DATE_OPTIONS.map((opt) => {
                  const selected = filter.dateOption === opt.id;
                  return (
                    <button
                      key={opt.id}
                      type="button"
                      onClick={() => handleSelectDateOption(opt.id)}
                      data-testid={`filter_date_${opt.id.toLowerCase()}`}
                      className="tap-feedback"
                      style={{
                        border: 'none',
                        borderRadius: '14px',
                        padding: '6px 10px',
                        backgroundColor: selected
                          ? 'var(--pao-red)'
                          : 'var(--pao-cream-light)',
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

              <div
                style={{
                  height: '1px',
                  backgroundColor: 'var(--pao-border-light)',
                  margin: '12px 0 10px',
                }}
              />

              {/* Turno & Preferencial */}
              <div
                style={{
                  fontSize: '11px',
                  fontWeight: 600,
                  color: 'var(--pao-anthracite-muted)',
                  marginBottom: '6px',
                }}
              >
                Turno e prioridade
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                <PeriodChip
                  title="Todos Turnos"
                  isSelected={filter.selectedPeriod === null}
                  onClick={() => setFilter((p) => ({ ...p, selectedPeriod: null }))}
                />
                <PeriodChip
                  title="Manhã"
                  isSelected={filter.selectedPeriod === 'MANHA'}
                  onClick={() => setFilter((p) => ({ ...p, selectedPeriod: 'MANHA' as ShiftPeriod }))}
                />
                <PeriodChip
                  title="Noite"
                  isSelected={filter.selectedPeriod === 'NOITE'}
                  onClick={() => setFilter((p) => ({ ...p, selectedPeriod: 'NOITE' as ShiftPeriod }))}
                />
                <PeriodChip
                  title="★ Preferencial"
                  isSelected={filter.preferentialOnly}
                  onClick={() => setFilter((p) => ({ ...p, preferentialOnly: !p.preferentialOnly }))}
                />
              </div>

              {/* Classificação */}
              <div
                style={{
                  fontSize: '11px',
                  fontWeight: 600,
                  color: 'var(--pao-anthracite-muted)',
                  marginTop: '10px',
                  marginBottom: '6px',
                }}
              >
                Classificação
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                <PeriodChip
                  title="Todas"
                  isSelected={filter.selectedClassification === null}
                  onClick={() => setFilter((p) => ({ ...p, selectedClassification: null }))}
                />
                <PeriodChip
                  title="Novos"
                  isSelected={filter.selectedClassification === 'NOVO'}
                  onClick={() => setFilter((p) => ({ ...p, selectedClassification: 'NOVO' }))}
                />
                <PeriodChip
                  title="Recorrentes"
                  isSelected={filter.selectedClassification === 'RECORRENTE'}
                  onClick={() =>
                    setFilter((p) => ({ ...p, selectedClassification: 'RECORRENTE' }))
                  }
                />
              </div>

              {/* Hoster */}
              {availableNonAdminHosters.length > 0 && (
                <>
                  <div
                    style={{
                      fontSize: '11px',
                      fontWeight: 600,
                      color: 'var(--pao-anthracite-muted)',
                      marginTop: '10px',
                      marginBottom: '6px',
                    }}
                  >
                    Hoster
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                    <PeriodChip
                      title="Todos"
                      isSelected={filter.selectedHosterId === null}
                      onClick={() => setFilter((p) => ({ ...p, selectedHosterId: null }))}
                    />
                    {availableNonAdminHosters.map((h) => (
                      <PeriodChip
                        key={h.id}
                        title={h.name}
                        isSelected={filter.selectedHosterId === h.id}
                        onClick={() => setFilter((p) => ({ ...p, selectedHosterId: h.id }))}
                      />
                    ))}
                  </div>
                </>
              )}

              {/* Origem */}
              <div
                style={{
                  fontSize: '11px',
                  fontWeight: 600,
                  color: 'var(--pao-anthracite-muted)',
                  marginTop: '10px',
                  marginBottom: '6px',
                }}
              >
                Origem (clientes novos)
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                <PeriodChip
                  title="Todas"
                  isSelected={filter.selectedOrigin === null}
                  onClick={() => setFilter((p) => ({ ...p, selectedOrigin: null }))}
                />
                {ClientOrigins.map((orig) => (
                  <PeriodChip
                    key={orig}
                    title={orig}
                    isSelected={filter.selectedOrigin === orig}
                    onClick={() => setFilter((p) => ({ ...p, selectedOrigin: orig }))}
                  />
                ))}
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
                      <span
                        style={{
                          fontSize: '13px',
                          fontWeight: 700,
                          color: 'var(--pao-anthracite)',
                        }}
                      >
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
        )}
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
