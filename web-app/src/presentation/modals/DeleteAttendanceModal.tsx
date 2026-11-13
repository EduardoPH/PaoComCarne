import { useState } from 'react';
import { AlertTriangle, Trash2 } from 'lucide-react';
import { AttendanceTypeDisplay, type AttendanceWithMembers } from '../../domain/models';
import { formatDateTime } from '../../domain/utils/dateTime';

export function DeleteAttendanceModal({
  item,
  onDismiss,
  onConfirmDelete,
}: {
  item: AttendanceWithMembers;
  onDismiss: () => void;
  onConfirmDelete: (reason: string | null) => Promise<void> | void;
}) {
  const att = item.attendance;
  const [reason, setReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  return (
    <div
      className="dialog-modal-backdrop"
      onClick={() => !isSubmitting && onDismiss()}
      data-testid="delete_attendance_dialog"
    >
      <div className="dialog-modal-card" onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <AlertTriangle size={24} color="#9C1819" />
          <span style={{ fontSize: '18px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
            Excluir Atendimento
          </span>
        </div>

        <div
          style={{
            marginTop: '10px',
            fontSize: '14px',
            fontWeight: 600,
            color: 'var(--pao-anthracite)',
          }}
        >
          Tem certeza de que deseja excluir este atendimento?
        </div>

        <div
          style={{
            marginTop: '12px',
            backgroundColor: 'var(--pao-cream-light)',
            border: '1px solid var(--pao-border-light)',
            borderRadius: '12px',
            padding: '12px',
            display: 'flex',
            flexDirection: 'column',
            gap: '5px',
          }}
        >
          <DeleteInfoRow
            label="Tipo"
            value={
              AttendanceTypeDisplay[att.type] + (att.isPreferential ? ' (Preferencial)' : '')
            }
          />
          <DeleteInfoRow label="Total de pessoas" value={String(att.totalMembers)} />
          <DeleteInfoRow
            label="Novos clientes"
            value={String(att.newMembers)}
            valueColor="#1E824C"
          />
          <DeleteInfoRow label="Recorrentes" value={String(att.returningMembers)} />
          <DeleteInfoRow label="Hoster responsável" value={att.hosterName} />
          <DeleteInfoRow label="Data e horário" value={formatDateTime(att.createdAt)} />
        </div>

        <div className="outlined-field-wrap" style={{ marginTop: '14px' }}>
          <span className="outlined-field-label">Motivo da exclusão (opcional)</span>
          <input
            type="text"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Ex.: cliente desistiu antes da acomodação"
            data-testid="input_deletion_reason"
            className="outlined-field-input"
            style={{ height: '46px', fontSize: '13px' }}
          />
        </div>

        <div
          style={{
            marginTop: '10px',
            fontSize: '11px',
            color: 'var(--pao-anthracite-muted)',
            lineHeight: 1.45,
          }}
        >
          O registro será removido dos indicadores e relatórios, permanecendo preservado no banco
          para auditoria.
        </div>

        <div
          style={{
            marginTop: '16px',
            display: 'flex',
            justifyContent: 'flex-end',
            gap: '8px',
          }}
        >
          <button
            type="button"
            disabled={isSubmitting}
            onClick={onDismiss}
            data-testid="btn_cancelar_exclusao"
            className="tap-feedback"
            style={{
              border: '1px solid var(--pao-border)',
              borderRadius: '10px',
              backgroundColor: '#FFFFFF',
              padding: '9px 14px',
              fontSize: '13px',
              fontWeight: 600,
              color: 'var(--pao-anthracite)',
            }}
          >
            Cancelar
          </button>
          <button
            type="button"
            disabled={isSubmitting}
            onClick={async () => {
              setIsSubmitting(true);
              await onConfirmDelete(reason.trim() ? reason.trim() : null);
              setIsSubmitting(false);
            }}
            data-testid="btn_confirmar_exclusao"
            className="tap-feedback"
            style={{
              border: 'none',
              borderRadius: '10px',
              backgroundColor: 'var(--status-red)',
              padding: '9px 14px',
              fontSize: '13px',
              fontWeight: 700,
              color: '#FFFFFF',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
            }}
          >
            <Trash2 size={14} />
            <span>Confirmar Exclusão</span>
          </button>
        </div>
      </div>
    </div>
  );
}

function DeleteInfoRow({
  label,
  value,
  valueColor = 'var(--pao-anthracite)',
}: {
  label: string;
  value: string;
  valueColor?: string;
}) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: '8px', fontSize: '12px' }}>
      <span style={{ color: 'var(--pao-anthracite-muted)' }}>{label}:</span>
      <span style={{ fontWeight: 700, color: valueColor, textAlign: 'right' }}>{value}</span>
    </div>
  );
}
