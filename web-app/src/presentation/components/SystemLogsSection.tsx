import { CheckCircle2, History, MonitorSmartphone, User, XCircle } from 'lucide-react';
import { LogResultDisplay, type LogResult, type SystemLog } from '../../domain/models';
import {
  endOfDay,
  startOfDay,
  type SystemLogsFilterState,
} from '../../domain/utils/adminFilters';
import { formatDateTime, fromInputDateValue, toInputDateValue } from '../../domain/utils/dateTime';

export function formatActionLabel(action: string): string {
  return action
    .toLowerCase()
    .split('_')
    .map((p) => (p.length > 0 ? p[0].toUpperCase() + p.slice(1) : p))
    .join(' ');
}

export function SystemLogsFilterCard({
  filter,
  availableUsers,
  availableActions,
  availableDevices,
  onStartDateChange,
  onEndDateChange,
  onUserChange,
  onActionChange,
  onDeviceChange,
  onResultChange,
  onClearFilters,
}: {
  filter: SystemLogsFilterState;
  availableUsers: string[];
  availableActions: string[];
  availableDevices: string[];
  onStartDateChange: (val: number | null) => void;
  onEndDateChange: (val: number | null) => void;
  onUserChange: (val: string | null) => void;
  onActionChange: (val: string | null) => void;
  onDeviceChange: (val: string | null) => void;
  onResultChange: (val: LogResult | null) => void;
  onClearFilters: () => void;
}) {
  return (
    <div
      style={{
        backgroundColor: '#FFFFFF',
        border: '1px solid var(--pao-border)',
        borderRadius: '16px',
        padding: '14px',
        display: 'flex',
        flexDirection: 'column',
        gap: '10px',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <History size={18} color="#9C1819" />
          <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
            Filtros de Auditoria e Logs
          </span>
        </div>
        <button
          type="button"
          onClick={onClearFilters}
          style={{
            border: 'none',
            background: 'transparent',
            fontSize: '11px',
            fontWeight: 700,
            color: 'var(--pao-red)',
            padding: '4px 6px',
          }}
        >
          Limpar
        </button>
      </div>

      {/* Period start / end */}
      <div style={{ display: 'flex', gap: '8px' }}>
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
          <span style={{ fontSize: '10px', color: 'var(--pao-anthracite-muted)' }}>Data inicial</span>
          <input
            type="date"
            value={filter.startMillis !== null ? toInputDateValue(filter.startMillis) : ''}
            onChange={(e) =>
              onStartDateChange(e.target.value ? startOfDay(fromInputDateValue(e.target.value)) : null)
            }
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
          <span style={{ fontSize: '10px', color: 'var(--pao-anthracite-muted)' }}>Data final</span>
          <input
            type="date"
            value={filter.endMillis !== null ? toInputDateValue(filter.endMillis) : ''}
            onChange={(e) =>
              onEndDateChange(e.target.value ? endOfDay(fromInputDateValue(e.target.value)) : null)
            }
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

      {/* Resultado */}
      <div>
        <div
          style={{
            fontSize: '11px',
            fontWeight: 600,
            color: 'var(--pao-anthracite-muted)',
            marginBottom: '6px',
          }}
        >
          Resultado
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
          <LogChip label="Todos" selected={filter.selectedResult === null} onClick={() => onResultChange(null)} />
          <LogChip
            label="Sucesso"
            selected={filter.selectedResult === 'SUCCESS'}
            onClick={() => onResultChange('SUCCESS')}
          />
          <LogChip
            label="Falha"
            selected={filter.selectedResult === 'FAILURE'}
            onClick={() => onResultChange('FAILURE')}
          />
        </div>
      </div>

      {/* Usuário */}
      {availableUsers.length > 0 && (
        <div>
          <div
            style={{
              fontSize: '11px',
              fontWeight: 600,
              color: 'var(--pao-anthracite-muted)',
              marginBottom: '6px',
            }}
          >
            Usuário
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
            <LogChip label="Todos" selected={filter.selectedUser === null} onClick={() => onUserChange(null)} />
            {availableUsers.map((u) => (
              <LogChip key={u} label={u} selected={filter.selectedUser === u} onClick={() => onUserChange(u)} />
            ))}
          </div>
        </div>
      )}

      {/* Dispositivo */}
      {availableDevices.length > 0 && (
        <div>
          <div
            style={{
              fontSize: '11px',
              fontWeight: 600,
              color: 'var(--pao-anthracite-muted)',
              marginBottom: '6px',
            }}
          >
            Dispositivo
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
            <LogChip
              label="Todos"
              selected={filter.selectedDevice === null}
              onClick={() => onDeviceChange(null)}
            />
            {availableDevices.map((d) => (
              <LogChip
                key={d}
                label={d}
                selected={filter.selectedDevice === d}
                onClick={() => onDeviceChange(d)}
              />
            ))}
          </div>
        </div>
      )}

      {/* Ação */}
      {availableActions.length > 0 && (
        <div>
          <div
            style={{
              fontSize: '11px',
              fontWeight: 600,
              color: 'var(--pao-anthracite-muted)',
              marginBottom: '6px',
            }}
          >
            Tipo de ação
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
            <LogChip label="Todas" selected={filter.selectedAction === null} onClick={() => onActionChange(null)} />
            {availableActions.map((act) => (
              <LogChip
                key={act}
                label={formatActionLabel(act)}
                selected={filter.selectedAction === act}
                onClick={() => onActionChange(act)}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export function SystemLogItemCard({ log }: { log: SystemLog }) {
  const isSuccess = log.result === 'SUCCESS';
  const badgeColor = isSuccess ? '#1E824C' : '#9C1819';

  return (
    <div
      data-testid={`system_log_item_${log.id}`}
      style={{
        backgroundColor: '#FFFFFF',
        border: '1px solid var(--pao-border-light)',
        borderRadius: '12px',
        padding: '12px',
        display: 'flex',
        flexDirection: 'column',
        gap: '6px',
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
            gap: '4px',
            padding: '3px 8px',
            borderRadius: '6px',
            backgroundColor: isSuccess ? 'rgba(30, 130, 76, 0.14)' : 'rgba(156, 24, 25, 0.14)',
            color: badgeColor,
            fontSize: '10px',
            fontWeight: 700,
          }}
        >
          {isSuccess ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
          <span>
            {formatActionLabel(log.action)} • {LogResultDisplay[log.result]}
          </span>
        </div>

        <span style={{ fontSize: '11px', color: 'var(--pao-anthracite-muted)' }}>
          {formatDateTime(log.createdAt)}
        </span>
      </div>

      <div style={{ fontSize: '12px', fontWeight: 500, color: 'var(--pao-anthracite)' }}>
        {log.description}
      </div>

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          fontSize: '11px',
          color: 'var(--pao-anthracite-muted)',
        }}
      >
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
          <User size={12} />
          {log.userName || 'Sistema'}
        </span>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
          <MonitorSmartphone size={12} />
          {log.deviceName}
        </span>
      </div>
    </div>
  );
}

function LogChip({
  label,
  selected,
  onClick,
}: {
  label: string;
  selected: boolean;
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
        padding: '5px 10px',
        backgroundColor: selected ? 'var(--pao-red)' : 'var(--pao-cream-light)',
        color: selected ? '#FFFFFF' : 'var(--pao-anthracite)',
        fontSize: '11px',
        fontWeight: selected ? 700 : 500,
      }}
    >
      {label}
    </button>
  );
}
