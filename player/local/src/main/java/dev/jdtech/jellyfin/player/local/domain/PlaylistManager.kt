package dev.jdtech.jellyfin.player.local.domain

import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import dev.jdtech.jellyfin.models.FindroidChapter
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.FindroidSources
import dev.jdtech.jellyfin.player.core.domain.models.ExternalSubtitle
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.core.domain.models.PlayerStream
import dev.jdtech.jellyfin.player.core.domain.models.TrickplayInfo
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import javax.inject.Inject
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber

class PlaylistManager
@Inject
internal constructor(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
) {
    private var startItem: FindroidItem? = null
    private var items: List<FindroidItem> = emptyList()
    private val playerItems: MutableList<PlayerItem> = mutableListOf()
    var currentItemIndex: Int = 0

    suspend fun getInitialItem(
        itemId: UUID,
        itemKind: BaseItemKind,
        mediaSourceIndex: Int? = null,
        startFromBeginning: Boolean = false,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlayerItem? {
        Timber.d("Retrieving initial player item")

        val initialItem =
            when (itemKind) {
                BaseItemKind.MOVIE -> {
                    val movie = repository.getMovie(itemId)

                    items = listOf(movie)
                    movie
                }
                BaseItemKind.SERIES -> {
                    val nextUpEpisode = repository.getNextUp(itemId).firstOrNull()

                    val season =
                        if (nextUpEpisode != null) {
                            repository.getSeason(nextUpEpisode.seasonId)
                        } else {
                            val seasons = repository.getSeasons(itemId)
                            if (seasons.isEmpty()) {
                                return null
                            }
                            seasons.first()
                        }

                    val episodes =
                        repository
                            .getEpisodes(
                                seriesId = itemId,
                                seasonId = season.id,
                                fields = listOf(ItemFields.CHAPTERS, ItemFields.TRICKPLAY),
                            )
                            .filter { !it.missing }

                    if (episodes.isEmpty()) {
                        return null
                    }

                    val episode = nextUpEpisode ?: episodes.first()

                    items = episodes
                    episode
                }
                BaseItemKind.SEASON -> {
                    val season = repository.getSeason(itemId)
                    val episodes =
                        repository
                            .getEpisodes(
                                seriesId = season.seriesId,
                                seasonId = season.id,
                                fields = listOf(ItemFields.CHAPTERS, ItemFields.TRICKPLAY),
                            )
                            .filter { !it.missing }

                    if (episodes.isEmpty()) {
                        return null
                    }

                    val episode = episodes.first()

                    items = episodes
                    episode
                }
                BaseItemKind.EPISODE -> {
                    val episode = repository.getEpisode(itemId)

                    val episodes =
                        repository
                            .getEpisodes(
                                seriesId = episode.seriesId,
                                seasonId = episode.seasonId,
                                fields = listOf(ItemFields.CHAPTERS, ItemFields.TRICKPLAY),
                            )
                            .filter { !it.missing }

                    items = episodes
                    episode
                }
                else -> null
            }

        if (initialItem == null) {
            return null
        }

        startItem = initialItem

        currentItemIndex = items.indexOfFirst { it.id == initialItem.id }

        val playbackPosition =
            if (!startFromBeginning) initialItem.playbackPositionTicks.div(10000) else 0
        val playerItem =
            initialItem.toPlayerItem(
                mediaSourceIndex,
                playbackPosition,
                audioStreamIndex,
                subtitleStreamIndex,
            )
        playerItems.add(playerItem)

        return playerItem
    }

    suspend fun getPreviousPlayerItem(): PlayerItem? {
        Timber.d("Retrieving previous player item")

        val itemIndex = currentItemIndex - 1
        val playerItem =
            when (startItem) {
                is FindroidMovie -> null
                is FindroidEpisode -> {
                    if (currentItemIndex == 0) {
                        null
                    } else {
                        val item = items[itemIndex]
                        if (playerItems.firstOrNull { it.itemId == item.id } == null) {
                            try {
                                item.toPlayerItem(null, 0L)
                            } catch (e: Exception) {
                                Timber.e("Failed to retrieve previous player item: $e")
                                null
                            }
                        } else {
                            null
                        }
                    }
                }
                else -> null
            }

        if (playerItem != null) {
            playerItems.add(playerItem)
        }

        return playerItem
    }

    suspend fun getNextPlayerItem(): PlayerItem? {
        Timber.d("Retrieving next player item")

        val itemIndex = currentItemIndex + 1
        val playerItem =
            when (startItem) {
                is FindroidMovie -> null
                is FindroidEpisode -> {
                    if (currentItemIndex == items.lastIndex) {
                        null
                    } else {
                        val item = items[itemIndex]
                        if (playerItems.firstOrNull { it.itemId == item.id } == null) {
                            try {
                                item.toPlayerItem(null, 0L)
                            } catch (e: Exception) {
                                Timber.e("Failed to retrieve next player item: $e")
                                null
                            }
                        } else {
                            null
                        }
                    }
                }
                else -> null
            }

        if (playerItem != null) {
            playerItems.add(playerItem)
        }

        return playerItem
    }

    fun setCurrentMediaItemIndex(itemId: UUID) {
        currentItemIndex = items.indexOfFirst { it.id == itemId }
    }

    private suspend fun FindroidItem.toPlayerItem(
        mediaSourceIndex: Int?,
        playbackPosition: Long,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlayerItem {
        Timber.d("Converting FindroidItem ${this.id} to PlayerItem")

        val maxBitrate =
            when (appPreferences.getValue(appPreferences.playerQuality)) {
                "30" -> 30_000_000
                "20" -> 20_000_000
                "10" -> 10_000_000
                "4" -> 4_000_000
                "1.5" -> 1_500_000
                else -> null
            }
        val savedAudioLanguage = appPreferences.getSavedAudioLanguage(id)
        val savedSubtitleLanguage = appPreferences.getSavedSubtitleLanguage(id)
        val preferredAudioLanguage = appPreferences.getValue(appPreferences.preferredAudioLanguage)
        val preferredSubtitleLanguage =
            appPreferences.getValue(appPreferences.preferredSubtitleLanguage)

        val requestedAudioIndex = audioStreamIndex ?: appPreferences.getSavedAudioStreamIndex(id)
        val requestedSubtitleIndex =
            when {
                subtitleStreamIndex != null -> subtitleStreamIndex
                savedSubtitleLanguage == "none" -> -1
                else -> appPreferences.getSavedSubtitleStreamIndex(id)
            }

        var mediaSources =
            repository.getMediaSources(
                id,
                true,
                maxBitrate,
                requestedAudioIndex,
                requestedSubtitleIndex,
            )
        var mediaSource =
            if (mediaSourceIndex == null) {
                mediaSources.firstOrNull { it.type == FindroidSourceType.LOCAL } ?: mediaSources[0]
            } else {
                mediaSources[mediaSourceIndex]
            }

        var resolvedAudioIndex =
            when {
                requestedAudioIndex == null -> null
                requestedAudioIndex < 0 -> requestedAudioIndex
                mediaSource.mediaStreams.any {
                    it.type == MediaStreamType.AUDIO && it.index == requestedAudioIndex
                } -> requestedAudioIndex
                else -> null
            }
        var resolvedSubtitleIndex =
            when {
                requestedSubtitleIndex == null -> null
                requestedSubtitleIndex < 0 -> requestedSubtitleIndex
                mediaSource.mediaStreams.any {
                    it.type == MediaStreamType.SUBTITLE && it.index == requestedSubtitleIndex
                } -> requestedSubtitleIndex
                else -> null
            }
        if (resolvedAudioIndex == null) {
            resolvedAudioIndex =
                findStreamIndex(mediaSource, MediaStreamType.AUDIO, savedAudioLanguage)
                    ?: findStreamIndex(mediaSource, MediaStreamType.AUDIO, preferredAudioLanguage)
        }
        if (resolvedSubtitleIndex == null) {
            resolvedSubtitleIndex =
                findStreamIndex(mediaSource, MediaStreamType.SUBTITLE, savedSubtitleLanguage)
                    ?: findStreamIndex(
                        mediaSource,
                        MediaStreamType.SUBTITLE,
                        preferredSubtitleLanguage,
                    )
        }
        if (
            maxBitrate != null &&
                (resolvedAudioIndex != requestedAudioIndex ||
                    resolvedSubtitleIndex != requestedSubtitleIndex)
        ) {
            mediaSources =
                repository.getMediaSources(
                    id,
                    true,
                    maxBitrate,
                    resolvedAudioIndex,
                    resolvedSubtitleIndex,
                )
            mediaSource =
                if (mediaSourceIndex == null) {
                    mediaSources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                        ?: mediaSources[0]
                } else {
                    mediaSources[mediaSourceIndex]
                }
        }
        Timber.d(
            "Playback source: ${mediaSource.transcodingUrl}, audio=$resolvedAudioIndex, subtitle=$resolvedSubtitleIndex"
        )
        val externalSubtitles =
            mediaSource.mediaStreams
                .filter { mediaStream ->
                    mediaStream.isExternal &&
                        mediaStream.type == MediaStreamType.SUBTITLE &&
                        !mediaStream.path.isNullOrBlank()
                }
                .map { mediaStream ->
                    ExternalSubtitle(
                        mediaStream.title,
                        mediaStream.language,
                        mediaStream.path!!.toUri(),
                        when (mediaStream.codec) {
                            "subrip" -> MimeTypes.APPLICATION_SUBRIP
                            "webvtt" -> MimeTypes.APPLICATION_SUBRIP
                            "ass" -> MimeTypes.TEXT_SSA
                            else -> MimeTypes.TEXT_UNKNOWN
                        },
                    )
                }
        val trickplayInfo =
            when (this) {
                is FindroidSources -> {
                    this.trickplayInfo?.get(mediaSource.id)?.let {
                        TrickplayInfo(
                            width = it.width,
                            height = it.height,
                            tileWidth = it.tileWidth,
                            tileHeight = it.tileHeight,
                            thumbnailCount = it.thumbnailCount,
                            interval = it.interval,
                            bandwidth = it.bandwidth,
                        )
                    }
                }
                else -> null
            }
        val audioStreams =
            mediaSource.mediaStreams
                .filter { it.type == MediaStreamType.AUDIO }
                .mapNotNull { stream ->
                    stream.index?.let { index ->
                        PlayerStream(
                            index = index,
                            title = stream.title.ifBlank { stream.displayTitle ?: stream.language },
                            language = stream.language,
                            codec = stream.codec,
                        )
                    }
                }
        val subtitleStreams =
            mediaSource.mediaStreams
                .filter { it.type == MediaStreamType.SUBTITLE }
                .mapNotNull { stream ->
                    stream.index?.let { index ->
                        PlayerStream(
                            index = index,
                            title = stream.title.ifBlank { stream.displayTitle ?: stream.language },
                            language = stream.language,
                            codec = stream.codec,
                        )
                    }
                }
        val itemKind =
            when (this) {
                is FindroidMovie -> "Movie"
                is FindroidEpisode -> "Episode"
                else -> ""
            }
        return PlayerItem(
            name = name,
            itemId = id,
            mediaSourceId = mediaSource.id,
            mediaSourceUri = mediaSource.transcodingUrl ?: mediaSource.path,
            playbackPosition = playbackPosition,
            parentIndexNumber = if (this is FindroidEpisode) parentIndexNumber else null,
            indexNumber = if (this is FindroidEpisode) indexNumber else null,
            indexNumberEnd = if (this is FindroidEpisode) indexNumberEnd else null,
            externalSubtitles = externalSubtitles,
            chapters = chapters.toPlayerChapters(),
            trickplayInfo = trickplayInfo,
            isTranscoding = mediaSource.transcodingUrl != null,
            itemKind = itemKind,
            audioStreams = audioStreams,
            subtitleStreams = subtitleStreams,
            selectedAudioStreamIndex = resolvedAudioIndex,
            selectedSubtitleStreamIndex = resolvedSubtitleIndex,
        )
    }

    private fun List<FindroidChapter>.toPlayerChapters(): List<PlayerChapter> {
        return this.map { chapter ->
            PlayerChapter(startPosition = chapter.startPosition, name = chapter.name)
        }
    }

    private fun findStreamIndex(
        source: dev.jdtech.jellyfin.models.FindroidSource,
        type: MediaStreamType,
        language: String?,
    ): Int? {
        if (language.isNullOrBlank() || language == "none") return null
        return source.mediaStreams
            .firstOrNull { it.type == type && languageMatches(it.language, language) }
            ?.index
    }

    private fun languageMatches(streamLanguage: String?, requestedLanguage: String?): Boolean {
        if (streamLanguage.isNullOrBlank() || requestedLanguage.isNullOrBlank()) return false
        val stream = streamLanguage.lowercase()
        val requested = requestedLanguage.lowercase()
        return stream == requested ||
            (stream.length >= 2 && requested.length >= 2 && stream.take(2) == requested.take(2))
    }
}
