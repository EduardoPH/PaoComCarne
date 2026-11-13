import { useState } from 'react';
import { Info, Pencil } from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { receptionRepository } from '../../data/repositories/receptionRepository';
import {
  AttendanceTypeDisplay,
  ShiftPeriodDisplay,
  type AttendanceWithMembers,
} from '../../domain/models';
import { formatTime } from '../../domain/utils/dateTime';
import { SyncStatusPill } from '../components/CommonComponents';
import { CorrectionModal } from '../modals/CorrectionModal';

export function InspectionScreen() {
  const { currentHoster, currentShift, shiftAttendances, sync, showSnackbar } = useApp();
  const [showCorrection, setShowCorrection] = useState(false);

  const totalAtt = shiftAttendances.length;
  const totalPeople = shiftAttendances.reduce((s, a) => s + a.attendance.totalMembers, 0);
  const newCount = shiftAttendances.reduce((s, a) => s + a.attendance.newMembers, 0);
  const returningCount = shiftAttendances.reduce((s, a) => s + a.attendance.returningMembers, 0);
  const lastAttendance = shiftAttendances[shiftAttendances.length - 1] ?? null;

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', padding: '0 20px', overflow: 'hidden' }}>
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
            Inspeção ao Vivo
          </div>
          <div style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
            {currentShift
              ? `Acompanhamento exclusivo do Turno ${ShiftPeriodDisplay[currentShift.period]}`
              : 'Nenhum turno aberto'}
          </div>
        </div>

        <SyncStatusPill syncState={sync.syncState} pendingCount={sync.pendingCount} />
      </div>

      <div
        style={{
          height: '1px',
          backgroundColor: 'var(--pao-border-light)',
          marginBottom: '12px',
        }}
      />

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
          <Info size={36} color="#D97706" />
          <div
            style={{
              marginTop: '10px',
              fontSize: '16px',
              fontWeight: 700,
              color: 'var(--pao-anthracite)',
            }}
          >
            Sem Turno Ativo
          </div>
          <div
            style={{
              marginTop: '4px',
              fontSize: '13px',
              color: 'var(--pao-anthracite-muted)',
              lineHeight: 1.45,
            }}
          >
            A inspeção ao vivo acompanha somente atendimentos de turnos abertos. Abra um turno na
            aba Recepção para acompanhar em tempo real.
          </div>
        </div>
      ) : (
        <div
          style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          {/* Totalizadores ao Vivo */}
          <div
            style={{
              backgroundColor: '#FFFFFF',
              border: '1px solid var(--pao-border)',
              borderRadius: '16px',
              padding: '16px',
              flexShrink: 0,
            }}
          >
            <div style={{ fontSize: '14px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
              Totalizadores do Turno Atual
            </div>

            <div style={{ display: 'flex', gap: '8px', marginTop: '12px' }}>
              <InspectionStatBox label="Atendimentos" value={String(totalAtt)} />
              <InspectionStatBox label="Total Pessoas" value={String(totalPeople)} />
            </div>

            <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
              <InspectionStatBox
                label="Clientes Novos"
                value={String(newCount)}
                valueColor="#1E824C"
              />
              <InspectionStatBox label="Recorrentes" value={String(returningCount)} />
            </div>
          </div>

          <div
            style={{
              marginTop: '14px',
              marginBottom: '6px',
              fontSize: '15px',
              fontWeight: 700,
              color: 'var(--pao-anthracite)',
              flexShrink: 0,
            }}
          >
            Histórico Cronológico ({shiftAttendances.length})
          </div>

          {shiftAttendances.length === 0 ? (
            <div
              style={{
                flex: 1,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '13px',
                color: 'var(--pao-anthracite-muted)',
                textAlign: 'center',
              }}
            >
              Nenhum atendimento registrado ainda neste turno.
            </div>
          ) : (
            <div
              className="scroll-area"
              style={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                gap: '10px',
                paddingBottom: '16px',
              }}
            >
              {shiftAttendances.map((item, idx) => (
                <InspectionAttendanceCard
                  key={item.attendance.id}
                  item={item}
                  index={idx + 1}
                  isLast={idx === shiftAttendances.length - 1}
                  onCorrect={() => setShowCorrection(true)}
                />
              ))}
            </div>
          )}
        </div>
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

function InspectionStatBox({
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
        flex: 1,
        backgroundColor: 'var(--pao-cream-light)',
        border: '1px solid var(--pao-border-light)',
        borderRadius: '10px',
        padding: '10px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
      }}
    >
      <div style={{ fontSize: '18px', fontWeight: 700, color: valueColor }}>{value}</div>
      <div style={{ fontSize: '11px', color: 'var(--pao-anthracite-muted)' }}>{label}</div>
    </div>
  );
}

function InspectionAttendanceCard({
  item,
  index,
  isLast,
  onCorrect,
}: {
  item: AttendanceWithMembers;
  index: number;
  isLast: boolean;
  onCorrect: () => void;
}) {
  const att = item.attendance;
  const newMembers = item.members.filter((m) => m.classification === 'NOVO');

  return (
    <div
      style={{
        backgroundColor: '#FFFFFF',
        border: '1px solid var(--pao-border-light)',
        borderRadius: '12px',
        padding: '12px',
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
          <div
            style={{
              width: '24px',
              height: '24px',
              borderRadius: '50%',
              backgroundColor: 'var(--pao-cream)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '11px',
              fontWeight: 700,
              color: 'var(--pao-red)',
            }}
          >
            {index}
          </div>
          <span style={{ fontSize: '14px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
            {AttendanceTypeDisplay[att.type]}
          </span>
          {att.isPreferential && (
            <span
              style={{
                borderRadius: '4px',
                backgroundColor: 'rgba(217, 119, 6, 0.18)',
                border: '1px solid rgba(217, 119, 6, 0.5)',
                padding: '2px 4px',
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
                borderRadius: '4px',
                backgroundColor: 'rgba(217, 119, 6, 0.15)',
                padding: '2px 4px',
                fontSize: '9px',
                fontWeight: 700,
                color: '#D97706',
              }}
            >
              Editado
            </span>
          )}
        </div>

        <span style={{ fontSize: '12px', fontWeight: 500, color: 'var(--pao-anthracite-muted)' }}>
          {formatTime(att.createdAt)}
        </span>
      </div>

      <div style={{ marginTop: '6px', fontSize: '12px', color: 'var(--pao-anthracite)' }}>
        Total: {att.totalMembers} pessoa(s) • {att.newMembers} novo(s) • {att.returningMembers}{' '}
        recorrente(s)
      </div>

      {newMembers.length > 0 && (
        <div style={{ marginTop: '4px', display: 'flex', flexDirection: 'column', gap: '2px' }}>
          {newMembers.map((m) => (
            <div
              key={m.id || m.memberNumber}
              style={{ fontSize: '11px', color: 'var(--pao-anthracite-muted)' }}
            >
              • Integrante {m.memberNumber}: {m.origin || 'Sem origem'}
            </div>
          ))}
        </div>
      )}

      {isLast && (
        <div style={{ marginTop: '8px', display: 'flex', justifyContent: 'flex-end' }}>
          <button
            type="button"
            onClick={onCorrect}
            data-testid="inspection_btn_corrigir_ultimo"
            className="tap-feedback"
            style={{
              height: '32px',
              padding: '0 10px',
              borderRadius: '6px',
              border: '1px solid #D97706',
              backgroundColor: 'transparent',
              color: '#D97706',
              fontSize: '11px',
              fontWeight: 700,
              display: 'inline-flex',
              alignItems: 'center',
              gap: '4px',
            }}
          >
            <Pencil size={12} color="#D97706" />
            <span>Corrigir este último</span>
          </button>
        </div>
      )}
    </div>
  );
}
