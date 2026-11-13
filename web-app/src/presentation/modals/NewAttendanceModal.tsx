import { useState } from 'react';
import {
  AlertTriangle,
  ArrowLeft,
  User,
  Users,
  UsersRound,
  X,
} from 'lucide-react';
import {
  AttendanceTypeDisplay,
  ClientOrigins,
  ShiftPeriodDisplay,
  type AttendanceMember,
  type AttendanceType,
  type ClientClassification,
  type MemberDraft,
  type Shift,
} from '../../domain/models';
import {
  NumericStepper,
  PaoPrimaryButton,
  PaoSecondaryButton,
} from '../components/CommonComponents';

export function NewAttendanceModal({
  shift,
  hosterName,
  onDismiss,
  onConfirm,
}: {
  shift: Shift;
  hosterName: string;
  onDismiss: () => void;
  onConfirm: (
    type: AttendanceType,
    total: number,
    newCount: number,
    returningCount: number,
    isPreferential: boolean,
    members: AttendanceMember[]
  ) => Promise<void>;
}) {
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [selectedType, setSelectedType] = useState<AttendanceType | null>(null);
  const [groupMemberCount, setGroupMemberCount] = useState(3);
  const [isPreferential, setIsPreferential] = useState(false);
  const [membersDraft, setMembersDraft] = useState<MemberDraft[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  const initializeMembers = (count: number) => {
    const list: MemberDraft[] = [];
    for (let i = 1; i <= count; i++) {
      list.push({ memberNumber: i, classification: 'RECORRENTE', origin: null });
    }
    setMembersDraft(list);
  };

  const updateMemberClassification = (idx: number, classification: ClientClassification) => {
    setMembersDraft((prev) =>
      prev.map((item, i) =>
        i === idx
          ? {
              ...item,
              classification,
              origin: classification === 'RECORRENTE' ? null : item.origin,
            }
          : item
      )
    );
  };

  const updateMemberOrigin = (idx: number, origin: string) => {
    setMembersDraft((prev) =>
      prev.map((item, i) => (i === idx ? { ...item, origin } : item))
    );
  };

  const stepTitle =
    step === 1 ? '1. Formato' : step === 2 ? '2. Integrantes' : '3. Resumo & Confirmação';

  const typeValue = selectedType ?? 'SOLO';
  const totalCount = membersDraft.length;
  const newCount = membersDraft.filter((m) => m.classification === 'NOVO').length;
  const returningCount = membersDraft.filter((m) => m.classification === 'RECORRENTE').length;

  return (
    <div className="fullscreen-modal-backdrop">
      <div className="fullscreen-modal-sheet">
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            height: '100%',
            padding: '12px 20px 20px',
          }}
        >
          {/* Top Bar */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            {step > 1 ? (
              <button
                type="button"
                onClick={() => {
                  setValidationError(null);
                  setStep((s) => (s === 3 ? 2 : 1));
                }}
                disabled={isSubmitting}
                data-testid="attendance_back_button"
                aria-label="Voltar"
                style={{
                  width: '48px',
                  height: '48px',
                  border: 'none',
                  background: 'transparent',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <ArrowLeft size={22} color="#261D1C" />
              </button>
            ) : (
              <button
                type="button"
                onClick={onDismiss}
                disabled={isSubmitting}
                data-testid="attendance_close_button"
                aria-label="Fechar"
                style={{
                  width: '48px',
                  height: '48px',
                  border: 'none',
                  background: 'transparent',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <X size={22} color="#261D1C" />
              </button>
            )}

            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
              <div style={{ fontSize: '17px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
                {stepTitle}
              </div>
              <div style={{ display: 'flex', gap: '6px', marginTop: '4px', alignItems: 'center' }}>
                {[1, 2, 3].map((s) => (
                  <span
                    key={s}
                    style={{
                      width: s === step ? '28px' : '14px',
                      height: '4px',
                      borderRadius: '2px',
                      backgroundColor: s <= step ? 'var(--pao-red)' : 'var(--pao-border)',
                      transition: 'width 0.15s ease',
                    }}
                  />
                ))}
              </div>
            </div>

            <div style={{ width: '48px', height: '48px' }} />
          </div>

          <div style={{ height: '1px', backgroundColor: 'var(--pao-border-light)', margin: '12px 0' }} />

          {validationError && (
            <div
              style={{
                backgroundColor: 'rgba(156, 24, 25, 0.12)',
                border: '1px solid rgba(156, 24, 25, 0.35)',
                borderRadius: '8px',
                padding: '12px',
                marginBottom: '12px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
              }}
            >
              <AlertTriangle size={20} color="#9C1819" style={{ flexShrink: 0 }} />
              <span style={{ fontSize: '13px', fontWeight: 500, color: '#9C1819' }}>
                {validationError}
              </span>
            </div>
          )}

          {/* Step 1: Formato */}
          {step === 1 && (
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
              <div
                className="scroll-area"
                style={{
                  flex: 1,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '14px',
                  paddingBottom: '12px',
                }}
              >
                <div style={{ fontSize: '16px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
                  Qual o formato do atendimento?
                </div>

                <PreferentialToggleCard
                  isPreferential={isPreferential}
                  onToggle={setIsPreferential}
                />

                <AttendanceTypeCard
                  title="Solo"
                  subtitle="1 pessoa"
                  icon={<User size={24} color={selectedType === 'SOLO' ? '#FFFFFF' : '#9C1819'} />}
                  isSelected={selectedType === 'SOLO'}
                  onClick={() => {
                    setSelectedType('SOLO');
                    initializeMembers(1);
                    setStep(2);
                  }}
                  testId="type_solo_card"
                />

                <AttendanceTypeCard
                  title="Dupla"
                  subtitle="2 pessoas"
                  icon={<Users size={24} color={selectedType === 'DUPLA' ? '#FFFFFF' : '#9C1819'} />}
                  isSelected={selectedType === 'DUPLA'}
                  onClick={() => {
                    setSelectedType('DUPLA');
                    initializeMembers(2);
                    setStep(2);
                  }}
                  testId="type_dupla_card"
                />

                <AttendanceTypeCard
                  title="Grupo"
                  subtitle="3 ou mais pessoas"
                  icon={
                    <UsersRound size={24} color={selectedType === 'GRUPO' ? '#FFFFFF' : '#9C1819'} />
                  }
                  isSelected={selectedType === 'GRUPO'}
                  onClick={() => setSelectedType('GRUPO')}
                  testId="type_grupo_card"
                />

                {selectedType === 'GRUPO' && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '4px' }}>
                    <NumericStepper
                      value={groupMemberCount}
                      onChange={setGroupMemberCount}
                      min={3}
                      max={30}
                      label="Quantidade de integrantes do grupo"
                      testIdPrefix="group_members"
                    />
                    <PaoPrimaryButton
                      text={`Continuar com ${groupMemberCount} pessoas`}
                      onClick={() => {
                        initializeMembers(groupMemberCount);
                        setStep(2);
                      }}
                      testId="continue_group_button"
                    />
                  </div>
                )}
              </div>

              <div style={{ paddingTop: '12px' }}>
                <PaoSecondaryButton
                  text="Cancelar"
                  onClick={onDismiss}
                  style={{ width: '100%' }}
                  testId="cancel_attendance_step1"
                />
              </div>
            </div>
          )}

          {/* Step 2: Integrantes */}
          {step === 2 && (
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
              <div style={{ fontSize: '16px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
                Classificação individual dos integrantes
              </div>
              <div
                style={{
                  fontSize: '12px',
                  color: 'var(--pao-anthracite-muted)',
                  marginBottom: '8px',
                }}
              >
                Defina se cada pessoa é nova ou recorrente e indique a origem para os novos.
              </div>

              {/* Ações rápidas */}
              <div
                style={{
                  backgroundColor: 'var(--pao-cream-light)',
                  border: '1px solid var(--pao-border-light)',
                  borderRadius: '12px',
                  padding: '10px',
                  marginBottom: '12px',
                }}
              >
                <div
                  style={{
                    fontSize: '11px',
                    fontWeight: 700,
                    color: 'var(--pao-anthracite-muted)',
                    marginBottom: '6px',
                  }}
                >
                  Ações rápidas:
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                  <QuickActionChip
                    text="Marcar todos Novos"
                    onClick={() =>
                      setMembersDraft((prev) =>
                        prev.map((m) => ({ ...m, classification: 'NOVO' }))
                      )
                    }
                    testId="quick_mark_all_new"
                  />
                  <QuickActionChip
                    text="Indicação p/ novos"
                    onClick={() =>
                      setMembersDraft((prev) =>
                        prev.map((m) =>
                          m.classification === 'NOVO' ? { ...m, origin: 'Indicação' } : m
                        )
                      )
                    }
                    testId="quick_origin_indicacao_all_new"
                  />
                  <QuickActionChip
                    text="Todos Recorrentes"
                    onClick={() =>
                      setMembersDraft((prev) =>
                        prev.map((m) => ({
                          ...m,
                          classification: 'RECORRENTE',
                          origin: null,
                        }))
                      )
                    }
                    testId="quick_mark_all_recurrent"
                  />
                </div>
              </div>

              {/* Lista de integrantes */}
              <div
                className="scroll-area"
                style={{
                  flex: 1,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '14px',
                  paddingBottom: '8px',
                }}
              >
                {membersDraft.map((draft, index) => (
                  <MemberClassificationCard
                    key={draft.memberNumber}
                    draft={draft}
                    onClassificationChange={(cls) => updateMemberClassification(index, cls)}
                    onOriginChange={(orig) => updateMemberOrigin(index, orig)}
                  />
                ))}
              </div>

              <div style={{ paddingTop: '14px', display: 'flex', gap: '10px' }}>
                <PaoSecondaryButton
                  text="Voltar"
                  onClick={() => setStep(1)}
                  style={{ flex: 1 }}
                  testId="step2_back_button"
                />
                <PaoPrimaryButton
                  text="Avançar para Resumo"
                  onClick={() => {
                    const missing = membersDraft.some(
                      (m) => m.classification === 'NOVO' && (!m.origin || !m.origin.trim())
                    );
                    if (missing) {
                      setValidationError('Selecione a origem de todos os clientes novos.');
                    } else {
                      setValidationError(null);
                      setStep(3);
                    }
                  }}
                  style={{ flex: 1.4 }}
                  testId="advance_to_summary_button"
                />
              </div>
            </div>
          )}

          {/* Step 3: Resumo & Confirmação */}
          {step === 3 && (
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
              <div style={{ fontSize: '16px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
                Resumo do Atendimento
              </div>
              <div
                style={{
                  fontSize: '12px',
                  color: 'var(--pao-anthracite-muted)',
                  marginBottom: '8px',
                }}
              >
                Revise as informações antes de confirmar o registro.
              </div>

              <div className="scroll-area" style={{ flex: 1 }}>
                <div
                  style={{
                    backgroundColor: '#FFFFFF',
                    border: '1px solid var(--pao-border)',
                    borderRadius: '16px',
                    padding: '14px',
                  }}
                >
                  <SummaryRow
                    label="Tipo de Atendimento"
                    value={AttendanceTypeDisplay[typeValue]}
                  />
                  <SummaryRow
                    label="Atendimento Preferencial"
                    value={isPreferential ? 'Sim (Preferencial)' : 'Não'}
                    valueColor={isPreferential ? '#D97706' : 'var(--pao-anthracite)'}
                  />
                  <SummaryRow label="Total de Pessoas" value={`${totalCount} pessoa(s)`} />
                  <SummaryRow label="Clientes Novos" value={String(newCount)} valueColor="#1E824C" />
                  <SummaryRow label="Clientes Recorrentes" value={String(returningCount)} />
                  <SummaryRow label="Hoster Responsável" value={hosterName} />
                  <SummaryRow label="Turno Atual" value={ShiftPeriodDisplay[shift.period]} />

                  {newCount > 0 && (
                    <>
                      <div
                        style={{
                          height: '1px',
                          backgroundColor: 'var(--pao-border-light)',
                          margin: '10px 0',
                        }}
                      />
                      <div
                        style={{
                          fontSize: '13px',
                          fontWeight: 600,
                          color: 'var(--pao-anthracite)',
                          marginBottom: '6px',
                        }}
                      >
                        Origem dos novos clientes:
                      </div>
                      {membersDraft
                        .filter((m) => m.classification === 'NOVO')
                        .map((m) => (
                          <div
                            key={m.memberNumber}
                            style={{
                              fontSize: '12px',
                              color: 'var(--pao-anthracite-muted)',
                              marginBottom: '2px',
                            }}
                          >
                            • Integrante {m.memberNumber}: {m.origin}
                          </div>
                        ))}
                    </>
                  )}
                </div>
              </div>

              <div style={{ paddingTop: '14px', display: 'flex', gap: '10px' }}>
                <PaoSecondaryButton
                  text="Voltar e Corrigir"
                  onClick={() => setStep(2)}
                  disabled={isSubmitting}
                  style={{ flex: 1 }}
                  testId="summary_back_button"
                />
                <PaoPrimaryButton
                  text="Confirmar Atendimento"
                  isLoading={isSubmitting}
                  onClick={async () => {
                    setIsSubmitting(true);
                    const members: AttendanceMember[] = membersDraft.map((d) => ({
                      id: '',
                      attendanceId: '',
                      memberNumber: d.memberNumber,
                      classification: d.classification,
                      origin: d.classification === 'NOVO' ? d.origin : null,
                      createdAt: Date.now(),
                    }));
                    await onConfirm(
                      typeValue,
                      totalCount,
                      newCount,
                      returningCount,
                      isPreferential,
                      members
                    );
                    setIsSubmitting(false);
                  }}
                  style={{ flex: 1.4 }}
                  testId="confirm_attendance_button"
                />
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export function PreferentialToggleCard({
  isPreferential,
  onToggle,
}: {
  isPreferential: boolean;
  onToggle: (val: boolean) => void;
}) {
  return (
    <div
      role="switch"
      aria-checked={isPreferential}
      tabIndex={0}
      onClick={() => onToggle(!isPreferential)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onToggle(!isPreferential);
        }
      }}
      data-testid="toggle_preferential_card"
      className="tap-feedback"
      style={{
        width: '100%',
        borderRadius: '12px',
        backgroundColor: isPreferential ? 'rgba(217, 119, 6, 0.12)' : 'var(--pao-cream-light)',
        border: `1px solid ${isPreferential ? '#D97706' : 'var(--pao-border-light)'}`,
        padding: '10px 14px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '10px',
        cursor: 'pointer',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1 }}>
        <div
          style={{
            width: '36px',
            height: '36px',
            borderRadius: '50%',
            backgroundColor: isPreferential ? '#D97706' : 'var(--pao-border-light)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '16px',
            fontWeight: 700,
            color: isPreferential ? '#FFFFFF' : 'var(--pao-anthracite-muted)',
            flexShrink: 0,
          }}
        >
          ★
        </div>
        <div>
          <div
            style={{
              fontSize: '13px',
              fontWeight: 700,
              color: isPreferential ? '#D97706' : 'var(--pao-anthracite)',
            }}
          >
            Atendimento Preferencial
          </div>
          <div style={{ fontSize: '11px', color: 'var(--pao-anthracite-muted)' }}>
            Idoso, gestante, PCD, autista ou mobilidade reduzida
          </div>
        </div>
      </div>

      {/* M3-like Toggle Switch */}
      <div
        data-testid="preferential_switch"
        style={{
          width: '48px',
          height: '28px',
          borderRadius: '14px',
          backgroundColor: isPreferential ? '#D97706' : 'var(--pao-border)',
          padding: '3px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: isPreferential ? 'flex-end' : 'flex-start',
          transition: 'background-color 0.15s ease',
          flexShrink: 0,
        }}
      >
        <div
          style={{
            width: '22px',
            height: '22px',
            borderRadius: '50%',
            backgroundColor: '#FFFFFF',
            boxShadow: '0 1px 3px rgba(0,0,0,0.2)',
          }}
        />
      </div>
    </div>
  );
}

export function QuickActionChip({
  text,
  onClick,
  testId,
}: {
  text: string;
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
        backgroundColor: '#FFFFFF',
        border: '1px solid rgba(156, 24, 25, 0.5)',
        borderRadius: '8px',
        padding: '6px 10px',
        fontSize: '11px',
        fontWeight: 700,
        color: 'var(--pao-red)',
      }}
    >
      {text}
    </button>
  );
}

function SummaryRow({
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
        padding: '4px 0',
      }}
    >
      <span style={{ fontSize: '13px', color: 'var(--pao-anthracite-muted)' }}>{label}</span>
      <span style={{ fontSize: '13px', fontWeight: 700, color: valueColor }}>{value}</span>
    </div>
  );
}

function AttendanceTypeCard({
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
        padding: '18px',
        borderRadius: '14px',
        backgroundColor: isSelected ? 'var(--pao-cream)' : '#FFFFFF',
        border: `${isSelected ? '2px' : '1px'} solid ${
          isSelected ? 'var(--pao-red)' : 'var(--pao-border)'
        }`,
        display: 'flex',
        alignItems: 'center',
        gap: '14px',
      }}
    >
      <div
        style={{
          width: '46px',
          height: '46px',
          borderRadius: '50%',
          backgroundColor: isSelected ? 'var(--pao-red)' : 'var(--pao-cream)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        {icon}
      </div>
      <div>
        <div style={{ fontSize: '17px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
          {title}
        </div>
        <div style={{ fontSize: '13px', color: 'var(--pao-anthracite-muted)' }}>{subtitle}</div>
      </div>
    </button>
  );
}

function MemberClassificationCard({
  draft,
  onClassificationChange,
  onOriginChange,
}: {
  draft: MemberDraft;
  onClassificationChange: (cls: ClientClassification) => void;
  onOriginChange: (orig: string) => void;
}) {
  const isRecorrente = draft.classification === 'RECORRENTE';
  const isNovo = draft.classification === 'NOVO';

  return (
    <div
      style={{
        backgroundColor: '#FFFFFF',
        border: '1px solid var(--pao-border)',
        borderRadius: '14px',
        padding: '14px',
      }}
    >
      <div style={{ fontSize: '14px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
        Integrante {draft.memberNumber}
      </div>

      <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
        <button
          type="button"
          onClick={() => onClassificationChange('RECORRENTE')}
          data-testid={`member_${draft.memberNumber}_recorrente`}
          className="tap-feedback"
          style={{
            flex: 1,
            padding: '10px',
            borderRadius: '8px',
            border: 'none',
            backgroundColor: isRecorrente ? 'var(--pao-anthracite)' : 'var(--pao-cream-light)',
            color: isRecorrente ? '#FFFFFF' : 'var(--pao-anthracite)',
            fontSize: '13px',
            fontWeight: 600,
          }}
        >
          Recorrente
        </button>

        <button
          type="button"
          onClick={() => onClassificationChange('NOVO')}
          data-testid={`member_${draft.memberNumber}_novo`}
          className="tap-feedback"
          style={{
            flex: 1,
            padding: '10px',
            borderRadius: '8px',
            border: 'none',
            backgroundColor: isNovo ? 'var(--pao-red)' : 'var(--pao-cream-light)',
            color: isNovo ? '#FFFFFF' : 'var(--pao-anthracite)',
            fontSize: '13px',
            fontWeight: 600,
          }}
        >
          Novo Cliente
        </button>
      </div>

      {isNovo && (
        <div style={{ marginTop: '12px' }}>
          <div
            style={{
              fontSize: '12px',
              fontWeight: 600,
              color: 'var(--pao-anthracite)',
              marginBottom: '6px',
            }}
          >
            Origem do cliente:
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
            {ClientOrigins.map((origin) => {
              const selected = draft.origin === origin;
              return (
                <button
                  key={origin}
                  type="button"
                  onClick={() => onOriginChange(origin)}
                  data-testid={`origin_${origin}_member_${draft.memberNumber}`}
                  className="tap-feedback"
                  style={{
                    borderRadius: '16px',
                    padding: '6px 10px',
                    backgroundColor: selected
                      ? 'rgba(156, 24, 25, 0.15)'
                      : 'var(--pao-cream-light)',
                    border: `1px solid ${selected ? 'var(--pao-red)' : 'var(--pao-border-light)'}`,
                    fontSize: '12px',
                    fontWeight: selected ? 700 : 500,
                    color: selected ? 'var(--pao-red)' : 'var(--pao-anthracite)',
                  }}
                >
                  {origin}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
