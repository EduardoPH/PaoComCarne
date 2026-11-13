package com.example.presentation.admin

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ExportRepository
import com.example.data.repository.ExportResult
import com.example.data.repository.ReceptionRepository
import com.example.data.repository.SystemLogRepository
import com.example.data.sync.SyncManager
import com.example.domain.model.Attendance
import com.example.domain.model.AttendanceMember
import com.example.domain.model.Hoster
import com.example.domain.model.LogResult
import com.example.domain.model.Shift
import com.example.domain.model.SyncState
import com.example.domain.model.SystemLog
import com.example.domain.model.SystemLogActions
import com.example.domain.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

enum class DateFilterOption(val label: String) {
    HOJE("Hoje"),
    ONTEM("Ontem"),
    ULTIMOS_7_DIAS("Últimos 7 dias"),
    ULTIMOS_30_DIAS("Últimos 30 dias"),
    MES_ATUAL("Este mês"),
    MES_ANTERIOR("Mês anterior"),
    INTERVALO_PERSONALIZADO("Personalizado"),
    TODOS("Todo o período")
}

enum class AdminSubTab(val label: String) {
    INDICADORES("Indicadores & Relatórios"),
    LOGS("Logs do Sistema")
}

data class AdminFilterState(
    val dateOption: DateFilterOption = DateFilterOption.HOJE,
    val draftStartMillis: Long = AdminDateCalculator.startOfDay(System.currentTimeMillis()),
    val draftEndMillis: Long = AdminDateCalculator.endOfDay(System.currentTimeMillis()),
    val appliedStartMillis: Long = AdminDateCalculator.startOfDay(System.currentTimeMillis()),
    val appliedEndMillis: Long = AdminDateCalculator.endOfDay(System.currentTimeMillis()),
    val selectedHosterId: String? = null,
    val selectedPeriod: String? = null, // "MANHA", "NOITE", or null (Todos)
    val selectedOrigin: String? = null,
    val selectedClassification: String? = null, // "NOVO", "RECORRENTE", or null
    val preferentialOnly: Boolean = false
)

data class ExportSuccessData(
    val file: File,
    val fileName: String,
    val rowCount: Int,
    val totalClients: Int,
    val periodLabel: String
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
    val activeSubTab: AdminSubTab = AdminSubTab.INDICADORES,
    val filter: AdminFilterState = AdminFilterState(),
    val activePeriodSummary: String = "Hoje",
    val dateValidationError: String? = null,
    val metrics: AdminMetrics = AdminMetrics(),
    val availableHosters: List<Hoster> = emptyList(),
    val openShifts: List<Shift> = emptyList(),
    val logsFilter: SystemLogsFilterState = SystemLogsFilterState(),
    val filteredLogs: List<SystemLog> = emptyList(),
    val availableLogUsers: List<String> = emptyList(),
    val availableLogActions: List<String> = emptyList(),
    val availableLogDevices: List<String> = emptyList(),
    val isExporting: Boolean = false,
    val exportSuccessData: ExportSuccessData? = null,
    val exportErrorMessage: String? = null
)

object AdminDateCalculator {
    fun startOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun endOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun validateDateRange(startMillis: Long, endMillis: Long): String? {
        return if (startOfDay(startMillis) > startOfDay(endMillis)) {
            "A data inicial não pode ser posterior à data final."
        } else {
            null
        }
    }

    fun rangeForOption(
        option: DateFilterOption,
        customStart: Long,
        customEnd: Long,
        referenceMillis: Long = System.currentTimeMillis()
    ): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = referenceMillis }
        return when (option) {
            DateFilterOption.HOJE -> {
                startOfDay(referenceMillis) to endOfDay(referenceMillis)
            }
            DateFilterOption.ONTEM -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                startOfDay(cal.timeInMillis) to endOfDay(cal.timeInMillis)
            }
            DateFilterOption.ULTIMOS_7_DIAS -> {
                val end = endOfDay(referenceMillis)
                cal.add(Calendar.DAY_OF_YEAR, -6)
                startOfDay(cal.timeInMillis) to end
            }
            DateFilterOption.ULTIMOS_30_DIAS -> {
                val end = endOfDay(referenceMillis)
                cal.add(Calendar.DAY_OF_YEAR, -29)
                startOfDay(cal.timeInMillis) to end
            }
            DateFilterOption.MES_ATUAL -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(cal.timeInMillis)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                start to endOfDay(cal.timeInMillis)
            }
            DateFilterOption.MES_ANTERIOR -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(cal.timeInMillis)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                start to endOfDay(cal.timeInMillis)
            }
            DateFilterOption.INTERVALO_PERSONALIZADO -> {
                startOfDay(customStart) to endOfDay(customEnd)
            }
            DateFilterOption.TODOS -> {
                0L to Long.MAX_VALUE
            }
        }
    }

    fun computeMetrics(
        shifts: List<Shift>,
        attendances: List<Attendance>,
        allMembers: List<AttendanceMember>,
        filter: AdminFilterState
    ): Pair<AdminMetrics, List<Shift>> {
        val (startMillis, endMillis) = rangeForOption(
            option = filter.dateOption,
            customStart = filter.appliedStartMillis,
            customEnd = filter.appliedEndMillis
        )
        val shiftsById = shifts.associateBy { it.id }
        val membersByAttendance = allMembers.groupBy { it.attendanceId }

        val filtered = attendances.filter { att ->
            if (att.isDeleted) return@filter false
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
            val members = membersByAttendance[att.id].orEmpty()
            val matchOrigin = filter.selectedOrigin == null || members.any {
                it.classification.name == "NOVO" && it.origin.equals(filter.selectedOrigin, ignoreCase = true)
            }
            matchDate && matchHoster && matchPeriod && matchClass && matchPref && matchOrigin
        }

        val originDist = mutableMapOf<String, Int>()
        filtered.forEach { att ->
            val members = membersByAttendance[att.id].orEmpty()
            members.forEach { m ->
                if (m.classification.name == "NOVO" && !m.origin.isNullOrBlank()) {
                    if (filter.selectedOrigin == null || m.origin.equals(filter.selectedOrigin, ignoreCase = true)) {
                        originDist[m.origin] = (originDist[m.origin] ?: 0) + 1
                    }
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

        return metrics to openShifts
    }
}

class AdminViewModel(
    private val receptionRepository: ReceptionRepository,
    private val exportRepository: ExportRepository,
    private val syncManager: SyncManager,
    private val systemLogRepository: SystemLogRepository? = null
) : ViewModel() {

    val syncState: StateFlow<SyncState> = syncManager.syncState
    val pendingCount: StateFlow<Int> = syncManager.pendingCount

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private val filterFlow = MutableStateFlow(AdminFilterState())
    private val logsFilterFlow = MutableStateFlow(SystemLogsFilterState())

    private val allShifts = receptionRepository.getAllShiftsFlow()
    private val allAttendances = receptionRepository.getAllAttendancesFlow()
    private val allMembers = receptionRepository.getAllMembersFlow()
    private val activeHosters = receptionRepository.getActiveHostersFlow()
    private val allLogs = receptionRepository.getSystemLogsFlow()

    private var hasLoggedAdminAccess = false

    init {
        syncManager.triggerSync()

        viewModelScope.launch {
            combine(
                allShifts,
                allAttendances,
                allMembers,
                activeHosters,
                filterFlow
            ) { shifts, attendances, members, hosters, filter ->
                val (metrics, openShifts) = AdminDateCalculator.computeMetrics(
                    shifts = shifts,
                    attendances = attendances,
                    allMembers = members,
                    filter = filter
                )
                Triple(metrics, openShifts, hosters)
            }.collect { (metrics, openShifts, hosters) ->
                val currentFilter = filterFlow.value
                _uiState.value = _uiState.value.copy(
                    filter = currentFilter,
                    activePeriodSummary = getPeriodDisplayLabel(currentFilter),
                    metrics = metrics,
                    openShifts = openShifts,
                    availableHosters = hosters
                )
            }
        }

        viewModelScope.launch {
            combine(allLogs, logsFilterFlow) { logs, lFilter ->
                val users = logs.mapNotNull { it.userName?.takeIf { n -> n.isNotBlank() } }.distinct().sorted()
                val actions = logs.map { it.action }.distinct().sorted()
                val devices = logs.map { it.deviceName }.distinct().sorted()
                val filtered = logs.filter { item ->
                    val matchStart = lFilter.startMillis == null || item.createdAt >= lFilter.startMillis
                    val matchEnd = lFilter.endMillis == null || item.createdAt <= lFilter.endMillis
                    val matchUser = lFilter.selectedUser == null || item.userName == lFilter.selectedUser
                    val matchAction = lFilter.selectedAction == null || item.action == lFilter.selectedAction
                    val matchDevice = lFilter.selectedDevice == null || item.deviceName == lFilter.selectedDevice
                    val matchResult = lFilter.selectedResult == null || item.result == lFilter.selectedResult
                    matchStart && matchEnd && matchUser && matchAction && matchDevice && matchResult
                }
                Triple(filtered, Triple(users, actions, devices), lFilter)
            }.collect { (filtered, meta, lFilter) ->
                _uiState.value = _uiState.value.copy(
                    logsFilter = lFilter,
                    filteredLogs = filtered,
                    availableLogUsers = meta.first,
                    availableLogActions = meta.second,
                    availableLogDevices = meta.third
                )
            }
        }
    }

    fun onAdminScreenOpened(actorId: String? = null, actorName: String? = null) {
        if (!hasLoggedAdminAccess) {
            hasLoggedAdminAccess = true
            systemLogRepository?.logAsync(
                action = SystemLogActions.ADMIN_ACCESS,
                entityType = "ADMIN",
                result = LogResult.SUCCESS,
                description = "Acesso ao painel Administrativo.",
                userId = actorId,
                userName = actorName
            )
        }
    }

    fun setSubTab(tab: AdminSubTab) {
        _uiState.value = _uiState.value.copy(activeSubTab = tab)
    }

    fun refreshRemoteData() {
        syncManager.triggerSync()
    }

    private fun getPeriodDisplayLabel(filter: AdminFilterState): String {
        val periodStr = when (filter.selectedPeriod) {
            "MANHA" -> " • Turno Manhã"
            "NOITE" -> " • Turno Noite"
            else -> ""
        }
        val base = when (filter.dateOption) {
            DateFilterOption.HOJE -> "Hoje (${DateTimeUtils.formatDate(filter.appliedStartMillis)})"
            DateFilterOption.ONTEM -> "Ontem (${DateTimeUtils.formatDate(filter.appliedStartMillis)})"
            DateFilterOption.ULTIMOS_7_DIAS -> "Últimos 7 dias (${DateTimeUtils.formatDate(filter.appliedStartMillis)} - ${DateTimeUtils.formatDate(filter.appliedEndMillis)})"
            DateFilterOption.ULTIMOS_30_DIAS -> "Últimos 30 dias (${DateTimeUtils.formatDate(filter.appliedStartMillis)} - ${DateTimeUtils.formatDate(filter.appliedEndMillis)})"
            DateFilterOption.MES_ATUAL -> "Este mês (${DateTimeUtils.formatDate(filter.appliedStartMillis)} - ${DateTimeUtils.formatDate(filter.appliedEndMillis)})"
            DateFilterOption.MES_ANTERIOR -> "Mês anterior (${DateTimeUtils.formatDate(filter.appliedStartMillis)} - ${DateTimeUtils.formatDate(filter.appliedEndMillis)})"
            DateFilterOption.INTERVALO_PERSONALIZADO -> "${DateTimeUtils.formatDate(filter.appliedStartMillis)} até ${DateTimeUtils.formatDate(filter.appliedEndMillis)}"
            DateFilterOption.TODOS -> "Todo o período"
        }
        return "$base$periodStr"
    }

    fun setDraftStartDate(millis: Long) {
        val cleanStart = AdminDateCalculator.startOfDay(millis)
        val err = AdminDateCalculator.validateDateRange(cleanStart, filterFlow.value.draftEndMillis)
        _uiState.value = _uiState.value.copy(dateValidationError = err)
        filterFlow.value = filterFlow.value.copy(
            dateOption = DateFilterOption.INTERVALO_PERSONALIZADO,
            draftStartMillis = cleanStart
        )
    }

    fun setDraftEndDate(millis: Long) {
        val cleanEnd = AdminDateCalculator.endOfDay(millis)
        val err = AdminDateCalculator.validateDateRange(filterFlow.value.draftStartMillis, cleanEnd)
        _uiState.value = _uiState.value.copy(dateValidationError = err)
        filterFlow.value = filterFlow.value.copy(
            dateOption = DateFilterOption.INTERVALO_PERSONALIZADO,
            draftEndMillis = cleanEnd
        )
    }

    fun applyDraftFilters(actorId: String? = null, actorName: String? = null) {
        val current = filterFlow.value
        val err = AdminDateCalculator.validateDateRange(current.draftStartMillis, current.draftEndMillis)
        if (err != null) {
            _uiState.value = _uiState.value.copy(dateValidationError = err)
            return
        }
        _uiState.value = _uiState.value.copy(dateValidationError = null)
        val updated = current.copy(
            dateOption = if (current.dateOption == DateFilterOption.TODOS) {
                DateFilterOption.INTERVALO_PERSONALIZADO
            } else {
                current.dateOption
            },
            appliedStartMillis = AdminDateCalculator.startOfDay(current.draftStartMillis),
            appliedEndMillis = AdminDateCalculator.endOfDay(current.draftEndMillis)
        )
        filterFlow.value = updated
        logFilterApplied(updated, actorId, actorName)
    }

    fun setDateFilter(opt: DateFilterOption, actorId: String? = null, actorName: String? = null) {
        val current = filterFlow.value
        val (start, end) = AdminDateCalculator.rangeForOption(
            option = opt,
            customStart = current.draftStartMillis,
            customEnd = current.draftEndMillis
        )
        _uiState.value = _uiState.value.copy(dateValidationError = null)
        val updated = if (opt == DateFilterOption.TODOS) {
            current.copy(
                dateOption = opt,
                appliedStartMillis = 0L,
                appliedEndMillis = Long.MAX_VALUE
            )
        } else {
            current.copy(
                dateOption = opt,
                draftStartMillis = start,
                draftEndMillis = end,
                appliedStartMillis = start,
                appliedEndMillis = end
            )
        }
        filterFlow.value = updated
        logFilterApplied(updated, actorId, actorName)
    }

    fun clearAllFilters(actorId: String? = null, actorName: String? = null) {
        val today = System.currentTimeMillis()
        val start = AdminDateCalculator.startOfDay(today)
        val end = AdminDateCalculator.endOfDay(today)
        _uiState.value = _uiState.value.copy(dateValidationError = null)
        val updated = AdminFilterState(
            dateOption = DateFilterOption.TODOS,
            draftStartMillis = start,
            draftEndMillis = end,
            appliedStartMillis = 0L,
            appliedEndMillis = Long.MAX_VALUE
        )
        filterFlow.value = updated
        logFilterApplied(updated, actorId, actorName)
    }

    fun setHosterFilter(hosterId: String?) {
        filterFlow.value = filterFlow.value.copy(selectedHosterId = hosterId)
    }

    fun setPeriodFilter(period: String?) {
        filterFlow.value = filterFlow.value.copy(selectedPeriod = period)
    }

    fun setOriginFilter(origin: String?) {
        filterFlow.value = filterFlow.value.copy(selectedOrigin = origin)
    }

    fun setClassificationFilter(cls: String?) {
        filterFlow.value = filterFlow.value.copy(selectedClassification = cls)
    }

    fun togglePreferentialFilter() {
        filterFlow.value = filterFlow.value.copy(preferentialOnly = !filterFlow.value.preferentialOnly)
    }

    private fun logFilterApplied(filter: AdminFilterState, actorId: String?, actorName: String?) {
        systemLogRepository?.logAsync(
            action = SystemLogActions.ADMIN_FILTER_APPLIED,
            entityType = "ADMIN",
            result = LogResult.SUCCESS,
            description = "Filtros aplicados: ${getPeriodDisplayLabel(filter)}.",
            userId = actorId,
            userName = actorName
        )
    }

    // System logs filters
    fun setLogsStartDate(millis: Long?) {
        logsFilterFlow.value = logsFilterFlow.value.copy(startMillis = millis)
    }

    fun setLogsEndDate(millis: Long?) {
        logsFilterFlow.value = logsFilterFlow.value.copy(endMillis = millis)
    }

    fun setLogsUserFilter(user: String?) {
        logsFilterFlow.value = logsFilterFlow.value.copy(selectedUser = user)
    }

    fun setLogsActionFilter(action: String?) {
        logsFilterFlow.value = logsFilterFlow.value.copy(selectedAction = action)
    }

    fun setLogsDeviceFilter(device: String?) {
        logsFilterFlow.value = logsFilterFlow.value.copy(selectedDevice = device)
    }

    fun setLogsResultFilter(result: LogResult?) {
        logsFilterFlow.value = logsFilterFlow.value.copy(selectedResult = result)
    }

    fun clearLogsFilters() {
        logsFilterFlow.value = SystemLogsFilterState()
    }

    fun dismissExportDialog() {
        _uiState.value = _uiState.value.copy(exportSuccessData = null)
    }

    fun dismissExportError() {
        _uiState.value = _uiState.value.copy(exportErrorMessage = null)
    }

    fun exportExcel(actorId: String? = null, actorName: String? = null) {
        val filter = filterFlow.value
        val (startMillis, endMillis) = AdminDateCalculator.rangeForOption(
            option = filter.dateOption,
            customStart = filter.appliedStartMillis,
            customEnd = filter.appliedEndMillis
        )
        val periodLabel = getPeriodDisplayLabel(filter)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExporting = true,
                exportSuccessData = null,
                exportErrorMessage = null
            )
            val result = exportRepository.exportReport(
                startMillis = startMillis,
                endMillis = endMillis,
                selectedHosterId = filter.selectedHosterId,
                selectedShiftPeriod = filter.selectedPeriod,
                selectedOrigin = filter.selectedOrigin,
                selectedClassification = filter.selectedClassification,
                preferentialOnly = filter.preferentialOnly,
                actorUserId = actorId,
                actorUserName = actorName
            )
            _uiState.value = _uiState.value.copy(isExporting = false)

            when (result) {
                is ExportResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        exportSuccessData = ExportSuccessData(
                            file = result.file,
                            fileName = result.fileName,
                            rowCount = result.rowCount,
                            totalClients = result.totalClients,
                            periodLabel = periodLabel
                        )
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
                exportErrorMessage = "Erro ao compartilhar arquivo: ${e.message}"
            )
        }
    }

    fun openExportedFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            shareExportedFile(context, file)
        }
    }
}
