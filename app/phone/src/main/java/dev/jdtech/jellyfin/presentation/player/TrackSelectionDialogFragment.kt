package dev.jdtech.jellyfin.presentation.player

import android.app.Dialog
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.media3.common.C
import androidx.media3.common.Tracks
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
                    // Keep the server stream order (same as the transcode dialog), but map
                    // every entry to the actual player group before selecting it.
                    val matchedGroups = mutableMapOf<Int, Int>()
                    val usedGroups = mutableSetOf<Int>()

                    serverStreams.forEach { stream ->
                        val groupIndex =
                            tracksGroups.indices.firstOrNull { index ->
                                index !in usedGroups &&
                                    groupMatchesStream(tracksGroups[index], stream)
                            }
                        if (groupIndex != null) {
                            matchedGroups[stream.index] = groupIndex
                            usedGroups.add(groupIndex)
                        }
                    }

                    // Second pass: if codec names differ, fall back to a unique language match.
                    serverStreams.forEach { stream ->
                        if (stream.index in matchedGroups) return@forEach
                        val candidates =
                            tracksGroups.indices.filter { index ->
                                index !in usedGroups &&
                                    languageMatches(
                                        tracksGroups[index].mediaTrackGroup.getFormat(0).language,
                                        stream.language,
                                    )
                            }
                        if (candidates.size == 1) {
                            matchedGroups[stream.index] = candidates.single()
                            usedGroups.add(candidates.single())
                        }
                    }

                    val entries = serverStreams.mapNotNull { stream ->
                        matchedGroups[stream.index]?.let { groupIndex -> stream to groupIndex }
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

    private fun groupMatchesStream(group: Tracks.Group, stream: PlayerStream): Boolean {
        val format = group.mediaTrackGroup.getFormat(0)
        val language = languageMatches(format.language, stream.language)
        val codec = codecMatches(format.codecs, stream.codec)
        if (language && codec) return true

        val label = format.label
        if (!label.isNullOrBlank() && stream.title.isNotBlank()) {
            if (
                label.contains(stream.title, ignoreCase = true) ||
                    stream.title.contains(label, ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    private fun languageMatches(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val first = a.lowercase()
        val second = b.lowercase()
        return first == second ||
            (first.length >= 2 && second.length >= 2 && first.take(2) == second.take(2))
    }

    private fun codecMatches(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val first = a.lowercase().filter { it.isLetterOrDigit() }
        val second = b.lowercase().filter { it.isLetterOrDigit() }
        return first.isNotEmpty() &&
            second.isNotEmpty() &&
            (first == second || first.contains(second) || second.contains(first))
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
