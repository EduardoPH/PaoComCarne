import { useEffect, useState } from 'react';
import { PlusSquare, Share, X } from 'lucide-react';

const DISMISS_KEY = 'pao_ios_install_dismissed';

function detectIosSafariNotStandalone(): boolean {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') return false;
  const ua = navigator.userAgent || '';
  const isIos =
    /iPad|iPhone|iPod/.test(ua) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  if (!isIos) return false;

  const isStandalone =
    (navigator as Navigator & { standalone?: boolean }).standalone === true ||
    window.matchMedia('(display-mode: standalone)').matches;

  return !isStandalone;
}

export function IosInstallBanner() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const dismissed = localStorage.getItem(DISMISS_KEY) === 'true';
    if (!dismissed && detectIosSafariNotStandalone()) {
      setVisible(true);
    }
  }, []);

  if (!visible) return null;

  const handleDismiss = () => {
    localStorage.setItem(DISMISS_KEY, 'true');
    setVisible(false);
  };

  return (
    <div
      style={{
        backgroundColor: 'var(--pao-cream-light)',
        border: '1px solid var(--pao-border)',
        borderRadius: '12px',
        padding: '10px 12px',
        marginBottom: '12px',
        display: 'flex',
        alignItems: 'flex-start',
        gap: '10px',
      }}
    >
      <img
        src="/icons/apple-touch-icon.png"
        alt="Instalar Pão com Carne"
        style={{ width: '36px', height: '36px', borderRadius: '8px', flexShrink: 0 }}
      />
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
          Instalar no iPhone (Tela de Início)
        </div>
        <div
          style={{
            marginTop: '3px',
            fontSize: '11px',
            color: 'var(--pao-anthracite-muted)',
            lineHeight: 1.4,
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            gap: '4px',
          }}
        >
          <span>Toque em</span>
          <Share size={13} color="#9C1819" />
          <strong>Compartilhar</strong>
          <span>e depois em</span>
          <PlusSquare size={13} color="#9C1819" />
          <strong>Adicionar à Tela de Início</strong>
        </div>
      </div>
      <button
        type="button"
        onClick={handleDismiss}
        aria-label="Dispensar aviso de instalação"
        style={{
          border: 'none',
          background: 'transparent',
          padding: '4px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <X size={16} color="#6B5855" />
      </button>
    </div>
  );
}
