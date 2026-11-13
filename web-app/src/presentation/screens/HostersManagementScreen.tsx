import { useMemo, useState } from 'react';
import {
  History,
  Key,
  LogOut,
  Pencil,
  Plus,
  RefreshCw,
  Shield,
  Trash2,
  User,
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { authRepository } from '../../data/repositories/authRepository';
import { syncManager } from '../../data/sync/syncManager';
import type { Hoster, LogResult } from '../../domain/models';
import { filterSystemLogs, type SystemLogsFilterState } from '../../domain/utils/adminFilters';
import { BrandLogoBox, SyncStatusPill } from '../components/CommonComponents';
import { SystemLogItemCard, SystemLogsFilterCard } from '../components/SystemLogsSection';

export function HostersManagementScreen({ onLogout }: { onLogout: () => void }) {
  const { hosters, systemLogs, sync } = useApp();

  const [selectedTab, setSelectedTab] = useState<'HOSTERS' | 'LOGS'>('HOSTERS');
  const [logsFilter, setLogsFilter] = useState<SystemLogsFilterState>({
    startMillis: null,
    endMillis: null,
    selectedUser: null,
    selectedAction: null,
    selectedDevice: null,
    selectedResult: null,
  });

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

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [hosterToEdit, setHosterToEdit] = useState<Hoster | null>(null);
  const [hosterToResetPin, setHosterToResetPin] = useState<Hoster | null>(null);
  const [hosterToDelete, setHosterToDelete] = useState<Hoster | null>(null);
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);

  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  return (
    <div
      style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        position: 'relative',
        padding: '0 20px',
        height: '100%',
        overflow: 'hidden',
      }}
    >
      {/* Header Bar */}
      <div
        style={{
          paddingTop: '16px',
          paddingBottom: '12px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '8px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1, minWidth: 0 }}>
          <BrandLogoBox size={44} radius={12} padding={4} />
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: '18px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
              Administrador
            </div>
            <div style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
              Gestão de Hosters & Auditoria
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexShrink: 0 }}>
          <SyncStatusPill
            syncState={sync.syncState}
            pendingCount={sync.pendingCount}
            onClick={() => void syncManager.triggerSync()}
          />

          <button
            type="button"
            onClick={() => setShowLogoutConfirm(true)}
            data-testid="admin_logout_button"
            className="tap-feedback"
            style={{
              backgroundColor: 'rgba(156, 24, 25, 0.12)',
              border: '1px solid rgba(156, 24, 25, 0.35)',
              borderRadius: '8px',
              padding: '6px 10px',
              display: 'flex',
              alignItems: 'center',
              gap: '4px',
              color: 'var(--pao-red)',
              fontSize: '12px',
              fontWeight: 700,
            }}
          >
            <LogOut size={15} color="#9C1819" />
            <span>Sair</span>
          </button>
        </div>
      </div>

      {/* Sub-tabs */}
      <div
        style={{
          backgroundColor: '#FFFFFF',
          border: '1px solid var(--pao-border)',
          borderRadius: '12px',
          padding: '4px',
          display: 'flex',
          gap: '6px',
          marginBottom: '12px',
          flexShrink: 0,
        }}
      >
        <button
          type="button"
          onClick={() => setSelectedTab('HOSTERS')}
          className="tap-feedback"
          style={{
            flex: 1,
            height: '36px',
            borderRadius: '9px',
            border: 'none',
            backgroundColor: selectedTab === 'HOSTERS' ? 'var(--pao-red)' : 'transparent',
            color: selectedTab === 'HOSTERS' ? '#FFFFFF' : 'var(--pao-anthracite-muted)',
            fontSize: '12px',
            fontWeight: 700,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '6px',
          }}
        >
          <User size={14} />
          <span>Hosters ({hosters.length})</span>
        </button>

        <button
          type="button"
          onClick={() => setSelectedTab('LOGS')}
          className="tap-feedback"
          style={{
            flex: 1,
            height: '36px',
            borderRadius: '9px',
            border: 'none',
            backgroundColor: selectedTab === 'LOGS' ? 'var(--pao-red)' : 'transparent',
            color: selectedTab === 'LOGS' ? '#FFFFFF' : 'var(--pao-anthracite-muted)',
            fontSize: '12px',
            fontWeight: 700,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '6px',
          }}
        >
          <History size={14} />
          <span>Logs ({systemLogs.length})</span>
        </button>
      </div>

      {actionMessage && (
        <div
          style={{
            backgroundColor: 'rgba(30, 130, 76, 0.12)',
            border: '1px solid rgba(30, 130, 76, 0.4)',
            borderRadius: '8px',
            padding: '10px',
            fontSize: '12px',
            fontWeight: 500,
            color: 'var(--pao-anthracite)',
            marginBottom: '10px',
          }}
        >
          {actionMessage}
        </div>
      )}

      {errorMessage && (
        <div
          style={{
            backgroundColor: 'rgba(156, 24, 25, 0.12)',
            border: '1px solid rgba(156, 24, 25, 0.4)',
            borderRadius: '8px',
            padding: '10px',
            fontSize: '12px',
            fontWeight: 500,
            color: 'var(--status-red)',
            marginBottom: '10px',
          }}
        >
          {errorMessage}
        </div>
      )}

      {selectedTab === 'HOSTERS' ? (
        <>
          {/* Info Card */}
          <div
            style={{
              backgroundColor: '#FFFFFF',
              border: '1px solid var(--pao-border-light)',
              borderRadius: '12px',
              padding: '12px',
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
              flexShrink: 0,
            }}
          >
            <RefreshCw size={22} color="#9C1819" style={{ flexShrink: 0 }} />
            <span style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
              Todas as inclusões, edições e exclusões são salvas localmente e sincronizadas no
              Supabase em tempo real.
            </span>
          </div>

          <div
            style={{
              marginTop: '14px',
              marginBottom: '8px',
              fontSize: '14px',
              fontWeight: 700,
              color: 'var(--pao-anthracite)',
              flexShrink: 0,
            }}
          >
            Hosters Cadastrados ({hosters.length})
          </div>

          {/* Hosters List */}
          <div
            className="scroll-area"
            style={{
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              gap: '8px',
              paddingBottom: '76px',
            }}
          >
            {hosters.map((hoster) => (
              <HosterItemCard
                key={hoster.id}
                hoster={hoster}
                onEdit={() => setHosterToEdit(hoster)}
                onResetPin={() => setHosterToResetPin(hoster)}
                onDelete={() => setHosterToDelete(hoster)}
              />
            ))}
          </div>
        </>
      ) : (
        <div
          className="scroll-area"
          style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            gap: '10px',
            paddingBottom: '20px',
          }}
        >
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
            filteredLogs.slice(0, 150).map((log) => (
              <SystemLogItemCard key={log.id} log={log} />
            ))
          )}
        </div>
      )}

      {/* Bottom logout button */}
      <div
        style={{
          paddingTop: '10px',
          paddingBottom: 'calc(14px + var(--safe-bottom))',
          backgroundColor: 'var(--pao-background)',
        }}
      >
        <button
          type="button"
          onClick={() => setShowLogoutConfirm(true)}
          data-testid="admin_bottom_logout_button"
          className="tap-feedback"
          style={{
            width: '100%',
            height: '48px',
            borderRadius: '12px',
            border: '1px solid rgba(156, 24, 25, 0.4)',
            backgroundColor: 'transparent',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '6px',
            fontSize: '13px',
            fontWeight: 700,
            color: 'var(--pao-red)',
          }}
        >
          <LogOut size={18} color="#9C1819" />
          <span>Sair do Login / Voltar ao Início</span>
        </button>
      </div>

      {/* FAB Add Hoster */}
      {selectedTab === 'HOSTERS' && (
        <button
          type="button"
          onClick={() => setShowCreateModal(true)}
          data-testid="fab_add_hoster"
          aria-label="Adicionar Hoster"
          className="tap-feedback"
          style={{
            position: 'absolute',
            right: '20px',
            bottom: 'calc(76px + var(--safe-bottom))',
            width: '56px',
            height: '56px',
            borderRadius: '50%',
            backgroundColor: 'var(--pao-red)',
            color: '#FFFFFF',
            border: 'none',
            boxShadow: '0 6px 16px rgba(156, 24, 25, 0.35)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Plus size={26} color="#FFFFFF" />
        </button>
      )}

      {/* Modals */}
      {showCreateModal && (
        <CreateHosterModal
          onDismiss={() => setShowCreateModal(false)}
          onConfirm={async (name, pin) => {
            setShowCreateModal(false);
            const res = await authRepository.createHoster(name, pin);
            if (res.ok) {
              setActionMessage(`Hoster "${res.value.name}" cadastrado com sucesso!`);
              setErrorMessage(null);
            } else {
              setErrorMessage(res.error);
            }
          }}
        />
      )}

      {hosterToEdit && (
        <EditHosterModal
          hoster={hosterToEdit}
          onDismiss={() => setHosterToEdit(null)}
          onConfirm={async (newName) => {
            const target = hosterToEdit;
            setHosterToEdit(null);
            const res = await authRepository.updateHosterName(target.id, newName);
            if (res.ok) {
              setActionMessage(`Nome atualizado para "${newName}"!`);
              setErrorMessage(null);
            } else {
              setErrorMessage(res.error);
            }
          }}
        />
      )}

      {hosterToResetPin && (
        <ResetPinModal
          hoster={hosterToResetPin}
          onDismiss={() => setHosterToResetPin(null)}
          onConfirm={async (newPin) => {
            const target = hosterToResetPin;
            setHosterToResetPin(null);
            const res = await authRepository.updateHosterPin(target.id, newPin);
            if (res.ok) {
              setActionMessage(`Senha de "${target.name}" alterada com sucesso!`);
              setErrorMessage(null);
            } else {
              setErrorMessage(res.error);
            }
          }}
        />
      )}

      {hosterToDelete && (
        <div className="dialog-modal-backdrop" onClick={() => setHosterToDelete(null)}>
          <div className="dialog-modal-card" onClick={(e) => e.stopPropagation()}>
            <div style={{ fontSize: '18px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
              Excluir Hoster
            </div>
            <div
              style={{
                marginTop: '10px',
                fontSize: '13px',
                color: 'var(--pao-anthracite-muted)',
              }}
            >
              Tem certeza que deseja remover {hosterToDelete.name}? Esta operação sincronizará a
              remoção no Supabase.
            </div>
            <div
              style={{
                marginTop: '18px',
                display: 'flex',
                justifyContent: 'flex-end',
                gap: '12px',
              }}
            >
              <button
                type="button"
                onClick={() => setHosterToDelete(null)}
                style={{
                  border: 'none',
                  background: 'transparent',
                  fontSize: '14px',
                  fontWeight: 600,
                  color: 'var(--pao-anthracite-muted)',
                  padding: '8px 12px',
                }}
              >
                Cancelar
              </button>
              <button
                type="button"
                onClick={async () => {
                  const target = hosterToDelete;
                  setHosterToDelete(null);
                  const res = await authRepository.deleteHoster(target.id);
                  if (res.ok) {
                    setActionMessage('Hoster removido com sucesso.');
                    setErrorMessage(null);
                  } else {
                    setErrorMessage(res.error);
                  }
                }}
                style={{
                  border: 'none',
                  background: 'transparent',
                  fontSize: '14px',
                  fontWeight: 700,
                  color: 'var(--status-red)',
                  padding: '8px 12px',
                }}
              >
                Excluir
              </button>
            </div>
          </div>
        </div>
      )}

      {showLogoutConfirm && (
        <div className="dialog-modal-backdrop" onClick={() => setShowLogoutConfirm(false)}>
          <div className="dialog-modal-card" onClick={(e) => e.stopPropagation()}>
            <div style={{ fontSize: '18px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
              Sair do Painel Admin
            </div>
            <div
              style={{
                marginTop: '10px',
                fontSize: '13px',
                color: 'var(--pao-anthracite-muted)',
              }}
            >
              Deseja realmente sair da sessão do administrador e voltar à tela inicial de login?
            </div>
            <div
              style={{
                marginTop: '18px',
                display: 'flex',
                justifyContent: 'flex-end',
                gap: '12px',
              }}
            >
              <button
                type="button"
                onClick={() => setShowLogoutConfirm(false)}
                style={{
                  border: 'none',
                  background: 'transparent',
                  fontSize: '14px',
                  fontWeight: 600,
                  color: 'var(--pao-anthracite-muted)',
                  padding: '8px 12px',
                }}
              >
                Cancelar
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowLogoutConfirm(false);
                  onLogout();
                }}
                style={{
                  border: 'none',
                  background: 'transparent',
                  fontSize: '14px',
                  fontWeight: 700,
                  color: 'var(--status-red)',
                  padding: '8px 12px',
                }}
              >
                Sair
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function HosterItemCard({
  hoster,
  onEdit,
  onResetPin,
  onDelete,
}: {
  hoster: Hoster;
  onEdit: () => void;
  onResetPin: () => void;
  onDelete: () => void;
}) {
  return (
    <div
      style={{
        backgroundColor: '#FFFFFF',
        border: '1px solid var(--pao-border-light)',
        borderRadius: '12px',
        padding: '10px 14px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '8px',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1, minWidth: 0 }}>
        <div
          style={{
            width: '36px',
            height: '36px',
            borderRadius: '50%',
            backgroundColor: hoster.isAdmin ? 'rgba(156, 24, 25, 0.15)' : 'var(--pao-cream)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          {hoster.isAdmin ? (
            <Shield size={18} color="#9C1819" />
          ) : (
            <User size={18} color="#9C1819" />
          )}
        </div>
        <div style={{ minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span
              style={{
                fontSize: '14px',
                fontWeight: 700,
                color: 'var(--pao-anthracite)',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {hoster.name}
            </span>
            {hoster.isAdmin && (
              <span
                style={{
                  backgroundColor: 'var(--pao-red)',
                  color: '#FFFFFF',
                  fontSize: '9px',
                  fontWeight: 700,
                  padding: '2px 5px',
                  borderRadius: '4px',
                }}
              >
                ADMIN
              </span>
            )}
          </div>
          <div
            style={{
              fontSize: '11px',
              color: hoster.isActivated ? '#1E824C' : '#D97706',
            }}
          >
            {hoster.isActivated ? 'Ativo • Senha cadastrada' : 'Pendente primeiro acesso'}
          </div>
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', flexShrink: 0 }}>
        <button
          type="button"
          onClick={onResetPin}
          aria-label="Alterar senha"
          style={{
            width: '40px',
            height: '40px',
            border: 'none',
            background: 'transparent',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Key size={18} color="#6B5855" />
        </button>
        <button
          type="button"
          onClick={onEdit}
          aria-label="Editar nome"
          style={{
            width: '40px',
            height: '40px',
            border: 'none',
            background: 'transparent',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Pencil size={18} color="#6B5855" />
        </button>
        {!hoster.isAdmin && (
          <button
            type="button"
            onClick={onDelete}
            aria-label="Excluir"
            style={{
              width: '40px',
              height: '40px',
              border: 'none',
              background: 'transparent',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Trash2 size={18} color="#9C1819" />
          </button>
        )}
      </div>
    </div>
  );
}

function CreateHosterModal({
  onDismiss,
  onConfirm,
}: {
  onDismiss: () => void;
  onConfirm: (name: string, pin: string | null) => Promise<void>;
}) {
  const [name, setName] = useState('');
  const [pin, setPin] = useState('');
  const [error, setError] = useState<string | null>(null);

  return (
    <div className="dialog-modal-backdrop" onClick={onDismiss}>
      <div className="dialog-modal-card" onClick={(e) => e.stopPropagation()}>
        <div style={{ fontSize: '18px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
          Novo Hoster
        </div>
        <div
          style={{
            marginTop: '6px',
            fontSize: '12px',
            color: 'var(--pao-anthracite-muted)',
          }}
        >
          Adicione um membro para a equipe de recepção:
        </div>

        <div className="outlined-field-wrap" style={{ marginTop: '14px' }}>
          <span className="outlined-field-label">Nome completo</span>
          <input
            type="text"
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              setError(null);
            }}
            className="outlined-field-input"
          />
        </div>

        <div className="outlined-field-wrap" style={{ marginTop: '14px' }}>
          <span className="outlined-field-label">Senha inicial (opcional)</span>
          <input
            type="text"
            value={pin}
            onChange={(e) => setPin(e.target.value)}
            className="outlined-field-input"
          />
        </div>

        {error && (
          <div style={{ marginTop: '8px', fontSize: '11px', color: 'var(--status-red)' }}>
            {error}
          </div>
        )}

        <div
          style={{
            marginTop: '18px',
            display: 'flex',
            justifyContent: 'flex-end',
            gap: '12px',
          }}
        >
          <button
            type="button"
            onClick={onDismiss}
            style={{
              border: 'none',
              background: 'transparent',
              fontSize: '14px',
              color: 'var(--pao-anthracite-muted)',
              padding: '8px 12px',
            }}
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => {
              if (!name.trim()) {
                setError('O nome é obrigatório.');
              } else {
                void onConfirm(name, pin.trim() ? pin.trim() : null);
              }
            }}
            style={{
              border: 'none',
              background: 'transparent',
              fontSize: '14px',
              fontWeight: 700,
              color: 'var(--pao-red)',
              padding: '8px 12px',
            }}
          >
            Cadastrar
          </button>
        </div>
      </div>
    </div>
  );
}

function EditHosterModal({
  hoster,
  onDismiss,
  onConfirm,
}: {
  hoster: Hoster;
  onDismiss: () => void;
  onConfirm: (newName: string) => Promise<void>;
}) {
  const [name, setName] = useState(hoster.name);
  const [error, setError] = useState<string | null>(null);

  return (
    <div className="dialog-modal-backdrop" onClick={onDismiss}>
      <div className="dialog-modal-card" onClick={(e) => e.stopPropagation()}>
        <div style={{ fontSize: '18px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
          Editar Nome do Hoster
        </div>

        <div className="outlined-field-wrap" style={{ marginTop: '16px' }}>
          <span className="outlined-field-label">Nome completo</span>
          <input
            type="text"
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              setError(null);
            }}
            className="outlined-field-input"
          />
        </div>

        {error && (
          <div style={{ marginTop: '8px', fontSize: '11px', color: 'var(--status-red)' }}>
            {error}
          </div>
        )}

        <div
          style={{
            marginTop: '18px',
            display: 'flex',
            justifyContent: 'flex-end',
            gap: '12px',
          }}
        >
          <button
            type="button"
            onClick={onDismiss}
            style={{
              border: 'none',
              background: 'transparent',
              fontSize: '14px',
              color: 'var(--pao-anthracite-muted)',
              padding: '8px 12px',
            }}
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => {
              if (!name.trim()) {
                setError('O nome não pode ficar vazio.');
              } else {
                void onConfirm(name.trim());
              }
            }}
            style={{
              border: 'none',
              background: 'transparent',
              fontSize: '14px',
              fontWeight: 700,
              color: 'var(--pao-red)',
              padding: '8px 12px',
            }}
          >
            Salvar
          </button>
        </div>
      </div>
    </div>
  );
}

function ResetPinModal({
  hoster,
  onDismiss,
  onConfirm,
}: {
  hoster: Hoster;
  onDismiss: () => void;
  onConfirm: (newPin: string) => Promise<void>;
}) {
  const [pin, setPin] = useState('');
  const [error, setError] = useState<string | null>(null);

  return (
    <div className="dialog-modal-backdrop" onClick={onDismiss}>
      <div className="dialog-modal-card" onClick={(e) => e.stopPropagation()}>
        <div style={{ fontSize: '18px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
          Definir Senha de {hoster.name}
        </div>

        <div className="outlined-field-wrap" style={{ marginTop: '16px' }}>
          <span className="outlined-field-label">Nova senha (mínimo 4 caracteres)</span>
          <input
            type="text"
            value={pin}
            onChange={(e) => {
              setPin(e.target.value);
              setError(null);
            }}
            className="outlined-field-input"
          />
        </div>

        {error && (
          <div style={{ marginTop: '8px', fontSize: '11px', color: 'var(--status-red)' }}>
            {error}
          </div>
        )}

        <div
          style={{
            marginTop: '18px',
            display: 'flex',
            justifyContent: 'flex-end',
            gap: '12px',
          }}
        >
          <button
            type="button"
            onClick={onDismiss}
            style={{
              border: 'none',
              background: 'transparent',
              fontSize: '14px',
              color: 'var(--pao-anthracite-muted)',
              padding: '8px 12px',
            }}
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => {
              if (pin.length < 4) {
                setError('A senha deve ter pelo menos 4 caracteres.');
              } else {
                void onConfirm(pin);
              }
            }}
            style={{
              border: 'none',
              background: 'transparent',
              fontSize: '14px',
              fontWeight: 700,
              color: 'var(--pao-red)',
              padding: '8px 12px',
            }}
          >
            Salvar Senha
          </button>
        </div>
      </div>
    </div>
  );
}
