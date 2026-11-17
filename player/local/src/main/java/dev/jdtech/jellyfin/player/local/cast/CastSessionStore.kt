package dev.jdtech.jellyfin.player.local.cast

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StoredCastSession(
    val deviceLocation: String,
    val deviceUdn: String,
    val deviceName: String,
    val itemId: UUID,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val basePositionMs: Long = 0,
    val isPlaying: Boolean,
    val isTranscoded: Boolean,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val maxBitrate: Int? = null,
    val container: String = "ts",
    val maxHeight: Int? = null,
    val updatedAt: Long,
)

/**
 * Persists the active cast so a restarted app can re-attach to the television and show the real
 * progress. Only the metadata is stored; the television keeps playing independently.
 */
@Singleton
class CastSessionStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("cast_session", Context.MODE_PRIVATE)

    fun save(session: StoredCastSession) {
        preferences
            .edit()
            .putString(KEY_DEVICE_LOCATION, session.deviceLocation)
            .putString(KEY_DEVICE_UDN, session.deviceUdn)
            .putString(KEY_DEVICE_NAME, session.deviceName)
            .putString(KEY_ITEM_ID, session.itemId.toString())
            .putString(KEY_TITLE, session.title)
            .putLong(KEY_POSITION, session.positionMs)
            .putLong(KEY_DURATION, session.durationMs)
            .putLong(KEY_BASE_POSITION, session.basePositionMs)
            .putBoolean(KEY_PLAYING, session.isPlaying)
            .putBoolean(KEY_TRANSCODED, session.isTranscoded)
            .putLong(KEY_UPDATED_AT, session.updatedAt)
            .apply()

        preferences
            .edit()
            .apply {
                putNullableInt(KEY_AUDIO_INDEX, session.audioStreamIndex)
                putNullableInt(KEY_SUBTITLE_INDEX, session.subtitleStreamIndex)
                putNullableInt(KEY_MAX_BITRATE, session.maxBitrate)
                putString(KEY_CONTAINER, session.container)
                putNullableInt(KEY_MAX_HEIGHT, session.maxHeight)
            }
            .apply()
    }

    fun load(): StoredCastSession? {
        val location = preferences.getString(KEY_DEVICE_LOCATION, null) ?: return null
        val itemId =
            preferences.getString(KEY_ITEM_ID, null)?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            } ?: return null
        val updatedAt = preferences.getLong(KEY_UPDATED_AT, 0L)
        if (updatedAt <= 0L || System.currentTimeMillis() - updatedAt > MAX_AGE_MS) {
            clear()
            return null
        }
        return StoredCastSession(
            deviceLocation = location,
            deviceUdn = preferences.getString(KEY_DEVICE_UDN, "").orEmpty(),
            deviceName = preferences.getString(KEY_DEVICE_NAME, "").orEmpty(),
            itemId = itemId,
            title = preferences.getString(KEY_TITLE, "").orEmpty(),
            positionMs = preferences.getLong(KEY_POSITION, 0L),
            durationMs = preferences.getLong(KEY_DURATION, 0L),
            basePositionMs = preferences.getLong(KEY_BASE_POSITION, 0L),
            isPlaying = preferences.getBoolean(KEY_PLAYING, false),
            isTranscoded = preferences.getBoolean(KEY_TRANSCODED, false),
            audioStreamIndex = preferences.getNullableInt(KEY_AUDIO_INDEX),
            subtitleStreamIndex = preferences.getNullableInt(KEY_SUBTITLE_INDEX),
            maxBitrate = preferences.getNullableInt(KEY_MAX_BITRATE),
            container = preferences.getString(KEY_CONTAINER, "ts").orEmpty().ifBlank { "ts" },
            maxHeight = preferences.getNullableInt(KEY_MAX_HEIGHT),
            updatedAt = updatedAt,
        )
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun android.content.SharedPreferences.Editor.putNullableInt(key: String, value: Int?) {
        if (value == null) remove(key) else putInt(key, value)
    }

    private fun android.content.SharedPreferences.getNullableInt(key: String): Int? =
        if (contains(key)) getInt(key, 0) else null

    private companion object {
        const val KEY_DEVICE_LOCATION = "device_location"
        const val KEY_DEVICE_UDN = "device_udn"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_ITEM_ID = "item_id"
        const val KEY_TITLE = "title"
        const val KEY_POSITION = "position"
        const val KEY_DURATION = "duration"
        const val KEY_BASE_POSITION = "base_position"
        const val KEY_PLAYING = "playing"
        const val KEY_TRANSCODED = "transcoded"
        const val KEY_AUDIO_INDEX = "audio_index"
        const val KEY_SUBTITLE_INDEX = "subtitle_index"
        const val KEY_MAX_BITRATE = "max_bitrate"
        const val KEY_CONTAINER = "container"
        const val KEY_MAX_HEIGHT = "max_height"
        const val KEY_UPDATED_AT = "updated_at"
        const val MAX_AGE_MS = 12 * 60 * 60 * 1000L
    }
}
