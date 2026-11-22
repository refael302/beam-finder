package io.github.refael302.beamfinder.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.refael302.beamfinder.update.InstallProgress
import io.github.refael302.beamfinder.update.InstallResult
import io.github.refael302.beamfinder.update.UpdateChecker
import io.github.refael302.beamfinder.update.UpdateInfo
import io.github.refael302.beamfinder.update.UpdateInstaller
import io.github.refael302.beamfinder.update.UpdateResult
import kotlinx.coroutines.Job
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
    data class Downloading(val info: UpdateInfo, val bytesRead: Long, val totalBytes: Long?) : UpdateUiState
    data class Preparing(val info: UpdateInfo) : UpdateUiState
    data class AwaitingConfirmation(val info: UpdateInfo) : UpdateUiState
    data class NeedsPermission(val info: UpdateInfo) : UpdateUiState
    data class Error(val message: String, val info: UpdateInfo? = null) : UpdateUiState
}

class AboutViewModel(
    val currentVersion: String,
    private val checker: UpdateChecker,
    private val installer: UpdateInstaller,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var installJob: Job? = null

    fun checkForUpdate() {
        if (_state.value == UpdateUiState.Checking) return
        if (isBusy(_state.value)) return
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
            is UpdateUiState.Error -> s.info ?: return
            else -> return
        }
        if (installJob?.isActive == true) return
        _state.value = UpdateUiState.Downloading(info, 0L, null)
        installJob = viewModelScope.launch {
            _state.value = when (
                val r = installer.install(info) { progress ->
                    _state.value = when (progress) {
                        is InstallProgress.Downloading ->
                            UpdateUiState.Downloading(info, progress.bytesRead, progress.totalBytes)
                        InstallProgress.Preparing -> UpdateUiState.Preparing(info)
                        InstallProgress.AwaitingConfirmation -> UpdateUiState.AwaitingConfirmation(info)
                    }
                }
            ) {
                InstallResult.Success -> UpdateUiState.AwaitingConfirmation(info) // app usually restarts
                InstallResult.NotSupported -> UpdateUiState.NotSupported
                InstallResult.NeedsPermission -> UpdateUiState.NeedsPermission(info)
                is InstallResult.Error -> UpdateUiState.Error(r.message, info)
            }
        }
    }

    fun cancelInstall() {
        installJob?.cancel()
        installJob = null
        val info = when (val s = _state.value) {
            is UpdateUiState.Downloading -> s.info
            is UpdateUiState.Preparing -> s.info
            is UpdateUiState.AwaitingConfirmation -> s.info
            else -> null
        }
        _state.value = if (info != null) UpdateUiState.Available(info) else UpdateUiState.Idle
    }

    private fun isBusy(state: UpdateUiState): Boolean = when (state) {
        is UpdateUiState.Downloading,
        is UpdateUiState.Preparing,
        is UpdateUiState.AwaitingConfirmation,
        -> true
        else -> false
    }
}
