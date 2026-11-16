package dev.jdtech.jellyfin.presentation.player

import android.app.Dialog
import android.os.Build
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
import java.util.Locale

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
                        streams.map { it.label() }.toTypedArray(),
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
                    val labels = listOf(getString(R.string.none)) + streams.map { it.label() }
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

    private fun PlayerStream.label(): String {
        val parts = mutableListOf<String>()
        val localizedLanguage = languageName(language)
        val englishLanguage = englishLanguageName(language)
        val streamTitle = title.trim()

        if (
            streamTitle.isNotEmpty() &&
                !streamTitle.equals(englishLanguage, ignoreCase = true) &&
                !streamTitle.equals(language, ignoreCase = true)
        ) {
            parts.add(streamTitle)
        }
        localizedLanguage?.let { parts.add(it) }
        codec.takeIf { it.isNotBlank() }?.let { parts.add(it) }

        return parts.distinctBy { it.lowercase() }.joinToString(" - ").ifBlank { "Track $index" }
    }

    private fun languageName(code: String): String? {
        val tag = languageTag(code) ?: return null
        val locale =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                Locale.of(tag)
            } else {
                @Suppress("DEPRECATION") Locale(tag)
            }
        val name = locale.displayLanguage
        return name.takeIf { it.isNotBlank() && !it.equals(tag, true) && !it.equals(code, true) }
    }

    private fun englishLanguageName(code: String): String? {
        val tag = languageTag(code) ?: return null
        val locale =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                Locale.of(tag)
            } else {
                @Suppress("DEPRECATION") Locale(tag)
            }
        return locale.getDisplayLanguage(Locale.ENGLISH)
    }

    private fun languageTag(code: String): String? {
        if (code.isBlank()) return null
        return ISO3_TO_ISO1[code.lowercase()] ?: code
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

    private companion object {
        val ISO3_TO_ISO1 =
            mapOf(
                "eng" to "en",
                "jpn" to "ja",
                "chi" to "zh",
                "zho" to "zh",
                "ger" to "de",
                "deu" to "de",
                "fre" to "fr",
                "fra" to "fr",
                "spa" to "es",
                "kor" to "ko",
                "rus" to "ru",
                "ita" to "it",
                "por" to "pt",
                "dut" to "nl",
                "nld" to "nl",
                "pol" to "pl",
                "tur" to "tr",
                "ukr" to "uk",
                "cze" to "cs",
                "ces" to "cs",
                "slo" to "sk",
                "slk" to "sk",
                "slv" to "sl",
                "hun" to "hu",
                "rum" to "ro",
                "ron" to "ro",
                "bul" to "bg",
                "swe" to "sv",
                "dan" to "da",
                "fin" to "fi",
                "nor" to "no",
                "gre" to "el",
                "ell" to "el",
                "heb" to "he",
                "ara" to "ar",
                "hin" to "hi",
                "vie" to "vi",
                "tha" to "th",
                "ind" to "id",
                "may" to "ms",
                "msa" to "ms",
                "cat" to "ca",
                "baq" to "eu",
                "eus" to "eu",
                "glg" to "gl",
                "srp" to "sr",
                "hrv" to "hr",
                "bos" to "bs",
                "alb" to "sq",
                "sqi" to "sq",
                "mac" to "mk",
                "mkd" to "mk",
                "lit" to "lt",
                "lav" to "lv",
                "est" to "et",
                "ice" to "is",
                "isl" to "is",
                "wel" to "cy",
                "cym" to "cy",
                "bel" to "be",
                "aze" to "az",
                "kaz" to "kk",
                "uzb" to "uz",
                "tgl" to "tl",
                "fil" to "tl",
            )
    }
}
