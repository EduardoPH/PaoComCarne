import { useState } from 'react';
import {
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  CloudOff,
  CloudUpload,
  Info,
  RefreshCw,
  X,
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { syncManager } from '../../data/sync/syncManager';
import type { SyncQueueItem } from '../../domain/models';
import { formatDateTime, formatTime } from '../../domain/utils/dateTime';
import { PaoPrimaryButton, PaoSecondaryButton } from './CommonComponents';

export function SyncLogsModal({ onDismiss }: { onDismiss: () => void }) {
  const { sync } = useApp();
  const [selectedTab, setSelectedTab] = useState<0 | 1>(0);
  const failedCount = sync.queueItems.filter((q) => q.status === 'FAILED').length;

  let dotColor = '#1E824C';
  let statusLabel = 'Sincronizado';
  let StatusIcon = CheckCircle2;

  switch (sync.syncState) {
    case 'SINCRONIZADO':
      dotColor = '#1E824C';
      statusLabel = 'Sincronizado';
      StatusIcon = CheckCircle2;
      break;
    case 'OFFLINE':
      dotColor = '#D97706';
      statusLabel = 'Modo Offline';
      StatusIcon = CloudOff;
      break;
    case 'PENDENTE':
      dotColor = '#D97706';
      statusLabel = `Pendente (${sync.pendingCount})`;
      StatusIcon = CloudUpload;
      break;
    case 'SINCRONIZANDO':
      dotColor = '#1E6091';
      statusLabel = 'Sincronizando...';
      StatusIcon = RefreshCw;
      break;
    case 'ERRO':
      dotColor = '#9C1819';
      statusLabel = 'Erro de Sincronização';
      StatusIcon = AlertTriangle;
      break;
  }

  return (
    <div className="fullscreen-modal-backdrop">
      <div className="fullscreen-modal-sheet">
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            height: '100%',
            padding: '12px 20px 24px',
          }}
        >
          {/* Header */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <div
                style={{
                  width: '40px',
                  height: '40px',
                  borderRadius: '10px',
                  backgroundColor: 'var(--pao-cream)',
                  border: '1px solid var(--pao-border-light)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <RefreshCw size={22} color="#9C1819" />
              </div>
              <div>
                <div style={{ fontSize: '17px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
                  Central de Sincronização
                </div>
                <div style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
                  Supabase PostgreSQL • Cache & Logs
                </div>
              </div>
            </div>

            <button
              type="button"
              onClick={onDismiss}
              data-testid="close_sync_dialog_btn"
              aria-label="Fechar"
              style={{
                width: '44px',
                height: '44px',
                border: 'none',
                background: 'transparent',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <X size={22} color="#261D1C" />
            </button>
          </div>

          <div style={{ height: '14px' }} />

          {/* Status Overview Card */}
          <div
            style={{
              backgroundColor: '#FFFFFF',
              border: '1px solid var(--pao-border)',
              borderRadius: '14px',
              padding: '14px',
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
              <div
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '5px 10px',
                  borderRadius: '20px',
                  backgroundColor: `${dotColor}1F`,
                  border: `1px solid ${dotColor}59`,
                }}
              >
                <span
                  style={{
                    width: '8px',
                    height: '8px',
                    borderRadius: '50%',
                    backgroundColor: dotColor,
                  }}
                />
                <StatusIcon size={14} color={dotColor} />
                <span style={{ fontSize: '12px', fontWeight: 700, color: dotColor }}>
                  {statusLabel}
                </span>
              </div>

              <span
                style={{
                  fontSize: '12px',
                  fontWeight: 600,
                  color: sync.isOnline ? 'var(--status-green)' : 'var(--pao-anthracite-muted)',
                }}
              >
                {sync.isOnline ? '🟢 Rede Conectada' : '⚪ Sem Internet'}
              </span>
            </div>

            <div style={{ marginTop: '10px', fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
              {sync.lastSyncTime
                ? `Última sincronização com sucesso: ${formatDateTime(sync.lastSyncTime)}`
                : 'Nenhuma sincronização completa registrada nesta sessão.'}
            </div>

            {(sync.syncState !== 'SINCRONIZADO' || sync.pendingCount > 0) && (
              <div
                style={{
                  marginTop: '8px',
                  backgroundColor: 'rgba(217, 119, 6, 0.12)',
                  border: '1px solid rgba(217, 119, 6, 0.35)',
                  borderRadius: '8px',
                  padding: '10px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                }}
              >
                <Info size={16} color="#D97706" style={{ flexShrink: 0 }} />
                <span style={{ fontSize: '11px', color: 'var(--pao-anthracite)' }}>
                  As informações podem estar desatualizadas, sincronize-as. Os dados locais estão
                  protegidos em cache.
                </span>
              </div>
            )}

            <div style={{ marginTop: '12px', display: 'flex', gap: '8px' }}>
              <PaoPrimaryButton
                text="Sincronizar Agora"
                onClick={() => void syncManager.triggerSync()}
                isLoading={sync.syncState === 'SINCRONIZANDO'}
                style={{ flex: 1 }}
                testId="force_sync_btn"
              />
              {failedCount > 0 && (
                <button
                  type="button"
                  onClick={() => void syncManager.retryFailedOperations()}
                  data-testid="retry_failed_sync_btn"
                  className="tap-feedback"
                  style={{
                    flex: 1,
                    height: '54px',
                    borderRadius: '12px',
                    backgroundColor: 'rgba(156, 24, 25, 0.12)',
                    border: '1px solid rgba(156, 24, 25, 0.4)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '6px',
                    fontSize: '12px',
                    fontWeight: 700,
                    color: 'var(--status-red)',
                    padding: '0 10px',
                  }}
                >
                  <RefreshCw size={16} color="#9C1819" />
                  <span>Reenviar Falhas ({failedCount})</span>
                </button>
              )}
            </div>
          </div>

          <div style={{ height: '14px' }} />

          {/* Tabs */}
          <div
            style={{
              display: 'flex',
              borderBottom: '1px solid var(--pao-border-light)',
            }}
          >
            <button
              type="button"
              onClick={() => setSelectedTab(0)}
              style={{
                flex: 1,
                padding: '10px 6px',
                background: 'transparent',
                border: 'none',
                borderBottom:
                  selectedTab === 0 ? '3px solid var(--pao-red)' : '3px solid transparent',
                fontSize: '13px',
                fontWeight: selectedTab === 0 ? 700 : 500,
                color: selectedTab === 0 ? 'var(--pao-red)' : 'var(--pao-anthracite-muted)',
              }}
            >
              Fila de Envio ({sync.queueItems.length})
            </button>
            <button
              type="button"
              onClick={() => setSelectedTab(1)}
              style={{
                flex: 1,
                padding: '10px 6px',
                background: 'transparent',
                border: 'none',
                borderBottom:
                  selectedTab === 1 ? '3px solid var(--pao-red)' : '3px solid transparent',
                fontSize: '13px',
                fontWeight: selectedTab === 1 ? 700 : 500,
                color: selectedTab === 1 ? 'var(--pao-red)' : 'var(--pao-anthracite-muted)',
              }}
            >
              Logs em Tempo Real ({sync.syncLogs.length})
            </button>
          </div>

          <div style={{ height: '10px' }} />

          {/* Tab Content */}
          <div className="scroll-area" style={{ flex: 1 }}>
            {selectedTab === 0 ? (
              sync.queueItems.length === 0 ? (
                <div
                  style={{
                    backgroundColor: '#FFFFFF',
                    borderRadius: '12px',
                    padding: '24px',
                    marginTop: '8px',
                    textAlign: 'center',
                    border: '1px solid var(--pao-border-light)',
                  }}
                >
                  <CheckCircle2 size={36} color="#1E824C" style={{ margin: '0 auto' }} />
                  <div
                    style={{
                      marginTop: '8px',
                      fontSize: '15px',
                      fontWeight: 700,
                      color: 'var(--pao-anthracite)',
                    }}
                  >
                    Fila Vazia e Sincronizada
                  </div>
                  <div style={{ marginTop: '4px', fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
                    Não há operações pendentes no momento. Todos os dados estão no Supabase.
                  </div>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  {sync.queueItems.map((item) => (
                    <QueueItemCard key={item.id} item={item} />
                  ))}
                </div>
              )
            ) : sync.syncLogs.length === 0 ? (
              <div style={{ padding: '16px', fontSize: '13px', color: 'var(--pao-anthracite-muted)' }}>
                Nenhum log registrado ainda.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                {sync.syncLogs.map((logItem) => (
                  <div
                    key={logItem.id}
                    style={{
                      backgroundColor: logItem.isError ? 'rgba(156, 24, 25, 0.08)' : '#FFFFFF',
                      border: `1px solid ${
                        logItem.isError ? 'rgba(156, 24, 25, 0.3)' : 'var(--pao-border-light)'
                      }`,
                      borderRadius: '8px',
                      padding: '8px 10px',
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '8px',
                    }}
                  >
                    <span
                      style={{
                        fontSize: '11px',
                        fontWeight: 700,
                        fontFamily: 'monospace',
                        color: logItem.isError ? 'var(--status-red)' : 'var(--pao-anthracite-muted)',
                      }}
                    >
                      {formatTime(logItem.timestamp)}
                    </span>
                    <span
                      style={{
                        fontSize: '12px',
                        color: logItem.isError ? 'var(--status-red)' : 'var(--pao-anthracite)',
                        flex: 1,
                      }}
                    >
                      {logItem.message}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div style={{ height: '12px' }} />

          <PaoSecondaryButton
            text="Fechar"
            onClick={onDismiss}
            style={{ width: '100%' }}
            testId="dismiss_sync_dialog_btn"
          />
        </div>
      </div>
    </div>
  );
}

function QueueItemCard({ item }: { item: SyncQueueItem }) {
  const friendlyMap: Record<string, string> = {
    ATTENDANCE: 'Registro de Atendimento',
    ATTENDANCE_MEMBERS: 'Integrantes da Dupla/Grupo',
    ATTENDANCE_AUDIT: 'Auditoria de Correção',
    SHIFT: 'Abertura de Turno',
    SHIFT_CLOSE: 'Fechamento de Turno',
    QUEUE_EVENT: 'Atualização de Fila',
    HOSTER_INSERT: 'Cadastro de Hoster',
    HOSTER_UPDATE: 'Atualização de Hoster',
    HOSTER_ACTIVATION: 'Primeiro Acesso Hoster',
    HOSTER_DELETE: 'Exclusão de Hoster',
  };

  const friendlyType = friendlyMap[item.entityType] ?? item.entityType;
  const badge =
    item.status === 'FAILED'
      ? { bg: 'rgba(156, 24, 25, 0.15)', fg: '#9C1819', text: 'FALHA' }
      : item.status === 'SYNCING'
        ? { bg: 'rgba(30, 96, 145, 0.15)', fg: '#1E6091', text: 'ENVIANDO' }
        : { bg: 'rgba(217, 119, 6, 0.15)', fg: '#D97706', text: 'PENDENTE' };

  return (
    <div
      style={{
        backgroundColor: '#FFFFFF',
        border: `1px solid ${
          item.status === 'FAILED' ? 'rgba(156, 24, 25, 0.4)' : 'var(--pao-border-light)'
        }`,
        borderRadius: '10px',
        padding: '12px',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
          {friendlyType}
        </span>
        <span
          style={{
            backgroundColor: badge.bg,
            color: badge.fg,
            fontSize: '10px',
            fontWeight: 700,
            padding: '2px 6px',
            borderRadius: '6px',
          }}
        >
          {badge.text}
        </span>
      </div>

      <div
        style={{
          marginTop: '4px',
          display: 'flex',
          justifyContent: 'space-between',
          fontSize: '11px',
          color: 'var(--pao-anthracite-muted)',
        }}
      >
        <span style={{ fontFamily: 'monospace' }}>
          ID: {item.entityId.slice(0, 12)} • Op: {item.operation}
        </span>
        <span>Tentativas: {item.attempts}</span>
      </div>

      {item.lastError && (
        <div
          style={{
            marginTop: '6px',
            backgroundColor: 'rgba(156, 24, 25, 0.08)',
            borderRadius: '6px',
            padding: '8px',
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
          }}
        >
          <AlertCircle size={14} color="#9C1819" style={{ flexShrink: 0 }} />
          <span style={{ fontSize: '11px', color: 'var(--status-red)' }}>{item.lastError}</span>
        </div>
      )}
    </div>
  );
}
