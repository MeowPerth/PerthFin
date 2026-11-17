package dev.jdtech.jellyfin

import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Rational
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.cast.dlna.DlnaDevice
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.databinding.ActivityPlayerBinding
import dev.jdtech.jellyfin.player.local.cast.CastStatus
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.player.CastControls
import dev.jdtech.jellyfin.presentation.player.CastDeviceSheet
import dev.jdtech.jellyfin.presentation.player.QualitySelectionDialogFragment
import dev.jdtech.jellyfin.presentation.player.SpeedSelectionDialogFragment
import dev.jdtech.jellyfin.presentation.player.TrackSelectionDialogFragment
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.PlayerGestureHelper
import dev.jdtech.jellyfin.utils.PreviewScrubListener
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

var isControlsLocked: Boolean = false

@AndroidEntryPoint
class PlayerActivity : BasePlayerActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    lateinit var binding: ActivityPlayerBinding
    private var playerGestureHelper: PlayerGestureHelper? = null
    override val viewModel: PlayerViewModel by viewModels()
    private var previewScrubListener: PreviewScrubListener? = null
    private var wasZoom: Boolean = false
    private var isPlayerPortrait = false
    private var skipButtonTimeoutExpired: Boolean = true
    private var showCastPicker by mutableStateOf(false)

    private lateinit var skipSegmentButton: Button
    private lateinit var centerControls: View
    private lateinit var centerPlayPauseButton: ImageButton

    private val isPipSupported by lazy {
        // Check if device has PiP feature
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return@lazy false
        }

        // Check if PiP is enabled for the app
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager?
        appOps?.checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
            Process.myUid(),
            packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    private val handler = Handler(Looper.getMainLooper())
    private val skipButtonTimeout = Runnable {
        if (!binding.playerView.isControllerFullyVisible) {
            skipSegmentButton.isVisible = false
            skipButtonTimeoutExpired = true
        }
    }

    private val timeUpdateRunnable =
        object : Runnable {
            override fun run() {
                updatePlayerTime()
                handler.postDelayed(this, 500)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemId = UUID.fromString(intent.extras!!.getString("itemId"))
        val itemKind = intent.extras!!.getString("itemKind")
        val startFromBeginning = intent.extras!!.getBoolean("startFromBeginning")
        val castOnStart = intent.extras!!.getBoolean("castOnStart")

        isPlayerPortrait =
            if (appPreferences.getValue(appPreferences.playerDefaultLandscape)) {
                false
            } else {
                appPreferences.getValue(appPreferences.playerLastOrientation) == "portrait"
            }
        requestedOrientation =
            if (isPlayerPortrait) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupCastUi()

        binding.playerView.player = viewModel.player
        adjustPlayerControls(isPlayerPortrait)
        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                if (visibility == View.GONE) {
                    hideSystemUI()
                }
            }
        )

        val playerControls = binding.playerView.findViewById<View>(R.id.player_controls)
        val lockedControls = binding.playerView.findViewById<View>(R.id.locked_player_view)

        isControlsLocked = false

        configureInsets(playerControls) {
            isPlayerPortrait || appPreferences.getValue(appPreferences.playerNotchMode)
        }
        configureInsets(lockedControls) {
            isPlayerPortrait || appPreferences.getValue(appPreferences.playerNotchMode)
        }

        centerControls = binding.playerView.findViewById(R.id.exo_center_controls)
        centerPlayPauseButton = binding.playerView.findViewById(R.id.btn_center_play_pause)
        centerPlayPauseButton.setOnClickListener {
            val player = viewModel.player
            if (player.isPlaying) player.pause() else player.play()
        }
        centerControls.isVisible = false
        viewModel.player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateCenterControls()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateCenterControls()
                }
            }
        )

        if (appPreferences.getValue(appPreferences.playerGestures)) {
            playerGestureHelper =
                PlayerGestureHelper(
                    appPreferences,
                    this,
                    binding.playerView,
                    getSystemService(AUDIO_SERVICE) as AudioManager,
                )
        }

        binding.playerView.findViewById<View>(R.id.back_button).setOnClickListener {
            finishPlayback()
        }

        val videoNameTextView = binding.playerView.findViewById<TextView>(R.id.video_name)

        val audioButton = binding.playerView.findViewById<ImageButton>(R.id.btn_audio_track)
        val subtitleButton = binding.playerView.findViewById<ImageButton>(R.id.btn_subtitle)
        val speedButton = binding.playerView.findViewById<ImageButton>(R.id.btn_speed)
        val qualityButton = binding.playerView.findViewById<ImageButton>(R.id.btn_quality)
        skipSegmentButton = binding.playerView.findViewById(R.id.btn_skip_segment)
        val pipButton = binding.playerView.findViewById<ImageButton>(R.id.btn_pip)
        val lockButton = binding.playerView.findViewById<ImageButton>(R.id.btn_lockview)
        val unlockButton = binding.playerView.findViewById<ImageButton>(R.id.btn_unlock)
        val orientationButton = binding.playerView.findViewById<ImageButton>(R.id.btn_orientation)
        val castButton = binding.playerView.findViewById<ImageButton>(R.id.btn_cast)
        orientationButton.setOnClickListener {
            applyPlayerOrientation(
                portrait = !isPlayerPortrait,
                persist = !appPreferences.getValue(appPreferences.playerDefaultLandscape),
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { uiState ->
                        Timber.d("$uiState")
                        uiState.apply {
                            // Title
                            videoNameTextView.text = currentItemTitle

                            // Media segment
                            currentSegment?.let { segment ->
                                // Skip Button - text
                                skipSegmentButton.text = getString(currentSkipButtonStringRes)
                                // Skip Button - visibility
                                skipSegmentButton.isVisible = !isInPictureInPictureMode
                                if (skipSegmentButton.isVisible) {
                                    skipButtonTimeoutExpired = false
                                    handler.removeCallbacks(skipButtonTimeout)
                                    handler.postDelayed(
                                        skipButtonTimeout,
                                        viewModel.segmentsSkipButtonDuration * 1000,
                                    )
                                }
                                // Skip Button - onClick
                                skipSegmentButton.setOnClickListener {
                                    viewModel.skipSegment(segment)
                                    skipSegmentButton.isVisible = false
                                }
                            } ?: run { skipSegmentButton.isVisible = false }

                            binding.playerView.setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { visibility ->
                                    if (skipButtonTimeoutExpired && currentSegment != null) {
                                        skipSegmentButton.visibility = visibility
                                    }
                                }
                            )

                            // Trickplay
                            previewScrubListener?.let { it.currentTrickplay = currentTrickplay }

                            playerGestureHelper?.let { it.currentTrickplay = currentTrickplay }

                            // Chapters
                            val playerControlView =
                                findViewById<PlayerControlView>(R.id.exo_controller)
                            if (currentChapters.isNotEmpty()) {
                                val numOfChapters = currentChapters.size
                                playerControlView.setExtraAdGroupMarkers(
                                    LongArray(numOfChapters) { index ->
                                        currentChapters[index].startPosition
                                    },
                                    BooleanArray(numOfChapters) { false },
                                )
                            } else {
                                playerControlView.setExtraAdGroupMarkers(null, null)
                            }

                            // File Loaded
                            if (fileLoaded) {
                                audioButton.isEnabled = true
                                audioButton.imageAlpha = 255
                                lockButton.isEnabled = true
                                lockButton.imageAlpha = 255
                                subtitleButton.isEnabled = true
                                subtitleButton.imageAlpha = 255
                                speedButton.isEnabled = true
                                speedButton.imageAlpha = 255
                                qualityButton.isEnabled = true
                                qualityButton.imageAlpha = 255
                                pipButton.isEnabled = true
                                pipButton.imageAlpha = 255
                                castButton.isEnabled = true
                                castButton.imageAlpha = 255
                            }
                        }
                    }
                }

                launch {
                    viewModel.eventsChannelFlow.collect { event ->
                        when (event) {
                            is PlayerEvents.NavigateBack -> finishPlayback()
                            is PlayerEvents.IsPlayingChanged -> {
                                if (event.isPlaying) {
                                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                } else {
                                    window.clearFlags(
                                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                    )
                                }

                                if (appPreferences.getValue(appPreferences.playerPipGesture)) {
                                    try {
                                        setPictureInPictureParams(pipParams(event.isPlaying))
                                    } catch (_: IllegalArgumentException) {}
                                }
                            }
                        }
                    }
                }

                launch {
                    while (true) {
                        viewModel.updatePlaybackProgress()
                        delay(5000L)
                    }
                }

                if (
                    appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButton) ||
                        appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkip)
                ) {
                    launch {
                        while (true) {
                            viewModel.updateCurrentSegment()
                            delay(1000L)
                        }
                    }
                }

                launch {
                    viewModel.castState.collect { state ->
                        val overlay = state.isActive || state.status == CastStatus.ERROR
                        binding.castControls.isVisible = overlay
                        binding.playerView.useController = !overlay
                        if (overlay) {
                            hideSystemUI()
                        }
                    }
                }
            }
        }

        audioButton.isEnabled = false
        audioButton.imageAlpha = 75

        lockButton.isEnabled = false
        lockButton.imageAlpha = 75

        subtitleButton.isEnabled = false
        subtitleButton.imageAlpha = 75

        speedButton.isEnabled = false
        speedButton.imageAlpha = 75

        qualityButton.isEnabled = false
        qualityButton.imageAlpha = 75

        castButton.isEnabled = false
        castButton.imageAlpha = 75
        if (!appPreferences.getValue(appPreferences.dlnaEnabled)) {
            castButton.isVisible = false
        }
        castButton.setOnClickListener {
            showCastPicker = true
            binding.castPicker.isVisible = true
        }

        if (isPipSupported) {
            pipButton.isEnabled = false
            pipButton.imageAlpha = 75
        } else {
            val pipSpace = binding.playerView.findViewById<Space>(R.id.space_pip)
            pipButton.isVisible = false
            pipSpace.isVisible = false
        }

        audioButton.setOnClickListener {
            TrackSelectionDialogFragment(C.TRACK_TYPE_AUDIO, viewModel)
                .show(supportFragmentManager, "trackselectiondialog")
        }

        val exoPlayerControlView = findViewById<FrameLayout>(R.id.player_controls)
        val lockedLayout = findViewById<FrameLayout>(R.id.locked_player_view)

        lockButton.setOnClickListener {
            exoPlayerControlView.visibility = View.GONE
            lockedLayout.visibility = View.VISIBLE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            isControlsLocked = true
        }

        unlockButton.setOnClickListener {
            exoPlayerControlView.visibility = View.VISIBLE
            lockedLayout.visibility = View.GONE
            applyPlayerOrientation(isPlayerPortrait, persist = false)
            isControlsLocked = false
        }

        subtitleButton.setOnClickListener {
            TrackSelectionDialogFragment(C.TRACK_TYPE_TEXT, viewModel)
                .show(supportFragmentManager, "trackselectiondialog")
        }

        speedButton.setOnClickListener {
            SpeedSelectionDialogFragment(viewModel)
                .show(supportFragmentManager, "speedselectiondialog")
        }

        qualityButton.setOnClickListener {
            QualitySelectionDialogFragment(viewModel)
                .show(supportFragmentManager, "qualityselectiondialog")
        }

        pipButton.setOnClickListener { pictureInPicture() }

        // Set marker color
        val timeBar = binding.playerView.findViewById<DefaultTimeBar>(R.id.exo_progress)
        timeBar.setAdMarkerColor(Color.WHITE)

        if (appPreferences.getValue(appPreferences.playerTrickplay)) {
            val imagePreview = binding.playerView.findViewById<ImageView>(R.id.image_preview)
            previewScrubListener = PreviewScrubListener(imagePreview, timeBar, viewModel.player)

            timeBar.addListener(previewScrubListener!!)
        }

        viewModel.initializePlayer(
            itemId = itemId,
            itemKind = itemKind ?: "",
            startFromBeginning = startFromBeginning,
            castOnStart = castOnStart,
        )
        hideSystemUI()
        handler.post(timeUpdateRunnable)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val itemId = UUID.fromString(intent.extras!!.getString("itemId"))
        val itemKind = intent.extras!!.getString("itemKind")
        val startFromBeginning = intent.extras!!.getBoolean("startFromBeginning")
        val castOnStart = intent.extras!!.getBoolean("castOnStart")

        viewModel.initializePlayer(
            itemId = itemId,
            itemKind = itemKind ?: "",
            startFromBeginning = startFromBeginning,
            castOnStart = castOnStart,
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                appPreferences.getValue(appPreferences.playerPipGesture) &&
                viewModel.player.isPlaying &&
                !isControlsLocked
        ) {
            pictureInPicture()
        }
    }

    private fun setupCastUi() {
        binding.castControls.setContent {
            FindroidTheme {
                val castState by viewModel.castState.collectAsStateWithLifecycle()
                if (castState.isActive || castState.status == CastStatus.ERROR) {
                    CastControls(
                        state = castState,
                        onBack = { finishPlayback() },
                        onPlayPause = {
                            if (castState.isPlaying) {
                                viewModel.pauseCast()
                            } else {
                                viewModel.resumeCast()
                            }
                        },
                        onSeek = { viewModel.seekCast(it) },
                        onAudioTrack = {
                            TrackSelectionDialogFragment(C.TRACK_TYPE_AUDIO, viewModel)
                                .show(supportFragmentManager, "cast_trackselectiondialog")
                        },
                        onSubtitleTrack = {
                            TrackSelectionDialogFragment(C.TRACK_TYPE_TEXT, viewModel)
                                .show(supportFragmentManager, "cast_trackselectiondialog")
                        },
                        onQuality = {
                            QualitySelectionDialogFragment(viewModel)
                                .show(supportFragmentManager, "cast_qualityselectiondialog")
                        },
                        notchModeEnabled = appPreferences.getValue(appPreferences.playerNotchMode),
                        onStop = {
                            showCastPicker = false
                            binding.castPicker.isVisible = false
                            viewModel.stopCastingAndResumeLocal()
                        },
                    )
                }
            }
        }

        binding.castPicker.setContent {
            FindroidTheme {
                if (showCastPicker) {
                    CastDeviceSheet(
                        onDismiss = {
                            showCastPicker = false
                            binding.castPicker.isVisible = false
                        },
                        onSelect = { device: DlnaDevice ->
                            showCastPicker = false
                            binding.castPicker.isVisible = false
                            viewModel.castTo(device)
                        },
                    )
                }
            }
        }
    }

    private fun finishPlayback() {
        try {
            viewModel.player.clearVideoSurfaceView(
                binding.playerView.videoSurfaceView as SurfaceView
            )
        } catch (e: Exception) {
            Timber.e(e)
        }
        handler.removeCallbacks(skipButtonTimeout)
        finish()
    }

    private fun pipParams(
        enableAutoEnter: Boolean = viewModel.player.isPlaying
    ): PictureInPictureParams {
        val displayAspectRatio = Rational(binding.playerView.width, binding.playerView.height)

        val aspectRatio =
            binding.playerView.player?.videoSize?.let {
                Rational(
                    it.width.coerceAtMost((it.height * 2.39f).toInt()),
                    it.height.coerceAtMost((it.width * 2.39f).toInt()),
                )
            }

        val sourceRectHint =
            if (displayAspectRatio < aspectRatio!!) {
                val space =
                    ((binding.playerView.height -
                            (binding.playerView.width.toFloat() / aspectRatio.toFloat())) / 2)
                        .toInt()
                Rect(
                    0,
                    space,
                    binding.playerView.width,
                    (binding.playerView.width.toFloat() / aspectRatio.toFloat()).toInt() + space,
                )
            } else {
                val space =
                    ((binding.playerView.width -
                            (binding.playerView.height.toFloat() * aspectRatio.toFloat())) / 2)
                        .toInt()
                Rect(
                    space,
                    0,
                    (binding.playerView.height.toFloat() * aspectRatio.toFloat()).toInt() + space,
                    binding.playerView.height,
                )
            }

        val builder =
            PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .setSourceRectHint(sourceRectHint)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(enableAutoEnter)
        }

        return builder.build()
    }

    private fun pictureInPicture() {
        if (!isPipSupported) {
            return
        }

        try {
            enterPictureInPictureMode(pipParams())
        } catch (_: IllegalArgumentException) {}
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.isInPictureInPictureMode = isInPictureInPictureMode
        when (isInPictureInPictureMode) {
            true -> {
                binding.playerView.useController = false
                skipSegmentButton.isVisible = false

                wasZoom = playerGestureHelper?.isZoomEnabled == true
                playerGestureHelper?.updateZoomMode(false)

                // Brightness mode Auto
                window.attributes =
                    window.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
            }

            false -> {
                binding.playerView.useController = true
                playerGestureHelper?.updateZoomMode(wasZoom)

                // Override auto brightness
                if (
                    appPreferences.getValue(appPreferences.playerGesturesVB) &&
                        appPreferences.getValue(appPreferences.playerGesturesBrightnessRemember)
                ) {
                    window.attributes =
                        window.attributes.apply {
                            screenBrightness =
                                appPreferences.getValue(appPreferences.playerBrightness)
                        }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::binding.isInitialized) {
            adjustPlayerControls(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)
            binding.playerView.requestApplyInsets()
        }
    }

    private fun applyPlayerOrientation(portrait: Boolean, persist: Boolean) {
        isPlayerPortrait = portrait
        requestedOrientation =
            if (portrait) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        if (persist) {
            appPreferences.setValue(
                appPreferences.playerLastOrientation,
                if (portrait) "portrait" else "landscape",
            )
        }
        if (::binding.isInitialized) {
            adjustPlayerControls(portrait)
            binding.playerView.requestApplyInsets()
        }
    }

    private fun adjustPlayerControls(portrait: Boolean) {
        val extraButtons = binding.playerView.findViewById<LinearLayout>(R.id.extra_buttons)
        val title = binding.playerView.findViewById<TextView>(R.id.video_name)
        title.isVisible = !portrait

        val density = resources.displayMetrics.density
        val horizontalPadding = ((if (portrait) 8 else 16) * density).toInt()
        val verticalPadding = (8 * density).toInt()
        val spaceWidth = ((if (portrait) 4 else 16) * density).toInt()

        applyControlSpacing(extraButtons, horizontalPadding, verticalPadding, spaceWidth)

        // Keep the exit button and title on the same centre line as the first row
        // of the right-hand control buttons.
        binding.playerView
            .findViewById<ImageButton>(R.id.back_button)
            .setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding,
            )

        // Orientation changes don't always redispatch window insets. Reset the
        // bottom padding before requesting insets, otherwise the bottom bar keeps
        // the previous orientation's inset and leaves a large gap.
        val bottomControls = binding.playerView.findViewById<View>(R.id.bottom_controls)
        val baseBottomPadding = (4 * density).toInt()
        bottomControls.setPadding(
            bottomControls.paddingLeft,
            bottomControls.paddingTop,
            bottomControls.paddingRight,
            baseBottomPadding,
        )
        binding.playerView.requestApplyInsets()
        configureBottomBar(portrait)
    }

    private fun configureBottomBar(portrait: Boolean) {
        val bottomControls = binding.playerView.findViewById<ConstraintLayout>(R.id.bottom_controls)
        val density = resources.displayMetrics.density
        val constraints = ConstraintSet()
        constraints.clone(bottomControls)

        val play = R.id.exo_play_pause
        val progress = R.id.exo_progress
        val time = R.id.exo_bottom_time

        for (id in listOf(play, progress, time)) {
            constraints.clear(id, ConstraintSet.START)
            constraints.clear(id, ConstraintSet.END)
            constraints.clear(id, ConstraintSet.TOP)
            constraints.clear(id, ConstraintSet.BOTTOM)
        }

        constraints.connect(play, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraints.connect(
            play,
            ConstraintSet.BOTTOM,
            ConstraintSet.PARENT_ID,
            ConstraintSet.BOTTOM,
        )
        constraints.connect(
            progress,
            ConstraintSet.START,
            play,
            ConstraintSet.END,
            (-4 * density).toInt(),
        )
        constraints.connect(progress, ConstraintSet.TOP, play, ConstraintSet.TOP)
        constraints.connect(progress, ConstraintSet.BOTTOM, play, ConstraintSet.BOTTOM)
        constraints.connect(time, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

        if (portrait) {
            // Portrait: time sits above the progress line, which extends to the right edge.
            constraints.connect(
                progress,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END,
            )
            constraints.connect(
                time,
                ConstraintSet.BOTTOM,
                progress,
                ConstraintSet.TOP,
                (2 * density).toInt(),
            )
        } else {
            // Landscape: play | progress | time on one line.
            constraints.connect(progress, ConstraintSet.END, time, ConstraintSet.START)
            constraints.setMargin(progress, ConstraintSet.END, (8 * density).toInt())
            constraints.connect(time, ConstraintSet.TOP, play, ConstraintSet.TOP)
            constraints.connect(time, ConstraintSet.BOTTOM, play, ConstraintSet.BOTTOM)
        }

        constraints.applyTo(bottomControls)
    }

    private fun applyControlSpacing(
        group: LinearLayout,
        horizontalPadding: Int,
        verticalPadding: Int,
        spaceWidth: Int,
    ) {
        for (index in 0 until group.childCount) {
            when (val child = group.getChildAt(index)) {
                is ImageButton ->
                    child.setPadding(
                        horizontalPadding,
                        verticalPadding,
                        horizontalPadding,
                        verticalPadding,
                    )
                is Space -> child.layoutParams = child.layoutParams.apply { width = spaceWidth }
                is LinearLayout ->
                    applyControlSpacing(child, horizontalPadding, verticalPadding, spaceWidth)
            }
        }
    }

    private fun updateCenterControls() {
        val player = viewModel.player
        centerControls.isVisible = !player.isPlaying && player.playbackState == Player.STATE_READY
        centerPlayPauseButton.setImageResource(
            if (player.isPlaying) CoreR.drawable.ic_pause else CoreR.drawable.ic_play
        )
    }

    private fun updatePlayerTime() {
        val player = binding.playerView.player ?: return
        val timeView = binding.playerView.findViewById<TextView>(R.id.exo_bottom_time) ?: return
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        timeView.text = "${formatPlayerTime(position)}/${formatPlayerTime(duration)}"
    }

    private fun formatPlayerTime(milliseconds: Long): String {
        val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeUpdateRunnable)
        playerGestureHelper?.release()
        super.onDestroy()
    }
}
