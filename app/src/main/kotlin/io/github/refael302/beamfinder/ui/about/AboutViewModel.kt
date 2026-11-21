package io.github.refael302.beamfinder.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.refael302.beamfinder.update.InstallResult
import io.github.refael302.beamfinder.update.UpdateChecker
import io.github.refael302.beamfinder.update.UpdateInfo
import io.github.refael302.beamfinder.update.UpdateInstaller
import io.github.refael302.beamfinder.update.UpdateResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data object NotSupported : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data object Installing : UpdateUiState
    data class NeedsPermission(val info: UpdateInfo) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class AboutViewModel(
    val currentVersion: String,
    private val checker: UpdateChecker,
    private val installer: UpdateInstaller,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun checkForUpdate() {
        if (_state.value == UpdateUiState.Checking) return
        _state.value = UpdateUiState.Checking
        viewModelScope.launch {
            _state.value = when (val r = checker.check(currentVersion)) {
                UpdateResult.UpToDate -> UpdateUiState.UpToDate
                UpdateResult.NotSupported -> UpdateUiState.NotSupported
                is UpdateResult.Available -> UpdateUiState.Available(r.info)
                is UpdateResult.Error -> UpdateUiState.Error(r.message)
            }
        }
    }

    fun install() {
        val info = when (val s = _state.value) {
            is UpdateUiState.Available -> s.info
            is UpdateUiState.NeedsPermission -> s.info
            else -> return
        }
        _state.value = UpdateUiState.Installing
        viewModelScope.launch {
            _state.value = when (val r = installer.install(info)) {
                InstallResult.Started -> UpdateUiState.Installing
                InstallResult.NotSupported -> UpdateUiState.NotSupported
                InstallResult.NeedsPermission -> UpdateUiState.NeedsPermission(info)
                is InstallResult.Error -> UpdateUiState.Error(r.message)
            }
        }
    }
}
