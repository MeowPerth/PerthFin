package dev.jdtech.jellyfin.presentation.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.player.local.R as PlayerR
import dev.jdtech.jellyfin.player.local.cast.CastSession
import java.util.UUID

/**
 * Intercepts "play" while a cast is running.
 *
 * Starting a different video used to silently stop the cast and start local playback. Now the user
 * decides: re-cast the new video to the television, or do nothing at all.
 */
object CastPlaybackGuard {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CastEntryPoint {
        fun castSession(): CastSession
    }

    fun launch(context: Context, intent: Intent) {
        val castSession =
            EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    CastEntryPoint::class.java,
                )
                .castSession()
        val state = castSession.state.value
        val newItemId =
            intent.getStringExtra("itemId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        if (!state.isActive || state.itemId == newItemId) {
            context.startActivity(intent)
            return
        }

        val activity = context.findActivity()
        if (activity == null) {
            context.startActivity(intent)
            return
        }

        val title =
            state.title.ifBlank { activity.getString(PlayerR.string.cast_switch_unknown_title) }
        val deviceName =
            state.device?.displayName?.takeIf { it.isNotBlank() }
                ?: activity.getString(PlayerR.string.cast_switch_unknown_device)

        MaterialAlertDialogBuilder(activity)
            .setTitle(PlayerR.string.cast_switch_title)
            .setMessage(activity.getString(PlayerR.string.cast_switch_message, title, deviceName))
            .setPositiveButton(PlayerR.string.cast_switch_confirm) { _, _ ->
                intent.putExtra("castOnStart", true)
                context.startActivity(intent)
            }
            .setNegativeButton(PlayerR.string.cast_switch_cancel, null)
            .show()
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
