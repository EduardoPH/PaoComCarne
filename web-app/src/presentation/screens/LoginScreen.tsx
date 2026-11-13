import { useState } from 'react';
import {
  AlertCircle,
  ArrowLeftRight,
  CheckCircle2,
  Eye,
  EyeOff,
  Info,
  Lock,
  User,
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { APP_VERSION_NAME } from '../../domain/appVersion';
import { authRepository } from '../../data/repositories/authRepository';
import { syncManager } from '../../data/sync/syncManager';
import type { Hoster } from '../../domain/models';
import {
  BrandLogoBox,
  PaoPrimaryButton,
  PaoSecondaryButton,
  SyncStatusPill,
} from '../components/CommonComponents';
import { IosInstallBanner } from '../components/IosInstallBanner';

export function LoginScreen() {
  const { hosters, sync } = useApp();
  const [selectedHoster, setSelectedHoster] = useState<Hoster | null>(null);
  const [isSelectingManually, setIsSelectingManually] = useState(false);
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const isFirstAccess = selectedHoster
    ? selectedHoster.id === 'admin' || selectedHoster.isAdmin
      ? false
      : !selectedHoster.isActivated
    : false;

  const handleSelectHoster = (h: Hoster | null) => {
    setSelectedHoster(h);
    setPassword('');
    setConfirmPassword('');
    setErrorMessage(null);
    setIsSelectingManually(false);
  };

  const handleSubmit = async () => {
    if (!selectedHoster) {
      setErrorMessage('Selecione o seu nome na lista.');
      return;
    }
    if (!password.trim()) {
      setErrorMessage('Informe a sua senha.');
      return;
    }

    if (isFirstAccess) {
      if (password.length < 4) {
        setErrorMessage('A senha deve ter no mínimo 4 caracteres.');
        return;
      }
      if (password !== confirmPassword) {
        setErrorMessage('As senhas não coincidem.');
        return;
      }

      setIsLoading(true);
      setErrorMessage(null);
      const res = await authRepository.activateHoster(selectedHoster.id, password);
      setIsLoading(false);
      if (!res.ok) {
        setErrorMessage(res.error);
      }
    } else {
      setIsLoading(true);
      setErrorMessage(null);
      const res = await authRepository.authenticate(selectedHoster.id, password);
      setIsLoading(false);
      if (!res.ok) {
        setErrorMessage(res.error);
      }
    }
  };

  return (
    <div
      className="scroll-area"
      style={{
        flex: 1,
        padding: '0 24px 32px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
      }}
    >
      {/* Header with Sync Pill */}
      <div
        style={{
          width: '100%',
          paddingTop: '16px',
          display: 'flex',
          justifyContent: 'flex-end',
        }}
      >
        <SyncStatusPill
          syncState={sync.syncState}
          pendingCount={sync.pendingCount}
          onClick={() => void syncManager.triggerSync()}
        />
      </div>

      <div style={{ height: '12px' }} />

      {/* iOS Safari Add to Home Screen helper when opened in mobile browser */}
      <div style={{ width: '100%' }}>
        <IosInstallBanner />
      </div>

      {/* Brand Identity */}
      <BrandLogoBox size={80} radius={20} padding={6} />

      <div
        style={{
          marginTop: '10px',
          fontSize: '22px',
          fontWeight: 800,
          color: 'var(--pao-anthracite)',
        }}
      >
        Pão com Carne
      </div>
      <div
        style={{
          fontSize: '13px',
          fontWeight: 600,
          color: 'var(--pao-anthracite-muted)',
          letterSpacing: '0.5px',
        }}
      >
        Sistema de Recepção
      </div>

      <div style={{ height: '18px' }} />

      {/* Main Login Card */}
      <div
        style={{
          width: '100%',
          backgroundColor: '#FFFFFF',
          border: '1px solid var(--pao-border)',
          borderRadius: '16px',
          padding: '18px',
        }}
      >
        {selectedHoster && !isSelectingManually ? (
          <>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: '6px',
              }}
            >
              <span
                style={{
                  fontSize: '13px',
                  fontWeight: 600,
                  color: 'var(--pao-anthracite-muted)',
                }}
              >
                Hoster Selecionado
              </span>

              <button
                type="button"
                onClick={() => setIsSelectingManually(true)}
                data-testid="btn_trocar_hoster"
                style={{
                  border: 'none',
                  background: 'transparent',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '4px',
                  padding: '4px 6px',
                  fontSize: '13px',
                  fontWeight: 700,
                  color: 'var(--pao-red)',
                }}
              >
                <ArrowLeftRight size={15} color="#9C1819" />
                <span>Trocar</span>
              </button>
            </div>

            <button
              type="button"
              onClick={() => setIsSelectingManually(true)}
              data-testid="selected_hoster_pill"
              className="tap-feedback"
              style={{
                width: '100%',
                borderRadius: '10px',
                backgroundColor: 'var(--pao-cream)',
                border: '1.5px solid var(--pao-red)',
                padding: '10px 14px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                textAlign: 'left',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <User size={20} color="#9C1819" />
                <div>
                  <div
                    style={{
                      fontSize: '15px',
                      fontWeight: 700,
                      color: 'var(--pao-anthracite)',
                    }}
                  >
                    {selectedHoster.name}
                  </div>
                  {!selectedHoster.isActivated && !selectedHoster.isAdmin && (
                    <div style={{ fontSize: '11px', color: '#D97706', fontWeight: 600 }}>
                      Primeiro acesso
                    </div>
                  )}
                </div>
              </div>
              <CheckCircle2 size={20} color="#9C1819" />
            </button>
          </>
        ) : (
          <>
            <div
              style={{
                fontSize: '15px',
                fontWeight: 700,
                color: 'var(--pao-anthracite)',
                marginBottom: '10px',
              }}
            >
              Selecione o seu nome
            </div>

            {hosters.length === 0 ? (
              <div style={{ fontSize: '13px', color: 'var(--pao-anthracite-muted)' }}>
                Carregando equipe de recepção...
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {hosters.map((hoster) => {
                  const isSelected = selectedHoster?.id === hoster.id;
                  return (
                    <button
                      key={hoster.id}
                      type="button"
                      onClick={() => handleSelectHoster(hoster)}
                      data-testid={`hoster_item_${hoster.id}`}
                      className="tap-feedback"
                      style={{
                        width: '100%',
                        borderRadius: '10px',
                        backgroundColor: isSelected ? 'var(--pao-cream)' : 'var(--pao-cream-light)',
                        border: `${isSelected ? '1.5px' : '1px'} solid ${
                          isSelected ? 'var(--pao-red)' : 'var(--pao-border-light)'
                        }`,
                        padding: '10px 14px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        textAlign: 'left',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <User size={20} color={isSelected ? '#9C1819' : '#6B5855'} />
                        <div>
                          <div
                            style={{
                              fontSize: '14px',
                              fontWeight: isSelected ? 700 : 500,
                              color: 'var(--pao-anthracite)',
                            }}
                          >
                            {hoster.name}
                          </div>
                          {!hoster.isActivated && !hoster.isAdmin && (
                            <div style={{ fontSize: '11px', color: '#D97706', fontWeight: 600 }}>
                              Primeiro acesso
                            </div>
                          )}
                        </div>
                      </div>
                      {isSelected && <CheckCircle2 size={20} color="#9C1819" />}
                    </button>
                  );
                })}
              </div>
            )}
          </>
        )}

        {/* Password & Login Form */}
        {selectedHoster && !isSelectingManually && (
          <div style={{ marginTop: '16px' }}>
            {isFirstAccess && (
              <div
                style={{
                  backgroundColor: 'rgba(217, 119, 6, 0.12)',
                  border: '1px solid rgba(217, 119, 6, 0.3)',
                  borderRadius: '8px',
                  padding: '10px',
                  marginBottom: '12px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                }}
              >
                <Info size={18} color="#D97706" style={{ flexShrink: 0 }} />
                <span style={{ fontSize: '12px', color: 'var(--pao-anthracite)' }}>
                  Primeiro acesso de {selectedHoster.name}. Crie uma senha individual.
                </span>
              </div>
            )}

            <div className="outlined-field-wrap">
              <span className="outlined-field-label">
                {isFirstAccess ? 'Criar nova senha' : 'Senha de acesso'}
              </span>
              <Lock
                size={18}
                color="#6B5855"
                style={{
                  position: 'absolute',
                  left: '14px',
                  top: '50%',
                  transform: 'translateY(-50%)',
                }}
              />
              <input
                type={passwordVisible ? 'text' : 'password'}
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value);
                  setErrorMessage(null);
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !isFirstAccess) {
                    void handleSubmit();
                  }
                }}
                data-testid="password_input"
                autoComplete="current-password"
                className="outlined-field-input with-left-icon with-right-icon"
              />
              <button
                type="button"
                onClick={() => setPasswordVisible((v) => !v)}
                aria-label="Alternar visibilidade"
                style={{
                  position: 'absolute',
                  right: '6px',
                  top: '50%',
                  transform: 'translateY(-50%)',
                  width: '36px',
                  height: '36px',
                  border: 'none',
                  background: 'transparent',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                {passwordVisible ? (
                  <Eye size={18} color="#6B5855" />
                ) : (
                  <EyeOff size={18} color="#6B5855" />
                )}
              </button>
            </div>

            {isFirstAccess && (
              <div className="outlined-field-wrap" style={{ marginTop: '14px' }}>
                <span className="outlined-field-label">Confirmar nova senha</span>
                <Lock
                  size={18}
                  color="#6B5855"
                  style={{
                    position: 'absolute',
                    left: '14px',
                    top: '50%',
                    transform: 'translateY(-50%)',
                  }}
                />
                <input
                  type={passwordVisible ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={(e) => {
                    setConfirmPassword(e.target.value);
                    setErrorMessage(null);
                  }}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') void handleSubmit();
                  }}
                  data-testid="confirm_password_input"
                  className="outlined-field-input with-left-icon"
                />
              </div>
            )}

            {errorMessage && (
              <div
                style={{
                  marginTop: '12px',
                  backgroundColor: 'rgba(156, 24, 25, 0.1)',
                  border: '1px solid rgba(156, 24, 25, 0.3)',
                  borderRadius: '8px',
                  padding: '10px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                }}
              >
                <AlertCircle size={18} color="#9C1819" style={{ flexShrink: 0 }} />
                <span style={{ fontSize: '12px', fontWeight: 500, color: '#9C1819' }}>
                  {errorMessage}
                </span>
              </div>
            )}

            <div style={{ marginTop: '16px', display: 'flex', gap: '10px' }}>
              <PaoSecondaryButton
                text="Voltar"
                onClick={() => {
                  setSelectedHoster(null);
                  setIsSelectingManually(true);
                  setErrorMessage(null);
                }}
                style={{ flex: 1, height: '54px' }}
                testId="login_back_button"
              />
              <PaoPrimaryButton
                text={isFirstAccess ? 'Cadastrar e Entrar' : 'Entrar'}
                onClick={() => void handleSubmit()}
                isLoading={isLoading}
                style={{ flex: 1.4 }}
                testId="login_button"
              />
            </div>
          </div>
        )}
      </div>

      <div
        style={{
          marginTop: '24px',
          fontSize: '12px',
          color: 'var(--pao-anthracite-muted)',
        }}
      >
        Operação Interna • Versão {APP_VERSION_NAME}
      </div>
    </div>
  );
}
