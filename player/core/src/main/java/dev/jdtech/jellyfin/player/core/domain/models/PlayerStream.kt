package dev.jdtech.jellyfin.player.core.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PlayerStream(
    val index: Int,
    val title: String,
    val language: String,
    val codec: String,
    val isDefault: Boolean = false,
) : Parcelable
