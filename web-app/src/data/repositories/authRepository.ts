import { localDb } from '../local/database';
import { ADMIN_VALID_HASH, ADMIN_VALID_SALT, syncManager } from '../sync/syncManager';
import { systemLogRepository } from './systemLogRepository';
import { SystemLogActions, type Hoster } from '../../domain/models';
import { generateSalt, hashPassword, verifyPassword } from '../../domain/utils/security';

const SESSION_KEY_ID = 'pao_current_hoster_id';
const SESSION_KEY_NAME = 'pao_current_hoster_name';
const SESSION_KEY_IS_ADMIN = 'pao_current_hoster_is_admin';

export type ActionResult<T> =
  | { ok: true; value: T }
  | { ok: false; error: string };

class AuthRepository {
  private currentHosterValue: Hoster | null = null;
  private listeners = new Set<(hoster: Hoster | null) => void>();

  constructor() {
    this.restoreSession();
  }

  subscribe(listener: (hoster: Hoster | null) => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  getCurrentHoster(): Hoster | null {
    return this.currentHosterValue;
  }

  private setSession(hoster: Hoster | null): void {
    this.currentHosterValue = hoster;
    if (typeof localStorage !== 'undefined') {
      if (hoster) {
        localStorage.setItem(SESSION_KEY_ID, hoster.id);
        localStorage.setItem(SESSION_KEY_NAME, hoster.name);
        localStorage.setItem(SESSION_KEY_IS_ADMIN, String(hoster.isAdmin));
      } else {
        localStorage.removeItem(SESSION_KEY_ID);
        localStorage.removeItem(SESSION_KEY_NAME);
        localStorage.removeItem(SESSION_KEY_IS_ADMIN);
      }
    }
    this.listeners.forEach((fn) => fn(hoster));
  }

  private restoreSession(): void {
    if (typeof localStorage === 'undefined') return;
    const savedId = localStorage.getItem(SESSION_KEY_ID);
    const savedName = localStorage.getItem(SESSION_KEY_NAME);
    if (savedId && savedName) {
      const savedIsAdmin = localStorage.getItem(SESSION_KEY_IS_ADMIN) === 'true';
      this.currentHosterValue = {
        id: savedId,
        name: savedName,
        pinHash: '',
        pinSalt: '',
        isActivated: true,
        active: true,
        isAdmin: savedIsAdmin,
        createdAt: Date.now(),
      };
    }
  }

  async getActiveHosters(): Promise<Hoster[]> {
    return localDb.getAllActiveHosters();
  }

  async createHoster(name: string, pin?: string | null): Promise<ActionResult<Hoster>> {
    const cleanName = name.trim();
    if (!cleanName) return { ok: false, error: 'O nome é obrigatório.' };

    const id = `h-${crypto.randomUUID().slice(0, 8)}`;
    let salt = '';
    let hash = '';
    let isActivated = false;

    if (pin && pin.trim().length > 0) {
      salt = generateSalt();
      hash = hashPassword(pin, salt);
      isActivated = true;
    }

    const entity: Hoster = {
      id,
      name: cleanName,
      authUserId: null,
      pinHash: hash,
      pinSalt: salt,
      isActivated,
      active: true,
      isAdmin: false,
      createdAt: Date.now(),
    };

    await localDb.insertOrUpdateHoster(entity);

    await localDb.enqueueSyncOperation({
      entityType: 'HOSTER_INSERT',
      entityId: id,
      operation: 'INSERT',
      payloadJson: JSON.stringify({
        id: entity.id,
        name: entity.name,
        auth_user_id: null,
        pin_hash: hash || null,
        pin_salt: salt || null,
        is_activated: isActivated,
        active: true,
        is_admin: false,
      }),
      status: 'PENDING',
    });

    void syncManager.triggerSync();
    return { ok: true, value: entity };
  }

  async updateHosterName(id: string, newName: string): Promise<ActionResult<void>> {
    const existing = await localDb.getHosterById(id);
    if (!existing) return { ok: false, error: 'Hoster não encontrado' };

    const cleanName = newName.trim();
    await localDb.insertOrUpdateHoster({ ...existing, name: cleanName });

    await localDb.enqueueSyncOperation({
      entityType: 'HOSTER_UPDATE',
      entityId: id,
      operation: 'UPDATE',
      payloadJson: JSON.stringify({ name: cleanName }),
      status: 'PENDING',
    });

    void syncManager.triggerSync();
    return { ok: true, value: undefined };
  }

  async updateHosterPin(id: string, newPin: string): Promise<ActionResult<void>> {
    const existing = await localDb.getHosterById(id);
    if (!existing) return { ok: false, error: 'Hoster não encontrado' };

    const salt = generateSalt();
    const hash = hashPassword(newPin, salt);

    await localDb.insertOrUpdateHoster({
      ...existing,
      pinSalt: salt,
      pinHash: hash,
      isActivated: true,
    });

    await localDb.enqueueSyncOperation({
      entityType: 'HOSTER_UPDATE',
      entityId: id,
      operation: 'UPDATE',
      payloadJson: JSON.stringify({
        pin_salt: salt,
        pin_hash: hash,
        is_activated: true,
      }),
      status: 'PENDING',
    });

    void syncManager.triggerSync();
    return { ok: true, value: undefined };
  }

  async deleteHoster(id: string): Promise<ActionResult<void>> {
    if (id === 'admin') {
      return { ok: false, error: 'Não é permitido excluir o usuário Administrador.' };
    }
    await localDb.deleteHosterById(id);

    await localDb.enqueueSyncOperation({
      entityType: 'HOSTER_DELETE',
      entityId: id,
      operation: 'DELETE',
      payloadJson: '{}',
      status: 'PENDING',
    });

    void syncManager.triggerSync();
    return { ok: true, value: undefined };
  }

  async activateHoster(hosterId: string, pin: string): Promise<ActionResult<Hoster>> {
    const entity = await localDb.getHosterById(hosterId);
    if (!entity) return { ok: false, error: 'Hoster não encontrado no banco.' };

    const salt = generateSalt();
    const hash = hashPassword(pin, salt);

    const updated: Hoster = {
      ...entity,
      pinSalt: salt,
      pinHash: hash,
      isActivated: true,
    };
    await localDb.insertOrUpdateHoster(updated);

    await localDb.enqueueSyncOperation({
      entityType: 'HOSTER_ACTIVATION',
      entityId: hosterId,
      operation: 'UPDATE',
      payloadJson: JSON.stringify({
        pin_salt: salt,
        pin_hash: hash,
        is_activated: true,
      }),
      status: 'PENDING',
    });

    this.setSession(updated);
    systemLogRepository.logAsync({
      action: SystemLogActions.FIRST_ACCESS_ACTIVATION,
      entityType: 'HOSTER',
      entityId: updated.id,
      userId: updated.id,
      userName: updated.name,
      result: 'SUCCESS',
      description: `Primeiro acesso ativado por ${updated.name}.`,
    });
    void syncManager.triggerSync();
    return { ok: true, value: updated };
  }

  async authenticate(hosterId: string, pin: string): Promise<ActionResult<Hoster>> {
    const entity = await localDb.getHosterById(hosterId);

    if (hosterId === 'admin' || entity?.isAdmin) {
      const isDirectAdminMatch = pin === 'admin';
      const isHashMatch =
        entity !== null &&
        entity.pinSalt.trim().length > 0 &&
        verifyPassword(pin, entity.pinSalt, entity.pinHash);

      if (isDirectAdminMatch || isHashMatch) {
        const adminHoster: Hoster = {
          id: 'admin',
          name: 'Administrador',
          authUserId: null,
          pinHash: ADMIN_VALID_HASH,
          pinSalt: ADMIN_VALID_SALT,
          isActivated: true,
          active: true,
          isAdmin: true,
          createdAt: entity?.createdAt ?? Date.now(),
        };
        await localDb.insertOrUpdateHoster(adminHoster);
        this.setSession(adminHoster);
        systemLogRepository.logAsync({
          action: SystemLogActions.LOGIN_SUCCESS,
          entityType: 'HOSTER',
          entityId: adminHoster.id,
          userId: adminHoster.id,
          userName: adminHoster.name,
          result: 'SUCCESS',
          description: 'Login realizado como Administrador.',
        });
        return { ok: true, value: adminHoster };
      }

      systemLogRepository.logAsync({
        action: SystemLogActions.LOGIN_FAILURE,
        entityType: 'HOSTER',
        entityId: hosterId,
        userId: hosterId,
        userName: entity?.name ?? 'Administrador',
        result: 'FAILURE',
        description: 'Tentativa de login com senha incorreta para Administrador.',
      });
      return { ok: false, error: 'Senha incorreta.' };
    }

    if (!entity) {
      systemLogRepository.logAsync({
        action: SystemLogActions.LOGIN_FAILURE,
        entityType: 'HOSTER',
        entityId: hosterId,
        result: 'FAILURE',
        description: 'Tentativa de login com hoster inexistente.',
      });
      return { ok: false, error: 'Hoster não encontrado.' };
    }
    if (!entity.isActivated) {
      return { ok: false, error: 'Primeiro acesso pendente.' };
    }

    const valid = verifyPassword(pin, entity.pinSalt, entity.pinHash);
    if (!valid) {
      systemLogRepository.logAsync({
        action: SystemLogActions.LOGIN_FAILURE,
        entityType: 'HOSTER',
        entityId: entity.id,
        userId: entity.id,
        userName: entity.name,
        result: 'FAILURE',
        description: `Senha incorreta informada para ${entity.name}.`,
      });
      return { ok: false, error: 'Senha incorreta.' };
    }

    this.setSession(entity);
    systemLogRepository.logAsync({
      action: SystemLogActions.LOGIN_SUCCESS,
      entityType: 'HOSTER',
      entityId: entity.id,
      userId: entity.id,
      userName: entity.name,
      result: 'SUCCESS',
      description: `Login realizado por ${entity.name}.`,
    });
    return { ok: true, value: entity };
  }

  logout(): void {
    const prev = this.currentHosterValue;
    if (prev) {
      systemLogRepository.logAsync({
        action: SystemLogActions.LOGOUT,
        entityType: 'HOSTER',
        entityId: prev.id,
        userId: prev.id,
        userName: prev.name,
        result: 'SUCCESS',
        description: `Logout realizado por ${prev.name}.`,
      });
    }
    this.setSession(null);
  }
}

export const authRepository = new AuthRepository();
