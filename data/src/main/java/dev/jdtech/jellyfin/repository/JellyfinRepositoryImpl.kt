package dev.jdtech.jellyfin.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.CastMediaSource
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidPerson
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import dev.jdtech.jellyfin.models.toFindroidCollection
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidItem
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.models.toFindroidPerson
import dev.jdtech.jellyfin.models.toFindroidSeason
import dev.jdtech.jellyfin.models.toFindroidSegment
import dev.jdtech.jellyfin.models.toFindroidShow
import dev.jdtech.jellyfin.models.toFindroidSource
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.File
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CodecProfile
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceOptionsDto
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.ProfileCondition
import org.jellyfin.sdk.model.api.ProfileConditionType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SortOrder as ItemSortOrder
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodingProfile
import org.jellyfin.sdk.model.api.UserConfiguration
import timber.log.Timber

class JellyfinRepositoryImpl(
    private val context: Context,
    private val jellyfinApi: JellyfinApi,
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
) : JellyfinRepository {
    override suspend fun getPublicSystemInfo(): PublicSystemInfo =
        withContext(Dispatchers.IO) { jellyfinApi.systemApi.getPublicSystemInfo().content }

    override suspend fun getUserViews(): List<BaseItemDto> =
        withContext(Dispatchers.IO) {
            jellyfinApi.userViewApi.getUserViews(jellyfinApi.userId!!).content.items
        }

    override suspend fun getEpisode(itemId: UUID): FindroidEpisode =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(itemId, jellyfinApi.userId!!)
                .content
                .toFindroidEpisode(this@JellyfinRepositoryImpl, database)!!
        }

    override suspend fun getMovie(itemId: UUID): FindroidMovie =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(itemId, jellyfinApi.userId!!)
                .content
                .toFindroidMovie(this@JellyfinRepositoryImpl, database)
        }

    override suspend fun getShow(itemId: UUID): FindroidShow =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(itemId, jellyfinApi.userId!!)
                .content
                .toFindroidShow(this@JellyfinRepositoryImpl)
        }

    override suspend fun getSeason(itemId: UUID): FindroidSeason =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(itemId, jellyfinApi.userId!!)
                .content
                .toFindroidSeason(this@JellyfinRepositoryImpl)
        }

    override suspend fun getLibraries(): List<FindroidCollection> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi.getItems(jellyfinApi.userId!!).content.items.mapNotNull {
                it.toFindroidCollection(this@JellyfinRepositoryImpl)
            }
        }

    override suspend fun getItem(itemId: UUID): FindroidItem? =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(itemId = itemId, userId = jellyfinApi.userId!!)
                .content
                .toFindroidItem(this@JellyfinRepositoryImpl)
        }

    override suspend fun getItems(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
        startIndex: Int?,
        limit: Int?,
    ): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItems(
                    jellyfinApi.userId!!,
                    parentId = parentId,
                    includeItemTypes = includeTypes,
                    recursive = recursive,
                    sortBy = listOf(ItemSortBy.fromName(sortBy.sortString)),
                    sortOrder = listOf(ItemSortOrder.fromName(sortOrder.sortString)),
                    startIndex = startIndex,
                    limit = limit,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getItemsPaging(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
    ): Flow<PagingData<FindroidItem>> {
        return Pager(
                config = PagingConfig(pageSize = 10, enablePlaceholders = false),
                pagingSourceFactory = {
                    ItemsPagingSource(this, parentId, includeTypes, recursive, sortBy, sortOrder)
                },
            )
            .flow
    }

    override suspend fun getPerson(personId: UUID): FindroidPerson =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(personId, jellyfinApi.userId!!)
                .content
                .toFindroidPerson(this@JellyfinRepositoryImpl)
        }

    override suspend fun getPersonItems(
        personIds: List<UUID>,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
    ): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItems(
                    jellyfinApi.userId!!,
                    personIds = personIds,
                    includeItemTypes = includeTypes,
                    recursive = recursive,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getFavoriteItems(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItems(
                    jellyfinApi.userId!!,
                    filters = listOf(ItemFilter.IS_FAVORITE),
                    includeItemTypes =
                        listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE),
                    recursive = true,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getSearchItems(query: String): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItems(
                    jellyfinApi.userId!!,
                    searchTerm = query,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    recursive = true,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getSuggestions(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.suggestionApi
                .getSuggestions(
                    jellyfinApi.userId!!,
                    limit = 6,
                    type = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getResumeItems(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getResumeItems(
                    jellyfinApi.userId!!,
                    limit = 12,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getLatestMedia(parentId: UUID): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getLatestMedia(jellyfinApi.userId!!, parentId = parentId, limit = 16)
                .content
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getSeasons(seriesId: UUID): List<FindroidSeason> =
        withContext(Dispatchers.IO) {
            jellyfinApi.showApi.getSeasons(seriesId, jellyfinApi.userId!!).content.items.map {
                it.toFindroidSeason(this@JellyfinRepositoryImpl)
            }
        }

    override suspend fun getNextUp(seriesId: UUID?): List<FindroidEpisode> =
        withContext(Dispatchers.IO) {
            jellyfinApi.showApi
                .getNextUp(
                    jellyfinApi.userId!!,
                    limit = 24,
                    seriesId = seriesId,
                    enableResumable = false,
                )
                .content
                .items
                .mapNotNull { it.toFindroidEpisode(this@JellyfinRepositoryImpl) }
        }

    override suspend fun getEpisodes(
        seriesId: UUID,
        seasonId: UUID,
        fields: List<ItemFields>?,
        startItemId: UUID?,
        limit: Int?,
    ): List<FindroidEpisode> =
        withContext(Dispatchers.IO) {
            jellyfinApi.showApi
                .getEpisodes(
                    seriesId,
                    jellyfinApi.userId!!,
                    seasonId = seasonId,
                    fields = fields,
                    startItemId = startItemId,
                    limit = limit,
                )
                .content
                .items
                .mapNotNull { it.toFindroidEpisode(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getMediaSources(
        itemId: UUID,
        includePath: Boolean,
        maxBitrate: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ): List<FindroidSource> =
        withContext(Dispatchers.IO) {
            val cap = maxBitrate ?: 40_000_000
            val backend = appPreferences.getValue(appPreferences.playerBackend)
            val directProfiles = localDirectPlayProfiles(backend)
            val subtitleProfiles = localSubtitleProfiles(backend)

            fun profile(allowDirect: Boolean): DeviceProfile =
                DeviceProfile(
                    name = "PerthFin $backend ${cap / 1_000_000}Mbps",
                    maxStaticBitrate = cap,
                    maxStreamingBitrate = cap,
                    codecProfiles = emptyList(),
                    containerProfiles = emptyList(),
                    directPlayProfiles = if (allowDirect) directProfiles else emptyList(),
                    transcodingProfiles =
                        listOf(
                            TranscodingProfile(
                                container = "ts",
                                type = DlnaProfileType.VIDEO,
                                videoCodec = "h264",
                                audioCodec = "aac",
                                protocol = MediaStreamProtocol.HLS,
                                context = EncodingContext.STREAMING,
                                enableSubtitlesInManifest = true,
                                conditions = emptyList(),
                            )
                        ),
                    subtitleProfiles = subtitleProfiles,
                )

            suspend fun loadSources(allowDirect: Boolean): List<MediaSourceInfo> =
                jellyfinApi.mediaInfoApi
                    .getPostedPlaybackInfo(
                        itemId,
                        PlaybackInfoDto(
                            userId = jellyfinApi.userId!!,
                            deviceProfile = profile(allowDirect),
                            maxStreamingBitrate = cap,
                            audioStreamIndex = audioStreamIndex,
                            subtitleStreamIndex = subtitleStreamIndex,
                            enableDirectPlay = allowDirect,
                            enableDirectStream = false,
                            enableTranscoding = true,
                            allowVideoStreamCopy = true,
                            allowAudioStreamCopy = true,
                        ),
                    )
                    .content
                    .mediaSources

            var mediaSources = loadSources(allowDirect = true)
            var chosen = mediaSources.firstOrNull() ?: throw IllegalStateException("Server returned no usable media source")

            if (chosen.transcodingUrl == null) {
                val videoBitrate =
                    chosen.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.bitRate
                val overBitrate = videoBitrate != null && videoBitrate > cap.toLong()
                val selectedSubtitle =
                    subtitleStreamIndex
                        ?.takeIf { it >= 0 }
                        ?.let { index ->
                            chosen.mediaStreams?.firstOrNull {
                                it.type == MediaStreamType.SUBTITLE && it.index == index
                            }
                        }
                val subtitleUnsupported =
                    subtitleStreamIndex != null &&
                        subtitleStreamIndex >= 0 &&
                        (selectedSubtitle == null ||
                            !localSubtitleSupported(backend, selectedSubtitle.codec))

                if (overBitrate || subtitleUnsupported) {
                    mediaSources = loadSources(allowDirect = false)
                    chosen =
                        mediaSources.firstOrNull { it.transcodingUrl != null }
                            ?: throw IllegalStateException("Server returned no usable transcode stream")
                }
            }

            mediaSources
                .map { mediaSource ->
                    val source =
                        mediaSource.toFindroidSource(
                            this@JellyfinRepositoryImpl,
                            itemId,
                            includePath,
                        )
                    source.copy(
                        transcodingUrl =
                            source.transcodingUrl
                                ?.withQueryParam("AudioStreamIndex", audioStreamIndex?.toString())
                                ?.withQueryParam(
                                    "SubtitleStreamIndex",
                                    subtitleStreamIndex?.toString(),
                                )
                                ?.withQueryParam(
                                    "SubtitleMethod",
                                    if (subtitleStreamIndex != null && subtitleStreamIndex >= 0) {
                                        "Encode"
                                    } else {
                                        null
                                    },
                                )
                    )
                }
                .ifEmpty {
                    listOf(
                        chosen.toFindroidSource(this@JellyfinRepositoryImpl, itemId, includePath)
                    )
                }
        }

    override suspend fun getStreamUrl(itemId: UUID, mediaSourceId: String): String =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.videoApi.getVideoStreamUrl(
                    itemId,
                    static = true,
                    mediaSourceId = mediaSourceId,
                )
            } catch (e: Exception) {
                Timber.e(e)
                ""
            }
        }

    override suspend fun getCastMediaSource(
        itemId: UUID,
        maxBitrate: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        container: String,
        startPositionMs: Long,
        maxHeight: Int?,
    ): CastMediaSource =
        withContext(Dispatchers.IO) {
            val subtitleRequested = subtitleStreamIndex != null && subtitleStreamIndex >= 0
            val effectiveBitrate = maxBitrate ?: 40_000_000

            val deviceProfile =
                DeviceProfile(
                    name = "PerthFin DLNA",
                    maxStaticBitrate = effectiveBitrate,
                    maxStreamingBitrate = effectiveBitrate,
                    codecProfiles =
                        if (maxHeight != null) {
                            listOf(
                                CodecProfile(
                                    type = CodecType.VIDEO,
                                    conditions =
                                        listOf(
                                            ProfileCondition(
                                                ProfileConditionType.LESS_THAN_EQUAL,
                                                ProfileConditionValue.HEIGHT,
                                                maxHeight.toString(),
                                                true,
                                            )
                                        ),
                                    applyConditions = emptyList(),
                                    codec = "h264",
                                    container = null,
                                    subContainer = null,
                                )
                            )
                        } else {
                            emptyList()
                        },
                    containerProfiles = emptyList(),
                    directPlayProfiles = emptyList(),
                    transcodingProfiles =
                        listOf(
                            TranscodingProfile(
                                container = container,
                                type = DlnaProfileType.VIDEO,
                                videoCodec = "h264",
                                audioCodec = "aac",
                                protocol = MediaStreamProtocol.HTTP,
                                context = EncodingContext.STREAMING,
                                enableSubtitlesInManifest = false,
                                conditions = emptyList(),
                                maxAudioChannels = "2",
                                // Keep input timestamps when seeking; otherwise the subtitle
                                // burn-in filter produces negative PTS and DLNA TVs freeze after
                                // the first frame.
                                copyTimestamps = true,
                            )
                        ),
                    subtitleProfiles =
                        CAST_SUBTITLE_CODECS.map { codec ->
                            SubtitleProfile(codec, SubtitleDeliveryMethod.ENCODE)
                        },
                )

            val mediaSources =
                jellyfinApi.mediaInfoApi
                    .getPostedPlaybackInfo(
                        itemId,
                        PlaybackInfoDto(
                            userId = jellyfinApi.userId!!,
                            deviceProfile = deviceProfile,
                            maxStreamingBitrate = effectiveBitrate,
                            maxAudioChannels = 2,
                            audioStreamIndex = audioStreamIndex,
                            subtitleStreamIndex =
                                if (subtitleRequested) subtitleStreamIndex else -1,
                            enableDirectPlay = false,
                            enableDirectStream = false,
                            enableTranscoding = true,
                            allowVideoStreamCopy = false,
                            allowAudioStreamCopy = false,
                        ),
                    )
                    .content
                    .mediaSources

            val source =
                mediaSources.firstOrNull { it.transcodingUrl != null }
                    ?: throw IllegalStateException("Server returned no usable transcode stream")
            val rawUrl = source.transcodingUrl!!
            val absoluteUrl =
                if (rawUrl.startsWith("http", ignoreCase = true)) {
                    rawUrl
                } else {
                    getBaseUrl().trimEnd('/') + "/" + rawUrl.trimStart('/')
                }

            val maxWidth = maxHeight?.let { it * 16 / 9 }
            val url =
                appendApiKey(
                    absoluteUrl
                        .withQueryParam("AudioStreamIndex", audioStreamIndex?.toString())
                        .withQueryParam(
                            "SubtitleStreamIndex",
                            if (subtitleRequested) subtitleStreamIndex?.toString() else "-1",
                        )
                        .withQueryParam(
                            "SubtitleMethod",
                            if (subtitleRequested) "Encode" else null,
                        )
                        .withQueryParam(
                            "StartTimeTicks",
                            if (startPositionMs > 0) {
                                (startPositionMs * 10_000L).toString()
                            } else {
                                null
                            },
                        )
                        .withQueryParam("MaxWidth", maxWidth?.toString())
                        .withQueryParam("MaxHeight", maxHeight?.toString())
                        .withQueryParam("CopyTimestamps", "true")
                )

            CastMediaSource(
                url = url,
                mimeType = castMimeType(source.container, true, container),
                isTranscoded = true,
                sizeBytes = null,
            )
        }

    override suspend fun getSegments(itemId: UUID): List<FindroidSegment> =
        withContext(Dispatchers.IO) {
            val databaseSegments = database.getSegments(itemId).map { it.toFindroidSegment() }

            if (databaseSegments.isNotEmpty()) {
                return@withContext databaseSegments
            }

            try {
                val apiSegments =
                    jellyfinApi.mediaSegmentApi.getItemSegments(itemId).content.items.map {
                        it.toFindroidSegment()
                    }

                return@withContext apiSegments
            } catch (e: Exception) {
                Timber.e(e)
                return@withContext emptyList()
            }
        }

    override suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                try {
                    val sources = File(context.filesDir, "trickplay/$itemId").listFiles()
                    if (sources != null) {
                        return@withContext File(sources.first(), index.toString()).readBytes()
                    }
                } catch (_: Exception) {}

                return@withContext jellyfinApi.trickPlayApi
                    .getTrickplayTileImage(itemId, width, index)
                    .content
            } catch (_: Exception) {
                return@withContext null
            }
        }

    override suspend fun postCapabilities() {
        Timber.d("Sending capabilities")
        withContext(Dispatchers.IO) {
            jellyfinApi.sessionApi.postCapabilities(
                playableMediaTypes = listOf(MediaType.VIDEO),
                supportedCommands =
                    listOf(
                        GeneralCommandType.VOLUME_UP,
                        GeneralCommandType.VOLUME_DOWN,
                        GeneralCommandType.TOGGLE_MUTE,
                        GeneralCommandType.SET_AUDIO_STREAM_INDEX,
                        GeneralCommandType.SET_SUBTITLE_STREAM_INDEX,
                        GeneralCommandType.MUTE,
                        GeneralCommandType.UNMUTE,
                        GeneralCommandType.SET_VOLUME,
                        GeneralCommandType.DISPLAY_MESSAGE,
                        GeneralCommandType.PLAY,
                        GeneralCommandType.PLAY_STATE,
                        GeneralCommandType.PLAY_NEXT,
                        GeneralCommandType.PLAY_MEDIA_SOURCE,
                    ),
                supportsMediaControl = true,
            )
        }
    }

    override suspend fun postPlaybackStart(itemId: UUID) {
        Timber.d("Sending start $itemId")
        withContext(Dispatchers.IO) {
            jellyfinApi.sessionApi.reportPlaybackStart(
                PlaybackStartInfo(
                    itemId = itemId,
                    canSeek = true,
                    isPaused = false,
                    isMuted = false,
                    playMethod = PlayMethod.DIRECT_PLAY,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                )
            )
        }
    }

    override suspend fun postPlaybackStop(
        itemId: UUID,
        positionTicks: Long,
        playedPercentage: Int,
    ) {
        Timber.d("Sending stop $itemId")
        withContext(Dispatchers.IO) {
            when {
                playedPercentage < 10 -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, 0)
                    database.setPlayed(jellyfinApi.userId!!, itemId, false)
                }
                playedPercentage > 90 -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, 0)
                    database.setPlayed(jellyfinApi.userId!!, itemId, true)
                }
                else -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, positionTicks)
                    database.setPlayed(jellyfinApi.userId!!, itemId, false)
                }
            }
            try {
                jellyfinApi.sessionApi.reportPlaybackStopped(
                    PlaybackStopInfo(itemId = itemId, positionTicks = positionTicks, failed = false)
                )
            } catch (_: Exception) {}
        }
    }

    override suspend fun postPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean,
    ) {
        Timber.d("Posting progress of $itemId, position: $positionTicks")
        withContext(Dispatchers.IO) {
            database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, positionTicks)
            try {
                jellyfinApi.sessionApi.reportPlaybackProgress(
                    PlaybackProgressInfo(
                        itemId = itemId,
                        canSeek = true,
                        isPaused = isPaused,
                        isMuted = false,
                        playMethod = PlayMethod.DIRECT_PLAY,
                        repeatMode = RepeatMode.REPEAT_NONE,
                        playbackOrder = PlaybackOrder.DEFAULT,
                        positionTicks = positionTicks,
                    )
                )
            } catch (_: Exception) {}
        }
    }

    override suspend fun markAsFavorite(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setFavorite(jellyfinApi.userId!!, itemId, true)
            try {
                jellyfinApi.userDataApi.markFavoriteItem(itemId)
            } catch (_: Exception) {}
        }
    }

    override suspend fun unmarkAsFavorite(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setFavorite(jellyfinApi.userId!!, itemId, false)
            try {
                jellyfinApi.userDataApi.unmarkFavoriteItem(itemId)
            } catch (_: Exception) {}
        }
    }

    override suspend fun markAsPlayed(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setPlayed(jellyfinApi.userId!!, itemId, true)
            try {
                jellyfinApi.userDataApi.markPlayedItem(itemId)
            } catch (_: Exception) {}
        }
    }

    override suspend fun markAsUnplayed(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setPlayed(jellyfinApi.userId!!, itemId, false)
            try {
                jellyfinApi.userDataApi.markUnplayedItem(itemId)
            } catch (_: Exception) {}
        }
    }

    override fun getBaseUrl() = jellyfinApi.api.baseUrl.orEmpty()

    override fun getAccessToken() = jellyfinApi.api.accessToken.orEmpty()

    override suspend fun updateDeviceName(name: String) {
        withContext(Dispatchers.IO) {
            jellyfinApi.jellyfin.deviceInfo?.id?.let { id ->
                jellyfinApi.deviceApi.updateDeviceOptions(
                    id,
                    DeviceOptionsDto(0, customName = name),
                )
            }
        }
    }

    override suspend fun getUserConfiguration(): UserConfiguration =
        withContext(Dispatchers.IO) { jellyfinApi.userApi.getCurrentUser().content.configuration!! }

    override suspend fun getCurrentUserImageTag(): String? =
        withContext(Dispatchers.IO) { jellyfinApi.userApi.getCurrentUser().content.primaryImageTag }

    override fun getUserId(): UUID {
        return jellyfinApi.userId!!
    }

    private fun appendApiKey(url: String): String {
        if (Regex("[?&](ApiKey|api_key)=", RegexOption.IGNORE_CASE).containsMatchIn(url)) {
            return url
        }
        val token = getAccessToken()
        if (token.isBlank()) return url
        val separator = if ('?' in url) "&" else "?"
        return url + separator + "ApiKey=" + URLEncoder.encode(token, "UTF-8")
    }

    private fun localDirectPlayProfiles(backend: String): List<DirectPlayProfile> {
        val isMpv = backend.equals("mpv", ignoreCase = true)
        val videoCodecs =
            if (isMpv) {
                "h264,hevc,vvc,vp8,vp9,av1"
            } else {
                "h263,h264,hevc,vp8,vp9,av1"
            }
        val audioCodecs =
            if (isMpv) {
                "opus,flac,mp3,aac,ac3,eac3,truehd,dts"
            } else {
                "aac,mp3,ac3,eac3,dts,truehd,flac,alac,opus,vorbis,pcm"
            }
        val containers =
            if (isMpv) {
                listOf("mp4", "mkv", "mov", "avi", "ts", "webm")
            } else {
                listOf("mp4", "mkv", "ts", "webm")
            }
        return containers.map { container ->
            DirectPlayProfile(
                container = container,
                audioCodec = audioCodecs,
                videoCodec = videoCodecs,
                type = DlnaProfileType.VIDEO,
            )
        }
    }

    private fun localSubtitleProfiles(backend: String): List<SubtitleProfile> =
        LOCAL_SUBTITLE_CODECS.map { codec ->
            val method =
                if (localSubtitleSupported(backend, codec)) {
                    SubtitleDeliveryMethod.EXTERNAL
                } else {
                    SubtitleDeliveryMethod.ENCODE
                }
            SubtitleProfile(codec, method)
        }

    private fun localSubtitleSupported(backend: String, codec: String?): Boolean {
        val value = codec?.trim()?.lowercase().orEmpty()
        if (value.isEmpty()) return false
        return if (backend.equals("mpv", ignoreCase = true)) {
            value in MPV_SUBTITLE_CODECS
        } else {
            value in EXOPLAYER_SUBTITLE_CODECS
        }
    }

    private fun castMimeType(
        sourceContainer: String?,
        isTranscoded: Boolean,
        transcodeContainer: String,
    ): String {
        if (isTranscoded) {
            return if (transcodeContainer.equals("ts", ignoreCase = true)) {
                "video/mp2t"
            } else {
                "video/mp4"
            }
        }
        return when (sourceContainer?.lowercase()) {
            "mp4",
            "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "ts",
            "m2ts",
            "mpegts" -> "video/mp2t"
            "webm" -> "video/webm"
            else -> "video/mp4"
        }
    }

    private companion object {
        const val CAST_SUBTITLE_BITRATE = 40_000_000
        val CAST_SUBTITLE_CODECS = listOf("srt", "vtt", "ass", "subrip", "pgssub", "dvdsub")
        val LOCAL_SUBTITLE_CODECS =
            listOf("srt", "subrip", "vtt", "ass", "ssa", "pgssub", "dvdsub", "mov_text")
        val EXOPLAYER_SUBTITLE_CODECS = setOf("srt", "subrip", "vtt", "ass", "ssa", "pgssub")
        val MPV_SUBTITLE_CODECS = setOf("srt", "subrip", "vtt", "ass", "ssa", "dvdsub")
    }
}

private fun String.withQueryParam(key: String, value: String?): String {
    if (value == null) return this
    val regex = Regex("([?&])${Regex.escape(key)}=[^&]*", RegexOption.IGNORE_CASE)
    return if (regex.containsMatchIn(this)) {
        regex.replace(this, "$1$key=$value")
    } else {
        this + (if ('?' in this) "&" else "?") + "$key=$value"
    }
}
