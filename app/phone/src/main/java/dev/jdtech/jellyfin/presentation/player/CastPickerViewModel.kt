package dev.jdtech.jellyfin.presentation.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.cast.dlna.DlnaClient
import dev.jdtech.jellyfin.cast.dlna.DlnaDevice
import dev.jdtech.jellyfin.player.local.R as PlayerR
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CastPickerViewModel
@Inject
constructor(
    private val dlnaClient: DlnaClient,
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class UiState(
        val devices: List<DlnaDevice> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var discoveryJob: Job? = null

    fun discover() {
        discoveryJob?.cancel()
        _state.value = UiState(isLoading = true)

        discoveryJob = viewModelScope.launch {
            val found = mutableListOf<DlnaDevice>()
            val timeout =
                appPreferences
                    .getValue(appPreferences.dlnaDiscoveryTimeout)
                    .coerceIn(1_000L, 15_000L)
            try {
                dlnaClient.discoverDevices(Duration.ofMillis(timeout)).collect { device ->
                    found.removeAll { it.udn == device.udn }
                    found.add(device)
                    _state.update { it.copy(devices = found.toList()) }
                }
                _state.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = context.getString(PlayerR.string.cast_search_failed),
                    )
                }
            }
        }
    }
}
