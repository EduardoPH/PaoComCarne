package com.example.domain.util

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxGenerator {

    data class AttendanceRow(
        val id: String,
        val date: String,
        val shift: String,
        val hoster: String,
        val type: String,
        val isPreferential: String,
        val totalMembers: Int,
        val newMembers: Int,
        val returningMembers: Int
    )

    data class ClientRow(
        val attendanceId: String,
        val hoster: String,
        val memberNumber: Int,
        val classification: String,
        val origin: String,
        val isPreferential: String
    )

    data class DailySummary(
        val dateLabel: String,
        val totalClients: Int,
        val morningClients: Int,
        val nightClients: Int,
        val newClients: Int,
        val returningClients: Int,
        val preferentialCount: Int,
        val originsBreakdown: Map<String, Int>,
        val totalAttendances: Int = 0,
        val soloCount: Int = 0,
        val duplaCount: Int = 0,
        val grupoCount: Int = 0
    )

    data class ReportConsolidatedSummary(
        val dailySummaries: List<DailySummary>,
        val grandTotalClients: Int,
        val grandMorningClients: Int,
        val grandNightClients: Int,
        val grandNewClients: Int,
        val grandReturningClients: Int,
        val grandPreferentialCount: Int,
        val grandOriginsBreakdown: Map<String, Int>,
        val grandTotalAttendances: Int = 0,
        val grandSoloCount: Int = 0,
        val grandDuplaCount: Int = 0,
        val grandGrupoCount: Int = 0
    )

    fun generateWorkbook(
        destinationFile: File,
        attendances: List<AttendanceRow>,
        clients: List<ClientRow>,
        summary: ReportConsolidatedSummary
    ) {
        val fos = FileOutputStream(destinationFile)
        val zos = ZipOutputStream(fos)

        try {
            // 1. [Content_Types].xml
            addZipEntry(zos, "[Content_Types].xml", buildContentTypesXml())

            // 2. _rels/.rels
            addZipEntry(zos, "_rels/.rels", buildRootRelsXml())

            // 3. xl/_rels/workbook.xml.rels
            addZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml())

            // 4. xl/styles.xml
            addZipEntry(zos, "xl/styles.xml", buildStylesXml())

            // 5. xl/workbook.xml
            addZipEntry(zos, "xl/workbook.xml", buildWorkbookXml())

            // 6. xl/worksheets/sheet1.xml (Atendimentos)
            addZipEntry(zos, "xl/worksheets/sheet1.xml", buildSheet1Xml(attendances))

            // 7. xl/worksheets/sheet2.xml (Clientes)
            addZipEntry(zos, "xl/worksheets/sheet2.xml", buildSheet2Xml(clients))

            // 8. xl/worksheets/sheet3.xml (Resumo Diário e Origens)
            addZipEntry(zos, "xl/worksheets/sheet3.xml", buildSheet3Xml(summary))

        } finally {
            zos.flush()
            zos.close()
            fos.close()
        }
    }

    private fun addZipEntry(zos: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        val bytes = content.toByteArray(Charsets.UTF_8)
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun escapeXml(input: String?): String {
        if (input == null) return ""
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildContentTypesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun buildRootRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun buildWorkbookRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
</Relationships>"""

    private fun buildWorkbookXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Atendimentos" sheetId="1" r:id="rId1"/>
    <sheet name="Clientes" sheetId="2" r:id="rId2"/>
    <sheet name="Resumo Diário &amp; Origens" sheetId="3" r:id="rId4"/>
  </sheets>
</workbook>"""

    private fun buildStylesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
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
</styleSheet>"""

    private fun buildSheet1Xml(rows: List<AttendanceRow>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
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
""")

        // Header (Row 1)
        val headers = listOf(
            "ID do Atendimento",
            "Data",
            "Turno",
            "Hoster",
            "Tipo",
            "Preferencial",
            "Total de Integrantes",
            "Clientes Novos",
            "Clientes Recorrentes"
        )
        sb.append("    <row r=\"1\">\n")
        headers.forEachIndexed { colIdx, header ->
            val colLetter = ('A' + colIdx).toString()
            sb.append("      <c r=\"${colLetter}1\" s=\"1\" t=\"inlineStr\"><is><t>${escapeXml(header)}</t></is></c>\n")
        }
        sb.append("    </row>\n")

        // Data rows
        rows.forEachIndexed { index, row ->
            val rowNum = index + 2
            sb.append("    <row r=\"$rowNum\">\n")
            sb.append("      <c r=\"A$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(row.id)}</t></is></c>\n")
            sb.append("      <c r=\"B$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(row.date)}</t></is></c>\n")
            sb.append("      <c r=\"C$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(row.shift)}</t></is></c>\n")
            sb.append("      <c r=\"D$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(row.hoster)}</t></is></c>\n")
            sb.append("      <c r=\"E$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(row.type)}</t></is></c>\n")
            sb.append("      <c r=\"F$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(row.isPreferential)}</t></is></c>\n")
            sb.append("      <c r=\"G$rowNum\"><v>${row.totalMembers}</v></c>\n")
            sb.append("      <c r=\"H$rowNum\"><v>${row.newMembers}</v></c>\n")
            sb.append("      <c r=\"I$rowNum\"><v>${row.returningMembers}</v></c>\n")
            sb.append("    </row>\n")
        }

        // Conclusão / Totais no fim da planilha de Atendimentos
        val soloCount = rows.count { it.type.equals("SOLO", ignoreCase = true) }
        val duplaCount = rows.count { it.type.equals("DUPLA", ignoreCase = true) }
        val grupoCount = rows.count { it.type.equals("GRUPO", ignoreCase = true) }
        val totalMembers = rows.sumOf { it.totalMembers }
        val totalNew = rows.sumOf { it.newMembers }
        val totalReturning = rows.sumOf { it.returningMembers }
        val totalPref = rows.count { it.isPreferential.equals("Sim", ignoreCase = true) }

        var summaryRow = rows.size + 3
        sb.append("    <row r=\"$summaryRow\">\n")
        sb.append("      <c r=\"A$summaryRow\" s=\"1\" t=\"inlineStr\"><is><t>CONCLUSÃO — TOTAIS DE ATENDIMENTOS</t></is></c>\n")
        sb.append("      <c r=\"B$summaryRow\" s=\"1\" t=\"inlineStr\"><is><t>QUANTIDADE</t></is></c>\n")
        sb.append("      <c r=\"C$summaryRow\" s=\"1\" t=\"inlineStr\"><is><t>DETALHAMENTO</t></is></c>\n")
        sb.append("    </row>\n")
        summaryRow++

        val attendanceConclusions = listOf(
            Triple("Total de Atendimentos", rows.size, "Registros válidos no período"),
            Triple("Total Solo", soloCount, "Atendimentos individuais (1 pessoa)"),
            Triple("Total Dupla", duplaCount, "Atendimentos em dupla (2 pessoas)"),
            Triple("Total Grupo", grupoCount, "Atendimentos em grupo (3+ pessoas)"),
            Triple("Total de Clientes / Integrantes", totalMembers, "Soma de pessoas atendidas"),
            Triple("Total Clientes Novos", totalNew, "Primeira vez na casa"),
            Triple("Total Clientes Recorrentes", totalReturning, "Clientes habituais"),
            Triple("Total Atendimentos Preferenciais", totalPref, "Prioridade legal")
        )
        attendanceConclusions.forEachIndexed { idx, (label, value, detail) ->
            val labelStyle = if (idx in 0..3) " s=\"3\"" else ""
            sb.append("    <row r=\"$summaryRow\">\n")
            sb.append("      <c r=\"A$summaryRow\"$labelStyle t=\"inlineStr\"><is><t>${escapeXml(label)}</t></is></c>\n")
            sb.append("      <c r=\"B$summaryRow\"><v>$value</v></c>\n")
            sb.append("      <c r=\"C$summaryRow\" t=\"inlineStr\"><is><t>${escapeXml(detail)}</t></is></c>\n")
            sb.append("    </row>\n")
            summaryRow++
        }

        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }

    private fun buildSheet2Xml(clients: List<ClientRow>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
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
""")

        // Header (Row 1)
        val headers = listOf(
            "ID do Atendimento",
            "Hoster",
            "Identificador do Integrante",
            "Classificação",
            "Origem",
            "Preferencial"
        )
        sb.append("    <row r=\"1\">\n")
        headers.forEachIndexed { colIdx, header ->
            val colLetter = ('A' + colIdx).toString()
            sb.append("      <c r=\"${colLetter}1\" s=\"1\" t=\"inlineStr\"><is><t>${escapeXml(header)}</t></is></c>\n")
        }
        sb.append("    </row>\n")

        // Data rows
        clients.forEachIndexed { index, client ->
            val rowNum = index + 2
            sb.append("    <row r=\"$rowNum\">\n")
            sb.append("      <c r=\"A$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(client.attendanceId)}</t></is></c>\n")
            sb.append("      <c r=\"B$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(client.hoster)}</t></is></c>\n")
            sb.append("      <c r=\"C$rowNum\"><v>${client.memberNumber}</v></c>\n")
            sb.append("      <c r=\"D$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(client.classification)}</t></is></c>\n")
            sb.append("      <c r=\"E$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(client.origin)}</t></is></c>\n")
            sb.append("      <c r=\"F$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(client.isPreferential)}</t></is></c>\n")
            sb.append("    </row>\n")
        }

        // Conclusão / Total de clientes no fim da planilha de Clientes
        val totalClients = clients.size
        val totalNewClients = clients.count { it.classification.equals("Novo", ignoreCase = true) }
        val totalReturningClients = clients.count { it.classification.equals("Recorrente", ignoreCase = true) }
        val totalPreferentialClients = clients.count { it.isPreferential.equals("Sim", ignoreCase = true) }

        var summaryRow = clients.size + 3
        sb.append("    <row r=\"$summaryRow\">\n")
        sb.append("      <c r=\"A$summaryRow\" s=\"1\" t=\"inlineStr\"><is><t>CONCLUSÃO — TOTAIS DE CLIENTES</t></is></c>\n")
        sb.append("      <c r=\"B$summaryRow\" s=\"1\" t=\"inlineStr\"><is><t>QUANTIDADE</t></is></c>\n")
        sb.append("      <c r=\"C$summaryRow\" s=\"1\" t=\"inlineStr\"><is><t>DETALHAMENTO</t></is></c>\n")
        sb.append("    </row>\n")
        summaryRow++

        val clientConclusions = listOf(
            Triple("Total de cliente", totalClients, "Total geral de clientes listados"),
            Triple("Total Clientes Novos", totalNewClients, "Primeira visita"),
            Triple("Total Clientes Recorrentes", totalReturningClients, "Clientes habituais"),
            Triple("Total em Atendimentos Preferenciais", totalPreferentialClients, "Clientes em atendimentos prioritários")
        )
        clientConclusions.forEachIndexed { idx, (label, value, detail) ->
            val labelStyle = if (idx == 0) " s=\"3\"" else ""
            sb.append("    <row r=\"$summaryRow\">\n")
            sb.append("      <c r=\"A$summaryRow\"$labelStyle t=\"inlineStr\"><is><t>${escapeXml(label)}</t></is></c>\n")
            sb.append("      <c r=\"B$summaryRow\"><v>$value</v></c>\n")
            sb.append("      <c r=\"C$summaryRow\" t=\"inlineStr\"><is><t>${escapeXml(detail)}</t></is></c>\n")
            sb.append("    </row>\n")
            summaryRow++
        }

        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }

    private fun buildSheet3Xml(summary: ReportConsolidatedSummary): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="38" customWidth="1"/>
    <col min="2" max="2" width="16" customWidth="1"/>
    <col min="3" max="3" width="24" customWidth="1"/>
  </cols>
  <sheetData>
""")

        var currentRow = 1
        val standardOrigins = listOf("Instagram", "TikTok", "Indicação", "Google", "iFood", "Já foi na antiga loja")

        // 1. Cada dia individualmente
        summary.dailySummaries.forEach { day ->
            // Banner do Dia (Estilo Vermelho)
            sb.append("    <row r=\"$currentRow\">\n")
            sb.append("      <c r=\"A$currentRow\" s=\"1\" t=\"inlineStr\"><is><t>DIA: ${escapeXml(day.dateLabel)}</t></is></c>\n")
            sb.append("      <c r=\"B$currentRow\" s=\"1\" t=\"inlineStr\"><is><t>TOTAL</t></is></c>\n")
            sb.append("      <c r=\"C$currentRow\" s=\"1\" t=\"inlineStr\"><is><t>DETALHAMENTO</t></is></c>\n")
            sb.append("    </row>\n")
            currentRow++

            // Total de clientes do dia
            sb.append("    <row r=\"$currentRow\">\n")
            sb.append("      <c r=\"A$currentRow\" s=\"3\" t=\"inlineStr\"><is><t>Total de clientes</t></is></c>\n")
            sb.append("      <c r=\"B$currentRow\"><v>${day.totalClients}</v></c>\n")
            sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Clientes atendidos no dia</t></is></c>\n")
            sb.append("    </row>\n")
            currentRow++

            // Cliente manhã
            sb.append("    <row r=\"$currentRow\">\n")
            sb.append("      <c r=\"A$currentRow\" t=\"inlineStr\"><is><t>• Clientes manhã</t></is></c>\n")
            sb.append("      <c r=\"B$currentRow\"><v>${day.morningClients}</v></c>\n")
            sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Turno da Manhã</t></is></c>\n")
            sb.append("    </row>\n")
            currentRow++

            // Clientes noite
            sb.append("    <row r=\"$currentRow\">\n")
            sb.append("      <c r=\"A$currentRow\" t=\"inlineStr\"><is><t>• Clientes noite</t></is></c>\n")
            sb.append("      <c r=\"B$currentRow\"><v>${day.nightClients}</v></c>\n")
            sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Turno da Noite</t></is></c>\n")
            sb.append("    </row>\n")
            currentRow++

            // Clientes novos
            sb.append("    <row r=\"$currentRow\">\n")
            sb.append("      <c r=\"A$currentRow\" t=\"inlineStr\"><is><t>• Clientes novos</t></is></c>\n")
            sb.append("      <c r=\"B$currentRow\"><v>${day.newClients}</v></c>\n")
            sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Primeira vez na casa</t></is></c>\n")
            sb.append("    </row>\n")
            currentRow++

            // Clientes recorrentes
            sb.append("    <row r=\"$currentRow\">\n")
            sb.append("      <c r=\"A$currentRow\" t=\"inlineStr\"><is><t>• Clientes recorrentes</t></is></c>\n")
            sb.append("      <c r=\"B$currentRow\"><v>${day.returningClients}</v></c>\n")
            sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Já conheciam a hamburgueria</t></is></c>\n")
            sb.append("    </row>\n")
            currentRow++

            // Total de atendimentos e tipos (Solo / Dupla / Grupo) do dia
            sb.append("    <row r=\"$currentRow\">\n")
            sb.append("      <c r=\"A$currentRow\" t=\"inlineStr\"><is><t>• Total de atendimentos</t></is></c>\n")
            sb.append("      <c r=\"B$currentRow\"><v>${day.totalAttendances}</v></c>\n")
            sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Registros de atendimento no dia</t></is></c>\n")
            sb.append("    </row>\n")
            currentRow++

            sb.append("    <row r=\"$currentRow\">\n")
            sb.append("      <c r=\"A$currentRow\" t=\"inlineStr\"><is><t>• Total Solo</t></is></c>\n")
            sb.append("      <c r=\"B$currentRow\"><v>${day.soloCount}</v></c>\n")
            sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Atendimentos individuais</t></is></c>\n")
            sb.append("    </row>\n")
            currentRow++

            sb.append("    <row r=\"$currentRow\">\n")
            sb.append("      <c r=\"A$currentRow\" t=\"inlineStr\"><is><t>• Total Dupla</t></is></c>\n")
            sb.append("      <c r=\"B$currentRow\"><v>${day.duplaCount}</v></c>\n")
            sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Atendimentos em dupla</t></is></c>\n")
            sb.append("    </row>\n")
            currentRow++

            sb.append("    <row r=\"$currentRow\">\n")
            sb.append("      <c r=\"A$currentRow\" t=\"inlineStr\"><is><t>• Total Grupo</t></is></c>\n")
            sb.append("      <c r=\"B$currentRow\"><v>${day.grupoCount}</v></c>\n")
            sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Atendimentos em grupo</t></is></c>\n")
            sb.append("    </row>\n")
            currentRow++

            // Preferenciais
            sb.append("    <row r=\"$currentRow\">\n")
            sb.append("      <c r=\"A$currentRow\" t=\"inlineStr\"><is><t>• Atendimentos preferenciais</t></is></c>\n")
            sb.append("      <c r=\"B$currentRow\"><v>${day.preferentialCount}</v></c>\n")
            sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Prioridade legal</t></is></c>\n")
            sb.append("    </row>\n")
            currentRow++

            // Sub-seção: De onde conheceram
            sb.append("    <row r=\"$currentRow\">\n")
            sb.append("      <c r=\"A$currentRow\" s=\"2\" t=\"inlineStr\"><is><t>De onde conheceram (Novos)</t></is></c>\n")
            sb.append("      <c r=\"B$currentRow\" s=\"2\" t=\"inlineStr\"><is><t>Qtd</t></is></c>\n")
            sb.append("      <c r=\"C$currentRow\" s=\"2\" t=\"inlineStr\"><is><t>% dos Novos</t></is></c>\n")
            sb.append("    </row>\n")
            currentRow++

            val allDayOrigins = (standardOrigins + day.originsBreakdown.keys).distinct()
            allDayOrigins.forEach { origin ->
                val count = day.originsBreakdown[origin] ?: 0
                val pct = if (day.newClients > 0) "${(count * 100) / day.newClients}%" else "0%"
                sb.append("    <row r=\"$currentRow\">\n")
                sb.append("      <c r=\"A$currentRow\" t=\"inlineStr\"><is><t>    $origin</t></is></c>\n")
                sb.append("      <c r=\"B$currentRow\"><v>$count</v></c>\n")
                sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>$pct</t></is></c>\n")
                sb.append("    </row>\n")
                currentRow++
            }

            // Linha em branco para separação visual elegante
            currentRow++
        }

        // 2. Total Geral no Fim
        sb.append("    <row r=\"$currentRow\">\n")
        sb.append("      <c r=\"A$currentRow\" s=\"1\" t=\"inlineStr\"><is><t>TOTAL GERAL DO PERÍODO</t></is></c>\n")
        sb.append("      <c r=\"B$currentRow\" s=\"1\" t=\"inlineStr\"><is><t>CONSOLIDADO</t></is></c>\n")
        sb.append("      <c r=\"C$currentRow\" s=\"1\" t=\"inlineStr\"><is><t>MÉTRICAS GLOBAIS</t></is></c>\n")
        sb.append("    </row>\n")
        currentRow++

        // Total de cliente geral
        sb.append("    <row r=\"$currentRow\">\n")
        sb.append("      <c r=\"A$currentRow\" s=\"3\" t=\"inlineStr\"><is><t>Total de cliente</t></is></c>\n")
        sb.append("      <c r=\"B$currentRow\"><v>${summary.grandTotalClients}</v></c>\n")
        sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Total absoluto de clientes</t></is></c>\n")
        sb.append("    </row>\n")
        currentRow++

        // Total de atendimentos geral e tipos (Solo / Dupla / Grupo)
        sb.append("    <row r=\"$currentRow\">\n")
        sb.append("      <c r=\"A$currentRow\" s=\"3\" t=\"inlineStr\"><is><t>Total de atendimentos</t></is></c>\n")
        sb.append("      <c r=\"B$currentRow\"><v>${summary.grandTotalAttendances}</v></c>\n")
        sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Total absoluto de atendimentos</t></is></c>\n")
        sb.append("    </row>\n")
        currentRow++

        sb.append("    <row r=\"$currentRow\">\n")
        sb.append("      <c r=\"A$currentRow\" s=\"3\" t=\"inlineStr\"><is><t>• Total Solo</t></is></c>\n")
        sb.append("      <c r=\"B$currentRow\"><v>${summary.grandSoloCount}</v></c>\n")
        sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Atendimentos individuais</t></is></c>\n")
        sb.append("    </row>\n")
        currentRow++

        sb.append("    <row r=\"$currentRow\">\n")
        sb.append("      <c r=\"A$currentRow\" s=\"3\" t=\"inlineStr\"><is><t>• Total Dupla</t></is></c>\n")
        sb.append("      <c r=\"B$currentRow\"><v>${summary.grandDuplaCount}</v></c>\n")
        sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Atendimentos em dupla</t></is></c>\n")
        sb.append("    </row>\n")
        currentRow++

        sb.append("    <row r=\"$currentRow\">\n")
        sb.append("      <c r=\"A$currentRow\" s=\"3\" t=\"inlineStr\"><is><t>• Total Grupo</t></is></c>\n")
        sb.append("      <c r=\"B$currentRow\"><v>${summary.grandGrupoCount}</v></c>\n")
        sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Atendimentos em grupo</t></is></c>\n")
        sb.append("    </row>\n")
        currentRow++

        // Cliente manhã geral
        sb.append("    <row r=\"$currentRow\">\n")
        sb.append("      <c r=\"A$currentRow\" t=\"inlineStr\"><is><t>• Cliente manhã</t></is></c>\n")
        sb.append("      <c r=\"B$currentRow\"><v>${summary.grandMorningClients}</v></c>\n")
        sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Soma turnos manhã</t></is></c>\n")
        sb.append("    </row>\n")
        currentRow++

        // Clientes noite geral
        sb.append("    <row r=\"$currentRow\">\n")
        sb.append("      <c r=\"A$currentRow\" t=\"inlineStr\"><is><t>• Clientes noite</t></is></c>\n")
        sb.append("      <c r=\"B$currentRow\"><v>${summary.grandNightClients}</v></c>\n")
        sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Soma turnos noite</t></is></c>\n")
        sb.append("    </row>\n")
        currentRow++

        // Clientes novos geral
        sb.append("    <row r=\"$currentRow\">\n")
        sb.append("      <c r=\"A$currentRow\" t=\"inlineStr\"><is><t>• Total clientes novos</t></is></c>\n")
        sb.append("      <c r=\"B$currentRow\"><v>${summary.grandNewClients}</v></c>\n")
        sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Primeira visita</t></is></c>\n")
        sb.append("    </row>\n")
        currentRow++

        // Clientes recorrentes geral
        sb.append("    <row r=\"$currentRow\">\n")
        sb.append("      <c r=\"A$currentRow\" t=\"inlineStr\"><is><t>• Total clientes recorrentes</t></is></c>\n")
        sb.append("      <c r=\"B$currentRow\"><v>${summary.grandReturningClients}</v></c>\n")
        sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Clientes habituais</t></is></c>\n")
        sb.append("    </row>\n")
        currentRow++

        // Preferenciais geral
        sb.append("    <row r=\"$currentRow\">\n")
        sb.append("      <c r=\"A$currentRow\" t=\"inlineStr\"><is><t>• Total atendimentos preferenciais</t></is></c>\n")
        sb.append("      <c r=\"B$currentRow\"><v>${summary.grandPreferentialCount}</v></c>\n")
        sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>Total preferencial</t></is></c>\n")
        sb.append("    </row>\n")
        currentRow++

        // Origens geral
        sb.append("    <row r=\"$currentRow\">\n")
        sb.append("      <c r=\"A$currentRow\" s=\"2\" t=\"inlineStr\"><is><t>De onde conheceram (Consolidado)</t></is></c>\n")
        sb.append("      <c r=\"B$currentRow\" s=\"2\" t=\"inlineStr\"><is><t>Total Geral</t></is></c>\n")
        sb.append("      <c r=\"C$currentRow\" s=\"2\" t=\"inlineStr\"><is><t>% Total dos Novos</t></is></c>\n")
        sb.append("    </row>\n")
        currentRow++

        val allGrandOrigins = (standardOrigins + summary.grandOriginsBreakdown.keys).distinct()
        allGrandOrigins.forEach { origin ->
            val grandCount = summary.grandOriginsBreakdown[origin] ?: 0
            val pct = if (summary.grandNewClients > 0) "${(grandCount * 100) / summary.grandNewClients}%" else "0%"
            sb.append("    <row r=\"$currentRow\">\n")
            sb.append("      <c r=\"A$currentRow\" t=\"inlineStr\"><is><t>    $origin</t></is></c>\n")
            sb.append("      <c r=\"B$currentRow\"><v>$grandCount</v></c>\n")
            sb.append("      <c r=\"C$currentRow\" t=\"inlineStr\"><is><t>$pct</t></is></c>\n")
            sb.append("    </row>\n")
            currentRow++
        }

        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }
}
