import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { localDb } from '../data/local/database';
import { deviceIdentity } from '../data/local/deviceIdentity';
import { authRepository } from '../data/repositories/authRepository';
import { receptionRepository } from '../data/repositories/receptionRepository';
import { syncManager, type SyncSnapshot } from '../data/sync/syncManager';
import { updateService, type UpdateUiState } from '../data/update/updateService';
import type {
  Attendance,
  AttendanceMember,
  AttendanceWithMembers,
  Hoster,
  QueueEvent,
  Shift,
  SystemLog,
} from '../domain/models';

interface AppContextValue {
  ready: boolean;
  currentHoster: Hoster | null;
  hosters: Hoster[];
  currentShift: Shift | null;
  latestQueue: QueueEvent | null;
  shiftAttendances: AttendanceWithMembers[];
  allShifts: Shift[];
  allAttendances: Attendance[];
  allMembers: AttendanceMember[];
  systemLogs: SystemLog[];
  sync: SyncSnapshot;
  updateState: UpdateUiState;
  deviceName: string;
  setDeviceDisplayName: (name: string) => void;
  recheckVersion: () => Promise<void>;
  applyWebUpdate: () => Promise<void>;
  dismissOptionalUpdateBanner: () => void;
  showSyncLogsModal: boolean;
  setShowSyncLogsModal: (show: boolean) => void;
  snackbarMessage: string | null;
  showSnackbar: (msg: string) => void;
  clearSnackbar: () => void;
  reloadLocalState: () => Promise<void>;
}

export const WEB_MAINTENANCE_MODE = true;

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(WEB_MAINTENANCE_MODE);
  const [currentHoster, setCurrentHoster] = useState<Hoster | null>(() =>
    WEB_MAINTENANCE_MODE ? null : authRepository.getCurrentHoster()
  );
  const [hosters, setHosters] = useState<Hoster[]>([]);
  const [currentShift, setCurrentShift] = useState<Shift | null>(null);
  const [latestQueue, setLatestQueue] = useState<QueueEvent | null>(null);
  const [shiftAttendances, setShiftAttendances] = useState<AttendanceWithMembers[]>([]);
  const [allShifts, setAllShifts] = useState<Shift[]>([]);
  const [allAttendances, setAllAttendances] = useState<Attendance[]>([]);
  const [allMembers, setAllMembers] = useState<AttendanceMember[]>([]);
  const [systemLogs, setSystemLogs] = useState<SystemLog[]>([]);
  const [sync, setSync] = useState<SyncSnapshot>(() => syncManager.getSnapshot());
  const [updateState, setUpdateState] = useState<UpdateUiState>(() => updateService.getState());
  const [deviceName, setDeviceName] = useState<string>(() =>
    deviceIdentity.getDeviceDisplayName()
  );
  const [showSyncLogsModal, setShowSyncLogsModal] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState<string | null>(null);

  const showSnackbar = useCallback((msg: string) => {
    setSnackbarMessage(msg);
  }, []);

  const clearSnackbar = useCallback(() => {
    setSnackbarMessage(null);
  }, []);

  const setDeviceDisplayName = useCallback((name: string) => {
    deviceIdentity.setFriendlyName(name);
    setDeviceName(deviceIdentity.getDeviceDisplayName());
  }, []);

  const recheckVersion = useCallback(async () => {
    if (WEB_MAINTENANCE_MODE) return;
    const online = typeof navigator !== 'undefined' ? navigator.onLine : syncManager.getSnapshot().isOnline;
    const h = authRepository.getCurrentHoster();
    await updateService.checkVersion(online, h?.id ?? null, h?.name ?? null);
  }, []);

  const applyWebUpdate = useCallback(async () => {
    if (WEB_MAINTENANCE_MODE) return;
    const h = authRepository.getCurrentHoster();
    await updateService.applyWebUpdate(h?.id ?? null, h?.name ?? null);
  }, []);

  const dismissOptionalUpdateBanner = useCallback(() => {
    updateService.dismissOptionalBanner();
  }, []);

  useEffect(() => {
    if (!snackbarMessage) return;
    const t = setTimeout(() => setSnackbarMessage(null), 5000);
    return () => clearTimeout(t);
  }, [snackbarMessage]);

  const reloadLocalState = useCallback(async () => {
    if (WEB_MAINTENANCE_MODE) return;
    const activeHoster = authRepository.getCurrentHoster();
    const [activeHosters, shifts, atts, members, logs] = await Promise.all([
      authRepository.getActiveHosters(),
      receptionRepository.getAllShifts(),
      receptionRepository.getAllAttendances(),
      receptionRepository.getAllMembers(),
      receptionRepository.getAllSystemLogs(),
    ]);

    setHosters(activeHosters);
    setAllShifts(shifts);
    setAllAttendances(atts);
    setAllMembers(members);
    setSystemLogs(logs);

    if (activeHoster && !activeHoster.isAdmin) {
      const shift = await receptionRepository.getActiveShift(activeHoster.id);
      setCurrentShift(shift);
      if (shift) {
        const [q, attWithMembers] = await Promise.all([
          receptionRepository.getLatestQueueForShift(shift.id),
          receptionRepository.getAttendancesWithMembersForShift(shift.id),
        ]);
        setLatestQueue(q);
        setShiftAttendances(attWithMembers);
      } else {
        setLatestQueue(null);
        setShiftAttendances([]);
      }
    } else {
      setCurrentShift(null);
      setLatestQueue(null);
      setShiftAttendances([]);
    }
  }, []);

  useEffect(() => {
    if (WEB_MAINTENANCE_MODE) {
      setReady(true);
      return;
    }

    let unsubDb: (() => void) | undefined;
    let unsubSync: (() => void) | undefined;
    let unsubAuth: (() => void) | undefined;
    let unsubUpdate: (() => void) | undefined;

    (async () => {
      await syncManager.init();
      await reloadLocalState();
      setReady(true);
    })();

    unsubSync = syncManager.subscribe(() => {
      setSync(syncManager.getSnapshot());
    });

    unsubUpdate = updateService.subscribe(() => {
      setUpdateState(updateService.getState());
    });

    unsubDb = localDb.subscribe(() => {
      void reloadLocalState();
    });

    unsubAuth = authRepository.subscribe((hoster) => {
      setCurrentHoster(hoster);
      void reloadLocalState();
    });

    const handleVisibility = () => {
      if (typeof document !== 'undefined' && document.visibilityState === 'visible') {
        const online = typeof navigator !== 'undefined' ? navigator.onLine : false;
        const h = authRepository.getCurrentHoster();
        void updateService.checkVersion(online, h?.id ?? null, h?.name ?? null);
        if (online) {
          void syncManager.fetchAllRemoteData();
        }
      }
    };

    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', handleVisibility);
    }

    return () => {
      unsubDb?.();
      unsubSync?.();
      unsubUpdate?.();
      unsubAuth?.();
      if (typeof document !== 'undefined') {
        document.removeEventListener('visibilitychange', handleVisibility);
      }
    };
  }, [reloadLocalState]);

  return (
    <AppContext.Provider
      value={{
        ready,
        currentHoster,
        hosters,
        currentShift,
        latestQueue,
        shiftAttendances,
        allShifts,
        allAttendances,
        allMembers,
        systemLogs,
        sync,
        updateState,
        deviceName,
        setDeviceDisplayName,
        recheckVersion,
        applyWebUpdate,
        dismissOptionalUpdateBanner,
        showSyncLogsModal,
        setShowSyncLogsModal,
        snackbarMessage,
        showSnackbar,
        clearSnackbar,
        reloadLocalState,
      }}
    >
      {children}
    </AppContext.Provider>
  );
}

export function useApp(): AppContextValue {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used inside AppProvider');
  return ctx;
}
