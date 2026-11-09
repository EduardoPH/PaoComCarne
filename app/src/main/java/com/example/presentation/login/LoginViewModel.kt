package com.example.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuthRepository
import com.example.data.sync.SyncManager
import com.example.domain.model.Hoster
import com.example.domain.model.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LoginUiState(
    val selectedHoster: Hoster? = null,
    val password: String = "",
    val confirmPassword: String = "",
    val isFirstAccess: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    val hosters: StateFlow<List<Hoster>> = authRepository.getActiveHostersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncState: StateFlow<SyncState> = syncManager.syncState
    val pendingCount: StateFlow<Int> = syncManager.pendingCount

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun selectHoster(hoster: Hoster?) {
        _uiState.value = _uiState.value.copy(
            selectedHoster = hoster,
            isFirstAccess = if (hoster != null) !hoster.isActivated else false,
            password = "",
            confirmPassword = "",
            errorMessage = null
        )
    }

    fun clearSelectedHoster() {
        selectHoster(null)
    }

    fun onPasswordChange(text: String) {
        _uiState.value = _uiState.value.copy(password = text, errorMessage = null)
    }

    fun onConfirmPasswordChange(text: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = text, errorMessage = null)
    }

    fun triggerSync() {
        syncManager.triggerSync()
    }

    fun submit(onSuccess: (Hoster) -> Unit) {
        val state = _uiState.value
        val hoster = state.selectedHoster ?: run {
            _uiState.value = state.copy(errorMessage = "Selecione o seu nome na lista.")
            return
        }

        if (state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Informe a sua senha.")
            return
        }

        if (state.isFirstAccess) {
            if (state.password.length < 4) {
                _uiState.value = state.copy(errorMessage = "A senha deve ter no mínimo 4 caracteres.")
                return
            }
            if (state.password != state.confirmPassword) {
                _uiState.value = state.copy(errorMessage = "As senhas não coincidem.")
                return
            }

            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            viewModelScope.launch {
                val result = authRepository.activateHoster(hoster.id, state.password)
                _uiState.value = _uiState.value.copy(isLoading = false)
                result.fold(
                    onSuccess = { activatedHoster ->
                        onSuccess(activatedHoster)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            errorMessage = error.message ?: "Erro ao registrar primeiro acesso."
                        )
                    }
                )
            }
        } else {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            viewModelScope.launch {
                val result = authRepository.authenticate(hoster.id, state.password)
                _uiState.value = _uiState.value.copy(isLoading = false)
                result.fold(
                    onSuccess = { authenticatedHoster ->
                        onSuccess(authenticatedHoster)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            errorMessage = error.message ?: "Falha na autenticação."
                        )
                    }
                )
            }
        }
    }
}
