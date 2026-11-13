import { localDb } from '../local/database';
import { deviceIdentity } from '../local/deviceIdentity';
import type { LogResult, SystemLog } from '../../domain/models';
import { toIsoUtc } from '../../domain/utils/dateTime';

const SENSITIVE_KV_PATTERN =
  /(pin_hash|pinHash|pin_salt|pinSalt|password|senha|token|authorization|apikey|anon_key)\s*[:=]\s*[^\s,;]+/gi;
const BEARER_PATTERN = /Bearer\s+[A-Za-z0-9._-]+/gi;

function genLogId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `log-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
}

export function sanitizeLogDescription(raw: string): string {
  const clean = raw
    .trim()
    .replace(SENSITIVE_KV_PATTERN, '$1=[REDACTED]')
    .replace(BEARER_PATTERN, 'Bearer [REDACTED]');
  return clean.slice(0, 500);
}

class SystemLogRepository {
  private syncTriggerFn: (() => void) | null = null;

  setSyncTrigger(fn: () => void): void {
    this.syncTriggerFn = fn;
  }

  async log(params: {
    action: string;
    entityType: string;
    result: LogResult;
    description: string;
    entityId?: string | null;
    userId?: string | null;
    userName?: string | null;
    triggerSync?: boolean;
  }): Promise<SystemLog> {
    const {
      action,
      entityType,
      result,
      description,
      entityId = null,
      userId = null,
      userName = null,
      triggerSync = true,
    } = params;

    const id = genLogId();
    const now = Date.now();
    const deviceName = deviceIdentity.getDeviceDisplayName();
    const safeDescription = sanitizeLogDescription(description);

    const entry: SystemLog = {
      id,
      userId,
      userName,
      action,
      entityType,
      entityId,
      deviceName,
      result,
      description: safeDescription,
      createdAt: now,
      syncedAt: null,
    };

    await localDb.insertSystemLog(entry);

    await localDb.enqueueSyncOperation({
      entityType: 'SYSTEM_LOG',
      entityId: id,
      operation: 'INSERT',
      payloadJson: JSON.stringify({
        id,
        user_id: userId,
        user_name: userName,
        action,
        entity_type: entityType,
        entity_id: entityId,
        device_name: deviceName,
        result,
        description: safeDescription,
        created_at: toIsoUtc(now),
      }),
      status: 'PENDING',
    });

    if (triggerSync && this.syncTriggerFn) {
      this.syncTriggerFn();
    }

    return entry;
  }

  logAsync(params: Parameters<SystemLogRepository['log']>[0]): void {
    void this.log(params);
  }
}

export const systemLogRepository = new SystemLogRepository();
