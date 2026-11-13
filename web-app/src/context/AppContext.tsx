import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { localDb } from '../data/local/database';
import { authRepository } from '../data/repositories/authRepository';
import { receptionRepository } from '../data/repositories/receptionRepository';
import { syncManager, type SyncSnapshot } from '../data/sync/syncManager';
import type {
  Attendance,
  AttendanceMember,
  AttendanceWithMembers,
  Hoster,
  QueueEvent,
  Shift,
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
  sync: SyncSnapshot;
  showSyncLogsModal: boolean;
  setShowSyncLogsModal: (show: boolean) => void;
  snackbarMessage: string | null;
  showSnackbar: (msg: string) => void;
  clearSnackbar: () => void;
  reloadLocalState: () => Promise<void>;
}

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(false);
  const [currentHoster, setCurrentHoster] = useState<Hoster | null>(() =>
    authRepository.getCurrentHoster()
  );
  const [hosters, setHosters] = useState<Hoster[]>([]);
  const [currentShift, setCurrentShift] = useState<Shift | null>(null);
  const [latestQueue, setLatestQueue] = useState<QueueEvent | null>(null);
  const [shiftAttendances, setShiftAttendances] = useState<AttendanceWithMembers[]>([]);
  const [allShifts, setAllShifts] = useState<Shift[]>([]);
  const [allAttendances, setAllAttendances] = useState<Attendance[]>([]);
  const [allMembers, setAllMembers] = useState<AttendanceMember[]>([]);
  const [sync, setSync] = useState<SyncSnapshot>(() => syncManager.getSnapshot());
  const [showSyncLogsModal, setShowSyncLogsModal] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState<string | null>(null);

  const showSnackbar = useCallback((msg: string) => {
    setSnackbarMessage(msg);
  }, []);

  const clearSnackbar = useCallback(() => {
    setSnackbarMessage(null);
  }, []);

  useEffect(() => {
    if (!snackbarMessage) return;
    const t = setTimeout(() => setSnackbarMessage(null), 3500);
    return () => clearTimeout(t);
  }, [snackbarMessage]);

  const reloadLocalState = useCallback(async () => {
    const activeHoster = authRepository.getCurrentHoster();
    const [activeHosters, shifts, atts, members] = await Promise.all([
      authRepository.getActiveHosters(),
      receptionRepository.getAllShifts(),
      receptionRepository.getAllAttendances(),
      receptionRepository.getAllMembers(),
    ]);

    setHosters(activeHosters);
    setAllShifts(shifts);
    setAllAttendances(atts);
    setAllMembers(members);

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
    let unsubDb: (() => void) | undefined;
    let unsubSync: (() => void) | undefined;
    let unsubAuth: (() => void) | undefined;

    (async () => {
      await syncManager.init();
      await reloadLocalState();
      setReady(true);
    })();

    unsubSync = syncManager.subscribe(() => {
      setSync(syncManager.getSnapshot());
    });

    unsubDb = localDb.subscribe(() => {
      void reloadLocalState();
    });

    unsubAuth = authRepository.subscribe((hoster) => {
      setCurrentHoster(hoster);
      void reloadLocalState();
    });

    return () => {
      unsubDb?.();
      unsubSync?.();
      unsubAuth?.();
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
        sync,
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
