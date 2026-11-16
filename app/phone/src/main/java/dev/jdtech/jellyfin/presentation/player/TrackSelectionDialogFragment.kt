package dev.jdtech.jellyfin.presentation.player

import android.app.Dialog
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.media3.common.C
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.jdtech.jellyfin.player.core.domain.models.PlayerStream
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.player.local.domain.buildStreamLabel
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import java.lang.IllegalStateException

class TrackSelectionDialogFragment(
    private val type: @C.TrackType Int,
    private val viewModel: PlayerViewModel,
) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val titleResource =
            when (type) {
                C.TRACK_TYPE_AUDIO -> R.string.select_audio_track
                C.TRACK_TYPE_TEXT -> R.string.select_subtitle_track
                else -> throw IllegalStateException("TrackType must be AUDIO or TEXT")
            }

        return activity?.let { activity ->
            val builder = MaterialAlertDialogBuilder(activity)
            builder.setTitle(getString(titleResource))

            when {
                type == C.TRACK_TYPE_AUDIO &&
                    viewModel.isTranscoding &&
                    viewModel.audioStreams.isNotEmpty() -> {
                    val streams = viewModel.audioStreams
                    val checked =
                        streams
                            .indexOfFirst { it.index == viewModel.selectedAudioIndex }
                            .coerceAtLeast(0)
                    builder.setSingleChoiceItems(
                        streams
                            .map { buildStreamLabel(it.title, it.language, it.codec, false) }
                            .toTypedArray(),
                        checked,
                    ) { dialog, which ->
                        viewModel.selectAudioStream(streams[which].index)
                        dialog.dismiss()
                    }
                }

                type == C.TRACK_TYPE_TEXT &&
                    viewModel.isTranscoding &&
                    viewModel.subtitleStreams.isNotEmpty() -> {
                    val streams = viewModel.subtitleStreams
                    val selected = viewModel.selectedSubtitleIndex
                    val checked =
                        if (selected == null || selected < 0) {
                            0
                        } else {
                            streams.indexOfFirst { it.index == selected } + 1
                        }
                    val labels =
                        listOf(getString(R.string.none)) +
                            streams.map { buildStreamLabel(it.title, it.language, it.codec, true) }
                    builder.setSingleChoiceItems(labels.toTypedArray(), checked.coerceAtLeast(0)) {
                        dialog,
                        which ->
                        viewModel.selectSubtitleStream(
                            if (which == 0) null else streams[which - 1].index
                        )
                        dialog.dismiss()
                    }
                }

                else -> {
                    val tracksGroups =
                        viewModel.player.currentTracks.groups.filter {
                            it.type == type && it.isSupported
                        }
                    val serverStreams =
                        if (type == C.TRACK_TYPE_AUDIO) {
                            viewModel.audioStreams
                        } else {
                            viewModel.subtitleStreams
                        }
                    // Keep the server stream order, and map the nth track of each
                    // language to the nth player group of that language.
                    val groupsByLanguage =
                        tracksGroups.indices.groupBy { index ->
                            languageKey(tracksGroups[index].mediaTrackGroup.getFormat(0).language)
                        }
                    val occurrences = mutableMapOf<String, Int>()
                    val entries = serverStreams.mapNotNull { stream ->
                        val key = languageKey(stream.language)
                        val occurrence = occurrences.getOrDefault(key, 0)
                        occurrences[key] = occurrence + 1
                        groupsByLanguage[key]?.getOrNull(occurrence)?.let { groupIndex ->
                            stream to groupIndex
                        }
                    }
                    val fallbackEntries: List<Pair<PlayerStream?, Int>> =
                        if (entries.isEmpty()) {
                            tracksGroups.indices.map { null to it }
                        } else {
                            entries
                        }

                    val names =
                        fallbackEntries
                            .map { (stream, groupIndex) ->
                                val format = tracksGroups[groupIndex].mediaTrackGroup.getFormat(0)
                                buildStreamLabel(
                                    title = stream?.title ?: format.label,
                                    language = stream?.language ?: format.language.orEmpty(),
                                    codec = stream?.codec ?: format.codecs.orEmpty(),
                                    isSubtitle = type == C.TRACK_TYPE_TEXT,
                                )
                            }
                            .toTypedArray()

                    builder.setSingleChoiceItems(
                        arrayOf(getString(R.string.none)) + names,
                        fallbackEntries.indexOfFirst { tracksGroups[it.second].isSelected } + 1,
                    ) { dialog, which ->
                        if (which == 0) {
                            viewModel.saveDirectStreamSelection(type, null, -1)
                            viewModel.switchToTrack(type, -1)
                        } else {
                            val (stream, groupIndex) = fallbackEntries[which - 1]
                            val format = tracksGroups[groupIndex].mediaTrackGroup.getFormat(0)
                            viewModel.saveDirectStreamSelection(
                                type,
                                stream?.language ?: format.language,
                                stream?.index,
                            )
                            viewModel.switchToTrack(type, groupIndex)
                        }
                        dialog.dismiss()
                    }
                }
            }

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun languageKey(language: String?): String {
        val value = language?.trim()?.lowercase().orEmpty()
        return if (value.length >= 2) value.take(2) else value
    }

    override fun onDestroy() {
        super.onDestroy()
        // Fix for hiding the system bars on API < 30
        activity?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
