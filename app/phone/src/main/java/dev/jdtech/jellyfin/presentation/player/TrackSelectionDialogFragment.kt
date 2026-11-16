package dev.jdtech.jellyfin.presentation.player

import android.app.Dialog
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.media3.common.C
import androidx.media3.common.Format
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
                    // Labels come from the actual player groups so what is shown always
                    // matches what will be selected. Enrich with the server title only when
                    // the track can be matched uniquely.
                    val names =
                        tracksGroups
                            .map { group ->
                                val format = group.mediaTrackGroup.getFormat(0)
                                val matched = serverStreamFor(format, serverStreams)
                                buildStreamLabel(
                                    title = matched?.title ?: format.label,
                                    language = format.language.orEmpty(),
                                    codec = format.codecs.orEmpty(),
                                    isSubtitle = type == C.TRACK_TYPE_TEXT,
                                )
                            }
                            .toTypedArray()
                    builder.setSingleChoiceItems(
                        arrayOf(getString(R.string.none)) + names,
                        tracksGroups.indexOfFirst { it.isSelected } + 1,
                    ) { dialog, which ->
                        val format =
                            if (which == 0) {
                                null
                            } else {
                                tracksGroups[which - 1].mediaTrackGroup.getFormat(0)
                            }
                        val serverIndex =
                            when {
                                which == 0 -> -1
                                format == null -> null
                                else -> serverStreamFor(format, serverStreams)?.index
                            }
                        viewModel.saveDirectStreamSelection(
                            type,
                            format?.language,
                            serverIndex,
                        )
                        viewModel.switchToTrack(type, which - 1)
                        dialog.dismiss()
                    }
                }
            }

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun serverStreamFor(format: Format, streams: List<PlayerStream>): PlayerStream? {
        val exact = streams.filter { stream ->
            languageMatches(format.language, stream.language) &&
                codecMatches(format.codecs, stream.codec)
        }
        if (exact.size == 1) return exact.single()

        val sameLanguage = streams.filter { stream ->
            languageMatches(format.language, stream.language)
        }
        if (sameLanguage.size == 1) return sameLanguage.single()

        // Fall back to the title when language/codec cannot distinguish the tracks.
        val byTitle = streams.filter { stream ->
            !format.label.isNullOrBlank() &&
                stream.title.isNotBlank() &&
                format.label!!.contains(stream.title, ignoreCase = true)
        }
        return byTitle.singleOrNull()
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
