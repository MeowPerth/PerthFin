package dev.jdtech.jellyfin.presentation.player

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.cast.dlna.DlnaDevice
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.player.local.R as PlayerR
import dev.jdtech.jellyfin.player.local.cast.CastState
import dev.jdtech.jellyfin.player.local.cast.CastStatus
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDeviceSheet(
    onDismiss: () -> Unit,
    onSelect: (DlnaDevice) -> Unit,
    viewModel: CastPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.discover() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { PlayerSheetDragHandle() },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(PlayerR.string.cast_to_tv),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(12.dp))

            when {
                state.devices.isEmpty() && state.isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            text = stringResource(PlayerR.string.cast_searching),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }

                state.devices.isEmpty() -> {
                    Text(
                        text = stringResource(PlayerR.string.cast_no_devices),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(PlayerR.string.cast_no_devices_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    TextButton(onClick = viewModel::discover) {
                        Text(stringResource(PlayerR.string.cast_retry))
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(state.devices, key = { it.udn }) { device ->
                            DeviceRow(
                                device = device,
                                onClick = { onSelect(device) },
                            )
                        }
                    }
                    if (state.isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Text(
                                text = stringResource(PlayerR.string.cast_searching),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    } else {
                        TextButton(onClick = viewModel::discover) {
                            Text(stringResource(PlayerR.string.cast_retry))
                        }
                    }
                }
            }

            state.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DlnaDevice, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(enabled = device.canPlay(), onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(CoreR.drawable.ic_tv),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Column(Modifier.padding(start = 16.dp)) {
            Text(
                text = device.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle =
                listOfNotNull(device.modelName, device.manufacturer)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CastControls(
    state: CastState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onStop: () -> Unit,
    onAudioTrack: () -> Unit,
    onSubtitleTrack: () -> Unit,
    onQuality: () -> Unit,
    onEpisodes: () -> Unit,
    showEpisodes: Boolean = true,
    notchModeEnabled: Boolean = true,
) {
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val durationMs = state.durationMs.coerceAtLeast(1)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val buttonWidth = if (isLandscape) 56.dp else 40.dp
    val buttonHeight = 40.dp
    val buttonSpacing = if (isLandscape) 16.dp else 4.dp
    // Material Slider insets its visible track by ~10dp. Pad by half a button
    // minus that inset so the track starts/ends at the button centres.
    val sliderHorizontalPadding = (buttonWidth / 2) - 10.dp

    val layoutDirection = LocalLayoutDirection.current
    val compensateCutout = !isLandscape || notchModeEnabled
    val cutoutInsets =
        if (compensateCutout) WindowInsets.displayCutout else WindowInsets(0, 0, 0, 0)
    val cutoutPadding = cutoutInsets.asPaddingValues()
    val cutoutLeft = cutoutPadding.calculateLeftPadding(layoutDirection)
    val cutoutRight = cutoutPadding.calculateRightPadding(layoutDirection)
    // The Column avoids the cutout; compensate so the exit pill is centred on
    // the physical screen rather than on the reduced content area.
    val exitOffset = (cutoutRight - cutoutLeft) / 2

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f))) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(cutoutInsets)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            // Same top-row geometry as the local player: the two leading slots
            // (PiP/speed) are reserved so audio/subtitle/bitrate line up exactly.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.width(buttonWidth).height(buttonHeight),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_arrow_left),
                        contentDescription = stringResource(PlayerR.string.player_controls_exit),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(buttonWidth).height(buttonHeight))
                Spacer(Modifier.width(buttonWidth).height(buttonHeight))
                IconButton(
                    onClick = onAudioTrack,
                    modifier = Modifier.width(buttonWidth).height(buttonHeight),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_speaker),
                        contentDescription = stringResource(PlayerR.string.select_audio_track),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(
                    onClick = onSubtitleTrack,
                    modifier = Modifier.width(buttonWidth).height(buttonHeight),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_closed_caption),
                        contentDescription = stringResource(PlayerR.string.select_subtitle_track),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(
                    onClick = onQuality,
                    modifier = Modifier.width(buttonWidth).height(buttonHeight),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_settings),
                        contentDescription = stringResource(PlayerR.string.select_bitrate),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Second row mirrors the local player's second control row: status on
            // the left and three reserved slots on the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
            ) {
                Text(
                    text =
                        when (state.status) {
                            CastStatus.CONNECTING ->
                                stringResource(
                                    PlayerR.string.cast_connecting,
                                    state.device?.displayName.orEmpty(),
                                )
                            CastStatus.ERROR ->
                                state.errorMessage ?: stringResource(PlayerR.string.cast_error)
                            CastStatus.PAUSED -> stringResource(PlayerR.string.cast_status_paused)
                            else ->
                                stringResource(
                                    PlayerR.string.cast_playing_on,
                                    state.device?.displayName.orEmpty(),
                                )
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f).padding(start = buttonWidth + buttonSpacing),
                )
                if (showEpisodes) {
                    IconButton(
                        onClick = onEpisodes,
                        modifier = Modifier.width(buttonWidth).height(buttonHeight),
                    ) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_logs),
                            contentDescription = stringResource(PlayerR.string.episodes),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Box(
                Modifier.fillMaxWidth().offset(x = exitOffset),
                contentAlignment = Alignment.Center,
            ) {
                OutlinedButton(
                    onClick = onStop,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_x),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(PlayerR.string.cast_exit),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Spacer(Modifier.weight(1f))

            Slider(
                value =
                    (dragPosition ?: state.positionMs.toFloat()).coerceIn(0f, durationMs.toFloat()),
                onValueChange = { dragPosition = it },
                onValueChangeFinished = {
                    dragPosition?.let { onSeek(it.toLong()) }
                    dragPosition = null
                },
                valueRange = 0f..durationMs.toFloat(),
                enabled =
                    state.durationMs > 0 &&
                        (state.status == CastStatus.PLAYING || state.status == CastStatus.PAUSED),
                colors =
                    SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor =
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
                        disabledThumbColor = MaterialTheme.colorScheme.primary,
                        disabledActiveTrackColor = MaterialTheme.colorScheme.primary,
                        disabledInactiveTrackColor =
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
                    ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = sliderHorizontalPadding),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = buttonWidth / 2),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime(dragPosition?.toLong() ?: state.positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
                Text(
                    text = formatTime(state.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }

        // Screen-centred play/pause control.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FilledIconButton(
                onClick = onPlayPause,
                enabled = state.status != CastStatus.CONNECTING && state.status != CastStatus.ERROR,
                colors =
                    if (state.status == CastStatus.ERROR) {
                        IconButtonDefaults.filledIconButtonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.primary,
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        IconButtonDefaults.filledIconButtonColors()
                    },
                modifier = Modifier.size(80.dp),
            ) {
                Icon(
                    painter =
                        painterResource(
                            when {
                                state.status == CastStatus.ERROR ->
                                    CoreR.drawable.ic_warning_triangle
                                state.isPlaying -> CoreR.drawable.ic_pause
                                else -> CoreR.drawable.ic_play
                            }
                        ),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

private fun formatTime(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
