import { useState } from 'react';
import { Hourglass } from 'lucide-react';
import {
  NumericStepper,
  PaoPrimaryButton,
  PaoSecondaryButton,
} from '../components/CommonComponents';

export function UpdateQueueModal({
  initialDuos,
  initialGroups,
  onDismiss,
  onSave,
}: {
  initialDuos: number;
  initialGroups: number;
  onDismiss: () => void;
  onSave: (duos: number, groups: number) => void;
}) {
  const [duos, setDuos] = useState(initialDuos);
  const [groups, setGroups] = useState(initialGroups);

  return (
    <div className="dialog-modal-backdrop" onClick={onDismiss}>
      <div className="dialog-modal-card" onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
          <div
            style={{
              width: '44px',
              height: '44px',
              borderRadius: '12px',
              backgroundColor: 'var(--pao-cream)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Hourglass size={22} color="#9C1819" />
          </div>
          <div
            style={{
              marginTop: '10px',
              fontSize: '18px',
              fontWeight: 700,
              color: 'var(--pao-anthracite)',
            }}
          >
            Atualizar Fila de Espera
          </div>
          <div
            style={{
              fontSize: '12px',
              color: 'var(--pao-anthracite-muted)',
              textAlign: 'center',
            }}
          >
            Controle manual de duplas e grupos aguardando mesa
          </div>
        </div>

        <div style={{ marginTop: '16px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <NumericStepper
            value={duos}
            onChange={setDuos}
            min={0}
            max={50}
            label="Duplas aguardando"
            testIdPrefix="queue_duos"
          />

          <NumericStepper
            value={groups}
            onChange={setGroups}
            min={0}
            max={50}
            label="Grupos aguardando"
            testIdPrefix="queue_groups"
          />

          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <button
              type="button"
              onClick={() => {
                setDuos(0);
                setGroups(0);
              }}
              data-testid="reset_queue_button"
              style={{
                border: 'none',
                background: 'transparent',
                color: 'var(--status-red)',
                fontSize: '13px',
                fontWeight: 600,
                padding: '6px 4px',
              }}
            >
              Zerar Fila de Espera
            </button>
          </div>
        </div>

        <div style={{ marginTop: '12px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <PaoPrimaryButton
            text="Salvar Atualização"
            onClick={() => onSave(duos, groups)}
            testId="save_queue_button"
          />
          <PaoSecondaryButton
            text="Cancelar"
            onClick={onDismiss}
            style={{ width: '100%' }}
            testId="cancel_queue_button"
          />
        </div>
      </div>
    </div>
  );
}
