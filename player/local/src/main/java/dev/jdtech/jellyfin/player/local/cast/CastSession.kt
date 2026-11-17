package dev.jdtech.jellyfin.player.local.cast

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.cast.dlna.DidlMedia
import dev.jdtech.jellyfin.cast.dlna.DlnaClient
import dev.jdtech.jellyfin.cast.dlna.DlnaDevice
import dev.jdtech.jellyfin.cast.dlna.TransportState
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.net.URI
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns the DLNA session for the whole process.
 *
 * The session deliberately outlives the player screen: leaving the player keeps the television
 * playing and keeps reporting progress. If Android kills the process the television keeps playing
 * and the last persisted position is restored on the next launch.
 */
@Singleton
class CastSession
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val dlnaClient: DlnaClient,
    private val store: CastSessionStore,
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(CastState())
    val state: StateFlow<CastState> = _state.asStateFlow()

    private var castJob: Job? = null
    private var pollJob: Job? = null
    private var restoreJob: Job? = null
    private var lastRequest: CastRequest? = null
    private var castStartedAt = 0L
    private var lastProgressReportedAt = 0L
    private var lastReportedPaused: Boolean? = null
    private var generation = 0L
    private var pendingBasePositionMs = 0L
    private var baseResolved = true
    private var clockModeKnown = false
    private var clockIsAbsolute = false
    private var clockDetectAfter = 0L

    fun cast(device: DlnaDevice, request: CastRequest) {
        restoreJob?.cancel()
        clockModeKnown = false
        clockIsAbsolute = false
        // TODO: fall back to an app-side relay when the TV cannot reach the server address.
        if (isLoopback(repository.getBaseUrl())) {
            generation++
            castJob?.cancel()
            pollJob?.cancel()
            _state.value =
                CastState(
                    status = CastStatus.ERROR,
                    device = device,
                    itemId = request.itemId,
                    title = request.title,
                    errorMessage = context.getString(R.string.cast_unreachable),
                )
            return
        }
        performCast(device, request, isRecast = false)
    }

    /** Re-push the current item after the audio track, subtitle or quality changed. */
    fun recast(request: CastRequest) {
        val device = _state.value.device ?: return
        if (!_state.value.isActive) return
        performCast(device, request, isRecast = true)
    }

    private fun performCast(device: DlnaDevice, request: CastRequest, isRecast: Boolean) {
        castJob?.cancel()
        pollJob?.cancel()
        val castGeneration = ++generation
        lastRequest = request
        _state.value =
            CastState(
                status = CastStatus.CONNECTING,
                device = device,
                itemId = request.itemId,
                title = request.title,
                positionMs = request.positionMs,
                durationMs = request.durationMs,
            )

        castJob = scope.launch {
            try {
                val source =
                    repository.getCastMediaSource(
                        itemId = request.itemId,
                        maxBitrate = request.maxBitrate,
                        audioStreamIndex = request.audioStreamIndex,
                        subtitleStreamIndex = request.subtitleStreamIndex,
                        container = request.container,
                        startPositionMs = request.positionMs,
                        maxHeight = request.maxHeight,
                    )
                if (castGeneration != generation) return@launch

                Timber.d(
                    "Cast request audio=%s subtitle=%s bitrate=%s container=%s maxHeight=%s",
                    request.audioStreamIndex,
                    request.subtitleStreamIndex,
                    request.maxBitrate,
                    request.container,
                    request.maxHeight,
                )
                Timber.d(
                    "DLNA stream transcoded=%s start=%d url=%s",
                    source.isTranscoded,
                    request.positionMs,
                    source.url.replace(Regex("(ApiKey|api_key)=[^&]+"), "$1=***"),
                )

                val media =
                    DidlMedia(
                        id = request.itemId.toString(),
                        title = request.title,
                        url = source.url,
                        mimeType = source.mimeType,
                        sizeBytes = source.sizeBytes,
                        duration =
                            request.durationMs.takeIf { it > 0 }?.let { Duration.ofMillis(it) },
                        isTranscoded = source.isTranscoded,
                    )

                dlnaClient.setUri(device, media)
                if (castGeneration != generation) {
                    runCatching { dlnaClient.stop(device) }
                    return@launch
                }
                if (!source.isTranscoded && request.positionMs > 0) {
                    runCatching { dlnaClient.seek(device, Duration.ofMillis(request.positionMs)) }
                }
                dlnaClient.resume(device)
                if (castGeneration != generation) {
                    runCatching { dlnaClient.stop(device) }
                    return@launch
                }
                if (!source.isTranscoded && request.positionMs > 0) {
                    // Some renderers ignore Seek while stopped; retry shortly after playback
                    // starts.
                    delay(300L)
                    runCatching { dlnaClient.seek(device, Duration.ofMillis(request.positionMs)) }
                }
                if (castGeneration != generation) return@launch
                castStartedAt = System.currentTimeMillis()

                if (!isRecast) {
                    runCatching { repository.postPlaybackStart(request.itemId) }
                }

                val initialBasePositionMs: Long
                if (!source.isTranscoded) {
                    pendingBasePositionMs = 0L
                    baseResolved = true
                    initialBasePositionMs = 0L
                } else if (isRecast && clockModeKnown) {
                    pendingBasePositionMs = 0L
                    baseResolved = true
                    initialBasePositionMs = if (clockIsAbsolute) 0L else request.positionMs
                } else {
                    pendingBasePositionMs = if (request.positionMs > 0L) request.positionMs else 0L
                    baseResolved = pendingBasePositionMs <= 0L
                    clockDetectAfter = System.currentTimeMillis() + CLOCK_DETECT_DELAY_MS
                    initialBasePositionMs = 0L
                }
                lastReportedPaused = false
                lastProgressReportedAt = System.currentTimeMillis()
                _state.value =
                    _state.value.copy(
                        status = CastStatus.PLAYING,
                        positionMs = request.positionMs,
                        basePositionMs = initialBasePositionMs,
                        isPlaying = true,
                        isTranscoded = source.isTranscoded,
                        errorMessage = null,
                    )
                persist()
                reportProgress(request.positionMs, _state.value.durationMs, isPaused = false)
                startPolling(castGeneration)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (castGeneration != generation) return@launch
                Timber.e(e, "Failed to start cast")
                store.clear()
                _state.update {
                    it.copy(
                        status = CastStatus.ERROR,
                        isPlaying = false,
                        errorMessage = context.getString(R.string.cast_error),
                    )
                }
            }
        }
    }

    fun pause() {
        val device = _state.value.device ?: return
        if (!_state.value.isActive) return
        val commandGeneration = generation
        scope.launch {
            try {
                dlnaClient.pause(device)
                if (commandGeneration != generation || !_state.value.isActive) return@launch
                _state.update { it.copy(status = CastStatus.PAUSED, isPlaying = false) }
                persist()
                reportProgress(
                    _state.value.positionMs,
                    _state.value.durationMs,
                    isPaused = true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun resume() {
        val device = _state.value.device ?: return
        if (!_state.value.isActive) return
        val commandGeneration = generation
        scope.launch {
            try {
                dlnaClient.resume(device)
                if (commandGeneration != generation || !_state.value.isActive) return@launch
                _state.update { it.copy(status = CastStatus.PLAYING, isPlaying = true) }
                persist()
                reportProgress(
                    _state.value.positionMs,
                    _state.value.durationMs,
                    isPaused = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val state = _state.value
        val device = state.device ?: return
        if (!state.isActive) return
        val target =
            positionMs.coerceAtLeast(0L).let {
                if (state.durationMs > 0) it.coerceAtMost(state.durationMs) else it
            }

        if (state.isTranscoded) {
            // Progressive transcodes are not reliably seekable over HTTP. Re-push the stream
            // from the requested position and treat that point as the new zero point.
            val request = lastRequest ?: return
            Timber.d("Seeking transcoded cast by re-pushing at %d ms", target)
            performCast(device, request.copy(positionMs = target), isRecast = true)
            return
        }

        val commandGeneration = generation
        scope.launch {
            try {
                dlnaClient.seek(device, Duration.ofMillis(target))
                if (commandGeneration != generation || !_state.value.isActive) return@launch
                _state.update { it.copy(positionMs = target, basePositionMs = 0L) }
                persist()
                reportProgress(target, _state.value.durationMs, isPaused = !_state.value.isPlaying)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun stop() {
        val snapshot = _state.value
        generation++
        castJob?.cancel()
        castJob = null
        pollJob?.cancel()
        pollJob = null
        restoreJob?.cancel()
        restoreJob = null
        lastRequest = null
        // Reset immediately so a caller that resumes local playback does not see a stale cast.
        _state.value = CastState()
        scope.launch {
            snapshot.device?.let { device -> runCatching { dlnaClient.stop(device) } }
            if (snapshot.status == CastStatus.PLAYING || snapshot.status == CastStatus.PAUSED) {
                reportStop(snapshot, completed = false)
            }
            store.clear()
        }
    }

    /**
     * Re-attach to a cast that was active when the process died. The television is the source of
     * truth for the current position.
     */
    fun restore() {
        if (!appPreferences.getValue(appPreferences.dlnaEnabled)) {
            store.clear()
            return
        }
        val saved = store.load() ?: return
        if (_state.value.isActive) return

        pendingBasePositionMs = saved.basePositionMs.takeIf { it > 0L } ?: 0L
        baseResolved = pendingBasePositionMs <= 0L
        clockModeKnown = false
        clockIsAbsolute = false
        clockDetectAfter = System.currentTimeMillis() + CLOCK_DETECT_DELAY_MS
        val restoreGeneration = ++generation
        restoreJob = scope.launch {
            _state.value =
                CastState(
                    status = CastStatus.CONNECTING,
                    itemId = saved.itemId,
                    title = saved.title,
                    positionMs = saved.positionMs,
                    basePositionMs = 0L,
                    durationMs = saved.durationMs,
                )

            val device = runCatching { dlnaClient.loadDevice(saved.deviceLocation) }.getOrNull()
            if (restoreGeneration != generation) return@launch
            if (device == null || (saved.deviceUdn.isNotBlank() && device.udn != saved.deviceUdn)) {
                store.clear()
                _state.value = CastState()
                return@launch
            }
            _state.value = _state.value.copy(device = device)

            val transport = runCatching { dlnaClient.transportInfo(device) }.getOrNull()
            if (restoreGeneration != generation) return@launch
            if (transport == null) {
                store.clear()
                _state.value = CastState()
                return@launch
            }
            val position = runCatching { dlnaClient.positionInfo(device) }.getOrNull()
            val relativeMs = position?.relativeTime?.toMillis()
            val tvPositionMs = relativeMs?.coerceAtLeast(0L) ?: saved.positionMs
            val durationMs =
                saved.durationMs.takeIf { it > 0 }
                    ?: position?.trackDuration?.toMillis()?.takeIf { it > 0 }
                    ?: 0L
            lastRequest =
                CastRequest(
                    itemId = saved.itemId,
                    title = saved.title,
                    positionMs = saved.basePositionMs,
                    durationMs = durationMs,
                    maxBitrate = saved.maxBitrate,
                    audioStreamIndex = saved.audioStreamIndex,
                    subtitleStreamIndex = saved.subtitleStreamIndex,
                    container = saved.container,
                    maxHeight = saved.maxHeight,
                )

            val ended =
                transport.state == TransportState.STOPPED ||
                    transport.state == TransportState.NO_MEDIA_PRESENT
            val positionMs = if (ended) maxOf(tvPositionMs, saved.positionMs) else saved.positionMs
            if (ended) {
                val completed = durationMs > 0 && positionMs >= durationMs - END_THRESHOLD_MS
                val restored =
                    CastState(
                        status = CastStatus.PAUSED,
                        device = device,
                        itemId = saved.itemId,
                        title = saved.title,
                        positionMs = positionMs,
                        basePositionMs = saved.basePositionMs,
                        durationMs = durationMs,
                    )
                reportStop(restored, completed)
                store.clear()
                _state.value = CastState()
                return@launch
            }

            val status = if (transport.state.isPaused) CastStatus.PAUSED else CastStatus.PLAYING
            _state.value =
                CastState(
                    status = status,
                    device = device,
                    itemId = saved.itemId,
                    title = saved.title,
                    positionMs = positionMs,
                    basePositionMs = 0L,
                    durationMs = durationMs,
                    isPlaying = transport.state.isPlaying,
                    isTranscoded = saved.isTranscoded,
                )
            persist()
            castStartedAt = System.currentTimeMillis()
            lastReportedPaused = null
            lastProgressReportedAt = 0L
            reportProgress(positionMs, durationMs, isPaused = status == CastStatus.PAUSED)
            startPolling(restoreGeneration)
        }
    }

    private fun startPolling(pollGeneration: Long) {
        pollJob?.cancel()
        pollJob = scope.launch {
            var failures = 0
            var stoppedPolls = 0
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (pollGeneration != generation) break
                val current = _state.value
                if (!current.isActive) break
                val device = current.device ?: break

                try {
                    val transport = dlnaClient.transportInfo(device)
                    val position = runCatching { dlnaClient.positionInfo(device) }.getOrNull()
                    if (pollGeneration != generation) break
                    failures = 0

                    val relativeMs = position?.relativeTime?.toMillis()
                    val durationMs =
                        current.durationMs.takeIf { it > 0 }
                            ?: position?.trackDuration?.toMillis()?.takeIf { it > 0 }
                            ?: 0L

                    var basePositionMs = current.basePositionMs
                    if (
                        !baseResolved &&
                            relativeMs != null &&
                            relativeMs > 0L &&
                            System.currentTimeMillis() >= clockDetectAfter
                    ) {
                        val elapsedMs =
                            (System.currentTimeMillis() - castStartedAt).coerceAtLeast(0L)
                        val pending = pendingBasePositionMs
                        val absoluteDistance = kotlin.math.abs(relativeMs - (pending + elapsedMs))
                        val relativeDistance = kotlin.math.abs(relativeMs - elapsedMs)
                        val useAbsoluteTime = absoluteDistance <= relativeDistance
                        basePositionMs = if (useAbsoluteTime) 0L else pending
                        baseResolved = true
                        clockModeKnown = true
                        clockIsAbsolute = useAbsoluteTime
                        Timber.d(
                            "Resolved cast clock absolute=%s pending=%d rel=%d elapsed=%d",
                            useAbsoluteTime,
                            pending,
                            relativeMs,
                            elapsedMs,
                        )
                    }

                    val rawPositionMs =
                        if (relativeMs != null && (baseResolved || relativeMs > 0L)) {
                            (basePositionMs + relativeMs).coerceAtLeast(0L)
                        } else {
                            // Keep the requested position while the renderer still reports 00:00
                            // during startup/buffering.
                            current.positionMs
                        }
                    val positionMs =
                        if (durationMs > 0) rawPositionMs.coerceIn(0L, durationMs)
                        else rawPositionMs

                    Timber.d(
                        "Cast position rel=%s base=%d -> %d/%d state=%s",
                        relativeMs,
                        basePositionMs,
                        positionMs,
                        durationMs,
                        transport.state,
                    )
                    _state.update {
                        it.copy(
                            status =
                                when {
                                    transport.state.isPaused -> CastStatus.PAUSED
                                    transport.state.isPlaying -> CastStatus.PLAYING
                                    else -> it.status
                                },
                            positionMs = positionMs,
                            basePositionMs = basePositionMs,
                            durationMs = durationMs,
                            isPlaying = transport.state.isPlaying,
                        )
                    }
                    persist()

                    if (
                        (transport.state == TransportState.STOPPED ||
                            transport.state == TransportState.NO_MEDIA_PRESENT) &&
                            System.currentTimeMillis() - castStartedAt > START_GRACE_MS
                    ) {
                        stoppedPolls++
                        if (stoppedPolls >= 2) {
                            // Some renderers report 00:00:00 after reaching the end, so use
                            // the highest position seen during this session.
                            val finalPosition = maxOf(positionMs, current.positionMs)
                            val completed =
                                durationMs > 0 && finalPosition >= durationMs - END_THRESHOLD_MS
                            if (!completed && finalPosition < 1_000L) {
                                store.clear()
                                _state.update {
                                    it.copy(
                                        status = CastStatus.ERROR,
                                        isPlaying = false,
                                        errorMessage = context.getString(R.string.cast_no_playback),
                                    )
                                }
                                pollJob = null
                                break
                            }
                            reportStop(
                                _state.value.copy(positionMs = finalPosition),
                                completed,
                            )
                            store.clear()
                            _state.value = CastState()
                            pollJob = null
                            break
                        }
                    } else {
                        stoppedPolls = 0
                    }

                    maybeReportProgress(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isPaused = transport.state.isPaused,
                    )
                } catch (e: Exception) {
                    if (pollGeneration != generation) break
                    failures++
                    Timber.w(e, "Failed to poll DLNA renderer")
                    if (failures >= MAX_POLL_FAILURES) {
                        store.clear()
                        _state.update {
                            it.copy(
                                status = CastStatus.ERROR,
                                errorMessage = context.getString(R.string.cast_error),
                            )
                        }
                        break
                    }
                }
            }
        }
    }

    private suspend fun maybeReportProgress(
        positionMs: Long,
        durationMs: Long,
        isPaused: Boolean,
    ) {
        val now = System.currentTimeMillis()
        if (now - lastProgressReportedAt < PROGRESS_INTERVAL_MS && isPaused == lastReportedPaused) {
            return
        }
        lastProgressReportedAt = now
        lastReportedPaused = isPaused
        reportProgress(positionMs, durationMs, isPaused)
    }

    private suspend fun reportProgress(positionMs: Long, durationMs: Long, isPaused: Boolean) {
        val itemId = _state.value.itemId ?: return
        runCatching {
            repository.postPlaybackProgress(
                itemId = itemId,
                positionTicks = positionMs.coerceAtLeast(0) * TICKS_PER_MS,
                isPaused = isPaused,
            )
        }
    }

    private suspend fun reportStop(snapshot: CastState, completed: Boolean) {
        val itemId = snapshot.itemId ?: return
        val durationMs = snapshot.durationMs
        val positionMs =
            if (completed && durationMs > 0) durationMs else snapshot.positionMs.coerceAtLeast(0)
        val percentage =
            when {
                completed -> 100
                durationMs > 0 -> ((positionMs * 100) / durationMs).toInt().coerceIn(0, 100)
                else -> 0
            }
        runCatching {
            repository.postPlaybackStop(
                itemId = itemId,
                positionTicks = positionMs * TICKS_PER_MS,
                playedPercentage = percentage,
            )
        }
    }

    private fun persist() {
        val state = _state.value
        val device = state.device ?: return
        val itemId = state.itemId ?: return
        val request = lastRequest
        store.save(
            StoredCastSession(
                deviceLocation = device.location,
                deviceUdn = device.udn,
                deviceName = device.displayName,
                itemId = itemId,
                title = state.title,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                basePositionMs = state.basePositionMs,
                isPlaying = state.isPlaying,
                isTranscoded = state.isTranscoded,
                audioStreamIndex = request?.audioStreamIndex,
                subtitleStreamIndex = request?.subtitleStreamIndex,
                maxBitrate = request?.maxBitrate,
                container = request?.container ?: "ts",
                maxHeight = request?.maxHeight,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private fun isLoopback(baseUrl: String): Boolean {
        val host = runCatching { URI(baseUrl).host }.getOrNull() ?: return false
        return host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "[::1]"
    }

    private fun fail(e: Exception) {
        Timber.e(e, "DLNA command failed")
        _state.update {
            it.copy(
                status = CastStatus.ERROR,
                isPlaying = false,
                errorMessage = context.getString(R.string.cast_error),
            )
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val PROGRESS_INTERVAL_MS = 10_000L
        const val MAX_POLL_FAILURES = 3
        const val END_THRESHOLD_MS = 5_000L
        const val START_GRACE_MS = 10_000L
        const val CLOCK_DETECT_DELAY_MS = 3_000L
        const val TICKS_PER_MS = 10_000L
    }
}
