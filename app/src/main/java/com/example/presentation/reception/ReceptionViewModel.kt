package com.example.presentation.reception

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuthRepository
import com.example.data.repository.ReceptionRepository
import com.example.data.sync.SyncManager
import com.example.domain.model.Attendance
import com.example.domain.model.AttendanceMember
import com.example.domain.model.AttendanceType
import com.example.domain.model.AttendanceWithMembers
import com.example.domain.model.Hoster
import com.example.domain.model.QueueEvent
import com.example.domain.model.Shift
import com.example.domain.model.ShiftPeriod
import com.example.domain.model.SyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReceptionUiState(
    val hoster: Hoster? = null,
    val shift: Shift? = null,
    val showNewAttendanceDialog: Boolean = false,
    val showUpdateQueueDialog: Boolean = false,
    val showCorrectionDialog: Boolean = false,
    val showCloseShiftDialog: Boolean = false,
    val showPeriodSelectDialog: Boolean = false,
    val attendancePendingDeletion: AttendanceWithMembers? = null,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null,
    val isProcessing: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReceptionViewModel(
    private val authRepository: AuthRepository,
    private val receptionRepository: ReceptionRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    val currentHoster: StateFlow<Hoster?> = authRepository.currentHoster
    val syncState: StateFlow<SyncState> = syncManager.syncState
    val pendingCount: StateFlow<Int> = syncManager.pendingCount

    private val _uiState = MutableStateFlow(ReceptionUiState())
    val uiState: StateFlow<ReceptionUiState> = _uiState.asStateFlow()

    // Current shift reactive flow
    val currentShift: StateFlow<Shift?> = currentHoster.flatMapLatest { hoster ->
        if (hoster != null) {
            receptionRepository.getActiveShiftFlow(hoster.id)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Current queue state reactive flow
    val latestQueue: StateFlow<QueueEvent?> = currentShift.flatMapLatest { shift ->
        if (shift != null) {
            receptionRepository.getLatestQueueFlow(shift.id)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Attendances in this shift
    val shiftAttendances: StateFlow<List<AttendanceWithMembers>> = currentShift.flatMapLatest { shift ->
        if (shift != null) {
            receptionRepository.getAttendancesWithMembersForShiftFlow(shift.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun triggerSync() {
        syncManager.triggerSync()
    }

    fun openShift(period: ShiftPeriod) {
        val hoster = currentHoster.value ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, showPeriodSelectDialog = false)
            val result = receptionRepository.openShift(hoster.id, hoster.name, period)
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                feedbackMessage = if (result.isSuccess) "Turno ${period.displayName} aberto com sucesso!" else null,
                errorMessage = result.exceptionOrNull()?.message
            )
        }
    }

    fun setShowNewAttendance(show: Boolean) {
        _uiState.value = _uiState.value.copy(showNewAttendanceDialog = show)
    }

    fun setShowUpdateQueue(show: Boolean) {
        _uiState.value = _uiState.value.copy(showUpdateQueueDialog = show)
    }

    fun setShowCorrection(show: Boolean) {
        _uiState.value = _uiState.value.copy(showCorrectionDialog = show)
    }

    fun setShowCloseShift(show: Boolean) {
        _uiState.value = _uiState.value.copy(showCloseShiftDialog = show)
    }

    fun setShowPeriodSelect(show: Boolean) {
        _uiState.value = _uiState.value.copy(showPeriodSelectDialog = show)
    }

    fun setAttendanceToDelete(item: AttendanceWithMembers?) {
        _uiState.value = _uiState.value.copy(attendancePendingDeletion = item)
    }

    fun confirmDeleteAttendance(reason: String?) {
        val item = _uiState.value.attendancePendingDeletion ?: return
        val hoster = currentHoster.value ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                attendancePendingDeletion = null
            )
            val res = receptionRepository.softDeleteAttendance(
                attendanceId = item.attendance.id,
                actorHosterId = hoster.id,
                actorHosterName = hoster.name,
                isActorAdmin = hoster.isAdmin,
                reason = reason
            )
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                feedbackMessage = if (res.isSuccess) "Atendimento excluído com sucesso! Indicadores atualizados." else null,
                errorMessage = res.exceptionOrNull()?.message
            )
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null, errorMessage = null)
    }

    fun registerAttendance(
        type: AttendanceType,
        total: Int,
        newCount: Int,
        returningCount: Int,
        isPreferential: Boolean = false,
        members: List<AttendanceMember>
    ) {
        val shift = currentShift.value ?: return
        val hoster = currentHoster.value ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, showNewAttendanceDialog = false)
            val res = receptionRepository.registerAttendance(
                shiftId = shift.id,
                hosterId = hoster.id,
                hosterName = hoster.name,
                type = type,
                totalMembers = total,
                newMembers = newCount,
                returningMembers = returningCount,
                membersList = members,
                isPreferential = isPreferential
            )
            val prefLabel = if (isPreferential) " (Preferencial)" else ""
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                feedbackMessage = if (res.isSuccess) "Atendimento ${type.displayName}$prefLabel registrado com sucesso!" else null,
                errorMessage = res.exceptionOrNull()?.message
            )
        }
    }

    fun correctLastAttendance(
        newType: AttendanceType,
        newTotal: Int,
        newNewCount: Int,
        newReturningCount: Int,
        newIsPreferential: Boolean = false,
        newMembers: List<AttendanceMember>
    ) {
        val shift = currentShift.value ?: return
        val hoster = currentHoster.value ?: return
        val lastAtt = shiftAttendances.value.lastOrNull()?.attendance ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, showCorrectionDialog = false)
            val res = receptionRepository.correctLastAttendance(
                shiftId = shift.id,
                attendanceId = lastAtt.id,
                editorHosterId = hoster.id,
                editorHosterName = hoster.name,
                newType = newType,
                newTotal = newTotal,
                newNewMembers = newNewCount,
                newReturningMembers = newReturningCount,
                newMembersList = newMembers,
                newIsPreferential = newIsPreferential
            )
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                feedbackMessage = if (res.isSuccess) "Atendimento corrigido com sucesso! Auditoria registrada." else null,
                errorMessage = res.exceptionOrNull()?.message
            )
        }
    }

    fun updateQueue(duos: Int, groups: Int) {
        val shift = currentShift.value ?: return
        val hoster = currentHoster.value ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, showUpdateQueueDialog = false)
            val res = receptionRepository.updateQueue(shift.id, hoster.id, duos, groups)
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                feedbackMessage = if (res.isSuccess) "Fila de espera atualizada!" else null,
                errorMessage = res.exceptionOrNull()?.message
            )
        }
    }

    fun closeShift(onShiftClosed: () -> Unit) {
        val shift = currentShift.value ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, showCloseShiftDialog = false)
            val res = receptionRepository.closeShift(shift.id)
            _uiState.value = _uiState.value.copy(isProcessing = false)
            if (res.isSuccess) {
                onShiftClosed()
            } else {
                _uiState.value = _uiState.value.copy(errorMessage = res.exceptionOrNull()?.message)
            }
        }
    }

    suspend fun updateOwnName(newName: String): Result<Unit> {
        val hoster = currentHoster.value ?: return Result.failure(Exception("Nenhum hoster conectado."))
        return authRepository.updateHosterName(hoster.id, newName)
    }

    suspend fun changeOwnPassword(currentPin: String, newPin: String): Result<Unit> {
        val hoster = currentHoster.value ?: return Result.failure(Exception("Nenhum hoster conectado."))
        return authRepository.changeOwnPassword(hoster.id, currentPin, newPin)
    }
}
