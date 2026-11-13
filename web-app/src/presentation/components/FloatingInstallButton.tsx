import { useEffect, useState } from 'react';
import {
  Check,
  Copy,
  Download,
  Monitor,
  MoreVertical,
  PlusSquare,
  Share,
  Smartphone,
  X,
} from 'lucide-react';
import {
  getBrowserPlatformInfo,
  type PlatformInfo,
} from '../../domain/utils/platformDetect';
import { PaoPrimaryButton } from './CommonComponents';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
}

const SESSION_DISMISS_KEY = 'pao_floating_install_hidden';

export function FloatingInstallButton({
  hasBottomBar,
}: {
  hasBottomBar: boolean;
}) {
  const [platform, setPlatform] = useState<PlatformInfo>(() => getBrowserPlatformInfo());
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [dismissed, setDismissed] = useState<boolean>(() => {
    if (typeof sessionStorage === 'undefined') return false;
    return sessionStorage.getItem(SESSION_DISMISS_KEY) === 'true';
  });
  const [showModal, setShowModal] = useState(false);
  const [copiedLink, setCopiedLink] = useState(false);
  const [justInstalled, setJustInstalled] = useState(false);

  useEffect(() => {
    setPlatform(getBrowserPlatformInfo());

    const handleBeforeInstallPrompt = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
    };

    const handleAppInstalled = () => {
      setDeferredPrompt(null);
      setJustInstalled(true);
      setShowModal(false);
    };

    window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
    window.addEventListener('appinstalled', handleAppInstalled);

    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
      window.removeEventListener('appinstalled', handleAppInstalled);
    };
  }, []);

  if (platform.isStandalone || justInstalled || dismissed) {
    return null;
  }

  const handleFloatingClick = async () => {
    if (deferredPrompt) {
      try {
        await deferredPrompt.prompt();
        const choice = await deferredPrompt.userChoice;
        if (choice.outcome === 'accepted') {
          setDeferredPrompt(null);
          return;
        }
      } catch {
        // Fallback to modal instructions if native prompt throws
      }
    }
    setShowModal(true);
  };

  const handleDismiss = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.setItem(SESSION_DISMISS_KEY, 'true');
    }
    setDismissed(true);
  };

  const handleCopyUrl = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setCopiedLink(true);
      setTimeout(() => setCopiedLink(false), 2500);
    } catch {
      // Ignore clipboard failures on restricted contexts
    }
  };

  const isDesktop =
    platform.os === 'WINDOWS' || platform.os === 'MACOS' || platform.os === 'LINUX';
  const SystemIcon = isDesktop ? Monitor : Smartphone;

  return (
    <>
      {/* Floating Corner Pill */}
      <div
        data-testid="floating_install_container"
        style={{
          position: 'absolute',
          right: '14px',
          bottom: hasBottomBar ? 'calc(84px + var(--safe-bottom))' : 'calc(18px + var(--safe-bottom))',
          zIndex: 85,
          display: 'flex',
          alignItems: 'center',
          backgroundColor: 'var(--pao-red)',
          color: '#FFFFFF',
          borderRadius: '999px',
          border: '2px solid var(--pao-cream)',
          boxShadow: '0 8px 22px rgba(90, 19, 10, 0.38)',
          padding: '4px 6px 4px 12px',
          gap: '6px',
          animation: 'scaleIn 0.2s ease-out',
        }}
      >
        <button
          type="button"
          onClick={() => void handleFloatingClick()}
          data-testid="btn_floating_install"
          className="tap-feedback"
          style={{
            border: 'none',
            background: 'transparent',
            color: '#FFFFFF',
            display: 'flex',
            alignItems: 'center',
            gap: '9px',
            padding: '4px 2px',
            textAlign: 'left',
          }}
        >
          <div
            style={{
              width: '32px',
              height: '32px',
              borderRadius: '50%',
              backgroundColor: 'var(--pao-cream)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            <Download size={17} color="#9C1819" strokeWidth={2.5} />
          </div>
          <div style={{ lineHeight: 1.15 }}>
            <div style={{ fontSize: '13px', fontWeight: 800, letterSpacing: '0.2px' }}>
              Baixar App
            </div>
            <div
              style={{
                fontSize: '10px',
                fontWeight: 600,
                color: 'var(--pao-cream)',
                display: 'flex',
                alignItems: 'center',
                gap: '4px',
                marginTop: '2px',
              }}
            >
              <SystemIcon size={10} color="#F6D3B2" />
              <span>{platform.osLabel}</span>
            </div>
          </div>
        </button>

        <button
          type="button"
          onClick={handleDismiss}
          data-testid="btn_floating_install_dismiss"
          aria-label="Fechar botão de download"
          style={{
            width: '26px',
            height: '26px',
            borderRadius: '50%',
            border: 'none',
            backgroundColor: 'rgba(255, 255, 255, 0.16)',
            color: '#FFFFFF',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <X size={13} color="#FFFFFF" />
        </button>
      </div>

      {/* OS-Specific Install Modal */}
      {showModal && (
        <div className="dialog-modal-backdrop" onClick={() => setShowModal(false)}>
          <div
            className="dialog-modal-card"
            onClick={(e) => e.stopPropagation()}
            data-testid="install_guide_modal"
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <img
                src="/icons/apple-touch-icon.png"
                alt="Pão com Carne App"
                style={{
                  width: '48px',
                  height: '48px',
                  borderRadius: '12px',
                  border: '1px solid var(--pao-border)',
                  flexShrink: 0,
                }}
              />
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: '17px', fontWeight: 800, color: 'var(--pao-anthracite)' }}>
                  Instalar Pão com Carne
                </div>
                <div
                  style={{
                    marginTop: '3px',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '5px',
                    backgroundColor: 'var(--pao-cream-light)',
                    border: '1px solid var(--pao-border)',
                    borderRadius: '8px',
                    padding: '2px 8px',
                    fontSize: '11px',
                    fontWeight: 700,
                    color: 'var(--pao-red)',
                  }}
                >
                  <SystemIcon size={12} color="#9C1819" />
                  <span>
                    Sistema detectado: {platform.osLabel} • {platform.browserLabel}
                  </span>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowModal(false)}
                aria-label="Fechar"
                style={{
                  border: 'none',
                  background: 'transparent',
                  padding: '4px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <X size={20} color="#6B5855" />
              </button>
            </div>

            <div
              style={{
                height: '1px',
                backgroundColor: 'var(--pao-border-light)',
                margin: '14px 0',
              }}
            />

            {platform.os === 'IOS' && platform.isIosNonSafari && (
              <div
                style={{
                  backgroundColor: 'rgba(217, 119, 6, 0.12)',
                  border: '1px solid rgba(217, 119, 6, 0.38)',
                  borderRadius: '10px',
                  padding: '10px 12px',
                  marginBottom: '12px',
                  fontSize: '12px',
                  color: 'var(--pao-anthracite)',
                  lineHeight: 1.45,
                }}
              >
                <strong>Atenção no iOS:</strong> No iPhone/iPad, a instalação na Tela de Início
                precisa ser feita pelo navegador <strong>Safari</strong>. Copie o link abaixo e
                abra no Safari.
              </div>
            )}

            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {platform.os === 'IOS' ? (
                <>
                  <StepItem
                    num={1}
                    title={
                      platform.isIosNonSafari
                        ? 'Abra este link no Safari'
                        : 'Toque no botão Compartilhar'
                    }
                    detail={
                      platform.isIosNonSafari ? (
                        'Use o botão "Copiar Link" abaixo e cole no Safari do iPhone.'
                      ) : (
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', flexWrap: 'wrap' }}>
                          Toque no ícone <Share size={14} color="#9C1819" /> na barra inferior/superior do Safari.
                        </span>
                      )
                    }
                  />
                  <StepItem
                    num={2}
                    title="Adicionar à Tela de Início"
                    detail={
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', flexWrap: 'wrap' }}>
                        Role a lista e escolha <PlusSquare size={14} color="#9C1819" />{' '}
                        <strong>Adicionar à Tela de Início</strong>.
                      </span>
                    }
                  />
                  <StepItem
                    num={3}
                    title="Confirme em Adicionar"
                    detail="O ícone do Pão com Carne aparecerá no seu iPhone e abrirá em tela cheia como app nativo."
                  />
                </>
              ) : platform.os === 'ANDROID' ? (
                <>
                  <StepItem
                    num={1}
                    title="Abra o menu do navegador"
                    detail={
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', flexWrap: 'wrap' }}>
                        Toque nos 3 pontinhos <MoreVertical size={14} color="#9C1819" /> no canto superior direito do Chrome.
                      </span>
                    }
                  />
                  <StepItem
                    num={2}
                    title="Instalar aplicativo"
                    detail={
                      <span>
                        Toque em <strong>Instalar aplicativo</strong> ou{' '}
                        <strong>Adicionar à tela inicial</strong>.
                      </span>
                    }
                  />
                  <StepItem
                    num={3}
                    title="Confirme a instalação"
                    detail="O app será instalado no celular funcionando em tela cheia e com cache offline."
                  />
                </>
              ) : platform.os === 'MACOS' && platform.browser === 'SAFARI' ? (
                <>
                  <StepItem
                    num={1}
                    title="Menu Arquivo do Safari"
                    detail="Na barra superior do macOS, clique em Arquivo → Adicionar ao Dock."
                  />
                  <StepItem
                    num={2}
                    title="Confirmar"
                    detail="O sistema criará um app dedicado na sua Dock e pasta Aplicativos."
                  />
                </>
              ) : (
                <>
                  <StepItem
                    num={1}
                    title="Localize o botão de instalação"
                    detail="No Chrome ou Edge, clique no ícone de computador com seta na ponta direita da barra de endereços (ou no menu ⋮ → Salvar e compartilhar → Instalar app)."
                  />
                  <StepItem
                    num={2}
                    title="Confirmar instalação"
                    detail="O sistema abrirá o Pão com Carne em janela própria e criará um atalho na área de trabalho."
                  />
                </>
              )}
            </div>

            <div
              style={{
                marginTop: '16px',
                display: 'flex',
                flexDirection: 'column',
                gap: '8px',
              }}
            >
              {deferredPrompt && (
                <PaoPrimaryButton
                  text={`Instalar Agora (${platform.osLabel})`}
                  onClick={async () => {
                    await deferredPrompt.prompt();
                  }}
                  testId="modal_native_install_button"
                />
              )}

              {platform.isIosNonSafari && (
                <button
                  type="button"
                  onClick={() => void handleCopyUrl()}
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
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '6px',
                  }}
                >
                  {copiedLink ? <Check size={16} /> : <Copy size={16} />}
                  <span>{copiedLink ? 'Link Copiado!' : 'Copiar Link para abrir no Safari'}</span>
                </button>
              )}

              <button
                type="button"
                onClick={() => setShowModal(false)}
                className="tap-feedback"
                style={{
                  width: '100%',
                  height: '44px',
                  borderRadius: '12px',
                  border: 'none',
                  backgroundColor: 'var(--pao-cream-light)',
                  color: 'var(--pao-anthracite)',
                  fontSize: '13px',
                  fontWeight: 700,
                }}
              >
                Entendido
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

function StepItem({
  num,
  title,
  detail,
}: {
  num: number;
  title: string;
  detail: React.ReactNode;
}) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: '10px',
        backgroundColor: 'var(--pao-background)',
        border: '1px solid var(--pao-border-light)',
        borderRadius: '12px',
        padding: '10px 12px',
      }}
    >
      <div
        style={{
          width: '24px',
          height: '24px',
          borderRadius: '50%',
          backgroundColor: 'var(--pao-red)',
          color: '#FFFFFF',
          fontSize: '12px',
          fontWeight: 800,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        {num}
      </div>
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
          {title}
        </div>
        <div
          style={{
            marginTop: '2px',
            fontSize: '12px',
            color: 'var(--pao-anthracite-muted)',
            lineHeight: 1.4,
          }}
        >
          {detail}
        </div>
      </div>
    </div>
  );
}
