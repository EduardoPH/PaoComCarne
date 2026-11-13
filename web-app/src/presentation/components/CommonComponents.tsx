import React from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  CloudOff,
  CloudUpload,
  Minus,
  Plus,
  RefreshCw,
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import type { SyncState } from '../../domain/models';

export function SyncStatusPill({
  syncState,
  pendingCount,
  onClick,
}: {
  syncState: SyncState;
  pendingCount: number;
  onClick?: () => void;
}) {
  const { setShowSyncLogsModal } = useApp();

  const handleClick = () => {
    setShowSyncLogsModal(true);
    onClick?.();
  };

  let dotColor = 'var(--status-green)';
  let text = 'Sincronizado';
  let Icon = CheckCircle2;

  switch (syncState) {
    case 'SINCRONIZADO':
      dotColor = '#1E824C';
      text = 'Sincronizado';
      Icon = CheckCircle2;
      break;
    case 'OFFLINE':
      dotColor = '#D97706';
      text = 'Modo Offline';
      Icon = CloudOff;
      break;
    case 'PENDENTE':
      dotColor = '#D97706';
      text = `Pendente (${pendingCount})`;
      Icon = CloudUpload;
      break;
    case 'SINCRONIZANDO':
      dotColor = '#1E6091';
      text = 'Sincronizando...';
      Icon = RefreshCw;
      break;
    case 'ERRO':
      dotColor = '#9C1819';
      text = 'Erro de Sinc.';
      Icon = AlertTriangle;
      break;
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      data-testid="sync_status_pill"
      className="tap-feedback"
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '6px',
        padding: '6px 10px',
        borderRadius: '20px',
        backgroundColor: `${dotColor}1F`,
        border: `1px solid ${dotColor}59`,
        flexShrink: 0,
      }}
    >
      <span
        style={{
          width: '8px',
          height: '8px',
          borderRadius: '50%',
          backgroundColor: dotColor,
          flexShrink: 0,
        }}
      />
      <Icon
        size={14}
        color={dotColor}
        className={syncState === 'SINCRONIZANDO' ? 'spin' : undefined}
      />
      <span
        style={{
          fontSize: '11px',
          fontWeight: 600,
          color: 'var(--pao-anthracite)',
          whiteSpace: 'nowrap',
        }}
      >
        {text}
      </span>
    </button>
  );
}

export function PaoPrimaryButton({
  text,
  onClick,
  disabled = false,
  isLoading = false,
  style,
  testId,
}: {
  text: string;
  onClick: () => void;
  disabled?: boolean;
  isLoading?: boolean;
  style?: React.CSSProperties;
  testId?: string;
}) {
  const isDisabled = disabled || isLoading;
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={isDisabled}
      data-testid={testId}
      className="tap-feedback"
      style={{
        width: '100%',
        height: '54px',
        borderRadius: '12px',
        border: 'none',
        backgroundColor: isDisabled ? 'var(--pao-border)' : 'var(--pao-red)',
        color: '#FFFFFF',
        fontSize: '15px',
        fontWeight: 700,
        letterSpacing: '0.3px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        boxShadow: isDisabled ? 'none' : '0 2px 6px rgba(156, 24, 25, 0.22)',
        padding: '0 14px',
        ...style,
      }}
    >
      {isLoading ? (
        <span
          className="spin"
          style={{
            width: '22px',
            height: '22px',
            borderRadius: '50%',
            border: '2.5px solid rgba(255,255,255,0.35)',
            borderTopColor: '#ffffff',
          }}
        />
      ) : (
        text
      )}
    </button>
  );
}

export function PaoSecondaryButton({
  text,
  onClick,
  disabled = false,
  style,
  testId,
}: {
  text: string;
  onClick: () => void;
  disabled?: boolean;
  style?: React.CSSProperties;
  testId?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      data-testid={testId}
      className="tap-feedback"
      style={{
        height: '48px',
        borderRadius: '12px',
        border: '1.5px solid var(--pao-anthracite)',
        backgroundColor: 'transparent',
        color: 'var(--pao-anthracite)',
        fontSize: '14px',
        fontWeight: 600,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '0 14px',
        ...style,
      }}
    >
      {text}
    </button>
  );
}

export function NumericStepper({
  value,
  onChange,
  min = 0,
  max = 99,
  label,
  testIdPrefix = 'stepper',
}: {
  value: number;
  onChange: (val: number) => void;
  min?: number;
  max?: number;
  label: string;
  testIdPrefix?: string;
}) {
  return (
    <div
      style={{
        width: '100%',
        backgroundColor: 'var(--pao-cream-light)',
        border: '1px solid var(--pao-border-light)',
        borderRadius: '12px',
        padding: '10px 14px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '8px',
      }}
    >
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--pao-anthracite)' }}>
          {label}
        </div>
        <div style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
          {value === 0 ? 'Nenhum no momento' : `${value} selecionado(s)`}
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
        <button
          type="button"
          onClick={() => value > min && onChange(value - 1)}
          disabled={value <= min}
          data-testid={`${testIdPrefix}_decrement`}
          aria-label="Diminuir"
          className="tap-feedback"
          style={{
            width: '42px',
            height: '42px',
            borderRadius: '50%',
            backgroundColor: value > min ? '#FFFFFF' : 'var(--pao-border-light)',
            border: '1px solid var(--pao-border)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Minus
            size={18}
            color={value > min ? '#261D1C' : 'rgba(107, 88, 85, 0.5)'}
          />
        </button>

        <div
          style={{
            width: '44px',
            height: '42px',
            backgroundColor: '#FFFFFF',
            border: '1px solid var(--pao-border)',
            borderRadius: '8px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '18px',
            fontWeight: 700,
            color: 'var(--pao-anthracite)',
          }}
        >
          {value}
        </div>

        <button
          type="button"
          onClick={() => value < max && onChange(value + 1)}
          disabled={value >= max}
          data-testid={`${testIdPrefix}_increment`}
          aria-label="Aumentar"
          className="tap-feedback"
          style={{
            width: '42px',
            height: '42px',
            borderRadius: '50%',
            border: 'none',
            backgroundColor: value < max ? 'var(--pao-red)' : 'var(--pao-border-light)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Plus
            size={18}
            color={value < max ? '#FFFFFF' : 'rgba(107, 88, 85, 0.5)'}
          />
        </button>
      </div>
    </div>
  );
}

export function BrandLogoBox({ size = 80, radius = 20, padding = 6 }: { size?: number; radius?: number; padding?: number }) {
  return (
    <div
      style={{
        width: `${size}px`,
        height: `${size}px`,
        borderRadius: `${radius}px`,
        backgroundColor: 'var(--pao-red)',
        boxShadow: '0 4px 12px rgba(90, 19, 10, 0.2)',
        padding: `${padding}px`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0,
        overflow: 'hidden',
      }}
    >
      <img
        src="/icons/logo_pao_com_carne.png"
        alt="Logo Pão com Carne"
        style={{ width: '100%', height: '100%', objectFit: 'contain' }}
      />
    </div>
  );
}
