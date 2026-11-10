package com.example.presentation.admin

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ExportRepository
import com.example.data.repository.ExportResult
import com.example.data.repository.ReceptionRepository
import com.example.data.sync.SyncManager
import com.example.domain.model.Attendance
import com.example.domain.model.AttendanceMember
import com.example.domain.model.Shift
import com.example.domain.model.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

enum class DateFilterOption(val label: String) {
    TODOS("Todos os períodos"),
    HOJE("Hoje"),
    ONTEM("Ontem"),
    ULTIMOS_7_DIAS("Últimos 7 dias")
}

data class AdminFilterState(
    val dateOption: DateFilterOption = DateFilterOption.TODOS,
    val selectedHosterId: String? = null,
    val selectedPeriod: String? = null, // "MANHA", "NOITE", or null
    val selectedOrigin: String? = null,
    val selectedClassification: String? = null, // "NOVO", "RECORRENTE", or null
    val preferentialOnly: Boolean = false
)

data class AdminMetrics(
    val totalPeople: Int = 0,
    val totalNewClients: Int = 0,
    val totalReturningClients: Int = 0,
    val totalAttendances: Int = 0,
    val preferentialCount: Int = 0,
    val originDistribution: Map<String, Int> = emptyMap(),
    val hosterPerformance: Map<String, Int> = emptyMap(),
    val morningAttendances: Int = 0,
    val nightAttendances: Int = 0,
    val openShiftsCount: Int = 0,
    val closedShiftsCount: Int = 0
)

data class AdminUiState(
    val filter: AdminFilterState = AdminFilterState(),
    val metrics: AdminMetrics = AdminMetrics(),
    val openShifts: List<Shift> = emptyList(),
    val isExporting: Boolean = false,
    val exportMessage: String? = null,
    val exportErrorMessage: String? = null,
    val exportFileToShare: File? = null
)

class AdminViewModel(
    private val receptionRepository: ReceptionRepository,
    private val exportRepository: ExportRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    val syncState: StateFlow<SyncState> = syncManager.syncState
    val pendingCount: StateFlow<Int> = syncManager.pendingCount

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private val allShifts = receptionRepository.getAllShiftsFlow()
    private val allAttendances = receptionRepository.getAllAttendancesFlow()

    init {
        // Fetch all remote data into cache immediately
        syncManager.triggerSync()

        viewModelScope.launch {
            combine(allShifts, allAttendances, _uiState) { shifts, attendances, state ->
                computeMetrics(shifts, attendances, state.filter)
            }.collect { (metrics, openShiftsList) ->
                _uiState.value = _uiState.value.copy(
                    metrics = metrics,
                    openShifts = openShiftsList
                )
            }
        }
    }

    fun refreshRemoteData() {
        syncManager.triggerSync()
    }

    private suspend fun computeMetrics(
        shifts: List<Shift>,
        attendances: List<Attendance>,
        filter: AdminFilterState
    ): Pair<AdminMetrics, List<Shift>> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        val (startMillis, endMillis) = when (filter.dateOption) {
            DateFilterOption.TODOS -> Pair(0L, Long.MAX_VALUE)
            DateFilterOption.HOJE -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, Long.MAX_VALUE)
            }
            DateFilterOption.ONTEM -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            DateFilterOption.ULTIMOS_7_DIAS -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
        }

        val shiftsById = shifts.associateBy { it.id }

        // Filter attendances
        val filtered = attendances.filter { att ->
            val matchDate = att.createdAt in startMillis..endMillis
            val matchHoster = filter.selectedHosterId == null || att.hosterId == filter.selectedHosterId
            val shift = shiftsById[att.shiftId]
            val matchPeriod = filter.selectedPeriod == null || shift?.period?.name == filter.selectedPeriod
            val matchClass = when (filter.selectedClassification) {
                "NOVO" -> att.newMembers > 0
                "RECORRENTE" -> att.returningMembers > 0
                else -> true
            }
            val matchPref = !filter.preferentialOnly || att.isPreferential
            matchDate && matchHoster && matchPeriod && matchClass && matchPref
        }

        val allMembers = receptionRepository.getAllMembers()
        val membersByAttendance = allMembers.groupBy { it.attendanceId }

        val originDist = mutableMapOf<String, Int>()
        filtered.forEach { att ->
            val members = membersByAttendance[att.id].orEmpty()
            members.forEach { m ->
                if (m.classification.name == "NOVO" && !m.origin.isNullOrBlank()) {
                    originDist[m.origin] = (originDist[m.origin] ?: 0) + 1
                }
            }
        }

        val hosterPerf = mutableMapOf<String, Int>()
        filtered.forEach { att ->
            hosterPerf[att.hosterName] = (hosterPerf[att.hosterName] ?: 0) + att.totalMembers
        }

        var morningCount = 0
        var nightCount = 0
        var prefCount = 0
        filtered.forEach { att ->
            val shift = shiftsById[att.shiftId]
            if (shift?.period?.name == "MANHA") morningCount += att.totalMembers
            if (shift?.period?.name == "NOITE") nightCount += att.totalMembers
            if (att.isPreferential) prefCount += 1
        }

        val openShifts = shifts.filter { it.status.name == "OPEN" }
        val closedShifts = shifts.filter { it.status.name == "CLOSED" }

        val metrics = AdminMetrics(
            totalPeople = filtered.sumOf { it.totalMembers },
            totalNewClients = filtered.sumOf { it.newMembers },
            totalReturningClients = filtered.sumOf { it.returningMembers },
            totalAttendances = filtered.size,
            preferentialCount = prefCount,
            originDistribution = originDist,
            hosterPerformance = hosterPerf,
            morningAttendances = morningCount,
            nightAttendances = nightCount,
            openShiftsCount = openShifts.size,
            closedShiftsCount = closedShifts.size
        )

        return Pair(metrics, openShifts)
    }

    fun togglePreferentialFilter() {
        _uiState.value = _uiState.value.copy(
            filter = _uiState.value.filter.copy(preferentialOnly = !_uiState.value.filter.preferentialOnly)
        )
    }

    fun setDateFilter(opt: DateFilterOption) {
        _uiState.value = _uiState.value.copy(
            filter = _uiState.value.filter.copy(dateOption = opt)
        )
    }

    fun setHosterFilter(hosterId: String?) {
        _uiState.value = _uiState.value.copy(
            filter = _uiState.value.filter.copy(selectedHosterId = hosterId)
        )
    }

    fun setPeriodFilter(period: String?) {
        _uiState.value = _uiState.value.copy(
            filter = _uiState.value.filter.copy(selectedPeriod = period)
        )
    }

    fun setClassificationFilter(cls: String?) {
        _uiState.value = _uiState.value.copy(
            filter = _uiState.value.filter.copy(selectedClassification = cls)
        )
    }

    fun clearExportMessages() {
        _uiState.value = _uiState.value.copy(
            exportMessage = null,
            exportErrorMessage = null,
            exportFileToShare = null
        )
    }

    fun exportExcel() {
        val filter = _uiState.value.filter
        val cal = Calendar.getInstance()
        val (startMillis, endMillis) = when (filter.dateOption) {
            DateFilterOption.TODOS -> Pair(0L, System.currentTimeMillis())
            DateFilterOption.HOJE -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                Pair(cal.timeInMillis, System.currentTimeMillis())
            }
            DateFilterOption.ONTEM -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                Pair(start, cal.timeInMillis)
            }
            DateFilterOption.ULTIMOS_7_DIAS -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                Pair(cal.timeInMillis, System.currentTimeMillis())
            }
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportMessage = null, exportErrorMessage = null)
            val result = exportRepository.exportReport(
                startMillis = startMillis,
                endMillis = endMillis,
                selectedHosterId = filter.selectedHosterId,
                selectedShiftPeriod = filter.selectedPeriod
            )
            _uiState.value = _uiState.value.copy(isExporting = false)

            when (result) {
                is ExportResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        exportMessage = "Relatório ${result.fileName} gerado com sucesso! (${result.rowCount} atendimentos)",
                        exportFileToShare = result.file
                    )
                }
                is ExportResult.Blocked -> {
                    _uiState.value = _uiState.value.copy(
                        exportErrorMessage = result.reason
                    )
                }
                is ExportResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        exportErrorMessage = result.message
                    )
                }
            }
        }
    }

    fun shareExportedFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartilhar Relatório XLSX"))
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                exportErrorMessage = "Erro ao abrir compartilhamento: ${e.message}"
            )
        }
    }
}
