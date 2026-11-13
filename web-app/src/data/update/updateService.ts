import { localDb } from '../local/database';
import { buildApkPublicUrl, supabaseClient, type SupabaseAppReleaseDto } from '../remote/supabaseClient';
import { systemLogRepository } from '../repositories/systemLogRepository';
import { APP_VERSION_CODE, APP_VERSION_NAME } from '../../domain/appVersion';
import { SystemLogActions, type AppReleasePolicy } from '../../domain/models';
import { fromIsoUtc } from '../../domain/utils/dateTime';

const STORAGE_POLICY_KEY = 'pao_last_release_policy';

export type UpdateStatus =
  | 'CHECKING'
  | 'UP_TO_DATE'
  | 'OPTIONAL_AVAILABLE'
  | 'BLOCKED'
  | 'UPDATING'
  | 'ERROR';

export interface UpdateUiState {
  status: UpdateStatus;
  installedVersionName: string;
  installedVersionCode: number;
  policy: AppReleasePolicy | null;
  isBlocked: boolean;
  isOptionalBannerDismissed: boolean;
  progress: number;
  statusMessage: string | null;
  errorMessage: string | null;
}

export function evaluateVersionPolicy(
  installedCode: number,
  policy: AppReleasePolicy | null
): { status: UpdateStatus; isBlocked: boolean } {
  if (!policy || !policy.active) {
    return { status: 'UP_TO_DATE', isBlocked: false };
  }
  if (installedCode < policy.minSupportedCode) {
    return { status: 'BLOCKED', isBlocked: true };
  }
  if (policy.mandatory && installedCode < policy.versionCode) {
    return { status: 'BLOCKED', isBlocked: true };
  }
  if (installedCode < policy.versionCode) {
    return { status: 'OPTIONAL_AVAILABLE', isBlocked: false };
  }
  return { status: 'UP_TO_DATE', isBlocked: false };
}

function dtoToPolicy(dto: SupabaseAppReleaseDto, now: number): AppReleasePolicy {
  return {
    id: dto.id,
    versionName: dto.version_name,
    versionCode: dto.version_code,
    minSupportedCode: dto.min_supported_code,
    mandatory: dto.mandatory,
    apkPath: dto.apk_path,
    sha256: dto.sha256,
    changelog: dto.changelog,
    releaseReason:
      dto.release_reason?.trim() ||
      (dto.mandatory
        ? 'Esta versão anterior não é mais compatível com o banco de dados atual.'
        : 'Nova atualização com melhorias disponível.'),
    active: dto.active,
    createdAt: fromIsoUtc(dto.created_at),
    confirmedAt: now,
  };
}

function loadCachedPolicyFromStorage(): AppReleasePolicy | null {
  if (typeof localStorage === 'undefined') return null;
  try {
    const raw = localStorage.getItem(STORAGE_POLICY_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as AppReleasePolicy;
    if (typeof parsed.versionCode === 'number' && typeof parsed.minSupportedCode === 'number') {
      return parsed;
    }
    return null;
  } catch {
    return null;
  }
}

function saveCachedPolicyToStorage(policy: AppReleasePolicy): void {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(STORAGE_POLICY_KEY, JSON.stringify(policy));
  } catch {
    // ignore storage quota errors
  }
}

class UpdateService {
  private state: UpdateUiState;
  private listeners = new Set<() => void>();
  private lastLoggedBlockedCode: number | null = null;
  private lastLoggedAvailableCode: number | null = null;

  constructor() {
    const cached = loadCachedPolicyFromStorage();
    const evalRes = evaluateVersionPolicy(APP_VERSION_CODE, cached);
    this.state = {
      status: evalRes.status,
      installedVersionName: APP_VERSION_NAME,
      installedVersionCode: APP_VERSION_CODE,
      policy: cached,
      isBlocked: evalRes.isBlocked,
      isOptionalBannerDismissed: false,
      progress: 0,
      statusMessage: null,
      errorMessage: null,
    };
  }

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  getState(): UpdateUiState {
    return this.state;
  }

  private emit(partial: Partial<UpdateUiState>): void {
    this.state = { ...this.state, ...partial };
    this.listeners.forEach((fn) => fn());
  }

  async checkVersion(
    isOnline: boolean,
    actorId?: string | null,
    actorName?: string | null
  ): Promise<void> {
    let policy = loadCachedPolicyFromStorage() ?? (await localDb.getLatestAppRelease());

    if (isOnline) {
      try {
        const res = await supabaseClient.getActiveAppReleases();
        if (res.ok && res.data && res.data.length > 0) {
          policy = dtoToPolicy(res.data[0], Date.now());
          saveCachedPolicyToStorage(policy);
          await localDb.upsertAppRelease(policy);
        }
      } catch {
        // Fallback to cached local policy when offline or server unreachable
      }
    }

    const evalRes = evaluateVersionPolicy(APP_VERSION_CODE, policy);
    this.emit({
      status: evalRes.status,
      isBlocked: evalRes.isBlocked,
      policy,
      errorMessage: null,
    });

    if (evalRes.isBlocked && policy && this.lastLoggedBlockedCode !== policy.versionCode) {
      this.lastLoggedBlockedCode = policy.versionCode;
      systemLogRepository.logAsync({
        action: SystemLogActions.MANDATORY_UPDATE_DETECTED,
        entityType: 'APP_RELEASE',
        entityId: policy.id,
        result: 'SUCCESS',
        description: `Versão web ${APP_VERSION_NAME} (cód. ${APP_VERSION_CODE}) bloqueada; mínima exigida cód. ${policy.minSupportedCode}.`,
        userId: actorId ?? null,
        userName: actorName ?? null,
        triggerSync: false,
      });
    } else if (
      evalRes.status === 'OPTIONAL_AVAILABLE' &&
      policy &&
      this.lastLoggedAvailableCode !== policy.versionCode
    ) {
      this.lastLoggedAvailableCode = policy.versionCode;
      systemLogRepository.logAsync({
        action: SystemLogActions.UPDATE_AVAILABLE,
        entityType: 'APP_RELEASE',
        entityId: policy.id,
        result: 'SUCCESS',
        description: `Nova versão disponível: v${policy.versionName} (cód. ${policy.versionCode}).`,
        userId: actorId ?? null,
        userName: actorName ?? null,
        triggerSync: false,
      });
    } else if (policy && isOnline && evalRes.status === 'UP_TO_DATE') {
      systemLogRepository.logAsync({
        action: SystemLogActions.VERSION_CHECK,
        entityType: 'APP_RELEASE',
        entityId: policy.id,
        result: 'SUCCESS',
        description: `Versão compatível confirmada (v${APP_VERSION_NAME}, cód. ${APP_VERSION_CODE}).`,
        userId: actorId ?? null,
        userName: actorName ?? null,
        triggerSync: false,
      });
    }
  }

  dismissOptionalBanner(): void {
    this.emit({ isOptionalBannerDismissed: true });
  }

  getApkDownloadUrl(): string | null {
    const path = this.state.policy?.apkPath;
    if (!path || !path.trim()) return null;
    return buildApkPublicUrl(path);
  }

  async applyWebUpdate(actorId?: string | null, actorName?: string | null): Promise<void> {
    this.emit({
      status: 'UPDATING',
      progress: 0.25,
      statusMessage: 'Limpando cache de aplicação e preservando registros locais...',
      errorMessage: null,
    });

    systemLogRepository.logAsync({
      action: SystemLogActions.UPDATE_DOWNLOAD_STARTED,
      entityType: 'APP_RELEASE',
      entityId: this.state.policy?.id ?? null,
      result: 'SUCCESS',
      description: `Atualização Web solicitada para v${this.state.policy?.versionName ?? 'nova'}.`,
      userId: actorId ?? null,
      userName: actorName ?? null,
      triggerSync: false,
    });

    try {
      // Clear browser CacheStorage (never IndexedDB or localStorage!)
      if (typeof caches !== 'undefined') {
        const keys = await caches.keys();
        await Promise.all(keys.map((k) => caches.delete(k)));
      }
      this.emit({ progress: 0.65, statusMessage: 'Verificando Service Worker...' });

      if (typeof navigator !== 'undefined' && 'serviceWorker' in navigator) {
        const regs = await navigator.serviceWorker.getRegistrations();
        await Promise.all(regs.map((r) => r.update()));
      }
      this.emit({ progress: 1.0, statusMessage: 'Recarregando aplicação atualizada...' });

      systemLogRepository.logAsync({
        action: SystemLogActions.UPDATE_INSTALL_REQUESTED,
        entityType: 'APP_RELEASE',
        entityId: this.state.policy?.id ?? null,
        result: 'SUCCESS',
        description: 'Recarregamento para nova versão Web executado sem perda de dados locais.',
        userId: actorId ?? null,
        userName: actorName ?? null,
        triggerSync: false,
      });

      if (typeof window !== 'undefined') {
        const url = new URL(window.location.href);
        url.searchParams.set('_v', String(Date.now()));
        window.location.replace(url.toString());
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Falha ao atualizar cache web.';
      this.emit({
        status: this.state.isBlocked ? 'BLOCKED' : 'ERROR',
        errorMessage: msg,
        progress: 0,
        statusMessage: null,
      });
      systemLogRepository.logAsync({
        action: SystemLogActions.UPDATE_DOWNLOAD_FAILED,
        entityType: 'APP_RELEASE',
        entityId: this.state.policy?.id ?? null,
        result: 'FAILURE',
        description: `Erro na atualização web: ${msg}`,
        userId: actorId ?? null,
        userName: actorName ?? null,
        triggerSync: false,
      });
    }
  }
}

export const updateService = new UpdateService();
