import type {
  Attendance,
  AttendanceAudit,
  AttendanceMember,
  Hoster,
  QueueEvent,
  Shift,
  SyncQueueItem,
} from '../../domain/models';

const DB_NAME = 'pao_com_carne_db';
const DB_VERSION = 1;

export type StoreName =
  | 'hosters'
  | 'shifts'
  | 'attendances'
  | 'attendance_members'
  | 'attendance_audit'
  | 'queue_events'
  | 'sync_queue';

type StoreMap = {
  hosters: Hoster;
  shifts: Shift;
  attendances: Attendance;
  attendance_members: AttendanceMember;
  attendance_audit: AttendanceAudit;
  queue_events: QueueEvent;
  sync_queue: SyncQueueItem;
};

type Listener = () => void;

class LocalDatabase {
  private dbPromise: Promise<IDBDatabase | null> | null = null;
  private memory: { [K in StoreName]: Map<string | number, StoreMap[K]> } = {
    hosters: new Map(),
    shifts: new Map(),
    attendances: new Map(),
    attendance_members: new Map(),
    attendance_audit: new Map(),
    queue_events: new Map(),
    sync_queue: new Map(),
  };
  private nextSyncId = 1;
  private listeners = new Set<Listener>();

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify(): void {
    this.listeners.forEach((fn) => {
      try {
        fn();
      } catch {
        // ignore subscriber errors
      }
    });
  }

  private async openDb(): Promise<IDBDatabase | null> {
    if (typeof indexedDB === 'undefined') return null;
    if (!this.dbPromise) {
      this.dbPromise = new Promise((resolve) => {
        try {
          const req = indexedDB.open(DB_NAME, DB_VERSION);
          req.onupgradeneeded = () => {
            const db = req.result;
            const stores: { name: StoreName; keyPath: string; autoIncrement?: boolean }[] = [
              { name: 'hosters', keyPath: 'id' },
              { name: 'shifts', keyPath: 'id' },
              { name: 'attendances', keyPath: 'id' },
              { name: 'attendance_members', keyPath: 'id' },
              { name: 'attendance_audit', keyPath: 'id' },
              { name: 'queue_events', keyPath: 'id' },
              { name: 'sync_queue', keyPath: 'id', autoIncrement: true },
            ];
            for (const s of stores) {
              if (!db.objectStoreNames.contains(s.name)) {
                db.createObjectStore(s.name, {
                  keyPath: s.keyPath,
                  autoIncrement: Boolean(s.autoIncrement),
                });
              }
            }
          };
          req.onsuccess = () => resolve(req.result);
          req.onerror = () => resolve(null);
        } catch {
          resolve(null);
        }
      });
    }
    return this.dbPromise;
  }

  private async getAllFromStore<K extends StoreName>(store: K): Promise<StoreMap[K][]> {
    const db = await this.openDb();
    if (!db) {
      return Array.from(this.memory[store].values());
    }
    return new Promise((resolve, reject) => {
      const tx = db.transaction(store, 'readonly');
      const os = tx.objectStore(store);
      const req = os.getAll();
      req.onsuccess = () => resolve((req.result as StoreMap[K][]) || []);
      req.onerror = () => reject(req.error);
    });
  }

  private async getByIdFromStore<K extends StoreName>(
    store: K,
    id: string | number
  ): Promise<StoreMap[K] | null> {
    const db = await this.openDb();
    if (!db) {
      return this.memory[store].get(id) ?? null;
    }
    return new Promise((resolve, reject) => {
      const tx = db.transaction(store, 'readonly');
      const os = tx.objectStore(store);
      const req = os.get(id);
      req.onsuccess = () => resolve((req.result as StoreMap[K]) ?? null);
      req.onerror = () => reject(req.error);
    });
  }

  private async putManyInStore<K extends StoreName>(
    store: K,
    items: StoreMap[K][],
    notifyChange = true
  ): Promise<void> {
    if (items.length === 0) return;
    const db = await this.openDb();
    if (!db) {
      for (const item of items) {
        const key = (item as { id: string | number }).id;
        this.memory[store].set(key, item);
      }
      if (notifyChange) this.notify();
      return;
    }
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(store, 'readwrite');
      const os = tx.objectStore(store);
      for (const item of items) {
        os.put(item);
      }
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
    if (notifyChange) this.notify();
  }

  private async deleteFromStore<K extends StoreName>(
    store: K,
    id: string | number,
    notifyChange = true
  ): Promise<void> {
    const db = await this.openDb();
    if (!db) {
      this.memory[store].delete(id);
      if (notifyChange) this.notify();
      return;
    }
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(store, 'readwrite');
      tx.objectStore(store).delete(id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
    if (notifyChange) this.notify();
  }

  // --- Hosters ---
  async getAllActiveHosters(): Promise<Hoster[]> {
    const all = await this.getAllFromStore('hosters');
    return all
      .filter((h) => h.active)
      .sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'));
  }

  async getHosterById(id: string): Promise<Hoster | null> {
    return this.getByIdFromStore('hosters', id);
  }

  async insertOrUpdateHoster(hoster: Hoster): Promise<void> {
    await this.putManyInStore('hosters', [hoster]);
  }

  async insertOrUpdateHosters(hosters: Hoster[]): Promise<void> {
    await this.putManyInStore('hosters', hosters);
  }

  async deleteHosterById(id: string): Promise<void> {
    await this.deleteFromStore('hosters', id);
  }

  // --- Shifts ---
  async getAllShifts(): Promise<Shift[]> {
    const all = await this.getAllFromStore('shifts');
    return all.sort((a, b) => b.openedAt - a.openedAt);
  }

  async getShiftById(id: string): Promise<Shift | null> {
    return this.getByIdFromStore('shifts', id);
  }

  async getActiveShiftForHoster(hosterId: string): Promise<Shift | null> {
    const all = await this.getAllFromStore('shifts');
    const matches = all
      .filter((s) => s.hosterId === hosterId && s.status === 'OPEN')
      .sort((a, b) => b.openedAt - a.openedAt);
    return matches[0] ?? null;
  }

  async getOpenShifts(): Promise<Shift[]> {
    const all = await this.getAllFromStore('shifts');
    return all.filter((s) => s.status === 'OPEN').sort((a, b) => b.openedAt - a.openedAt);
  }

  async getOpenShiftsInRange(startMillis: number, endMillis: number): Promise<Shift[]> {
    const open = await this.getOpenShifts();
    return open.filter((s) => s.openedAt >= startMillis && s.openedAt <= endMillis);
  }

  async insertOrUpdateShift(shift: Shift): Promise<void> {
    await this.putManyInStore('shifts', [shift]);
  }

  async insertOrUpdateShifts(shifts: Shift[]): Promise<void> {
    await this.putManyInStore('shifts', shifts);
  }

  // --- Attendances ---
  async getAllAttendances(): Promise<Attendance[]> {
    const all = await this.getAllFromStore('attendances');
    return all.sort((a, b) => b.createdAt - a.createdAt);
  }

  async getAttendanceById(id: string): Promise<Attendance | null> {
    return this.getByIdFromStore('attendances', id);
  }

  async getAttendancesForShift(shiftId: string): Promise<Attendance[]> {
    const all = await this.getAllFromStore('attendances');
    return all.filter((a) => a.shiftId === shiftId).sort((a, b) => a.createdAt - b.createdAt);
  }

  async getLastAttendanceForShift(shiftId: string): Promise<Attendance | null> {
    const list = await this.getAttendancesForShift(shiftId);
    return list.length > 0 ? list[list.length - 1] : null;
  }

  async insertOrUpdateAttendance(att: Attendance): Promise<void> {
    await this.putManyInStore('attendances', [att]);
  }

  async insertOrUpdateAttendances(atts: Attendance[]): Promise<void> {
    await this.putManyInStore('attendances', atts);
  }

  // --- Attendance Members ---
  async getAllMembers(): Promise<AttendanceMember[]> {
    return this.getAllFromStore('attendance_members');
  }

  async getMembersForAttendance(attendanceId: string): Promise<AttendanceMember[]> {
    const all = await this.getAllFromStore('attendance_members');
    return all
      .filter((m) => m.attendanceId === attendanceId)
      .sort((a, b) => a.memberNumber - b.memberNumber);
  }

  async insertMembers(members: AttendanceMember[]): Promise<void> {
    await this.putManyInStore('attendance_members', members);
  }

  async deleteMembersForAttendance(attendanceId: string): Promise<void> {
    const existing = await this.getMembersForAttendance(attendanceId);
    if (existing.length === 0) return;
    const db = await this.openDb();
    if (!db) {
      for (const m of existing) {
        this.memory.attendance_members.delete(m.id);
      }
      this.notify();
      return;
    }
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction('attendance_members', 'readwrite');
      const os = tx.objectStore('attendance_members');
      for (const m of existing) {
        os.delete(m.id);
      }
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
    this.notify();
  }

  // --- Attendance Audit ---
  async insertAudit(audit: AttendanceAudit): Promise<void> {
    await this.putManyInStore('attendance_audit', [audit]);
  }

  // --- Queue Events ---
  async getLatestQueueEventForShift(shiftId: string): Promise<QueueEvent | null> {
    const all = await this.getAllFromStore('queue_events');
    const filtered = all
      .filter((q) => q.shiftId === shiftId)
      .sort((a, b) => b.createdAt - a.createdAt);
    return filtered[0] ?? null;
  }

  async insertQueueEvent(event: QueueEvent): Promise<void> {
    await this.putManyInStore('queue_events', [event]);
  }

  async insertQueueEvents(events: QueueEvent[]): Promise<void> {
    await this.putManyInStore('queue_events', events);
  }

  // --- Sync Queue ---
  async getAllSyncQueue(): Promise<SyncQueueItem[]> {
    const all = await this.getAllFromStore('sync_queue');
    return all.sort((a, b) => a.createdAt - b.createdAt || a.id - b.id);
  }

  async getPendingOperations(): Promise<SyncQueueItem[]> {
    const all = await this.getAllSyncQueue();
    return all.filter((item) => item.status === 'PENDING' || item.status === 'SYNCING');
  }

  async getPendingCount(): Promise<number> {
    const all = await this.getAllSyncQueue();
    return all.length;
  }

  async enqueueSyncOperation(
    op: Omit<SyncQueueItem, 'id' | 'attempts' | 'lastError' | 'createdAt' | 'updatedAt'>
  ): Promise<void> {
    const now = Date.now();
    const record: Omit<SyncQueueItem, 'id'> = {
      ...op,
      attempts: 0,
      lastError: null,
      createdAt: now,
      updatedAt: now,
    };

    const db = await this.openDb();
    if (!db) {
      const id = this.nextSyncId++;
      this.memory.sync_queue.set(id, { ...record, id });
      this.notify();
      return;
    }
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction('sync_queue', 'readwrite');
      tx.objectStore('sync_queue').add(record);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
    this.notify();
  }

  async updateSyncOperation(item: SyncQueueItem): Promise<void> {
    await this.putManyInStore('sync_queue', [item]);
  }

  async deleteSyncOperation(id: number): Promise<void> {
    await this.deleteFromStore('sync_queue', id);
  }

  async resetFailedToPending(): Promise<void> {
    const all = await this.getAllSyncQueue();
    const failed = all.filter((i) => i.status === 'FAILED');
    if (failed.length === 0) return;
    const now = Date.now();
    await this.putManyInStore(
      'sync_queue',
      failed.map((f) => ({ ...f, status: 'PENDING', updatedAt: now }))
    );
  }

  /** Helper for unit tests to isolate clean in-memory state */
  resetMemory(): void {
    (Object.keys(this.memory) as StoreName[]).forEach((k) => {
      this.memory[k].clear();
    });
    this.nextSyncId = 1;
    this.notify();
  }
}

export const localDb = new LocalDatabase();
