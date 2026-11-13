import { useState } from 'react';
import { Info, Lock, LogOut, MonitorSmartphone, Pencil, User } from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { deviceIdentity } from '../../data/local/deviceIdentity';
import { authRepository } from '../../data/repositories/authRepository';
import { receptionRepository } from '../../data/repositories/receptionRepository';
import { ShiftPeriodDisplay } from '../../domain/models';
import { formatDate, formatTime } from '../../domain/utils/dateTime';
import { SyncStatusPill } from '../components/CommonComponents';
import { CloseShiftModal } from '../modals/CloseShiftModal';

export function ShiftScreen({
  onShiftClosed,
}: {
  onShiftClosed?: () => void;
} = {}) {
  const {
    currentHoster,
    currentShift,
    shiftAttendances,
    sync,
    deviceName,
    setDeviceDisplayName,
    showSnackbar,
  } = useApp();
  const [showCloseShift, setShowCloseShift] = useState(false);
  const [showEditDevice, setShowEditDevice] = useState(false);
  const [draftDeviceName, setDraftDeviceName] = useState('');

  const totalAtt = shiftAttendances.length;
  const totalPeople = shiftAttendances.reduce((s, a) => s + a.attendance.totalMembers, 0);
  const newCount = shiftAttendances.reduce((s, a) => s + a.attendance.newMembers, 0);
  const returningCount = shiftAttendances.reduce((s, a) => s + a.attendance.returningMembers, 0);

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <div
        className="scroll-area"
        style={{
          flex: 1,
          padding: '0 20px 24px',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        {/* Header */}
        <div
          style={{
            paddingTop: '16px',
            paddingBottom: '12px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '10px',
          }}
        >
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: '18px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
              Dados do Turno
            </div>
            <div style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
              Informações do hoster e encerramento
            </div>
          </div>

          <SyncStatusPill syncState={sync.syncState} pendingCount={sync.pendingCount} />
        </div>

        <div
          style={{
            height: '1px',
            backgroundColor: 'var(--pao-border-light)',
            marginBottom: '16px',
          }}
        />

        {/* Hoster Info Card */}
        <div
          style={{
            backgroundColor: '#FFFFFF',
            border: '1px solid var(--pao-border)',
            borderRadius: '16px',
            padding: '16px',
            display: 'flex',
            alignItems: 'center',
            gap: '14px',
          }}
        >
          <div
            style={{
              width: '48px',
              height: '48px',
              borderRadius: '50%',
              backgroundColor: 'var(--pao-cream)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            <User size={26} color="#9C1819" />
          </div>
          <div>
            <div style={{ fontSize: '16px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
              {currentHoster?.name || 'Hoster'}
            </div>
            <div style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
              Hoster em atividade neste dispositivo
            </div>
          </div>
        </div>

        <div style={{ height: '10px' }} />

        {/* Device Identity Card */}
        <div
          style={{
            backgroundColor: '#FFFFFF',
            border: '1px solid var(--pao-border-light)',
            borderRadius: '14px',
            padding: '10px 14px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '10px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', minWidth: 0, flex: 1 }}>
            <MonitorSmartphone size={20} color="#9C1819" style={{ flexShrink: 0 }} />
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: '11px', color: 'var(--pao-anthracite-muted)' }}>
                Dispositivo identificado nos logs
              </div>
              <div
                style={{
                  fontSize: '13px',
                  fontWeight: 700,
                  color: 'var(--pao-anthracite)',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {deviceName}
              </div>
            </div>
          </div>
          <button
            type="button"
            onClick={() => {
              setDraftDeviceName(deviceIdentity.getFriendlyName() || '');
              setShowEditDevice(true);
            }}
            aria-label="Editar nome do dispositivo"
            data-testid="btn_edit_device_name"
            className="tap-feedback"
            style={{
              width: '36px',
              height: '36px',
              border: 'none',
              background: 'transparent',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            <Pencil size={17} color="#9C1819" />
          </button>
        </div>

        <div style={{ height: '14px' }} />

        {!currentShift ? (
          <div
            style={{
              backgroundColor: '#FFFFFF',
              border: '1px solid var(--pao-border)',
              borderRadius: '16px',
              padding: '20px',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              textAlign: 'center',
            }}
          >
            <Info size={32} color="#D97706" />
            <div style={{ marginTop: '8px', fontSize: '15px', fontWeight: 700 }}>
              Nenhum Turno Ativo
            </div>
            <div
              style={{
                marginTop: '4px',
                fontSize: '12px',
                color: 'var(--pao-anthracite-muted)',
              }}
            >
              Inicie um turno na tela Recepção para começar os trabalhos.
            </div>
          </div>
        ) : (
          <>
            {/* Shift Details Card */}
            <div
              style={{
                backgroundColor: '#FFFFFF',
                border: '1px solid var(--pao-border)',
                borderRadius: '16px',
                padding: '16px',
              }}
            >
              <div
                style={{
                  fontSize: '14px',
                  fontWeight: 700,
                  color: 'var(--pao-anthracite)',
                  marginBottom: '12px',
                }}
              >
                Situação do Turno Atual
              </div>

              <DetailLine label="Período" value={ShiftPeriodDisplay[currentShift.period]} />
              <DetailLine label="Data de Abertura" value={formatDate(currentShift.openedAt)} />
              <DetailLine label="Horário de Abertura" value={formatTime(currentShift.openedAt)} />
              <DetailLine label="Situação" value="Aberto" valueColor="#1E824C" />

              <div
                style={{
                  height: '1px',
                  backgroundColor: 'var(--pao-border-light)',
                  margin: '12px 0',
                }}
              />

              <div
                style={{
                  fontSize: '13px',
                  fontWeight: 700,
                  color: 'var(--pao-anthracite)',
                  marginBottom: '6px',
                }}
              >
                Balanço do Turno:
              </div>
              <DetailLine label="Atendimentos Registrados" value={String(totalAtt)} />
              <DetailLine label="Pessoas Atendidas" value={String(totalPeople)} />
              <DetailLine label="Novos Clientes" value={String(newCount)} valueColor="#1E824C" />
              <DetailLine label="Clientes Recorrentes" value={String(returningCount)} />
            </div>

            <div style={{ height: '16px' }} />

            {/* Encerrar Turno Button */}
            <button
              type="button"
              onClick={() => setShowCloseShift(true)}
              data-testid="btn_encerrar_turno_action"
              className="tap-feedback"
              style={{
                width: '100%',
                height: '52px',
                borderRadius: '12px',
                border: 'none',
                backgroundColor: 'var(--status-red)',
                color: '#FFFFFF',
                fontSize: '15px',
                fontWeight: 700,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                flexShrink: 0,
              }}
            >
              <Lock size={18} />
              <span>Encerrar Turno</span>
            </button>
          </>
        )}

        <div style={{ height: '20px' }} />

        {/* Logout action */}
        <button
          type="button"
          onClick={() => authRepository.logout()}
          data-testid="btn_logout"
          className="tap-feedback"
          style={{
            width: '100%',
            height: '48px',
            borderRadius: '12px',
            border: '1px solid var(--pao-border)',
            backgroundColor: 'transparent',
            color: 'var(--pao-anthracite-muted)',
            fontSize: '13px',
            fontWeight: 600,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '6px',
            flexShrink: 0,
          }}
        >
          <LogOut size={16} />
          <span>Trocar de Hoster / Sair</span>
        </button>
      </div>

      {showCloseShift && currentShift && (
        <CloseShiftModal
          shift={currentShift}
          totalAttendances={totalAtt}
          totalPeople={totalPeople}
          newClients={newCount}
          returningClients={returningCount}
          onDismiss={() => setShowCloseShift(false)}
          onConfirmClose={async () => {
            setShowCloseShift(false);
            const res = await receptionRepository.closeShift(currentShift.id);
            if (res.ok) {
              showSnackbar(
                'Turno encerrado com sucesso! Confira os resultados na tela de Administração.'
              );
              onShiftClosed?.();
            } else {
              showSnackbar(res.error);
            }
          }}
        />
      )}

      {showEditDevice && (
        <div className="dialog-modal-backdrop" onClick={() => setShowEditDevice(false)}>
          <div className="dialog-modal-card" onClick={(e) => e.stopPropagation()}>
            <div style={{ fontSize: '17px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
              Nome do Dispositivo
            </div>
            <div style={{ marginTop: '6px', fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
              Defina um apelido para identificar este aparelho nos logs do sistema (ex.: Recepção 1,
              iPad Entrada).
            </div>

            <div className="outlined-field-wrap" style={{ marginTop: '14px' }}>
              <span className="outlined-field-label">Apelido do dispositivo</span>
              <input
                type="text"
                value={draftDeviceName}
                onChange={(e) => setDraftDeviceName(e.target.value)}
                placeholder="Ex.: Recepção Balcão"
                className="outlined-field-input"
                style={{ height: '48px', fontSize: '14px' }}
              />
            </div>

            <div
              style={{
                marginTop: '16px',
                display: 'flex',
                justifyContent: 'flex-end',
                gap: '10px',
              }}
            >
              <button
                type="button"
                onClick={() => setShowEditDevice(false)}
                style={{
                  border: 'none',
                  background: 'transparent',
                  fontSize: '13px',
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
                  setDeviceDisplayName(draftDeviceName);
                  setShowEditDevice(false);
                }}
                style={{
                  border: 'none',
                  background: 'transparent',
                  fontSize: '13px',
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
      )}
    </div>
  );
}

function DetailLine({
  label,
  value,
  valueColor = 'var(--pao-anthracite)',
}: {
  label: string;
  value: string;
  valueColor?: string;
}) {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '3px 0',
      }}
    >
      <span style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>{label}</span>
      <span style={{ fontSize: '13px', fontWeight: 700, color: valueColor }}>{value}</span>
    </div>
  );
}
