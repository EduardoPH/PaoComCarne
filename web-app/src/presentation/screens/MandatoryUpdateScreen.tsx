import { AlertTriangle, CloudOff, Download, Lock, RefreshCw } from 'lucide-react';
import type { UpdateUiState } from '../../data/update/updateService';
import { BrandLogoBox, PaoPrimaryButton } from '../components/CommonComponents';

export function MandatoryUpdateScreen({
  state,
  isOnline,
  apkUrl,
  onApplyWebUpdate,
  onRecheckVersion,
}: {
  state: UpdateUiState;
  isOnline: boolean;
  apkUrl: string | null;
  onApplyWebUpdate: () => void;
  onRecheckVersion: () => void;
}) {
  const policy = state.policy;
  const isUpdating = state.status === 'UPDATING';
  const progressPct = Math.round(Math.min(1, Math.max(0, state.progress)) * 100);

  return (
    <div
      className="scroll-area"
      data-testid="mandatory_update_screen"
      style={{
        flex: 1,
        padding: '28px 24px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <BrandLogoBox size={68} radius={18} padding={8} />

      <div
        style={{
          marginTop: '18px',
          display: 'inline-flex',
          alignItems: 'center',
          gap: '6px',
          padding: '5px 12px',
          borderRadius: '20px',
          backgroundColor: 'rgba(156, 24, 25, 0.12)',
          border: '1px solid rgba(156, 24, 25, 0.35)',
          fontSize: '11px',
          fontWeight: 700,
          color: 'var(--status-red)',
        }}
      >
        <Lock size={14} />
        <span>VERSÃO INCOMPATÍVEL BLOQUEADA</span>
      </div>

      <h1
        style={{
          margin: '14px 0 0',
          fontSize: '23px',
          fontWeight: 800,
          color: 'var(--pao-anthracite)',
          textAlign: 'center',
        }}
      >
        Atualização Obrigatória
      </h1>

      <p
        style={{
          margin: '8px 0 0',
          fontSize: '13px',
          color: 'var(--pao-anthracite-muted)',
          textAlign: 'center',
          lineHeight: 1.5,
        }}
      >
        Esta versão do Pão com Carne foi descontinuada para evitar inconsistências nos registros.
        Atualize agora para continuar operando.
      </p>

      {/* Version details card */}
      <div
        style={{
          width: '100%',
          marginTop: '20px',
          backgroundColor: '#FFFFFF',
          border: '1px solid var(--pao-border)',
          borderRadius: '16px',
          padding: '16px',
          display: 'flex',
          flexDirection: 'column',
          gap: '10px',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
            Versão instalada
          </span>
          <span
            style={{
              fontSize: '12px',
              fontWeight: 700,
              color: 'var(--status-red)',
              backgroundColor: 'rgba(156, 24, 25, 0.12)',
              padding: '4px 8px',
              borderRadius: '8px',
            }}
          >
            v{state.installedVersionName} (cód. {state.installedVersionCode})
          </span>
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
            Versão necessária
          </span>
          <span
            style={{
              fontSize: '12px',
              fontWeight: 700,
              color: '#1E824C',
              backgroundColor: 'rgba(30, 130, 76, 0.12)',
              padding: '4px 8px',
              borderRadius: '8px',
            }}
          >
            v{policy?.versionName ?? '—'} (mín. cód. {policy?.minSupportedCode ?? '—'})
          </span>
        </div>

        {policy?.releaseReason && (
          <div
            style={{
              backgroundColor: 'var(--pao-cream-light)',
              border: '1px solid var(--pao-border-light)',
              borderRadius: '10px',
              padding: '10px',
            }}
          >
            <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
              Motivo da atualização
            </div>
            <div style={{ marginTop: '2px', fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
              {policy.releaseReason}
            </div>
          </div>
        )}

        {policy?.changelog && (
          <div
            style={{
              backgroundColor: '#FFFFFF',
              border: '1px solid var(--pao-border-light)',
              borderRadius: '10px',
              padding: '10px',
            }}
          >
            <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
              Novidades da versão
            </div>
            <div style={{ marginTop: '4px', fontSize: '12px', color: 'var(--pao-anthracite)' }}>
              {policy.changelog}
            </div>
          </div>
        )}

        <div style={{ fontSize: '11px', fontWeight: 600, color: '#1E824C' }}>
          Seus registros locais pendentes e fila de sincronização serão preservados durante a
          atualização.
        </div>
      </div>

      {!isOnline && (
        <div
          style={{
            width: '100%',
            marginTop: '14px',
            backgroundColor: 'rgba(217, 119, 6, 0.14)',
            border: '1px solid rgba(217, 119, 6, 0.45)',
            borderRadius: '12px',
            padding: '12px',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            fontSize: '12px',
            fontWeight: 500,
            color: 'var(--pao-anthracite)',
          }}
        >
          <CloudOff size={20} color="#D97706" style={{ flexShrink: 0 }} />
          <span>
            Sem internet no momento. Conecte-se para validar e aplicar a atualização obrigatória.
          </span>
        </div>
      )}

      {isUpdating && (
        <div
          style={{
            width: '100%',
            marginTop: '14px',
            backgroundColor: '#FFFFFF',
            border: '1px solid var(--pao-border)',
            borderRadius: '14px',
            padding: '14px',
          }}
        >
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              fontSize: '12px',
              fontWeight: 700,
            }}
          >
            <span>{state.statusMessage || 'Atualizando aplicação...'}</span>
            <span style={{ color: 'var(--pao-red)' }}>{progressPct}%</span>
          </div>
          <div
            style={{
              marginTop: '8px',
              width: '100%',
              height: '8px',
              borderRadius: '4px',
              backgroundColor: 'var(--pao-border-light)',
              overflow: 'hidden',
            }}
          >
            <div
              style={{
                width: `${progressPct}%`,
                height: '100%',
                backgroundColor: 'var(--pao-red)',
                transition: 'width 0.2s ease',
              }}
            />
          </div>
        </div>
      )}

      {state.errorMessage && (
        <div
          style={{
            width: '100%',
            marginTop: '14px',
            backgroundColor: 'rgba(156, 24, 25, 0.12)',
            border: '1px solid rgba(156, 24, 25, 0.4)',
            borderRadius: '12px',
            padding: '12px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            fontSize: '12px',
            fontWeight: 500,
            color: 'var(--status-red)',
          }}
        >
          <AlertTriangle size={18} style={{ flexShrink: 0 }} />
          <span>{state.errorMessage}</span>
        </div>
      )}

      <div
        style={{
          width: '100%',
          marginTop: '16px',
          display: 'flex',
          flexDirection: 'column',
          gap: '10px',
        }}
      >
        <PaoPrimaryButton
          text={isUpdating ? 'Atualizando...' : 'Atualizar Aplicativo Agora'}
          onClick={onApplyWebUpdate}
          disabled={!isOnline || isUpdating}
          isLoading={isUpdating}
          testId="btn_mandatory_update"
        />

        {apkUrl && isOnline && (
          <a
            href={apkUrl}
            target="_blank"
            rel="noopener noreferrer"
            data-testid="btn_download_apk_storage"
            className="tap-feedback"
            style={{
              width: '100%',
              height: '46px',
              borderRadius: '12px',
              border: '1.5px solid var(--pao-red)',
              backgroundColor: 'var(--pao-cream-light)',
              color: 'var(--pao-red)',
              fontSize: '13px',
              fontWeight: 700,
              textDecoration: 'none',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '8px',
            }}
          >
            <Download size={16} />
            <span>Baixar APK Oficial (Android)</span>
          </a>
        )}

        <button
          type="button"
          onClick={onRecheckVersion}
          data-testid="btn_recheck_version"
          className="tap-feedback"
          style={{
            width: '100%',
            height: '48px',
            borderRadius: '12px',
            border: '1px solid var(--pao-border)',
            backgroundColor: '#FFFFFF',
            color: 'var(--pao-anthracite)',
            fontSize: '13px',
            fontWeight: 600,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '8px',
          }}
        >
          <RefreshCw size={16} />
          <span>Verificar Versão Novamente</span>
        </button>
      </div>
    </div>
  );
}
