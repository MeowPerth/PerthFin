package dev.jdtech.jellyfin.presentation.player

import android.app.Dialog
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.settings.R as SettingsR
import java.lang.IllegalStateException

class QualitySelectionDialogFragment(private val viewModel: PlayerViewModel) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val values = listOf("40", "30", "20", "10", "4", "1.5")
        val labels =
            listOf(
                getString(SettingsR.string.quality_40),
                getString(SettingsR.string.quality_30),
                getString(SettingsR.string.quality_20),
                getString(SettingsR.string.quality_10),
                getString(SettingsR.string.quality_4),
                getString(SettingsR.string.quality_1_5),
            )

        return activity?.let { activity ->
            val builder = MaterialAlertDialogBuilder(activity)
            builder.setTitle(getString(R.string.select_bitrate)).setSingleChoiceItems(
                labels.toTypedArray(),
                values.indexOf(viewModel.selectedQuality).coerceAtLeast(0),
            ) { dialog, which ->
                viewModel.selectQuality(values[which])
                dialog.dismiss()
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
