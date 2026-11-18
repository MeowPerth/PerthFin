package dev.jdtech.jellyfin.player.local.presentation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.cast.dlna.DlnaDevice
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.core.domain.models.PlayerStream
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.player.local.cast.CastRequest
import dev.jdtech.jellyfin.player.local.cast.CastSession
import dev.jdtech.jellyfin.player.local.cast.CastState
import dev.jdtech.jellyfin.player.local.domain.PlaylistManager
import dev.jdtech.jellyfin.player.local.mpv.MPVPlayer
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.Constants
import java.util.UUID
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

@HiltViewModel
class PlayerViewModel
@Inject
constructor(
    private val application: Application,
    private val playlistManager: PlaylistManager,
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val castSession: CastSession,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel(), Player.Listener {
    val player: Player

    private val _uiState =
        MutableStateFlow(
            UiState(
                currentItemTitle = "",
                currentSegment = null,
                currentSkipButtonStringRes = R.string.player_controls_skip_intro,
                currentTrickplay = null,
                currentChapters = emptyList(),
                fileLoaded = false,
            )
        )
    val uiState = _uiState.asStateFlow()

    val castState: StateFlow<CastState> = castSession.state

    data class EpisodeChoice(
        val id: UUID,
        val title: String,
        val indexNumber: Int,
        val seasonName: String?,
    )

    private val _episodes = MutableStateFlow<List<EpisodeChoice>>(emptyList())
    val episodes: StateFlow<List<EpisodeChoice>> = _episodes.asStateFlow()

    private val _currentEpisodeId = MutableStateFlow<UUID?>(null)
    val currentEpisodeId: StateFlow<UUID?> = _currentEpisodeId.asStateFlow()

    private val _isEpisodeContent = MutableStateFlow(false)
    val isEpisodeContent: StateFlow<Boolean> = _isEpisodeContent.asStateFlow()

    private val _autoPlayNext =
        MutableStateFlow(appPreferences.getValue(appPreferences.autoPlayNext))
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext.asStateFlow()
    private var lastCastAutoNextItemId: UUID? = null

    fun setAutoPlayNext(enabled: Boolean) {
        if (_autoPlayNext.value == enabled) return
        _autoPlayNext.value = enabled
        appPreferences.setValue(appPreferences.autoPlayNext, enabled)

        if (!enabled) {
            // ExoPlayer's playlist may already contain the next episode. Drop it so
            // turning the switch off takes effect immediately, not next episode.
            val currentIndex = player.currentMediaItemIndex
            while (player.mediaItemCount > currentIndex + 1) {
                player.removeMediaItem(currentIndex + 1)
            }
            while (items.size > currentIndex + 1) {
                items.removeAt(items.lastIndex)
            }
            playlistManager.removeCachedPlayerItemsExcept(currentItemId)
        } else if (player.mediaItemCount <= player.currentMediaItemIndex + 1) {
            addNextMediaItemIfEnabled()
        }
    }

    private fun addNextMediaItemIfEnabled() {
        if (!_autoPlayNext.value || currentItemId == null) return
        viewModelScope.launch {
            val nextItem = playlistManager.getNextPlayerItem() ?: return@launch
            val insertIndex = player.currentMediaItemIndex + 1
            if (insertIndex > items.size) items.add(nextItem) else items.add(insertIndex, nextItem)
            player.addMediaItem(insertIndex, nextItem.toMediaItem())
        }
    }

    val isCasting: Boolean
        get() = castSession.state.value.isActive

    private val eventsChannel = Channel<PlayerEvents>()
    val eventsChannelFlow = eventsChannel.receiveAsFlow()

    data class UiState(
        val currentItemTitle: String,
        val currentSegment: FindroidSegment?,
        val currentSkipButtonStringRes: Int,
        val currentTrickplay: Trickplay?,
        val currentChapters: List<PlayerChapter>,
        val fileLoaded: Boolean,
    )

    private var items: MutableList<PlayerItem> = mutableListOf()

    private val trackSelector = DefaultTrackSelector(application)
    var playWhenReady = true
    private var currentMediaItemIndex = savedStateHandle["mediaItemIndex"] ?: 0
    private var playbackPosition: Long = savedStateHandle["position"] ?: 0
    private var currentItemId: UUID? = null
    private var currentItemKind: String? = null
    private var listenerAdded = false
    private var selectedAudioStreamIndex: Int? = null
    private var selectedSubtitleStreamIndex: Int? = null
    private var pendingDirectOverrides = false
    private var pendingAudioIndex: Int? = null
    private var pendingSubtitleIndex: Int? = null
    private var currentMediaItemSegments: List<FindroidSegment> = emptyList()

    // Segments preferences
    var segmentsSkipButton: Boolean = false
    private var segmentsSkipButtonTypes: Set<String> = emptySet()
    var segmentsSkipButtonDuration: Long = 0L
    var segmentsAutoSkip: Boolean = false
    private var segmentsAutoSkipTypes: Set<String> = emptySet()
    private var segmentsAutoSkipMode: String = "always"

    var playbackSpeed: Float = 1f

    var isInPictureInPictureMode: Boolean = false

    init {
        viewModelScope.launch {
            castSession.state.collect { cast ->
                if (
                    !_autoPlayNext.value ||
                        !cast.isActive ||
                        !cast.isPlaying ||
                        cast.durationMs <= 0L ||
                        cast.positionMs < cast.durationMs - 1_000L
                ) {
                    return@collect
                }
                val itemId = cast.itemId ?: return@collect
                if (lastCastAutoNextItemId == itemId) return@collect
                val index = _episodes.value.indexOfFirst { it.id == itemId }
                val next = _episodes.value.getOrNull(index + 1) ?: return@collect
                lastCastAutoNextItemId = itemId
                initializePlayer(
                    itemId = next.id,
                    itemKind = BaseItemKind.EPISODE.serialName,
                    startFromBeginning = true,
                    castOnStart = true,
                )
            }
        }

        segmentsSkipButton = appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButton)
        segmentsSkipButtonTypes =
            appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButtonType)
        segmentsSkipButtonDuration =
            appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButtonDuration)
        segmentsAutoSkip = appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkip)
        segmentsAutoSkipTypes =
            appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkipType)
        segmentsAutoSkipMode =
            appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkipMode)

        val audioAttributes =
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()

        trackSelector.setParameters(
            trackSelector
                .buildUponParameters()
                .setTunnelingEnabled(true)
                .setPreferredAudioLanguage(
                    appPreferences.getValue(appPreferences.preferredAudioLanguage)
                )
                .setPreferredTextLanguage(
                    appPreferences.getValue(appPreferences.preferredSubtitleLanguage)
                )
        )

        val playerBackend = appPreferences.getValue(appPreferences.playerBackend)
        player =
            when (playerBackend) {
                "exoplayer" -> {
                    val renderersFactory =
                        DefaultRenderersFactory(application)
                            .setExtensionRendererMode(
                                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                            )
                    ExoPlayer.Builder(application, renderersFactory)
                        .setAudioAttributes(audioAttributes, true)
                        .setTrackSelector(trackSelector)
                        .setSeekBackIncrementMs(
                            appPreferences.getValue(appPreferences.playerSeekBackInc)
                        )
                        .setSeekForwardIncrementMs(
                            appPreferences.getValue(appPreferences.playerSeekForwardInc)
                        )
                        .setPauseAtEndOfMediaItems(true)
                        .build()
                }
                "mpv" -> {
                    MPVPlayer.Builder(application)
                        .setAudioAttributes(audioAttributes, true)
                        .setTrackSelectionParameters(trackSelector.parameters)
                        .setSeekBackIncrementMs(
                            appPreferences.getValue(appPreferences.playerSeekBackInc)
                        )
                        .setSeekForwardIncrementMs(
                            appPreferences.getValue(appPreferences.playerSeekForwardInc)
                        )
                        .setPauseAtEndOfMediaItems(true)
                        .setVideoOutput(appPreferences.getValue(appPreferences.playerMpvVo))
                        .setAudioOutput(appPreferences.getValue(appPreferences.playerMpvAo))
                        .setHwDec(appPreferences.getValue(appPreferences.playerMpvHwdec))
                        .build()
                }

                else -> throw RuntimeException("$playerBackend is not a valid player backend")
            }

        viewModelScope.launch {
            appPreferences.observe(appPreferences.autoPlayNext).collect { enabled ->
                if (enabled != _autoPlayNext.value) {
                    setAutoPlayNext(enabled)
                }
            }
        }
    }

    fun initializePlayer(
        itemId: UUID,
        itemKind: String,
        startFromBeginning: Boolean,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        castOnStart: Boolean = false,
    ) {
        currentItemId = itemId
        currentItemKind = itemKind
        if (!listenerAdded) {
            player.addListener(this)
            listenerAdded = true
        }

        viewModelScope.launch {
            val currentCast = castSession.state.value
            val switchingCast = castOnStart && currentCast.isActive && currentCast.itemId != itemId
            if (currentCast.isActive && currentCast.itemId != itemId && !castOnStart) {
                // Starting a different item hands playback back to the phone.
                castSession.stop()
            } else if (currentCast.isActive && currentCast.itemId == itemId) {
                _isEpisodeContent.value = BaseItemKind.fromName(itemKind) == BaseItemKind.EPISODE
                loadEpisodeChoices(itemId)
                _uiState.update { it.copy(currentItemTitle = currentCast.title, fileLoaded = true) }
                return@launch
            }

            val startItem =
                try {
                    playlistManager.getInitialItem(
                        itemId = itemId,
                        itemKind = BaseItemKind.fromName(itemKind),
                        mediaSourceIndex = null,
                        startFromBeginning = startFromBeginning,
                        audioStreamIndex = audioStreamIndex,
                        subtitleStreamIndex = subtitleStreamIndex,
                    )
                } catch (e: Exception) {
                    Timber.e(e)
                    Toast.makeText(application, e.localizedMessage, Toast.LENGTH_LONG).show()
                    null
                }

            if (startItem == null) {
                Timber.e("No start item, stopping player initialization")
                return@launch
            }

            currentItemId = startItem.itemId
            currentItemKind = startItem.itemKind
            _isEpisodeContent.value = startItem.itemKind == BaseItemKind.EPISODE.serialName
            _currentEpisodeId.value = startItem.itemId
            val playlistEpisodes = playlistManager.episodeItems()
            if (playlistEpisodes.isNotEmpty()) {
                _episodes.value = playlistEpisodes.map { item ->
                    EpisodeChoice(
                        id = item.id,
                        title = item.name,
                        indexNumber = item.indexNumber,
                        seasonName = item.seasonName,
                    )
                }
            }
            selectedAudioStreamIndex =
                startItem.selectedAudioStreamIndex
                    ?: audioStreamIndex
                    ?: appPreferences.getSavedAudioStreamIndex(startItem.itemId)
            selectedSubtitleStreamIndex =
                startItem.selectedSubtitleStreamIndex
                    ?: subtitleStreamIndex
                    ?: appPreferences.getSavedSubtitleStreamIndex(startItem.itemId)
            if (!startItem.isTranscoding) {
                prepareDirectOverrides(startItem)
            }
            items = listOfNotNull(startItem).toMutableList()
            currentMediaItemIndex = items.indexOf(startItem)

            if (switchingCast) {
                val device = currentCast.device
                if (device != null) {
                    val position =
                        if (startFromBeginning) {
                            0L
                        } else {
                            playbackPosition.takeIf { it > 0L } ?: startItem.playbackPosition
                        }
                    val request = buildCastRequest(position)
                    if (request != null) {
                        _uiState.update {
                            it.copy(currentItemTitle = startItem.name, fileLoaded = true)
                        }
                        castSession.cast(device, request)
                        return@launch
                    }
                }
                castSession.stop()
            }

            val mediaItems = mutableListOf<MediaItem>()
            try {
                for (item in items) {
                    mediaItems.add(item.toMediaItem())
                }
            } catch (e: Exception) {
                Timber.e(e)
            }

            val startPosition =
                if (playbackPosition == 0L) {
                    items.getOrNull(currentMediaItemIndex)?.playbackPosition ?: C.TIME_UNSET
                } else {
                    playbackPosition
                }

            player.setMediaItems(mediaItems, 0, startPosition)
            player.prepare()
            player.play()
        }
    }

    val selectedQuality: String
        get() = appPreferences.getValue(appPreferences.playerQuality)

    fun saveDirectStreamSelection(
        type: @C.TrackType Int,
        language: String?,
        index: Int?,
    ) {
        val itemId = currentItemId ?: return
        if (type == C.TRACK_TYPE_AUDIO) {
            appPreferences.setSavedAudioStreamIndex(itemId, index)
            appPreferences.setSavedAudioLanguage(itemId, language)
        } else {
            appPreferences.setSavedSubtitleStreamIndex(itemId, index)
            appPreferences.setSavedSubtitleLanguage(
                itemId,
                if (index == null || index < 0) "none" else language,
            )
        }
    }

    private fun prepareDirectOverrides(item: PlayerItem) {
        pendingAudioIndex = item.selectedAudioStreamIndex
        pendingSubtitleIndex = item.selectedSubtitleStreamIndex
        pendingDirectOverrides = true
        applyDirectOverridesIfPossible()
    }

    private fun applyDirectOverridesIfPossible(tracks: Tracks = player.currentTracks) {
        if (!pendingDirectOverrides) return
        val item = items.getOrNull(currentMediaItemIndex) ?: return

        val params = player.trackSelectionParameters.buildUpon()
        var applied = false

        pendingAudioIndex?.let { index ->
            if (index < 0) {
                params.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                params.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                applied = true
            } else {
                findGroup(tracks, C.TRACK_TYPE_AUDIO, item.audioStreams, index)?.let { group ->
                    params.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    params.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                    applied = true
                }
            }
        }

        pendingSubtitleIndex?.let { index ->
            if (index < 0) {
                params.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                applied = true
            } else {
                findGroup(tracks, C.TRACK_TYPE_TEXT, item.subtitleStreams, index)?.let { group ->
                    params.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                    applied = true
                }
            }
        }

        if (applied) {
            player.trackSelectionParameters = params.build()
        }
        pendingDirectOverrides = false
    }

    private fun findGroup(
        tracks: Tracks,
        type: @C.TrackType Int,
        streams: List<PlayerStream>,
        index: Int,
    ): Tracks.Group? {
        val groups = tracks.groups.filter { it.type == type && it.isSupported }
        if (groups.isEmpty()) return null

        val target = streams.firstOrNull { it.index == index } ?: return null
        val key = languageKey(target.language)
        val sameLanguageStreams = streams.filter { languageKey(it.language) == key }
        val occurrence = sameLanguageStreams.indexOfFirst { it.index == target.index }
        if (occurrence < 0) return null
        val sameLanguageGroups = groups.filter { group ->
            languageKey(group.mediaTrackGroup.getFormat(0).language) == key
        }
        return sameLanguageGroups.getOrNull(occurrence)
    }

    private fun languageKey(language: String?): String {
        val value = language?.trim()?.lowercase().orEmpty()
        return if (value.length >= 2) value.take(2) else value
    }

    override fun onTracksChanged(tracks: Tracks) {
        if (pendingDirectOverrides) {
            applyDirectOverridesIfPossible(tracks)
        }
    }

    val isTranscoding: Boolean
        get() = items.getOrNull(currentMediaItemIndex)?.isTranscoding == true

    val selectedAudioIndex: Int?
        get() = selectedAudioStreamIndex

    val selectedSubtitleIndex: Int?
        get() = selectedSubtitleStreamIndex

    val audioStreams: List<PlayerStream>
        get() = items.getOrNull(currentMediaItemIndex)?.audioStreams ?: emptyList()

    val subtitleStreams: List<PlayerStream>
        get() = items.getOrNull(currentMediaItemIndex)?.subtitleStreams ?: emptyList()

    fun selectAudioStream(index: Int) {
        selectedAudioStreamIndex = index
        currentItemId?.let { itemId ->
            appPreferences.setSavedAudioStreamIndex(itemId, index)
            appPreferences.setSavedAudioLanguage(
                itemId,
                audioStreams.firstOrNull { it.index == index }?.language,
            )
        }
        if (recastIfCasting()) return
        restartCurrentItem()
    }

    fun selectSubtitleStream(index: Int?) {
        // -1 explicitly disables subtitles in Jellyfin's PlaybackInfo request
        val resolvedIndex = index ?: -1
        selectedSubtitleStreamIndex = resolvedIndex
        currentItemId?.let { itemId ->
            appPreferences.setSavedSubtitleStreamIndex(itemId, resolvedIndex)
            appPreferences.setSavedSubtitleLanguage(
                itemId,
                if (resolvedIndex < 0) {
                    "none"
                } else {
                    subtitleStreams.firstOrNull { it.index == resolvedIndex }?.language
                },
            )
        }
        if (recastIfCasting()) return
        restartCurrentItem()
    }

    private fun restartCurrentItem() {
        val itemId = currentItemId ?: return
        val itemKind = currentItemKind ?: return
        playbackPosition = player.currentPosition.coerceAtLeast(0L)
        player.stop()
        player.clearMediaItems()
        initializePlayer(
            itemId = itemId,
            itemKind = itemKind,
            startFromBeginning = false,
            audioStreamIndex = selectedAudioStreamIndex,
            subtitleStreamIndex = selectedSubtitleStreamIndex,
        )
    }

    fun selectQuality(quality: String) {
        appPreferences.setValue(appPreferences.playerQuality, quality)
        if (recastIfCasting()) return
        val itemId = currentItemId ?: return
        val itemKind = currentItemKind ?: return
        playbackPosition = player.currentPosition.coerceAtLeast(0L)
        player.stop()
        player.clearMediaItems()
        initializePlayer(
            itemId = itemId,
            itemKind = itemKind,
            startFromBeginning = false,
            audioStreamIndex = selectedAudioStreamIndex,
            subtitleStreamIndex = selectedSubtitleStreamIndex,
        )
    }

    private fun PlayerItem.toMediaItem(): MediaItem {
        val streamUrl = mediaSourceUri
        val mediaSubtitles = externalSubtitles.map { externalSubtitle ->
            MediaItem.SubtitleConfiguration.Builder(externalSubtitle.uri)
                .setLabel(
                    externalSubtitle.title.ifBlank { application.getString(R.string.external) }
                )
                .setMimeType(externalSubtitle.mimeType)
                .setLanguage(externalSubtitle.language)
                .build()
        }

        Timber.d("Stream url: $streamUrl")
        val mediaItem =
            MediaItem.Builder()
                .setMediaId(itemId.toString())
                .setUri(streamUrl)
                .apply {
                    if (isTranscoding) {
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                    }
                }
                .setMediaMetadata(MediaMetadata.Builder().setTitle(name).build())
                .setSubtitleConfigurations(mediaSubtitles)
                .build()

        return mediaItem
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun releasePlayer() {
        val mediaId = player.currentMediaItem?.mediaId
        val position = player.currentPosition
        val duration = player.duration
        val casting = castSession.state.value.isActive
        GlobalScope.launch {
            delay(200L)
            try {
                if (mediaId != null && duration != C.TIME_UNSET && !casting) {
                    Timber.d("Sending playback stop")
                    repository.postPlaybackStop(
                        UUID.fromString(mediaId),
                        position.times(10000),
                        position.div(duration.toFloat()).times(100).toInt(),
                    )
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        _uiState.update { it.copy(currentTrickplay = null) }
        playWhenReady = false
        playbackPosition = 0L
        currentMediaItemIndex = 0
        player.removeListener(this)
        player.release()
    }

    fun updatePlaybackProgress() {
        Timber.d("Updating playback progress")
        viewModelScope.launch(Dispatchers.Main) {
            if (castSession.state.value.isActive) {
                savedStateHandle["position"] = castSession.state.value.positionMs
                return@launch
            }
            savedStateHandle["position"] = player.currentPosition
            if (player.currentMediaItem != null && player.currentMediaItem!!.mediaId.isNotEmpty()) {
                val itemId = UUID.fromString(player.currentMediaItem!!.mediaId)
                try {
                    repository.postPlaybackProgress(
                        itemId,
                        player.currentPosition.times(10000),
                        !player.isPlaying,
                    )
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }
    }

    fun updateCurrentSegment() {
        Timber.d("Updating current segment")
        viewModelScope.launch(Dispatchers.Main) {
            if (currentMediaItemSegments.isEmpty()) {
                return@launch
            }

            val milliSeconds = player.currentPosition

            // Get current segment, - 100 milliseconds to avoid showing button after segment ends
            val currentSegment = currentMediaItemSegments.find { segment ->
                milliSeconds in segment.startTicks..<(segment.endTicks - 100L)
            }

            if (currentSegment == null) {
                // Remove button if not pressed and there is no current segment
                if (_uiState.value.currentSegment != null) {
                    _uiState.update { it.copy(currentSegment = null) }
                }
                return@launch
            }

            Timber.tag("SegmentInfo").d("currentSegment: %s", currentSegment)

            if (
                segmentsAutoSkip &&
                    segmentsAutoSkipTypes.contains(currentSegment.type.toString()) &&
                    (segmentsAutoSkipMode == Constants.PlayerMediaSegmentsAutoSkip.ALWAYS ||
                        (segmentsAutoSkipMode == Constants.PlayerMediaSegmentsAutoSkip.PIP &&
                            isInPictureInPictureMode))
            ) {
                // Auto Skip segment
                skipSegment(currentSegment)
            } else if (segmentsSkipButtonTypes.contains(currentSegment.type.toString())) {
                // Skip Button segment
                _uiState.update {
                    it.copy(
                        currentSegment = currentSegment,
                        currentSkipButtonStringRes = getSkipButtonTextStringId(currentSegment),
                    )
                }
            } else {
                _uiState.update { it.copy(currentSegment = null) }
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        Timber.d("Playing MediaItem: ${mediaItem?.mediaId}")
        savedStateHandle["mediaItemIndex"] = player.currentMediaItemIndex
        viewModelScope.launch {
            try {
                _currentEpisodeId.value =
                    player.currentMediaItem?.mediaId?.let {
                        runCatching { UUID.fromString(it) }.getOrNull()
                    }
                items
                    .first { it.itemId.toString() == player.currentMediaItem?.mediaId }
                    .let { item ->
                        val itemTitle =
                            if (item.parentIndexNumber != null && item.indexNumber != null) {
                                if (item.indexNumberEnd == null) {
                                    "S${item.parentIndexNumber}:E${item.indexNumber} - ${item.name}"
                                } else {
                                    "S${item.parentIndexNumber}:E${item.indexNumber}-${item.indexNumberEnd} - ${item.name}"
                                }
                            } else {
                                item.name
                            }
                        _uiState.update {
                            it.copy(
                                currentItemTitle = itemTitle,
                                currentSegment = null,
                                currentChapters = item.chapters,
                                fileLoaded = false,
                            )
                        }

                        repository.postPlaybackStart(item.itemId)

                        if (segmentsSkipButton || segmentsAutoSkip) {
                            getSegments(item.itemId)
                        }

                        if (appPreferences.getValue(appPreferences.playerTrickplay)) {
                            getTrickplay(item)
                        }

                        playlistManager.setCurrentMediaItemIndex(item.itemId)

                        val previousItem = playlistManager.getPreviousPlayerItem()
                        if (previousItem != null) {
                            items.add(player.currentMediaItemIndex, previousItem)
                            player.addMediaItem(
                                player.currentMediaItemIndex,
                                previousItem.toMediaItem(),
                            )
                        }

                        if (_autoPlayNext.value) {
                            val nextItem = playlistManager.getNextPlayerItem()
                            if (nextItem != null) {
                                items.add(player.currentMediaItemIndex + 1, nextItem)
                                player.addMediaItem(
                                    player.currentMediaItemIndex + 1,
                                    nextItem.toMediaItem(),
                                )
                            }
                        }

                        Timber.tag("PlayerItems").d(items.map { it.indexNumber }.toString())
                    }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        // Report playback stopped for current item and transition to the next one
        if (
            !playWhenReady &&
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                player.playbackState == ExoPlayer.STATE_READY
        ) {
            viewModelScope.launch {
                val mediaId = player.currentMediaItem?.mediaId
                val position = player.currentPosition
                val duration = player.duration
                try {
                    repository.postPlaybackStop(
                        UUID.fromString(mediaId),
                        position.times(10000),
                        position.div(duration.toFloat()).times(100).toInt(),
                    )
                } catch (e: Exception) {
                    Timber.e(e)
                }
                player.seekToNextMediaItem()
                player.play()
            }
        }
    }

    private suspend fun loadEpisodeChoices(itemId: UUID) {
        try {
            val episode = repository.getEpisode(itemId)
            val choices =
                repository
                    .getEpisodes(
                        seriesId = episode.seriesId,
                        seasonId = episode.seasonId,
                        fields = null,
                    )
                    .filter { !it.missing }
                    .map { item ->
                        EpisodeChoice(
                            id = item.id,
                            title = item.name,
                            indexNumber = item.indexNumber,
                            seasonName = item.seasonName,
                        )
                    }
            _episodes.value = choices
        } catch (e: Exception) {
            _episodes.value = emptyList()
            Timber.d(e, "No episode list for $itemId")
        }
    }

    fun selectEpisode(episode: EpisodeChoice) {
        val castActive = castSession.state.value.isActive
        initializePlayer(
            itemId = episode.id,
            itemKind = BaseItemKind.EPISODE.serialName,
            startFromBeginning = true,
            castOnStart = castActive,
        )
    }

    private fun playNextEpisodeIfEnabled(): Boolean {
        if (!_autoPlayNext.value) return false
        if (currentItemKind != BaseItemKind.EPISODE.serialName) return false
        val currentId = currentItemId ?: return false
        val list = _episodes.value
        val index = list.indexOfFirst { it.id == currentId }
        val next = list.getOrNull(index + 1) ?: return false
        initializePlayer(
            itemId = next.id,
            itemKind = BaseItemKind.EPISODE.serialName,
            startFromBeginning = true,
        )
        return true
    }

    override fun onPlaybackStateChanged(state: Int) {
        var stateString = "UNKNOWN_STATE             -"
        when (state) {
            ExoPlayer.STATE_IDLE -> {
                stateString = "ExoPlayer.STATE_IDLE      -"
            }
            ExoPlayer.STATE_BUFFERING -> {
                stateString = "ExoPlayer.STATE_BUFFERING -"
            }
            ExoPlayer.STATE_READY -> {
                stateString = "ExoPlayer.STATE_READY     -"
                _uiState.update { it.copy(fileLoaded = true) }
            }
            ExoPlayer.STATE_ENDED -> {
                stateString = "ExoPlayer.STATE_ENDED     -"
                if (!playNextEpisodeIfEnabled()) {
                    eventsChannel.trySend(PlayerEvents.NavigateBack)
                }
            }
        }
        Timber.d("Changed player state to $stateString")
    }

    fun castTo(device: DlnaDevice) {
        val request = buildCastRequest() ?: return
        player.pause()
        castSession.cast(device, request)
    }

    fun pauseCast() = castSession.pause()

    fun resumeCast() = castSession.resume()

    fun seekCast(positionMs: Long) = castSession.seekTo(positionMs)

    fun stopCastingAndResumeLocal() {
        val cast = castSession.state.value
        val itemId = currentItemId
        val itemKind = currentItemKind
        castSession.stop()
        if (itemId != null && itemKind != null) {
            val resumePosition =
                if (cast.positionMs > 0L) cast.positionMs else player.currentPosition
            playbackPosition = resumePosition.coerceAtLeast(0L)
            player.stop()
            player.clearMediaItems()
            initializePlayer(
                itemId = itemId,
                itemKind = itemKind,
                startFromBeginning = false,
                audioStreamIndex = selectedAudioStreamIndex,
                subtitleStreamIndex = selectedSubtitleStreamIndex,
            )
        }
    }

    private fun recastIfCasting(): Boolean {
        val cast = castSession.state.value
        if (!cast.isActive || cast.itemId != currentItemId) return false
        val request = buildCastRequest(cast.positionMs) ?: return false
        castSession.recast(request)
        return true
    }

    private fun buildCastRequest(positionMs: Long = player.currentPosition): CastRequest? {
        val item = items.getOrNull(currentMediaItemIndex) ?: return null
        val duration =
            player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                ?: item.durationMs.takeIf { it > 0L }
                ?: castSession.state.value.durationMs
        val bitrate = maxBitrateForQuality(selectedQuality)
        return CastRequest(
            itemId = item.itemId,
            title = item.name,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = duration,
            maxBitrate = bitrate,
            audioStreamIndex = selectedAudioStreamIndex,
            subtitleStreamIndex = selectedSubtitleStreamIndex ?: -1,
            container = appPreferences.getValue(appPreferences.dlnaContainer),
            maxHeight = maxHeightForBitrate(bitrate),
        )
    }

    private fun maxBitrateForQuality(quality: String): Int =
        when (quality) {
            "40" -> 40_000_000
            "30" -> 30_000_000
            "20" -> 20_000_000
            "10" -> 10_000_000
            "4" -> 4_000_000
            "1.5" -> 1_500_000
            else -> 40_000_000
        }

    private fun maxHeightForBitrate(bitrate: Int): Int =
        when {
            bitrate >= 30_000_000 -> 2160
            bitrate >= 10_000_000 -> 1080
            bitrate >= 4_000_000 -> 720
            else -> 480
        }

    override fun onCleared() {
        super.onCleared()
        Timber.d("Clearing Player ViewModel")
        releasePlayer()
    }

    fun switchToTrack(trackType: @C.TrackType Int, index: Int) {
        if (castSession.state.value.isActive) {
            val itemId = currentItemId ?: return
            if (trackType == C.TRACK_TYPE_AUDIO) {
                val audioIndex = appPreferences.getSavedAudioStreamIndex(itemId) ?: return
                // Disabling audio on the TV is not supported by most renderers.
                if (audioIndex < 0) return
                selectedAudioStreamIndex = audioIndex
            } else {
                selectedSubtitleStreamIndex =
                    appPreferences.getSavedSubtitleStreamIndex(itemId) ?: -1
            }
            recastIfCasting()
            return
        }
        // Index -1 equals disable track
        if (index == -1) {
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(trackType)
                    .setTrackTypeDisabled(trackType, true)
                    .build()
        } else {
            val group =
                player.currentTracks.groups.filter { it.type == trackType && it.isSupported }[index]
            val format = group.mediaTrackGroup.getFormat(0)
            Timber.d(
                "switchToTrack type=$trackType index=$index label=${format.label} language=${format.language} codecs=${format.codecs}"
            )
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(trackType)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                    .setTrackTypeDisabled(trackType, false)
                    .build()
        }
    }

    fun selectSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        playbackSpeed = speed
    }

    private suspend fun getSegments(itemId: UUID) {
        try {
            currentMediaItemSegments = repository.getSegments(itemId)
        } catch (e: Exception) {
            currentMediaItemSegments = emptyList()
            Timber.e(e)
        }
    }

    private suspend fun getTrickplay(item: PlayerItem) {
        val trickplayInfo = item.trickplayInfo ?: return
        Timber.d("Trickplay Resolution: ${trickplayInfo.width}")

        withContext(Dispatchers.Default) {
            val maxIndex =
                ceil(
                        trickplayInfo.thumbnailCount
                            .toDouble()
                            .div(trickplayInfo.tileWidth * trickplayInfo.tileHeight)
                    )
                    .toInt()
            val bitmaps = mutableListOf<Bitmap>()

            for (i in 0..maxIndex) {
                repository.getTrickplayData(item.itemId, trickplayInfo.width, i)?.let { byteArray ->
                    val fullBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                    for (offsetY in
                        0..<trickplayInfo.height * trickplayInfo.tileHeight step
                            trickplayInfo.height) {
                        for (offsetX in
                            0..<trickplayInfo.width * trickplayInfo.tileWidth step
                                trickplayInfo.width) {
                            val bitmap =
                                Bitmap.createBitmap(
                                    fullBitmap,
                                    offsetX,
                                    offsetY,
                                    trickplayInfo.width,
                                    trickplayInfo.height,
                                )
                            bitmaps.add(bitmap)
                        }
                    }
                }
            }
            _uiState.update {
                it.copy(currentTrickplay = Trickplay(trickplayInfo.interval, bitmaps))
            }
        }
    }

    fun skipSegment(segment: FindroidSegment) {
        if (shouldSkipToNextEpisode(segment)) {
            player.seekToNextMediaItem()
        } else {
            player.seekTo(segment.endTicks)
        }
        _uiState.update { it.copy(currentSegment = null) }
    }

    // Check if the outro segment's end time is within n milliseconds of the player's total duration
    private fun shouldSkipToNextEpisode(segment: FindroidSegment): Boolean {
        return if (segment.type == FindroidSegmentType.OUTRO && player.hasNextMediaItem()) {
            val segmentEndTimeMillis = segment.endTicks
            val playerDurationMillis = player.duration
            val thresholdMillis =
                playerDurationMillis -
                    appPreferences.getValue(appPreferences.playerMediaSegmentsNextEpisodeThreshold)

            segmentEndTimeMillis > thresholdMillis
        } else {
            false
        }
    }

    private fun getSkipButtonTextStringId(segment: FindroidSegment): Int {
        return when (shouldSkipToNextEpisode(segment)) {
            true -> R.string.player_controls_next_episode
            false ->
                when (segment.type) {
                    FindroidSegmentType.INTRO -> R.string.player_controls_skip_intro
                    FindroidSegmentType.OUTRO -> R.string.player_controls_skip_outro
                    FindroidSegmentType.RECAP -> R.string.player_controls_skip_recap
                    FindroidSegmentType.COMMERCIAL -> R.string.player_controls_skip_commercial
                    FindroidSegmentType.PREVIEW -> R.string.player_controls_skip_preview
                    else -> R.string.player_controls_skip_unknown
                }
        }
    }

    /**
     * Get chapters of current item
     *
     * @return list of [PlayerChapter]
     */
    private fun getChapters(): List<PlayerChapter> {
        return uiState.value.currentChapters
    }

    /**
     * Get the index of the current chapter
     *
     * @return the index of the current chapter
     */
    private fun getCurrentChapterIndex(): Int? {
        val chapters = getChapters()

        for (i in chapters.indices.reversed()) {
            if (chapters[i].startPosition < player.currentPosition) {
                return i
            }
        }

        return null
    }

    /**
     * Get the index of the next chapter
     *
     * @return the index of the next chapter
     */
    private fun getNextChapterIndex(): Int? {
        val chapters = getChapters()
        val currentChapterIndex = getCurrentChapterIndex() ?: return null

        return minOf(chapters.size - 1, currentChapterIndex + 1)
    }

    /**
     * Get the index of the previous chapter. Only use this for seeking as it will return the
     * current chapter when player position is more than 5 seconds past the start of the chapter
     *
     * @return the index of the previous chapter
     */
    private fun getPreviousChapterIndex(): Int? {
        val chapters = getChapters()
        val currentChapterIndex = getCurrentChapterIndex() ?: return null

        // Return current chapter when more than 5 seconds past chapter start
        if (player.currentPosition > chapters[currentChapterIndex].startPosition + 5000L) {
            return currentChapterIndex
        }

        return maxOf(0, currentChapterIndex - 1)
    }

    fun isLastChapter(): Boolean =
        getChapters().let { chapters -> getCurrentChapterIndex() == chapters.size - 1 }

    /**
     * Seek to chapter
     *
     * @param [chapterIndex] the index of the chapter to seek to
     * @return the [PlayerChapter] which has been sought to
     */
    private fun seekToChapter(chapterIndex: Int): PlayerChapter? {
        return getChapters().getOrNull(chapterIndex)?.also { chapter ->
            player.seekTo(chapter.startPosition)
        }
    }

    /**
     * Seek to the next chapter
     *
     * @return the [PlayerChapter] which has been sought to
     */
    fun seekToNextChapter(): PlayerChapter? {
        return getNextChapterIndex()?.let { seekToChapter(it) }
    }

    /**
     * Seek to the previous chapter Will seek to start of current chapter if player position is more
     * than 5 seconds past start of chapter
     *
     * @return the [PlayerChapter] which has been sought to
     */
    fun seekToPreviousChapter(): PlayerChapter? {
        return getPreviousChapterIndex()?.let { seekToChapter(it) }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        eventsChannel.trySend(PlayerEvents.IsPlayingChanged(isPlaying))
    }
}

sealed interface PlayerEvents {
    data object NavigateBack : PlayerEvents

    data class IsPlayingChanged(val isPlaying: Boolean) : PlayerEvents
}
