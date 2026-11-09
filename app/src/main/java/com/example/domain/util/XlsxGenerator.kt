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

    fun generateWorkbook(
        destinationFile: File,
        attendances: List<AttendanceRow>,
        clients: List<ClientRow>
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
</Relationships>"""

    private fun buildWorkbookXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Atendimentos" sheetId="1" r:id="rId1"/>
    <sheet name="Clientes" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>"""

    private fun buildStylesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
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
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill>
      <patternFill patternType="solid">
        <fgColor rgb="FFA62B2B"/>
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
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
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

        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }
}
