import { useState } from 'react';
import { Clock, Moon, Sun } from 'lucide-react';
import type { ShiftPeriod } from '../../domain/models';
import { PaoPrimaryButton, PaoSecondaryButton } from '../components/CommonComponents';

export function ShiftSelectModal({
  hosterName,
  onSelectPeriod,
  onCancel,
}: {
  hosterName: string;
  onSelectPeriod: (period: ShiftPeriod) => void;
  onCancel: () => void;
}) {
  const [selectedPeriod, setSelectedPeriod] = useState<ShiftPeriod>('MANHA');

  return (
    <div className="dialog-modal-backdrop" onClick={onCancel}>
      <div className="dialog-modal-card" onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
          <div
            style={{
              width: '48px',
              height: '48px',
              borderRadius: '12px',
              backgroundColor: 'var(--pao-cream)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Clock size={26} color="#9C1819" />
          </div>
          <div
            style={{
              marginTop: '10px',
              fontSize: '18px',
              fontWeight: 700,
              color: 'var(--pao-anthracite)',
            }}
          >
            Abertura de Turno
          </div>
          <div style={{ fontSize: '13px', color: 'var(--pao-anthracite-muted)' }}>
            Hoster: {hosterName}
          </div>
        </div>

        <div style={{ marginTop: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <div style={{ fontSize: '13px', color: 'var(--pao-anthracite-muted)' }}>
            Selecione o período do turno que deseja iniciar:
          </div>

          <ShiftPeriodOptionCard
            title="Manhã"
            subtitle="Almoço e primeiras horas"
            icon={<Sun size={24} color={selectedPeriod === 'MANHA' ? '#9C1819' : '#6B5855'} />}
            isSelected={selectedPeriod === 'MANHA'}
            onClick={() => setSelectedPeriod('MANHA')}
            testId="period_manha_option"
          />

          <ShiftPeriodOptionCard
            title="Noite"
            subtitle="Jantar e movimento noturno"
            icon={<Moon size={24} color={selectedPeriod === 'NOITE' ? '#9C1819' : '#6B5855'} />}
            isSelected={selectedPeriod === 'NOITE'}
            onClick={() => setSelectedPeriod('NOITE')}
            testId="period_noite_option"
          />
        </div>

        <div style={{ marginTop: '18px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <PaoPrimaryButton
            text="Iniciar Turno"
            onClick={() => onSelectPeriod(selectedPeriod)}
            testId="confirm_shift_start_button"
          />
          <PaoSecondaryButton
            text="Trocar Hoster"
            onClick={onCancel}
            style={{ width: '100%' }}
            testId="cancel_shift_button"
          />
        </div>
      </div>
    </div>
  );
}

function ShiftPeriodOptionCard({
  title,
  subtitle,
  icon,
  isSelected,
  onClick,
  testId,
}: {
  title: string;
  subtitle: string;
  icon: React.ReactNode;
  isSelected: boolean;
  onClick: () => void;
  testId: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      data-testid={testId}
      className="tap-feedback"
      style={{
        width: '100%',
        textAlign: 'left',
        padding: '14px',
        borderRadius: '12px',
        backgroundColor: isSelected ? 'var(--pao-cream)' : 'var(--pao-cream-light)',
        border: `${isSelected ? '1.5px' : '1px'} solid ${
          isSelected ? 'var(--pao-red)' : 'var(--pao-border-light)'
        }`,
        display: 'flex',
        alignItems: 'center',
        gap: '12px',
      }}
    >
      {icon}
      <div>
        <div
          style={{
            fontSize: '15px',
            fontWeight: isSelected ? 700 : 600,
            color: 'var(--pao-anthracite)',
          }}
        >
          {title}
        </div>
        <div style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>{subtitle}</div>
      </div>
    </button>
  );
}
