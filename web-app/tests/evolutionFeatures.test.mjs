import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import fs from 'node:fs/promises';
import JSZip from 'jszip';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, '..');
const outDir = path.join(rootDir, '.test-tmp-evo');

async function loadEvolutionModules() {
  const esbuild = await import('esbuild');
  await fs.mkdir(outDir, { recursive: true });
  const outFile = path.join(outDir, 'evolution-bundle.mjs');
  await esbuild.build({
    stdin: {
      contents: `
        export * as updateServiceMod from './src/data/update/updateService.ts';
        export * as adminFiltersMod from './src/domain/utils/adminFilters.ts';
        export * as systemLogMod from './src/data/repositories/systemLogRepository.ts';
        export * as exportRepoMod from './src/data/repositories/exportRepository.ts';
        export { receptionRepository } from './src/data/repositories/receptionRepository.ts';
        export { authRepository } from './src/data/repositories/authRepository.ts';
        export { localDb } from './src/data/local/database.ts';
      `,
      resolveDir: rootDir,
      loader: 'ts',
    },
    bundle: true,
    platform: 'node',
    format: 'esm',
    outfile: outFile,
    external: ['jszip', '@noble/hashes/*'],
  });
  return import(`${pathToFileURL(outFile).href}?t=${Date.now()}`);
}

test.after(async () => {
  await fs.rm(outDir, { recursive: true, force: true });
});

test('evaluateVersionPolicy enforces minSupportedCode and mandatory updates', async () => {
  const { updateServiceMod } = await loadEvolutionModules();
  const { evaluateVersionPolicy } = updateServiceMod;

  const basePolicy = {
    id: 'rel-1',
    versionName: '2.0',
    versionCode: 4,
    minSupportedCode: 3,
    mandatory: false,
    apkPath: 'releases/v2.0.apk',
    sha256: 'abc123',
    changelog: 'Correções gerais',
    releaseReason: 'Compatibilidade obrigatória',
    active: true,
    createdAt: Date.now(),
    confirmedAt: Date.now(),
  };

  // Installed below minSupportedCode -> BLOCKED
  assert.deepEqual(evaluateVersionPolicy(2, basePolicy), {
    status: 'BLOCKED',
    isBlocked: true,
  });

  // Installed >= minSupportedCode, below latest versionCode, not mandatory -> OPTIONAL_AVAILABLE
  assert.deepEqual(evaluateVersionPolicy(3, basePolicy), {
    status: 'OPTIONAL_AVAILABLE',
    isBlocked: false,
  });

  // Mandatory flag forces update when installed < versionCode
  assert.deepEqual(evaluateVersionPolicy(3, { ...basePolicy, mandatory: true }), {
    status: 'BLOCKED',
    isBlocked: true,
  });

  // Installed matches versionCode -> UP_TO_DATE
  assert.deepEqual(evaluateVersionPolicy(4, { ...basePolicy, mandatory: true }), {
    status: 'UP_TO_DATE',
    isBlocked: false,
  });

  // Inactive policy never blocks
  assert.deepEqual(evaluateVersionPolicy(1, { ...basePolicy, active: false, mandatory: true }), {
    status: 'UP_TO_DATE',
    isBlocked: false,
  });

  // Null policy -> UP_TO_DATE
  assert.deepEqual(evaluateVersionPolicy(1, null), {
    status: 'UP_TO_DATE',
    isBlocked: false,
  });
});

test('Admin filters validate date range and exclude soft-deleted attendances from metrics', async () => {
  const { adminFiltersMod } = await loadEvolutionModules();
  const { validateDateRange, rangeForOption, computeAdminMetrics } = adminFiltersMod;

  const nov1 = new Date(2026, 10, 1, 10, 0, 0).getTime();
  const nov13 = new Date(2026, 10, 13, 15, 0, 0).getTime();
  assert.equal(validateDateRange(nov1, nov13), null);
  assert.match(validateDateRange(nov13, nov1) ?? '', /não pode ser posterior/i);

  const [mStart, mEnd] = rangeForOption('MES_ATUAL', nov1, nov13, nov13);
  assert.equal(new Date(mStart).getDate(), 1);
  assert.equal(new Date(mStart).getMonth(), 10);
  assert.equal(new Date(mEnd).getDate(), 30);

  const [prevStart, prevEnd] = rangeForOption('MES_ANTERIOR', nov1, nov13, nov13);
  assert.equal(new Date(prevStart).getMonth(), 9);
  assert.equal(new Date(prevEnd).getDate(), 31);

  const shifts = [
    {
      id: 'shift-1',
      hosterId: 'h-1',
      hosterName: 'Carla',
      period: 'MANHA',
      openedAt: nov1,
      closedAt: nov1 + 3600000,
      status: 'CLOSED',
      deviceCreatedAt: nov1,
      syncedAt: nov1,
      syncStatus: 'SINCRONIZADO',
    },
  ];

  const attendances = [
    {
      id: 'att-active',
      shiftId: 'shift-1',
      hosterId: 'h-1',
      hosterName: 'Carla',
      type: 'DUPLA',
      totalMembers: 2,
      newMembers: 1,
      returningMembers: 1,
      createdAt: nov1 + 1000,
      updatedAt: nov1 + 1000,
      isEdited: false,
      isPreferential: true,
      syncStatus: 'SINCRONIZADO',
      isDeleted: false,
      deletedAt: null,
      deletedBy: null,
      deletionReason: null,
    },
    {
      id: 'att-deleted',
      shiftId: 'shift-1',
      hosterId: 'h-1',
      hosterName: 'Carla',
      type: 'GRUPO',
      totalMembers: 4,
      newMembers: 2,
      returningMembers: 2,
      createdAt: nov1 + 2000,
      updatedAt: nov1 + 5000,
      isEdited: false,
      isPreferential: false,
      syncStatus: 'SINCRONIZADO',
      isDeleted: true,
      deletedAt: nov1 + 5000,
      deletedBy: 'Carla',
      deletionReason: 'Cliente desistiu',
    },
  ];

  const members = [
    {
      id: 'm1',
      attendanceId: 'att-active',
      memberNumber: 1,
      classification: 'NOVO',
      origin: 'Instagram',
      createdAt: nov1,
    },
    {
      id: 'm2',
      attendanceId: 'att-active',
      memberNumber: 2,
      classification: 'RECORRENTE',
      origin: null,
      createdAt: nov1,
    },
    {
      id: 'm3',
      attendanceId: 'att-deleted',
      memberNumber: 1,
      classification: 'NOVO',
      origin: 'TikTok',
      createdAt: nov1,
    },
  ];

  const metrics = computeAdminMetrics(shifts, attendances, members, {
    dateOption: 'TODOS',
    draftStartMillis: nov1,
    draftEndMillis: nov13,
    appliedStartMillis: nov1,
    appliedEndMillis: nov13,
    selectedHosterId: null,
    selectedPeriod: null,
    selectedOrigin: null,
    selectedClassification: null,
    preferentialOnly: false,
  });

  assert.equal(metrics.totalAttendances, 1);
  assert.equal(metrics.totalPeople, 2);
  assert.equal(metrics.totalNewClients, 1);
  assert.equal(metrics.totalReturningClients, 1);
  assert.equal(metrics.morningClients, 2);
  assert.equal(metrics.preferentialCount, 1);
  assert.deepEqual(metrics.originDistribution, { Instagram: 1 });
});

test('Soft delete preserves DB records & members, enforces closed shift permission, logs audit, and excludes from XLSX export', async () => {
  const { receptionRepository, authRepository, exportRepoMod, localDb } =
    await loadEvolutionModules();
  localDb.resetMemory();

  const h1 = await authRepository.createHoster('Lucas Hoster', '1234');
  const h2 = await authRepository.createHoster('Outro Hoster', '5678');
  assert.equal(h1.ok, true);
  assert.equal(h2.ok, true);

  const shiftRes = await receptionRepository.openShift(h1.value.id, h1.value.name, 'NOITE');
  assert.equal(shiftRes.ok, true);

  const attKeep = await receptionRepository.registerAttendance({
    shiftId: shiftRes.value.id,
    hosterId: h1.value.id,
    hosterName: h1.value.name,
    type: 'SOLO',
    totalMembers: 1,
    newMembers: 1,
    returningMembers: 0,
    membersList: [
      {
        id: '',
        attendanceId: '',
        memberNumber: 1,
        classification: 'NOVO',
        origin: 'Indicação',
        createdAt: Date.now(),
      },
    ],
  });
  assert.equal(attKeep.ok, true);

  const attDelOpen = await receptionRepository.registerAttendance({
    shiftId: shiftRes.value.id,
    hosterId: h1.value.id,
    hosterName: h1.value.name,
    type: 'DUPLA',
    totalMembers: 2,
    newMembers: 1,
    returningMembers: 1,
    membersList: [
      {
        id: '',
        attendanceId: '',
        memberNumber: 1,
        classification: 'NOVO',
        origin: 'TikTok',
        createdAt: Date.now(),
      },
      {
        id: '',
        attendanceId: '',
        memberNumber: 2,
        classification: 'RECORRENTE',
        origin: null,
        createdAt: Date.now(),
      },
    ],
  });
  assert.equal(attDelOpen.ok, true);

  // Another hoster (non-admin) cannot delete h1's attendance
  const deniedOther = await receptionRepository.softDeleteAttendance({
    attendanceId: attDelOpen.value.id,
    actorHosterId: h2.value.id,
    actorHosterName: h2.value.name,
    actorIsAdmin: false,
    deletionReason: 'Teste indevido',
  });
  assert.equal(deniedOther.ok, false);

  // Responsible hoster can delete in OPEN shift
  const deletedOpen = await receptionRepository.softDeleteAttendance({
    attendanceId: attDelOpen.value.id,
    actorHosterId: h1.value.id,
    actorHosterName: h1.value.name,
    actorIsAdmin: false,
    deletionReason: 'Desistiu antes de sentar',
  });
  assert.equal(deletedOpen.ok, true);

  // Active queries hide deleted attendance, while full store keeps attendance & members
  const activeAtts = await localDb.getAllAttendances();
  assert.equal(activeAtts.length, 1);
  assert.equal(activeAtts[0].id, attKeep.value.id);

  const allAttsRaw = await localDb.getAllAttendancesIncludingDeleted();
  assert.equal(allAttsRaw.length, 2);
  const deletedRecord = allAttsRaw.find((a) => a.id === attDelOpen.value.id);
  assert.equal(deletedRecord?.isDeleted, true);
  assert.equal(deletedRecord?.deletedBy, 'Lucas Hoster');
  assert.equal(deletedRecord?.deletionReason, 'Desistiu antes de sentar');

  const preservedMembers = await localDb.getMembersForAttendance(attDelOpen.value.id);
  assert.equal(preservedMembers.length, 2);

  // Close shift and register one more attendance before closing first? We test deleting attKeep after closing:
  const closeRes = await receptionRepository.closeShift(shiftRes.value.id);
  assert.equal(closeRes.ok, true);

  // Non-admin hoster CANNOT delete inside CLOSED shift
  const deniedClosed = await receptionRepository.softDeleteAttendance({
    attendanceId: attKeep.value.id,
    actorHosterId: h1.value.id,
    actorHosterName: h1.value.name,
    actorIsAdmin: false,
    deletionReason: 'Tentativa tardia',
  });
  assert.equal(deniedClosed.ok, false);

  // Flush pending async system logs then clear sync queue so exportReport can proceed offline in test
  await new Promise((r) => setTimeout(r, 25));
  const queue = await localDb.getAllSyncQueue();
  for (const item of queue) {
    await localDb.deleteSyncOperation(item.id);
  }

  const exportRes = await exportRepoMod.exportReport({
    startMillis: 0,
    endMillis: Date.now() + 60000,
    actorUserName: 'Admin Teste',
  });
  assert.equal(exportRes.status, 'SUCCESS');
  assert.equal(exportRes.rowCount, 1);
  assert.equal(exportRes.totalClients, 1);

  const zip = await JSZip.loadAsync(exportRes.bytes);
  const sheet1Xml = await zip.file('xl/worksheets/sheet1.xml').async('string');
  assert.doesNotMatch(sheet1Xml, new RegExp(attDelOpen.value.id));
  assert.match(sheet1Xml, new RegExp(attKeep.value.id));

  // Verify system logs recorded ATTENDANCE_DELETE and REPORT_EXPORTED
  await new Promise((r) => setTimeout(r, 25));
  const logs = await localDb.getAllSystemLogs();
  assert.ok(logs.some((l) => l.action === 'ATENDIMENTO_EXCLUIDO' && l.result === 'SUCCESS'));
  assert.ok(logs.some((l) => l.action === 'ATENDIMENTO_FALHA_EXCLUIR' && l.result === 'FAILURE'));
  assert.ok(logs.some((l) => l.action === 'RELATORIO_EXPORTADO' && l.result === 'SUCCESS'));
});

test('SystemLog sanitization strips sensitive fields and filterSystemLogs narrows results', async () => {
  const { systemLogMod, adminFiltersMod } = await loadEvolutionModules();
  const { sanitizeLogDescription } = systemLogMod;
  const { filterSystemLogs } = adminFiltersMod;

  const dirty =
    'Falha auth hoster=Ana pin_hash=deadbeef pin_salt=1234 senha=segredo Bearer eyJhbGciOiJIUzI1NiJ9.abc.def';
  const clean = sanitizeLogDescription(dirty);
  assert.doesNotMatch(clean, /deadbeef/);
  assert.doesNotMatch(clean, /1234/);
  assert.doesNotMatch(clean, /segredo/);
  assert.doesNotMatch(clean, /eyJhbGciOi/);
  assert.match(clean, /pin_hash=\[REDACTED\]/);
  assert.match(clean, /Bearer \[REDACTED\]/);

  const now = Date.now();
  const logs = [
    {
      id: 'l1',
      userId: 'u1',
      userName: 'Ana',
      action: 'ATTENDANCE_CREATE',
      entityType: 'ATTENDANCE',
      entityId: 'a1',
      deviceName: 'Tablet Entrada',
      result: 'SUCCESS',
      description: 'OK',
      createdAt: now - 5000,
      syncedAt: now,
    },
    {
      id: 'l2',
      userId: 'u2',
      userName: 'Bruno',
      action: 'LOGIN_FAILURE',
      entityType: 'AUTH',
      entityId: null,
      deviceName: 'iPad Balcão',
      result: 'FAILURE',
      description: 'PIN inválido',
      createdAt: now - 1000,
      syncedAt: null,
    },
  ];

  assert.equal(
    filterSystemLogs(logs, {
      startMillis: null,
      endMillis: null,
      selectedUser: 'Bruno',
      selectedAction: null,
      selectedDevice: null,
      selectedResult: 'FAILURE',
    }).length,
    1
  );

  assert.equal(
    filterSystemLogs(logs, {
      startMillis: null,
      endMillis: null,
      selectedUser: null,
      selectedAction: 'ATTENDANCE_CREATE',
      selectedDevice: 'Tablet Entrada',
      selectedResult: null,
    }).length,
    1
  );
});
