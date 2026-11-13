import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import fs from 'node:fs/promises';
import JSZip from 'jszip';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, '..');

async function loadCoreModules() {
  const esbuild = await import('esbuild');
  const outDir = path.join(rootDir, '.test-tmp');
  await fs.mkdir(outDir, { recursive: true });
  const outFile = path.join(outDir, 'core-bundle.mjs');
  await esbuild.build({
    stdin: {
      contents: `
        export * as security from './src/domain/utils/security.ts';
        export * as xlsx from './src/domain/utils/xlsxGenerator.ts';
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

test('PBKDF2 admin password parity matches Android CryptoUtils hash', async () => {
  const { security } = await loadCoreModules();
  const ADMIN_VALID_SALT = 'c6bca501f28b49e6';
  const ADMIN_VALID_HASH = 'e1ef788d028b746c3594f232fd4a5213c3901c45acfc5ff3f552119d89d554f9';

  const computed = security.hashPassword('admin', ADMIN_VALID_SALT);
  assert.equal(computed, ADMIN_VALID_HASH);
  assert.equal(security.verifyPassword('admin', ADMIN_VALID_SALT, ADMIN_VALID_HASH), true);
  assert.equal(security.verifyPassword('errada', ADMIN_VALID_SALT, ADMIN_VALID_HASH), false);
});

test('ReceptionRepository validates mixed group members and sanitizes origins', async () => {
  const { receptionRepository, authRepository, localDb } = await loadCoreModules();

  const hosterRes = await authRepository.createHoster('Maria Teste', '1234');
  assert.equal(hosterRes.ok, true);

  const shiftRes = await receptionRepository.openShift(
    hosterRes.value.id,
    hosterRes.value.name,
    'NOITE'
  );
  assert.equal(shiftRes.ok, true);

  // Mixed Dupla: member 1 RECORRENTE (with stale origin), member 2 NOVO with TikTok
  const attRes = await receptionRepository.registerAttendance({
    shiftId: shiftRes.value.id,
    hosterId: hosterRes.value.id,
    hosterName: hosterRes.value.name,
    type: 'DUPLA',
    totalMembers: 2,
    newMembers: 1,
    returningMembers: 1,
    isPreferential: true,
    membersList: [
      {
        id: '',
        attendanceId: '',
        memberNumber: 1,
        classification: 'RECORRENTE',
        origin: 'Instagram', // should be sanitized to null!
        createdAt: Date.now(),
      },
      {
        id: '',
        attendanceId: '',
        memberNumber: 2,
        classification: 'NOVO',
        origin: 'TikTok',
        createdAt: Date.now(),
      },
    ],
  });
  assert.equal(attRes.ok, true);

  const allMembers = await localDb.getAllMembers();
  const savedForAtt = allMembers
    .filter((m) => m.attendanceId === attRes.value.id)
    .sort((a, b) => a.memberNumber - b.memberNumber);

  assert.equal(savedForAtt.length, 2);
  assert.equal(savedForAtt[0].classification, 'RECORRENTE');
  assert.equal(savedForAtt[0].origin, null);
  assert.equal(savedForAtt[1].classification, 'NOVO');
  assert.equal(savedForAtt[1].origin, 'TikTok');

  // Validation rejects NOVO without origin
  const badRes = await receptionRepository.registerAttendance({
    shiftId: shiftRes.value.id,
    hosterId: hosterRes.value.id,
    hosterName: hosterRes.value.name,
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
        origin: null,
        createdAt: Date.now(),
      },
    ],
  });
  assert.equal(badRes.ok, false);
});

test.after(async () => {
  await fs.rm(path.join(rootDir, '.test-tmp'), { recursive: true, force: true });
});

test('XLSX generator creates valid 3-sheet workbook with daily separation and grand total', async () => {
  const { xlsx: xlsxMod } = await loadCoreModules();

  const bytes = await xlsxMod.generateWorkbookBlob(
    [
      {
        id: 'att-1',
        date: '10/03/2026 12:30',
        shift: 'Manhã',
        hoster: 'Ana',
        type: 'DUPLA',
        isPreferential: 'Não',
        totalMembers: 2,
        newMembers: 1,
        returningMembers: 1,
      },
      {
        id: 'att-2',
        date: '11/03/2026 20:15',
        shift: 'Noite',
        hoster: 'Bruno',
        type: 'SOLO',
        isPreferential: 'Sim',
        totalMembers: 1,
        newMembers: 1,
        returningMembers: 0,
      },
    ],
    [
      {
        attendanceId: 'att-1',
        hoster: 'Ana',
        memberNumber: 1,
        classification: 'Recorrente',
        origin: '—',
        isPreferential: 'Não',
      },
      {
        attendanceId: 'att-1',
        hoster: 'Ana',
        memberNumber: 2,
        classification: 'Novo',
        origin: 'Instagram',
        isPreferential: 'Não',
      },
      {
        attendanceId: 'att-2',
        hoster: 'Bruno',
        memberNumber: 1,
        classification: 'Novo',
        origin: 'TikTok',
        isPreferential: 'Sim',
      },
    ],
    {
      dailySummaries: [
        {
          dateLabel: '10/03/2026',
          totalClients: 2,
          morningClients: 2,
          nightClients: 0,
          newClients: 1,
          returningClients: 1,
          preferentialCount: 0,
          originsBreakdown: { Instagram: 1 },
        },
        {
          dateLabel: '11/03/2026',
          totalClients: 1,
          morningClients: 0,
          nightClients: 1,
          newClients: 1,
          returningClients: 0,
          preferentialCount: 1,
          originsBreakdown: { TikTok: 1 },
        },
      ],
      grandTotalClients: 3,
      grandMorningClients: 2,
      grandNightClients: 1,
      grandNewClients: 2,
      grandReturningClients: 1,
      grandPreferentialCount: 1,
      grandOriginsBreakdown: { Instagram: 1, TikTok: 1 },
    }
  );

  const zip = await JSZip.loadAsync(bytes);
  const workbookXml = await zip.file('xl/workbook.xml').async('string');
  const sheet3Xml = await zip.file('xl/worksheets/sheet3.xml').async('string');

  assert.match(workbookXml, /Atendimentos/);
  assert.match(workbookXml, /Clientes/);
  assert.match(workbookXml, /Resumo Diário/);
  assert.match(sheet3Xml, /DIA: 10\/03\/2026/);
  assert.match(sheet3Xml, /DIA: 11\/03\/2026/);
  assert.match(sheet3Xml, /TOTAL GERAL DO PERÍODO/);
  assert.match(sheet3Xml, /Instagram/);
  assert.match(sheet3Xml, /TikTok/);
});
