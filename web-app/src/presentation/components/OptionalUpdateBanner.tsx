import { Download, Smartphone } from 'lucide-react';
import type { UpdateUiState } from '../../data/update/updateService';

export function OptionalUpdateBanner({
  state,
  onUpdateClick,
  onDismiss,
}: {
  state: UpdateUiState;
  onUpdateClick: () => void;
  onDismiss: () => void;
}) {
  const policy = state.policy;
  if (
    !policy ||
    state.isBlocked ||
    state.status !== 'OPTIONAL_AVAILABLE' ||
    state.isOptionalBannerDismissed
  ) {
    return null;
  }

  return (
    <div
      style={{
        margin: '8px 16px 0',
        backgroundColor: 'var(--pao-cream)',
        border: '1px solid rgba(156, 24, 25, 0.35)',
        borderRadius: '12px',
        padding: '10px 12px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '8px',
        flexShrink: 0,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flex: 1, minWidth: 0 }}>
        <div
          style={{
            width: '30px',
            height: '30px',
            borderRadius: '50%',
            backgroundColor: 'rgba(156, 24, 25, 0.14)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <Smartphone size={16} color="#9C1819" />
        </div>
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
            Nova versão disponível (v{policy.versionName})
          </div>
          <div
            style={{
              fontSize: '11px',
              color: 'var(--pao-anthracite-muted)',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {policy.changelog || 'Melhorias de estabilidade e desempenho.'}
          </div>
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '4px', flexShrink: 0 }}>
        <button
          type="button"
          onClick={onDismiss}
          style={{
            border: 'none',
            background: 'transparent',
            padding: '4px 8px',
            fontSize: '11px',
            fontWeight: 600,
            color: 'var(--pao-anthracite-muted)',
          }}
        >
          Depois
        </button>
        <button
          type="button"
          onClick={onUpdateClick}
          className="tap-feedback"
          style={{
            border: 'none',
            borderRadius: '8px',
            backgroundColor: 'var(--pao-red)',
            color: '#FFFFFF',
            padding: '6px 10px',
            fontSize: '11px',
            fontWeight: 700,
            display: 'inline-flex',
            alignItems: 'center',
            gap: '4px',
          }}
        >
          <Download size={13} />
          <span>Atualizar</span>
        </button>
      </div>
    </div>
  );
}
