package dev.jdtech.jellyfin

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.media3.session.MediaSession
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel

abstract class BasePlayerActivity : AppCompatActivity() {

    abstract val viewModel: PlayerViewModel

    private lateinit var mediaSession: MediaSession
    private var wasPip: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onStart() {
        super.onStart()

        mediaSession = MediaSession.Builder(this, viewModel.player).build()
    }

    override fun onResume() {
        super.onResume()

        if (wasPip) {
            wasPip = false
        } else {
            viewModel.player.playWhenReady = viewModel.playWhenReady
        }
        hideSystemUI()
    }

    override fun onPause() {
        super.onPause()

        if (isInPictureInPictureMode) {
            wasPip = true
        } else {
            viewModel.playWhenReady = viewModel.player.playWhenReady
            viewModel.player.playWhenReady = false
            viewModel.updatePlaybackProgress()
        }
    }

    override fun onStop() {
        super.onStop()

        mediaSession.release()

        if (wasPip) {
            finish()
        }
    }

    protected fun hideSystemUI() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    protected fun configureInsets(playerControls: View) {
        playerControls.setOnApplyWindowInsetsListener { view, windowInsets ->
            val cutout = windowInsets.displayCutout
            val left = cutout?.safeInsetLeft ?: 0
            val top = cutout?.safeInsetTop ?: 0
            val right = cutout?.safeInsetRight ?: 0
            val bottom = cutout?.safeInsetBottom ?: 0

            val topControls = view.findViewById<View>(R.id.top_controls)
            val bottomControls = view.findViewById<View>(R.id.bottom_controls)
            if (topControls != null || bottomControls != null) {
                // Keep the center controls centered on the full screen; only the
                // top and bottom bars need to avoid the display cutout.
                view.updatePadding(0, 0, 0, 0)
                topControls?.updatePadding(left = left, top = top, right = right)
                bottomControls?.updatePadding(left = left, right = right, bottom = bottom)
            } else {
                view.updatePadding(left = left, top = top, right = right, bottom = bottom)
            }
            return@setOnApplyWindowInsetsListener windowInsets
        }
    }
}
