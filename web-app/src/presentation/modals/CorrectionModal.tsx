import { useState } from 'react';
import { AlertTriangle, ArrowLeft, Pencil, X } from 'lucide-react';
import {
  AttendanceTypeDisplay,
  ClientOrigins,
  type AttendanceMember,
  type AttendanceType,
  type AttendanceWithMembers,
  type ClientClassification,
  type MemberDraft,
} from '../../domain/models';
import {
  NumericStepper,
  PaoPrimaryButton,
  PaoSecondaryButton,
} from '../components/CommonComponents';
import { PreferentialToggleCard, QuickActionChip } from './NewAttendanceModal';

export function CorrectionModal({
  attendanceWithMembers,
  editorHosterName,
  onDismiss,
  onConfirmCorrection,
}: {
  attendanceWithMembers: AttendanceWithMembers;
  editorHosterName: string;
  onDismiss: () => void;
  onConfirmCorrection: (
    newType: AttendanceType,
    newTotal: number,
    newNewCount: number,
    newReturningCount: number,
    newIsPreferential: boolean,
    newMembers: AttendanceMember[]
  ) => Promise<void>;
}) {
  const initial = attendanceWithMembers.attendance;
  const [step, setStep] = useState<1 | 2>(1);
  const [currentType, setCurrentType] = useState<AttendanceType>(initial.type);
  const [isPreferential, setIsPreferential] = useState(initial.isPreferential);
  const [groupMemberCount, setGroupMemberCount] = useState(
    initial.type === 'GRUPO' ? initial.totalMembers : 3
  );
  const [membersDraft, setMembersDraft] = useState<MemberDraft[]>(() =>
    attendanceWithMembers.members.map((m) => ({
      memberNumber: m.memberNumber,
      classification: m.classification,
      origin: m.origin,
    }))
  );
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  const adjustMemberCount = (count: number) => {
    setMembersDraft((prev) => {
      if (count > prev.length) {
        const next = [...prev];
        for (let i = prev.length + 1; i <= count; i++) {
          next.push({ memberNumber: i, classification: 'RECORRENTE', origin: null });
        }
        return next;
      }
      if (count < prev.length) {
        return prev.slice(0, count);
      }
      return prev;
    });
  };

  const total = membersDraft.length;
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
          {/* Top Header */}
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
                onClick={() => setStep(1)}
                disabled={isSubmitting}
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
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <Pencil size={15} color="#D97706" />
                <span style={{ fontSize: '16px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
                  Corrigir Último Atendimento
                </span>
              </div>
              <span style={{ fontSize: '11px', color: 'var(--pao-anthracite-muted)' }}>
                Auditoria será registrada para este atendimento
              </span>
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
                padding: '10px',
                marginBottom: '12px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
              }}
            >
              <AlertTriangle size={18} color="#9C1819" style={{ flexShrink: 0 }} />
              <span style={{ fontSize: '12px', color: '#9C1819' }}>{validationError}</span>
            </div>
          )}

          {step === 1 ? (
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
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
                <div>
                  <div
                    style={{
                      fontSize: '14px',
                      fontWeight: 700,
                      color: 'var(--pao-anthracite)',
                      marginBottom: '8px',
                    }}
                  >
                    Formato do Atendimento
                  </div>

                  <PreferentialToggleCard
                    isPreferential={isPreferential}
                    onToggle={setIsPreferential}
                  />

                  <div style={{ display: 'flex', gap: '8px', marginTop: '10px' }}>
                    {(['SOLO', 'DUPLA', 'GRUPO'] as AttendanceType[]).map((t) => {
                      const active = currentType === t;
                      return (
                        <button
                          key={t}
                          type="button"
                          onClick={() => {
                            setCurrentType(t);
                            adjustMemberCount(t === 'SOLO' ? 1 : t === 'DUPLA' ? 2 : groupMemberCount);
                          }}
                          className="tap-feedback"
                          style={{
                            flex: 1,
                            padding: '12px',
                            borderRadius: '10px',
                            border: 'none',
                            backgroundColor: active ? 'var(--pao-red)' : 'var(--pao-cream-light)',
                            color: active ? '#FFFFFF' : 'var(--pao-anthracite)',
                            fontSize: '13px',
                            fontWeight: 700,
                          }}
                        >
                          {AttendanceTypeDisplay[t]}
                        </button>
                      );
                    })}
                  </div>
                </div>

                {currentType === 'GRUPO' && (
                  <NumericStepper
                    value={groupMemberCount}
                    onChange={(val) => {
                      setGroupMemberCount(val);
                      adjustMemberCount(val);
                    }}
                    min={3}
                    max={30}
                    label="Integrantes do Grupo"
                  />
                )}

                <div>
                  <div
                    style={{
                      fontSize: '14px',
                      fontWeight: 700,
                      color: 'var(--pao-anthracite)',
                      marginBottom: '6px',
                    }}
                  >
                    Classificação dos Integrantes
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                    <QuickActionChip
                      text="Marcar todos Novos"
                      onClick={() =>
                        setMembersDraft((prev) =>
                          prev.map((m) => ({ ...m, classification: 'NOVO' }))
                        )
                      }
                      testId="correction_quick_mark_all_new"
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
                      testId="correction_quick_origin_indicacao"
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
                      testId="correction_quick_mark_all_rec"
                    />
                  </div>
                </div>

                {membersDraft.map((draft, index) => (
                  <EditableMemberCard
                    key={draft.memberNumber}
                    draft={draft}
                    onClassificationChange={(cls) => {
                      setMembersDraft((prev) =>
                        prev.map((m, i) =>
                          i === index
                            ? {
                                ...m,
                                classification: cls,
                                origin: cls === 'RECORRENTE' ? null : m.origin,
                              }
                            : m
                        )
                      );
                    }}
                    onOriginChange={(orig) => {
                      setMembersDraft((prev) =>
                        prev.map((m, i) => (i === index ? { ...m, origin: orig } : m))
                      );
                    }}
                  />
                ))}
              </div>

              <div style={{ paddingTop: '14px', display: 'flex', gap: '10px' }}>
                <PaoSecondaryButton
                  text="Cancelar"
                  onClick={onDismiss}
                  style={{ flex: 1 }}
                  testId="correction_cancel_button"
                />
                <PaoPrimaryButton
                  text="Avançar para Resumo da Correção"
                  onClick={() => {
                    const missing = membersDraft.some(
                      (m) => m.classification === 'NOVO' && (!m.origin || !m.origin.trim())
                    );
                    if (missing) {
                      setValidationError('Selecione a origem de todos os clientes novos.');
                    } else {
                      setValidationError(null);
                      setStep(2);
                    }
                  }}
                  style={{ flex: 1.5 }}
                  testId="correction_next_button"
                />
              </div>
            </div>
          ) : (
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
              <div style={{ fontSize: '16px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
                Confirmação da Correção
              </div>
              <div
                style={{
                  fontSize: '12px',
                  color: 'var(--pao-anthracite-muted)',
                  marginBottom: '12px',
                }}
              >
                O histórico anterior será preservado na auditoria interna.
              </div>

              <div className="scroll-area" style={{ flex: 1 }}>
                <div
                  style={{
                    backgroundColor: '#FFFFFF',
                    border: '1px solid var(--pao-border)',
                    borderRadius: '14px',
                    padding: '14px',
                    fontSize: '13px',
                    lineHeight: 1.6,
                  }}
                >
                  <div style={{ fontWeight: 700, color: 'var(--pao-red)', marginBottom: '6px' }}>
                    Novos Valores:
                  </div>
                  <div>Tipo: {AttendanceTypeDisplay[currentType]}</div>
                  <div
                    style={{
                      fontWeight: isPreferential ? 700 : 400,
                      color: isPreferential ? '#D97706' : 'var(--pao-anthracite)',
                    }}
                  >
                    Atendimento Preferencial: {isPreferential ? 'Sim (Preferencial)' : 'Não'}
                  </div>
                  <div>Total de Pessoas: {total}</div>
                  <div>
                    Novos: {newCount} | Recorrentes: {returningCount}
                  </div>
                  <div>Hoster que corrigiu: {editorHosterName}</div>

                  <div
                    style={{
                      height: '1px',
                      backgroundColor: 'var(--pao-border-light)',
                      margin: '10px 0',
                    }}
                  />

                  <div
                    style={{
                      fontSize: '12px',
                      fontWeight: 600,
                      color: 'var(--pao-anthracite-muted)',
                    }}
                  >
                    Valor Anterior Registrado:
                  </div>
                  <div style={{ fontSize: '12px', color: 'var(--pao-anthracite-muted)' }}>
                    {AttendanceTypeDisplay[initial.type]}
                    {initial.isPreferential ? ' [PREFERENCIAL]' : ''} ({initial.totalMembers}p:{' '}
                    {initial.newMembers} novos, {initial.returningMembers} rec)
                  </div>
                </div>
              </div>

              <div style={{ paddingTop: '14px', display: 'flex', gap: '10px' }}>
                <PaoSecondaryButton
                  text="Voltar"
                  onClick={() => setStep(1)}
                  disabled={isSubmitting}
                  style={{ flex: 1 }}
                />
                <PaoPrimaryButton
                  text="Confirmar Correção"
                  isLoading={isSubmitting}
                  onClick={async () => {
                    setIsSubmitting(true);
                    const members: AttendanceMember[] = membersDraft.map((d) => ({
                      id: '',
                      attendanceId: initial.id,
                      memberNumber: d.memberNumber,
                      classification: d.classification,
                      origin: d.classification === 'NOVO' ? d.origin : null,
                      createdAt: Date.now(),
                    }));
                    await onConfirmCorrection(
                      currentType,
                      total,
                      newCount,
                      returningCount,
                      isPreferential,
                      members
                    );
                    setIsSubmitting(false);
                  }}
                  style={{ flex: 1.4 }}
                  testId="confirm_correction_button"
                />
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function EditableMemberCard({
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
        border: '1px solid var(--pao-border-light)',
        borderRadius: '12px',
        padding: '12px',
      }}
    >
      <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--pao-anthracite)' }}>
        Integrante {draft.memberNumber}
      </div>

      <div style={{ display: 'flex', gap: '8px', marginTop: '6px' }}>
        <button
          type="button"
          onClick={() => onClassificationChange('RECORRENTE')}
          style={{
            flex: 1,
            padding: '8px',
            borderRadius: '8px',
            border: 'none',
            backgroundColor: isRecorrente ? 'var(--pao-anthracite)' : 'var(--pao-cream-light)',
            color: isRecorrente ? '#FFFFFF' : 'var(--pao-anthracite)',
            fontSize: '12px',
            fontWeight: 600,
          }}
        >
          Recorrente
        </button>

        <button
          type="button"
          onClick={() => onClassificationChange('NOVO')}
          style={{
            flex: 1,
            padding: '8px',
            borderRadius: '8px',
            border: 'none',
            backgroundColor: isNovo ? 'var(--pao-red)' : 'var(--pao-cream-light)',
            color: isNovo ? '#FFFFFF' : 'var(--pao-anthracite)',
            fontSize: '12px',
            fontWeight: 600,
          }}
        >
          Novo Cliente
        </button>
      </div>

      {isNovo && (
        <div style={{ marginTop: '10px' }}>
          <div style={{ fontSize: '11px', fontWeight: 600, color: 'var(--pao-anthracite)' }}>
            Origem:
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginTop: '4px' }}>
            {ClientOrigins.map((origin) => {
              const selected = draft.origin === origin;
              return (
                <button
                  key={origin}
                  type="button"
                  onClick={() => onOriginChange(origin)}
                  style={{
                    borderRadius: '14px',
                    padding: '4px 8px',
                    backgroundColor: selected
                      ? 'rgba(156, 24, 25, 0.15)'
                      : 'var(--pao-cream-light)',
                    border: `1px solid ${selected ? 'var(--pao-red)' : 'var(--pao-border-light)'}`,
                    fontSize: '11px',
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
