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
                    builder.setSingleChoiceItems(streams.map { it.label() }.toTypedArray(), 0) {
                        dialog,
                        which ->
                        viewModel.selectAudioStream(streams[which].index)
                        dialog.dismiss()
                    }
                }

                type == C.TRACK_TYPE_TEXT &&
                    viewModel.isTranscoding &&
                    viewModel.subtitleStreams.isNotEmpty() -> {
                    val streams = viewModel.subtitleStreams
                    val labels = listOf(getString(R.string.none)) + streams.map { it.label() }
                    builder.setSingleChoiceItems(labels.toTypedArray(), 0) { dialog, which ->
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
                    builder.setSingleChoiceItems(
                        arrayOf(getString(R.string.none)) + tracksGroups.getTrackNames(),
                        tracksGroups.indexOfFirst { it.isSelected } + 1,
                    ) { dialog, which ->
                        viewModel.switchToTrack(type, which - 1)
                        dialog.dismiss()
                    }
                }
            }

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun PlayerStream.label(): String = title.ifBlank {
        language.ifBlank { codec.ifBlank { "Track $index" } }
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
