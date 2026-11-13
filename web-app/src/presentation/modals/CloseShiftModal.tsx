import { Lock } from 'lucide-react';
import { ShiftPeriodDisplay, type Shift } from '../../domain/models';
import { PaoPrimaryButton, PaoSecondaryButton } from '../components/CommonComponents';

export function CloseShiftModal({
  shift,
  totalAttendances,
  totalPeople,
  newClients,
  returningClients,
  onDismiss,
  onConfirmClose,
}: {
  shift: Shift;
  totalAttendances: number;
  totalPeople: number;
  newClients: number;
  returningClients: number;
  onDismiss: () => void;
  onConfirmClose: () => void;
}) {
  return (
    <div className="dialog-modal-backdrop" onClick={onDismiss}>
      <div className="dialog-modal-card" onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
          <div
            style={{
              width: '48px',
              height: '48px',
              borderRadius: '12px',
              backgroundColor: 'rgba(156, 24, 25, 0.12)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Lock size={24} color="#9C1819" />
          </div>
          <div
            style={{
              marginTop: '10px',
              fontSize: '18px',
              fontWeight: 700,
              color: 'var(--pao-anthracite)',
            }}
          >
            Encerrar Turno?
          </div>
          <div style={{ fontSize: '13px', color: 'var(--pao-anthracite-muted)' }}>
            Turno {ShiftPeriodDisplay[shift.period]} • {shift.hosterName}
          </div>
        </div>

        <div style={{ marginTop: '12px', fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
          Após o encerramento, não será mais permitido registrar novos atendimentos nem editar
          registros deste turno.
        </div>

        <div
          style={{
            marginTop: '10px',
            backgroundColor: 'rgba(242, 234, 225, 0.4)',
            border: '1px solid var(--pao-border)',
            borderRadius: '12px',
            padding: '14px',
            display: 'flex',
            flexDirection: 'column',
            gap: '6px',
          }}
        >
          <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
            Resultados do Turno:
          </div>
          <div style={{ height: '1px', backgroundColor: 'var(--pao-border-light)' }} />
          <MetricRow label="Total de Atendimentos" value={String(totalAttendances)} />
          <MetricRow label="Total de Pessoas Atendidas" value={String(totalPeople)} />
          <MetricRow label="Clientes Novos" value={String(newClients)} valueColor="#1E824C" />
          <MetricRow label="Clientes Recorrentes" value={String(returningClients)} />
        </div>

        <div style={{ marginTop: '16px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <PaoPrimaryButton
            text="Confirmar Encerramento"
            onClick={onConfirmClose}
            testId="confirm_close_shift_button"
          />
          <PaoSecondaryButton
            text="Voltar"
            onClick={onDismiss}
            style={{ width: '100%' }}
            testId="cancel_close_shift_button"
          />
        </div>
      </div>
    </div>
  );
}

function MetricRow({
  label,
  value,
  valueColor = 'var(--pao-anthracite)',
}: {
  label: string;
  value: string;
  valueColor?: string;
}) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      <span style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>{label}</span>
      <span style={{ fontSize: '13px', fontWeight: 700, color: valueColor }}>{value}</span>
    </div>
  );
}
