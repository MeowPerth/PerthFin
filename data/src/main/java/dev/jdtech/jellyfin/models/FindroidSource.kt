package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.io.File
import java.util.UUID
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo

data class FindroidSource(
    val id: String,
    val name: String,
    val type: FindroidSourceType,
    val path: String,
    val size: Long,
    val mediaStreams: List<FindroidMediaStream>,
    val downloadId: Long? = null,
    val transcodingUrl: String? = null,
)

suspend fun MediaSourceInfo.toFindroidSource(
    jellyfinRepository: JellyfinRepository,
    itemId: UUID,
    includePath: Boolean = false,
): FindroidSource {
    val path =
        when (protocol) {
            MediaProtocol.FILE -> {
                try {
                    if (includePath) jellyfinRepository.getStreamUrl(itemId, id.orEmpty()) else ""
                } catch (_: Exception) {
                    ""
                }
            }
            MediaProtocol.HTTP -> this.path.orEmpty()
            else -> ""
        }
    val absoluteTranscodingUrl =
        transcodingUrl?.let { url ->
            val absolute =
                if (url.startsWith("http", ignoreCase = true)) {
                    url
                } else {
                    jellyfinRepository.getBaseUrl().trimEnd('/') + "/" + url.trimStart('/')
                }
            if (absolute.contains("api_key=", ignoreCase = true)) {
                absolute
            } else {
                absolute + (if ("?" in absolute) "&" else "?") + "api_key=" +
                    jellyfinRepository.getAccessToken()
            }
        }
    return FindroidSource(
        id = id.orEmpty(),
        name = name.orEmpty(),
        type = FindroidSourceType.REMOTE,
        path = path,
        size = size ?: 0,
        mediaStreams =
            mediaStreams?.map { it.toFindroidMediaStream(jellyfinRepository) } ?: emptyList(),
        transcodingUrl = absoluteTranscodingUrl,
    )
}

suspend fun FindroidSourceDto.toFindroidSource(
    serverDatabaseDao: ServerDatabaseDao
): FindroidSource {
    return FindroidSource(
        id = id,
        name = name,
        type = type,
        path = path,
        size = File(path).length(),
        mediaStreams =
            serverDatabaseDao.getMediaStreamsBySourceId(id).map { it.toFindroidMediaStream() },
        downloadId = downloadId,
    )
}

enum class FindroidSourceType {
    REMOTE,
    LOCAL,
}
