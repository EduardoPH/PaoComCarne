package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.domain.model.ShiftStatus
import com.example.domain.util.DateTimeUtils
import com.example.domain.util.XlsxGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class ExportResult {
    data class Success(
        val file: File,
        val fileName: String,
        val rowCount: Int,
        val totalClients: Int = 0
    ) : ExportResult()
    data class Blocked(val reason: String, val openShiftsCount: Int = 0, val pendingSyncCount: Int = 0) : ExportResult()
    data class Failure(val message: String) : ExportResult()
}

class ExportRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val shiftDao = database.shiftDao()
    private val attendanceDao = database.attendanceDao()
    private val memberDao = database.attendanceMemberDao()
    private val syncQueueDao = database.syncQueueDao()

    suspend fun exportReport(
        startMillis: Long,
        endMillis: Long,
        selectedHosterId: String? = null,
        selectedShiftPeriod: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            // Rule 1: Check pending sync items
            val pendingCount = syncQueueDao.getPendingCount()
            if (pendingCount > 0) {
                return@withContext ExportResult.Blocked(
                    reason = "Existem $pendingCount registros pendentes de sincronização com o servidor. Sincronize antes de exportar.",
                    pendingSyncCount = pendingCount
                )
            }

            // Rule 2: Check open shifts in the selected period
            val openShifts = shiftDao.getOpenShiftsInRange(startMillis, endMillis)
            if (openShifts.isNotEmpty()) {
                val hosterNames = openShifts.joinToString(", ") { "${it.hosterName} (${it.period})" }
                return@withContext ExportResult.Blocked(
                    reason = "Existem turnos abertos no período selecionado que impedem o fechamento e a exportação do relatório: $hosterNames. Encerre os turnos primeiro.",
                    openShiftsCount = openShifts.size
                )
            }

            val shifts = shiftDao.getAllShifts().associateBy { it.id }

            // Fetch attendances in period
            val allAttendances = attendanceDao.getAllAttendances()
            val filteredAttendances = allAttendances.filter { att ->
                val matchDate = att.createdAt in startMillis..endMillis
                val matchHoster = selectedHosterId == null || att.hosterId == selectedHosterId
                val shift = shifts[att.shiftId]
                val matchPeriod = selectedShiftPeriod == null || shift?.period == selectedShiftPeriod
                matchDate && matchHoster && matchPeriod
            }.sortedBy { it.createdAt }

            if (filteredAttendances.isEmpty()) {
                return@withContext ExportResult.Failure("Nenhum atendimento encontrado para os critérios e período selecionados.")
            }

            val attendanceIds = filteredAttendances.map { it.id }.toSet()
            val allMembers = memberDao.getAllMembers().filter { it.attendanceId in attendanceIds }
            val memberMap = allMembers.groupBy { it.attendanceId }

            // Build Aba 1 rows (Atendimentos)
            val attendanceRows = filteredAttendances.map { att ->
                val shift = shifts[att.shiftId]
                val periodDisplay = shift?.let { if (it.period == "MANHA") "Manhã" else "Noite" } ?: "N/D"
                XlsxGenerator.AttendanceRow(
                    id = att.id,
                    date = DateTimeUtils.formatDateTime(att.createdAt),
                    shift = periodDisplay,
                    hoster = att.hosterName,
                    type = att.type,
                    isPreferential = if (att.isPreferential) "Sim" else "Não",
                    totalMembers = att.totalMembers,
                    newMembers = att.newMembers,
                    returningMembers = att.returningMembers
                )
            }

            // Build Aba 2 rows (Clientes Individuais)
            val clientRows = mutableListOf<XlsxGenerator.ClientRow>()
            filteredAttendances.forEach { att ->
                val members = memberMap[att.id].orEmpty()
                members.forEach { m ->
                    clientRows.add(
                        XlsxGenerator.ClientRow(
                            attendanceId = att.id,
                            hoster = att.hosterName,
                            memberNumber = m.memberNumber,
                            classification = if (m.classification == "NOVO") "Novo" else "Recorrente",
                            origin = m.origin ?: "—",
                            isPreferential = if (att.isPreferential) "Sim" else "Não"
                        )
                    )
                }
            }

            // Build Aba 3 (Resumo Diário e Consolidado Geral)
            val byDay = filteredAttendances.groupBy { DateTimeUtils.formatDate(it.createdAt) }
            val dailySummaries = byDay.map { (dateLabel, dayAtts) ->
                val dayTotalClients = dayAtts.sumOf { it.totalMembers }
                val dayMorning = dayAtts.filter { shifts[it.shiftId]?.period == "MANHA" }.sumOf { it.totalMembers }
                val dayNight = dayAtts.filter { shifts[it.shiftId]?.period == "NOITE" }.sumOf { it.totalMembers }
                val dayNew = dayAtts.sumOf { it.newMembers }
                val dayRet = dayAtts.sumOf { it.returningMembers }
                val dayPref = dayAtts.count { it.isPreferential }

                val dayOrigins = mutableMapOf<String, Int>()
                dayAtts.forEach { att ->
                    val members = memberMap[att.id].orEmpty()
                    members.filter { it.classification == "NOVO" }.forEach { m ->
                        val orig = m.origin?.trim()?.takeIf { it.isNotBlank() } ?: "Outros"
                        dayOrigins[orig] = (dayOrigins[orig] ?: 0) + 1
                    }
                }

                XlsxGenerator.DailySummary(
                    dateLabel = dateLabel,
                    totalClients = dayTotalClients,
                    morningClients = dayMorning,
                    nightClients = dayNight,
                    newClients = dayNew,
                    returningClients = dayRet,
                    preferentialCount = dayPref,
                    originsBreakdown = dayOrigins
                )
            }

            val grandOrigins = mutableMapOf<String, Int>()
            filteredAttendances.forEach { att ->
                val members = memberMap[att.id].orEmpty()
                members.filter { it.classification == "NOVO" }.forEach { m ->
                    val orig = m.origin?.trim()?.takeIf { it.isNotBlank() } ?: "Outros"
                    grandOrigins[orig] = (grandOrigins[orig] ?: 0) + 1
                }
            }

            val grandTotalClients = filteredAttendances.sumOf { it.totalMembers }
            val grandMorningClients = filteredAttendances.filter { shifts[it.shiftId]?.period == "MANHA" }.sumOf { it.totalMembers }
            val grandNightClients = filteredAttendances.filter { shifts[it.shiftId]?.period == "NOITE" }.sumOf { it.totalMembers }
            val grandNewClients = filteredAttendances.sumOf { it.newMembers }
            val grandReturningClients = filteredAttendances.sumOf { it.returningMembers }
            val grandPreferentialCount = filteredAttendances.count { it.isPreferential }

            val consolidatedSummary = XlsxGenerator.ReportConsolidatedSummary(
                dailySummaries = dailySummaries,
                grandTotalClients = grandTotalClients,
                grandMorningClients = grandMorningClients,
                grandNightClients = grandNightClients,
                grandNewClients = grandNewClients,
                grandReturningClients = grandReturningClients,
                grandPreferentialCount = grandPreferentialCount,
                grandOriginsBreakdown = grandOrigins
            )

            val startDateStr = DateTimeUtils.formatFileDate(startMillis)
            val endDateStr = DateTimeUtils.formatFileDate(endMillis)
            val periodSuffix = if (selectedShiftPeriod != null) "_${selectedShiftPeriod}" else ""
            val fileName = "PaoComCarne_Relatorio_${startDateStr}_a_${endDateStr}${periodSuffix}.xlsx"

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val targetFile = File(exportDir, fileName)

            XlsxGenerator.generateWorkbook(targetFile, attendanceRows, clientRows, consolidatedSummary)

            ExportResult.Success(
                file = targetFile,
                fileName = fileName,
                rowCount = attendanceRows.size,
                totalClients = grandTotalClients
            )
        } catch (e: Exception) {
            ExportResult.Failure(e.message ?: "Erro desconhecido ao gerar arquivo Excel.")
        }
    }
}
