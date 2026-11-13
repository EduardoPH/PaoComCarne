import { useEffect, useState } from 'react';
import { AlertTriangle, Download, ShieldAlert, Smartphone, Wrench } from 'lucide-react';
import { buildApkPublicUrl, supabaseClient } from '../../data/remote/supabaseClient';
import { BrandLogoBox } from '../components/CommonComponents';

const FALLBACK_APK_PATH = 'app-releases/pao-com-carne-v1.0.apk';

export function MaintenanceScreen() {
  const [apkUrl, setApkUrl] = useState<string>(() => buildApkPublicUrl(FALLBACK_APK_PATH));
  const [latestVersionLabel, setLatestVersionLabel] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    if (typeof navigator !== 'undefined' && navigator.onLine) {
      void supabaseClient.getActiveAppReleases().then((res) => {
        if (!active || !res.ok || !res.data || res.data.length === 0) return;
        const latest = res.data[0];
        if (latest.apk_path && latest.apk_path.trim()) {
          setApkUrl(buildApkPublicUrl(latest.apk_path));
        }
        if (latest.version_name) {
          setLatestVersionLabel(`v${latest.version_name}`);
        }
      });
    }
    return () => {
      active = false;
    };
  }, []);

  return (
    <div
      data-testid="web_maintenance_screen"
      className="screen-transition scroll-area"
      style={{
        flex: 1,
        backgroundColor: 'var(--pao-background)',
        padding: '24px 20px',
        paddingTop: 'calc(28px + var(--safe-top))',
        paddingBottom: 'calc(28px + var(--safe-bottom))',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '16px',
      }}
    >
      <BrandLogoBox size={92} radius={24} padding={8} />

      <div style={{ textAlign: 'center' }}>
        <div
          style={{
            fontSize: '18px',
            fontWeight: 800,
            color: 'var(--pao-anthracite)',
            letterSpacing: '-0.2px',
          }}
        >
          Pão com Carne
        </div>
        <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--pao-anthracite-muted)' }}>
          Sistema de Recepção
        </div>
      </div>

      {/* Status Pill */}
      <div
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '8px',
          padding: '8px 16px',
          borderRadius: '24px',
          backgroundColor: 'rgba(156, 24, 25, 0.12)',
          border: '1px solid rgba(156, 24, 25, 0.35)',
          color: 'var(--pao-red)',
          fontSize: '13px',
          fontWeight: 800,
        }}
      >
        <Wrench size={16} color="#9C1819" />
        <span>Versão Web em Manutenção</span>
      </div>

      {/* Main Maintenance Card */}
      <div
        style={{
          width: '100%',
          backgroundColor: '#FFFFFF',
          border: '1px solid var(--pao-border)',
          borderRadius: '20px',
          padding: '20px',
          boxShadow: '0 8px 24px rgba(38, 29, 28, 0.08)',
          display: 'flex',
          flexDirection: 'column',
          gap: '14px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
          <div
            style={{
              width: '40px',
              height: '40px',
              borderRadius: '12px',
              backgroundColor: 'rgba(217, 119, 6, 0.14)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            <AlertTriangle size={22} color="#D97706" />
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <h1
              style={{
                margin: 0,
                fontSize: '16px',
                fontWeight: 800,
                color: 'var(--pao-anthracite)',
                lineHeight: 1.3,
              }}
            >
              Acesso Web Temporariamente Suspenso
            </h1>
            <p
              style={{
                margin: 0,
                fontSize: '13px',
                fontWeight: 500,
                color: 'var(--pao-anthracite-muted)',
                lineHeight: 1.45,
              }}
            >
              O aplicativo Web está em manutenção para correção definitiva na sincronização de logs
              e integridade operacional.
            </p>
          </div>
        </div>

        <div
          style={{
            backgroundColor: 'var(--pao-cream-light)',
            border: '1px solid var(--pao-border)',
            borderRadius: '14px',
            padding: '14px',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '10px',
          }}
        >
          <ShieldAlert size={18} color="#9C1819" style={{ flexShrink: 0, marginTop: '2px' }} />
          <div style={{ fontSize: '12px', color: 'var(--pao-anthracite)', lineHeight: 1.45 }}>
            Por segurança dos dados, não é possível acessar, registrar atendimentos ou operar pelo
            navegador enquanto a manutenção estiver ativa.
          </div>
        </div>

        {/* Native Android CTA */}
        <div
          style={{
            backgroundColor: 'rgba(156, 24, 25, 0.05)',
            border: '1.5px solid rgba(156, 24, 25, 0.28)',
            borderRadius: '16px',
            padding: '16px',
            display: 'flex',
            flexDirection: 'column',
            gap: '10px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Smartphone size={20} color="#9C1819" />
            <span style={{ fontSize: '14px', fontWeight: 800, color: 'var(--pao-red)' }}>
              Procure a versão nativa para Android
            </span>
          </div>

          <p
            style={{
              margin: 0,
              fontSize: '12.5px',
              fontWeight: 500,
              color: 'var(--pao-anthracite)',
              lineHeight: 1.45,
            }}
          >
            Para continuar utilizando o sistema normalmente, abra o aplicativo nativo{' '}
            <strong>Pão com Carne</strong> em um dispositivo Android ou baixe o instalador oficial
            (APK){latestVersionLabel ? ` (${latestVersionLabel})` : ''} abaixo:
          </p>

          <a
            href={apkUrl}
            target="_blank"
            rel="noopener noreferrer"
            data-testid="maintenance_download_android_apk"
            className="tap-feedback"
            style={{
              width: '100%',
              minHeight: '50px',
              borderRadius: '14px',
              backgroundColor: 'var(--pao-red)',
              color: '#FFFFFF',
              textDecoration: 'none',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '8px',
              fontSize: '13.5px',
              fontWeight: 700,
              boxShadow: '0 6px 16px rgba(156, 24, 25, 0.25)',
            }}
          >
            <Download size={18} color="#FFFFFF" />
            <span>Baixar Aplicativo Android (APK)</span>
          </a>
        </div>

        <div
          style={{
            textAlign: 'center',
            fontSize: '11.5px',
            color: 'var(--pao-anthracite-muted)',
            lineHeight: 1.4,
          }}
        >
          Caso precise de auxílio para instalar o app Android nativo no tablet ou celular da
          recepção, procure o Administrador responsável.
        </div>
      </div>
    </div>
  );
}
