package dev.jdtech.jellyfin.presentation.player

import android.app.Dialog
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.media3.common.C
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.player.local.domain.buildStreamLabel
import dev.jdtech.jellyfin.player.local.domain.getTrackNames
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
                    // Direct play: the server stream list and the player track list follow the
                    // same container order when every track is supported.
                    val names =
                        if (serverStreams.size == tracksGroups.size) {
                            serverStreams
                                .map {
                                    buildStreamLabel(
                                        it.title,
                                        it.language,
                                        it.codec,
                                        type == C.TRACK_TYPE_TEXT,
                                    )
                                }
                                .toTypedArray()
                        } else {
                            tracksGroups.getTrackNames()
                        }
                    builder.setSingleChoiceItems(
                        arrayOf(getString(R.string.none)) + names,
                        tracksGroups.indexOfFirst { it.isSelected } + 1,
                    ) { dialog, which ->
                        val language =
                            if (which == 0) {
                                null
                            } else {
                                tracksGroups[which - 1].mediaTrackGroup.getFormat(0).language
                            }
                        val serverIndex =
                            when {
                                which == 0 -> -1
                                serverStreams.size == tracksGroups.size ->
                                    serverStreams[which - 1].index
                                else -> null
                            }
                        viewModel.saveDirectStreamSelection(type, language, serverIndex)
                        viewModel.switchToTrack(type, which - 1)
                        dialog.dismiss()
                    }
                }
            }

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
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
