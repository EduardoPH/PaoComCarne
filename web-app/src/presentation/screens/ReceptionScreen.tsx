import { useState } from 'react';
import {
  AlertTriangle,
  Clock,
  Hourglass,
  Pencil,
  Plus,
  Users,
  UsersRound,
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { receptionRepository } from '../../data/repositories/receptionRepository';
import {
  AttendanceTypeDisplay,
  ShiftPeriodDisplay,
  type AttendanceWithMembers,
} from '../../domain/models';
import { formatTime } from '../../domain/utils/dateTime';
import {
  BrandLogoBox,
  PaoPrimaryButton,
  SyncStatusPill,
} from '../components/CommonComponents';
import { CorrectionModal } from '../modals/CorrectionModal';
import { NewAttendanceModal } from '../modals/NewAttendanceModal';
import { ShiftSelectModal } from '../modals/ShiftSelectModal';
import { UpdateQueueModal } from '../modals/UpdateQueueModal';

export function ReceptionScreen() {
  const {
    currentHoster,
    currentShift,
    latestQueue,
    shiftAttendances,
    sync,
    setShowSyncLogsModal,
    showSnackbar,
  } = useApp();

  const [showPeriodSelect, setShowPeriodSelect] = useState(false);
  const [showNewAttendance, setShowNewAttendance] = useState(false);
  const [showUpdateQueue, setShowUpdateQueue] = useState(false);
  const [showCorrection, setShowCorrection] = useState(false);

  const totalAtt = shiftAttendances.length;
  const totalPeople = shiftAttendances.reduce((s, a) => s + a.attendance.totalMembers, 0);
  const totalNew = shiftAttendances.reduce((s, a) => s + a.attendance.newMembers, 0);
  const totalRet = shiftAttendances.reduce((s, a) => s + a.attendance.returningMembers, 0);
  const lastAttendance = shiftAttendances[shiftAttendances.length - 1] ?? null;

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', padding: '0 20px', overflow: 'hidden' }}>
      {/* Header Bar */}
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
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1, minWidth: 0 }}>
          <BrandLogoBox size={38} radius={10} padding={3} />
          <div style={{ minWidth: 0 }}>
            <div
              style={{
                fontSize: '17px',
                fontWeight: 700,
                color: 'var(--pao-anthracite)',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {currentHoster?.name || 'Recepção'}
            </div>
            {currentShift ? (
              <div style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
                Turno {ShiftPeriodDisplay[currentShift.period]} • Aberto às{' '}
                {formatTime(currentShift.openedAt)}
              </div>
            ) : (
              <div style={{ fontSize: '12px', color: '#D97706', fontWeight: 600 }}>
                Nenhum turno em andamento
              </div>
            )}
          </div>
        </div>

        <SyncStatusPill syncState={sync.syncState} pendingCount={sync.pendingCount} />
      </div>

      <div
        style={{
          height: '1px',
          backgroundColor: 'var(--pao-border-light)',
          marginBottom: '10px',
        }}
      />

      {/* Outdated / Unsynced Banner */}
      {(sync.syncState !== 'SINCRONIZADO' || sync.pendingCount > 0) && (
        <button
          type="button"
          onClick={() => setShowSyncLogsModal(true)}
          className="tap-feedback"
          style={{
            width: '100%',
            backgroundColor: 'rgba(217, 119, 6, 0.12)',
            border: '1px solid rgba(217, 119, 6, 0.35)',
            borderRadius: '10px',
            padding: '8px 12px',
            marginBottom: '12px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            textAlign: 'left',
          }}
        >
          <AlertTriangle size={18} color="#D97706" style={{ flexShrink: 0 }} />
          <span
            style={{
              flex: 1,
              fontSize: '11px',
              fontWeight: 600,
              color: 'var(--pao-anthracite)',
            }}
          >
            As informações podem estar desatualizadas, sincronize-as
          </span>
          <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--pao-red)' }}>Logs</span>
        </button>
      )}

      {/* Body */}
      {!currentShift ? (
        <div
          style={{
            backgroundColor: '#FFFFFF',
            border: '1px solid var(--pao-border)',
            borderRadius: '16px',
            padding: '24px',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            textAlign: 'center',
          }}
        >
          <div
            style={{
              width: '54px',
              height: '54px',
              borderRadius: '50%',
              backgroundColor: 'var(--pao-cream)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Clock size={28} color="#9C1819" />
          </div>
          <div
            style={{
              marginTop: '14px',
              fontSize: '18px',
              fontWeight: 700,
              color: 'var(--pao-anthracite)',
            }}
          >
            Turno Não Iniciado
          </div>
          <div
            style={{
              marginTop: '6px',
              fontSize: '13px',
              color: 'var(--pao-anthracite-muted)',
              lineHeight: 1.45,
            }}
          >
            Olá, {currentHoster?.name}. Para começar a registrar atendimentos e controlar a fila de
            espera, abra o turno atual.
          </div>
          <div style={{ width: '100%', marginTop: '20px' }}>
            <PaoPrimaryButton
              text="Abrir Turno Agora"
              onClick={() => setShowPeriodSelect(true)}
              testId="open_shift_button"
            />
          </div>
        </div>
      ) : (
        <div
          className="scroll-area"
          style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            gap: '16px',
            paddingBottom: '16px',
          }}
        >
          {/* 1. Botão Gigante Novo Atendimento */}
          <button
            type="button"
            onClick={() => setShowNewAttendance(true)}
            data-testid="btn_novo_atendimento"
            className="tap-feedback"
            style={{
              width: '100%',
              height: '76px',
              borderRadius: '18px',
              border: 'none',
              backgroundColor: 'var(--pao-red)',
              color: '#FFFFFF',
              boxShadow: '0 4px 12px rgba(156, 24, 25, 0.28)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '14px',
              flexShrink: 0,
            }}
          >
            <div
              style={{
                width: '42px',
                height: '42px',
                borderRadius: '50%',
                backgroundColor: 'rgba(255, 255, 255, 0.2)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Plus size={26} color="#FFFFFF" strokeWidth={2.5} />
            </div>
            <div style={{ textAlign: 'left' }}>
              <div style={{ fontSize: '20px', fontWeight: 800, letterSpacing: '0.5px' }}>
                Novo Atendimento
              </div>
              <div style={{ fontSize: '12px', fontWeight: 500, color: 'var(--pao-cream)' }}>
                Solo • Dupla • Grupo
              </div>
            </div>
          </button>

          {/* 2. Painel Compacto da Fila de Espera */}
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
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Hourglass size={20} color="#9C1819" />
                <span style={{ fontSize: '15px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
                  Fila de Espera
                </span>
              </div>
              <span style={{ fontSize: '11px', color: 'var(--pao-anthracite-muted)' }}>
                {latestQueue
                  ? `Atualizado às ${formatTime(latestQueue.createdAt)}`
                  : 'Sem atualização'}
              </span>
            </div>

            <div style={{ display: 'flex', gap: '10px', marginTop: '14px' }}>
              <QueueCounterBox
                label="Duplas"
                count={latestQueue?.waitingDuos ?? 0}
                icon={<Users size={20} color={(latestQueue?.waitingDuos ?? 0) > 0 ? '#9C1819' : '#6B5855'} />}
              />
              <QueueCounterBox
                label="Grupos"
                count={latestQueue?.waitingGroups ?? 0}
                icon={
                  <UsersRound
                    size={20}
                    color={(latestQueue?.waitingGroups ?? 0) > 0 ? '#9C1819' : '#6B5855'}
                  />
                }
              />
            </div>

            <button
              type="button"
              onClick={() => setShowUpdateQueue(true)}
              data-testid="btn_atualizar_fila"
              className="tap-feedback"
              style={{
                marginTop: '12px',
                width: '100%',
                height: '44px',
                borderRadius: '10px',
                border: '1px solid var(--pao-anthracite)',
                backgroundColor: 'transparent',
                color: 'var(--pao-anthracite)',
                fontSize: '13px',
                fontWeight: 600,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '6px',
              }}
            >
              <Pencil size={15} color="#261D1C" />
              <span>Atualizar Fila</span>
            </button>
          </div>

          {/* 3. Resumo do Turno Atual */}
          <div
            style={{
              backgroundColor: '#FFFFFF',
              border: '1px solid var(--pao-border-light)',
              borderRadius: '14px',
              padding: '14px',
            }}
          >
            <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
              Resumo do Turno Atual
            </div>
            <div
              style={{
                marginTop: '10px',
                display: 'flex',
                justifyContent: 'space-between',
                textAlign: 'center',
              }}
            >
              <StatChip label="Atendimentos" value={String(totalAtt)} />
              <StatChip label="Pessoas" value={String(totalPeople)} />
              <StatChip label="Novos" value={String(totalNew)} valueColor="#1E824C" />
              <StatChip label="Recorrentes" value={String(totalRet)} />
            </div>
          </div>

          {/* 4. Último Atendimento Registrado */}
          {lastAttendance && (
            <LastAttendanceCard
              item={lastAttendance}
              onCorrect={() => setShowCorrection(true)}
            />
          )}
        </div>
      )}

      {/* Dialogs */}
      {showPeriodSelect && currentHoster && (
        <ShiftSelectModal
          hosterName={currentHoster.name}
          onSelectPeriod={async (period) => {
            setShowPeriodSelect(false);
            const res = await receptionRepository.openShift(
              currentHoster.id,
              currentHoster.name,
              period
            );
            showSnackbar(
              res.ok ? `Turno ${ShiftPeriodDisplay[period]} aberto com sucesso!` : res.error
            );
          }}
          onCancel={() => setShowPeriodSelect(false)}
        />
      )}

      {showNewAttendance && currentShift && currentHoster && (
        <NewAttendanceModal
          shift={currentShift}
          hosterName={currentHoster.name}
          onDismiss={() => setShowNewAttendance(false)}
          onConfirm={async (type, total, newCount, returningCount, isPreferential, members) => {
            setShowNewAttendance(false);
            const res = await receptionRepository.registerAttendance({
              shiftId: currentShift.id,
              hosterId: currentHoster.id,
              hosterName: currentHoster.name,
              type,
              totalMembers: total,
              newMembers: newCount,
              returningMembers: returningCount,
              membersList: members,
              isPreferential,
            });
            const prefLabel = isPreferential ? ' (Preferencial)' : '';
            showSnackbar(
              res.ok
                ? `Atendimento ${AttendanceTypeDisplay[type]}${prefLabel} registrado com sucesso!`
                : res.error
            );
          }}
        />
      )}

      {showUpdateQueue && currentShift && currentHoster && (
        <UpdateQueueModal
          initialDuos={latestQueue?.waitingDuos ?? 0}
          initialGroups={latestQueue?.waitingGroups ?? 0}
          onDismiss={() => setShowUpdateQueue(false)}
          onSave={async (duos, groups) => {
            setShowUpdateQueue(false);
            const res = await receptionRepository.updateQueue(
              currentShift.id,
              currentHoster.id,
              duos,
              groups
            );
            showSnackbar(res.ok ? 'Fila de espera atualizada!' : res.error);
          }}
        />
      )}

      {showCorrection && currentShift && currentHoster && lastAttendance && (
        <CorrectionModal
          attendanceWithMembers={lastAttendance}
          editorHosterName={currentHoster.name}
          onDismiss={() => setShowCorrection(false)}
          onConfirmCorrection={async (
            newType,
            newTotal,
            newNewCount,
            newReturningCount,
            newIsPreferential,
            newMembers
          ) => {
            setShowCorrection(false);
            const res = await receptionRepository.correctLastAttendance({
              shiftId: currentShift.id,
              attendanceId: lastAttendance.attendance.id,
              editorHosterId: currentHoster.id,
              editorHosterName: currentHoster.name,
              newType,
              newTotal,
              newNewMembers: newNewCount,
              newReturningMembers: newReturningCount,
              newMembersList: newMembers,
              newIsPreferential,
            });
            showSnackbar(
              res.ok ? 'Atendimento corrigido com sucesso! Auditoria registrada.' : res.error
            );
          }}
        />
      )}
    </div>
  );
}

function QueueCounterBox({
  label,
  count,
  icon,
}: {
  label: string;
  count: number;
  icon: React.ReactNode;
}) {
  return (
    <div
      style={{
        flex: 1,
        borderRadius: '12px',
        backgroundColor: count > 0 ? 'var(--pao-cream)' : 'var(--pao-cream-light)',
        border: `1px solid ${count > 0 ? 'rgba(156, 24, 25, 0.4)' : 'var(--pao-border-light)'}`,
        padding: '10px 12px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
        {icon}
        <span style={{ fontSize: '13px', fontWeight: 500, color: 'var(--pao-anthracite)' }}>
          {label}
        </span>
      </div>
      <span
        style={{
          fontSize: '18px',
          fontWeight: 700,
          color: count > 0 ? 'var(--pao-red)' : 'var(--pao-anthracite-muted)',
        }}
      >
        {count}
      </span>
    </div>
  );
}

function StatChip({
  label,
  value,
  valueColor = 'var(--pao-anthracite)',
}: {
  label: string;
  value: string;
  valueColor?: string;
}) {
  return (
    <div>
      <div style={{ fontSize: '16px', fontWeight: 700, color: valueColor }}>{value}</div>
      <div style={{ fontSize: '11px', color: 'var(--pao-anthracite-muted)' }}>{label}</div>
    </div>
  );
}

function LastAttendanceCard({
  item,
  onCorrect,
}: {
  item: AttendanceWithMembers;
  onCorrect: () => void;
}) {
  const att = item.attendance;
  const newMembersWithOrigin = item.members.filter(
    (m) => m.classification === 'NOVO' && m.origin && m.origin.trim()
  );

  return (
    <div
      style={{
        backgroundColor: '#FFFFFF',
        border: '1px solid var(--pao-border)',
        borderRadius: '14px',
        padding: '14px',
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
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
          <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
            Último Atendimento Registrado
          </span>
          {att.isPreferential && (
            <span
              style={{
                backgroundColor: 'rgba(217, 119, 6, 0.18)',
                border: '1px solid rgba(217, 119, 6, 0.5)',
                borderRadius: '4px',
                padding: '2px 6px',
                fontSize: '9px',
                fontWeight: 700,
                color: '#D97706',
              }}
            >
              ★ PREFERENCIAL
            </span>
          )}
          {att.isEdited && (
            <span
              style={{
                backgroundColor: 'rgba(217, 119, 6, 0.15)',
                borderRadius: '4px',
                padding: '2px 6px',
                fontSize: '10px',
                fontWeight: 700,
                color: '#D97706',
              }}
            >
              Corrigido
            </span>
          )}
        </div>

        <span style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
          {formatTime(att.createdAt)}
        </span>
      </div>

      <div
        style={{
          marginTop: '8px',
          fontSize: '13px',
          fontWeight: 600,
          color: 'var(--pao-anthracite)',
        }}
      >
        {AttendanceTypeDisplay[att.type]} • {att.totalMembers} pessoa(s) ({att.newMembers} novo,{' '}
        {att.returningMembers} recorrente)
      </div>

      {newMembersWithOrigin.length > 0 && (
        <div style={{ marginTop: '4px', fontSize: '11px', color: 'var(--pao-anthracite-muted)' }}>
          Origens:{' '}
          {newMembersWithOrigin.map((m) => `Int ${m.memberNumber}: ${m.origin}`).join(', ')}
        </div>
      )}

      <button
        type="button"
        onClick={onCorrect}
        data-testid="btn_corrigir_ultimo"
        className="tap-feedback"
        style={{
          marginTop: '10px',
          width: '100%',
          height: '38px',
          borderRadius: '8px',
          border: '1px solid #D97706',
          backgroundColor: 'transparent',
          color: '#D97706',
          fontSize: '12px',
          fontWeight: 700,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '6px',
        }}
      >
        <Pencil size={14} color="#D97706" />
        <span>Corrigir Último Atendimento</span>
      </button>
    </div>
  );
}
