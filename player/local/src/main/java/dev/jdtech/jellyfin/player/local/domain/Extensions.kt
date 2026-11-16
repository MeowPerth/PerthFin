package dev.jdtech.jellyfin.player.local.domain

import androidx.media3.common.C
import androidx.media3.common.Tracks

fun List<Tracks.Group>.getTrackNames(): Array<String> {
    return this.map { group ->
            val format = group.mediaTrackGroup.getFormat(0)
            buildStreamLabel(
                title = format.label,
                language = format.language.orEmpty(),
                codec = format.codecs.orEmpty(),
                isSubtitle = group.type == C.TRACK_TYPE_TEXT,
            )
        }
        .toTypedArray()
}
