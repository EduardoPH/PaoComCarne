import JSZip from 'jszip';

export interface AttendanceRow {
  id: string;
  date: string;
  shift: string;
  hoster: string;
  type: string;
  isPreferential: string;
  totalMembers: number;
  newMembers: number;
  returningMembers: number;
}

export interface ClientRow {
  attendanceId: string;
  hoster: string;
  memberNumber: number;
  classification: string;
  origin: string;
  isPreferential: string;
}

export interface DailySummary {
  dateLabel: string;
  totalClients: number;
  morningClients: number;
  nightClients: number;
  newClients: number;
  returningClients: number;
  preferentialCount: number;
  originsBreakdown: Record<string, number>;
}

export interface ReportConsolidatedSummary {
  dailySummaries: DailySummary[];
  grandTotalClients: number;
  grandMorningClients: number;
  grandNightClients: number;
  grandNewClients: number;
  grandReturningClients: number;
  grandPreferentialCount: number;
  grandOriginsBreakdown: Record<string, number>;
}

function escapeXml(input: string | null | undefined): string {
  if (!input) return '';
  return input
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function colLetter(index: number): string {
  return String.fromCharCode(65 + index);
}

function buildContentTypesXml(): string {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>`;
}

function buildRootRelsXml(): string {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>`;
}

function buildWorkbookRelsXml(): string {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
</Relationships>`;
}

function buildWorkbookXml(): string {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Atendimentos" sheetId="1" r:id="rId1"/>
    <sheet name="Clientes" sheetId="2" r:id="rId2"/>
    <sheet name="Resumo Diário &amp; Origens" sheetId="3" r:id="rId4"/>
  </sheets>
</workbook>`;
}

function buildStylesXml(): string {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font>
      <sz val="11"/>
      <color rgb="FF292421"/>
      <name val="Calibri"/>
    </font>
    <font>
      <b/>
      <sz val="11"/>
      <color rgb="FFFFFFFF"/>
      <name val="Calibri"/>
    </font>
    <font>
      <b/>
      <sz val="11"/>
      <color rgb="FF292421"/>
      <name val="Calibri"/>
    </font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill>
      <patternFill patternType="solid">
        <fgColor rgb="FFA62B2B"/>
        <bgColor indexed="64"/>
      </patternFill>
    </fill>
    <fill>
      <patternFill patternType="solid">
        <fgColor rgb="FFF5E8C7"/>
        <bgColor indexed="64"/>
      </patternFill>
    </fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
  </cellXfs>
</styleSheet>`;
}

function buildSheet1Xml(rows: AttendanceRow[]): string {
  const parts: string[] = [];
  parts.push(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="36" customWidth="1"/>
    <col min="2" max="2" width="18" customWidth="1"/>
    <col min="3" max="3" width="12" customWidth="1"/>
    <col min="4" max="4" width="22" customWidth="1"/>
    <col min="5" max="5" width="12" customWidth="1"/>
    <col min="6" max="6" width="14" customWidth="1"/>
    <col min="7" max="7" width="18" customWidth="1"/>
    <col min="8" max="8" width="16" customWidth="1"/>
    <col min="9" max="9" width="20" customWidth="1"/>
  </cols>
  <sheetData>
`);

  const headers = [
    'ID do Atendimento',
    'Data',
    'Turno',
    'Hoster',
    'Tipo',
    'Preferencial',
    'Total de Integrantes',
    'Clientes Novos',
    'Clientes Recorrentes',
  ];

  parts.push('    <row r="1">\n');
  headers.forEach((header, idx) => {
    parts.push(
      `      <c r="${colLetter(idx)}1" s="1" t="inlineStr"><is><t>${escapeXml(header)}</t></is></c>\n`
    );
  });
  parts.push('    </row>\n');

  rows.forEach((row, index) => {
    const rowNum = index + 2;
    parts.push(`    <row r="${rowNum}">\n`);
    parts.push(`      <c r="A${rowNum}" t="inlineStr"><is><t>${escapeXml(row.id)}</t></is></c>\n`);
    parts.push(`      <c r="B${rowNum}" t="inlineStr"><is><t>${escapeXml(row.date)}</t></is></c>\n`);
    parts.push(`      <c r="C${rowNum}" t="inlineStr"><is><t>${escapeXml(row.shift)}</t></is></c>\n`);
    parts.push(`      <c r="D${rowNum}" t="inlineStr"><is><t>${escapeXml(row.hoster)}</t></is></c>\n`);
    parts.push(`      <c r="E${rowNum}" t="inlineStr"><is><t>${escapeXml(row.type)}</t></is></c>\n`);
    parts.push(`      <c r="F${rowNum}" t="inlineStr"><is><t>${escapeXml(row.isPreferential)}</t></is></c>\n`);
    parts.push(`      <c r="G${rowNum}"><v>${row.totalMembers}</v></c>\n`);
    parts.push(`      <c r="H${rowNum}"><v>${row.newMembers}</v></c>\n`);
    parts.push(`      <c r="I${rowNum}"><v>${row.returningMembers}</v></c>\n`);
    parts.push('    </row>\n');
  });

  parts.push(`  </sheetData>\n</worksheet>`);
  return parts.join('');
}

function buildSheet2Xml(clients: ClientRow[]): string {
  const parts: string[] = [];
  parts.push(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="36" customWidth="1"/>
    <col min="2" max="2" width="22" customWidth="1"/>
    <col min="3" max="3" width="18" customWidth="1"/>
    <col min="4" max="4" width="18" customWidth="1"/>
    <col min="5" max="5" width="26" customWidth="1"/>
    <col min="6" max="6" width="14" customWidth="1"/>
  </cols>
  <sheetData>
`);

  const headers = [
    'ID do Atendimento',
    'Hoster',
    'Identificador do Integrante',
    'Classificação',
    'Origem',
    'Preferencial',
  ];

  parts.push('    <row r="1">\n');
  headers.forEach((header, idx) => {
    parts.push(
      `      <c r="${colLetter(idx)}1" s="1" t="inlineStr"><is><t>${escapeXml(header)}</t></is></c>\n`
    );
  });
  parts.push('    </row>\n');

  clients.forEach((client, index) => {
    const rowNum = index + 2;
    parts.push(`    <row r="${rowNum}">\n`);
    parts.push(`      <c r="A${rowNum}" t="inlineStr"><is><t>${escapeXml(client.attendanceId)}</t></is></c>\n`);
    parts.push(`      <c r="B${rowNum}" t="inlineStr"><is><t>${escapeXml(client.hoster)}</t></is></c>\n`);
    parts.push(`      <c r="C${rowNum}"><v>${client.memberNumber}</v></c>\n`);
    parts.push(`      <c r="D${rowNum}" t="inlineStr"><is><t>${escapeXml(client.classification)}</t></is></c>\n`);
    parts.push(`      <c r="E${rowNum}" t="inlineStr"><is><t>${escapeXml(client.origin)}</t></is></c>\n`);
    parts.push(`      <c r="F${rowNum}" t="inlineStr"><is><t>${escapeXml(client.isPreferential)}</t></is></c>\n`);
    parts.push('    </row>\n');
  });

  parts.push(`  </sheetData>\n</worksheet>`);
  return parts.join('');
}

function buildSheet3Xml(summary: ReportConsolidatedSummary): string {
  const parts: string[] = [];
  parts.push(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="38" customWidth="1"/>
    <col min="2" max="2" width="16" customWidth="1"/>
    <col min="3" max="3" width="24" customWidth="1"/>
  </cols>
  <sheetData>
`);

  let currentRow = 1;
  const standardOrigins = ['Instagram', 'TikTok', 'Indicação', 'Google', 'iFood', 'Já foi na antiga loja'];

  const appendTextNumRow = (label: string, val: number, detail: string, boldLabel = false) => {
    const sAttr = boldLabel ? ' s="3"' : '';
    parts.push(`    <row r="${currentRow}">\n`);
    parts.push(`      <c r="A${currentRow}"${sAttr} t="inlineStr"><is><t>${escapeXml(label)}</t></is></c>\n`);
    parts.push(`      <c r="B${currentRow}"><v>${val}</v></c>\n`);
    parts.push(`      <c r="C${currentRow}" t="inlineStr"><is><t>${escapeXml(detail)}</t></is></c>\n`);
    parts.push('    </row>\n');
    currentRow++;
  };

  for (const day of summary.dailySummaries) {
    parts.push(`    <row r="${currentRow}">\n`);
    parts.push(`      <c r="A${currentRow}" s="1" t="inlineStr"><is><t>DIA: ${escapeXml(day.dateLabel)}</t></is></c>\n`);
    parts.push(`      <c r="B${currentRow}" s="1" t="inlineStr"><is><t>TOTAL</t></is></c>\n`);
    parts.push(`      <c r="C${currentRow}" s="1" t="inlineStr"><is><t>DETALHAMENTO</t></is></c>\n`);
    parts.push('    </row>\n');
    currentRow++;

    appendTextNumRow('Total de clientes', day.totalClients, 'Clientes atendidos no dia', true);
    appendTextNumRow('• Clientes manhã', day.morningClients, 'Turno da Manhã');
    appendTextNumRow('• Clientes noite', day.nightClients, 'Turno da Noite');
    appendTextNumRow('• Clientes novos', day.newClients, 'Primeira vez na casa');
    appendTextNumRow('• Clientes recorrentes', day.returningClients, 'Já conheciam a hamburgueria');
    appendTextNumRow('• Atendimentos preferenciais', day.preferentialCount, 'Prioridade legal');

    parts.push(`    <row r="${currentRow}">\n`);
    parts.push(`      <c r="A${currentRow}" s="2" t="inlineStr"><is><t>De onde conheceram (Novos)</t></is></c>\n`);
    parts.push(`      <c r="B${currentRow}" s="2" t="inlineStr"><is><t>Qtd</t></is></c>\n`);
    parts.push(`      <c r="C${currentRow}" s="2" t="inlineStr"><is><t>% dos Novos</t></is></c>\n`);
    parts.push('    </row>\n');
    currentRow++;

    const dayOriginKeys = Array.from(new Set([...standardOrigins, ...Object.keys(day.originsBreakdown)]));
    for (const origin of dayOriginKeys) {
      const count = day.originsBreakdown[origin] ?? 0;
      const pct = day.newClients > 0 ? `${Math.floor((count * 100) / day.newClients)}%` : '0%';
      parts.push(`    <row r="${currentRow}">\n`);
      parts.push(`      <c r="A${currentRow}" t="inlineStr"><is><t>    ${escapeXml(origin)}</t></is></c>\n`);
      parts.push(`      <c r="B${currentRow}"><v>${count}</v></c>\n`);
      parts.push(`      <c r="C${currentRow}" t="inlineStr"><is><t>${pct}</t></is></c>\n`);
      parts.push('    </row>\n');
      currentRow++;
    }

    currentRow++;
  }

  parts.push(`    <row r="${currentRow}">\n`);
  parts.push(`      <c r="A${currentRow}" s="1" t="inlineStr"><is><t>TOTAL GERAL DO PERÍODO</t></is></c>\n`);
  parts.push(`      <c r="B${currentRow}" s="1" t="inlineStr"><is><t>CONSOLIDADO</t></is></c>\n`);
  parts.push(`      <c r="C${currentRow}" s="1" t="inlineStr"><is><t>MÉTRICAS GLOBAIS</t></is></c>\n`);
  parts.push('    </row>\n');
  currentRow++;

  appendTextNumRow('Total de cliente', summary.grandTotalClients, 'Total absoluto de clientes', true);
  appendTextNumRow('• Cliente manhã', summary.grandMorningClients, 'Soma turnos manhã');
  appendTextNumRow('• Clientes noite', summary.grandNightClients, 'Soma turnos noite');
  appendTextNumRow('• Total clientes novos', summary.grandNewClients, 'Primeira visita');
  appendTextNumRow('• Total clientes recorrentes', summary.grandReturningClients, 'Clientes habituais');
  appendTextNumRow('• Total atendimentos preferenciais', summary.grandPreferentialCount, 'Total preferencial');

  parts.push(`    <row r="${currentRow}">\n`);
  parts.push(`      <c r="A${currentRow}" s="2" t="inlineStr"><is><t>De onde conheceram (Consolidado)</t></is></c>\n`);
  parts.push(`      <c r="B${currentRow}" s="2" t="inlineStr"><is><t>Total Geral</t></is></c>\n`);
  parts.push(`      <c r="C${currentRow}" s="2" t="inlineStr"><is><t>% Total dos Novos</t></is></c>\n`);
  parts.push('    </row>\n');
  currentRow++;

  const grandOriginKeys = Array.from(
    new Set([...standardOrigins, ...Object.keys(summary.grandOriginsBreakdown)])
  );
  for (const origin of grandOriginKeys) {
    const grandCount = summary.grandOriginsBreakdown[origin] ?? 0;
    const pct =
      summary.grandNewClients > 0
        ? `${Math.floor((grandCount * 100) / summary.grandNewClients)}%`
        : '0%';
    parts.push(`    <row r="${currentRow}">\n`);
    parts.push(`      <c r="A${currentRow}" t="inlineStr"><is><t>    ${escapeXml(origin)}</t></is></c>\n`);
    parts.push(`      <c r="B${currentRow}"><v>${grandCount}</v></c>\n`);
    parts.push(`      <c r="C${currentRow}" t="inlineStr"><is><t>${pct}</t></is></c>\n`);
    parts.push('    </row>\n');
    currentRow++;
  }

  parts.push(`  </sheetData>\n</worksheet>`);
  return parts.join('');
}

export async function generateWorkbookBlob(
  attendances: AttendanceRow[],
  clients: ClientRow[],
  summary: ReportConsolidatedSummary
): Promise<Uint8Array> {
  const zip = new JSZip();
  zip.file('[Content_Types].xml', buildContentTypesXml());
  zip.file('_rels/.rels', buildRootRelsXml());
  zip.file('xl/_rels/workbook.xml.rels', buildWorkbookRelsXml());
  zip.file('xl/styles.xml', buildStylesXml());
  zip.file('xl/workbook.xml', buildWorkbookXml());
  zip.file('xl/worksheets/sheet1.xml', buildSheet1Xml(attendances));
  zip.file('xl/worksheets/sheet2.xml', buildSheet2Xml(clients));
  zip.file('xl/worksheets/sheet3.xml', buildSheet3Xml(summary));

  return zip.generateAsync({ type: 'uint8array', compression: 'DEFLATE' });
}
