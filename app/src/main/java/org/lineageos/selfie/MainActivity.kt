/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.selfie

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.TorchState
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat.getInsetsController
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.lineageos.selfie.ui.GridView
import org.lineageos.selfie.utils.CameraFacing
import org.lineageos.selfie.utils.CameraMode
import org.lineageos.selfie.utils.CameraSoundsUtils
import org.lineageos.selfie.utils.GridMode
import org.lineageos.selfie.utils.PhysicalCamera
import org.lineageos.selfie.utils.StorageUtils
import org.lineageos.selfie.utils.TimeUtils
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@androidx.camera.camera2.interop.ExperimentalCamera2Interop
@androidx.camera.core.ExperimentalZeroShutterLag
@androidx.camera.view.video.ExperimentalVideo
class MainActivity : AppCompatActivity() {
    private val aspectRatioButton by lazy { findViewById<ToggleButton>(R.id.aspectRatioButton) }
    private val bottomButtonsLayout by lazy { findViewById<ConstraintLayout>(R.id.bottomButtonsLayout) }
    private val cameraModeHighlight by lazy { findViewById<MaterialButton>(R.id.cameraModeHighlight) }
    private val effectButton by lazy { findViewById<ImageButton>(R.id.effectButton) }
    private val flashButton by lazy { findViewById<ImageButton>(R.id.flashButton) }
    private val flipCameraButton by lazy { findViewById<ImageButton>(R.id.flipCameraButton) }
    private val galleryButton by lazy { findViewById<ImageView>(R.id.galleryButton) }
    private val gridButton by lazy { findViewById<ImageButton>(R.id.gridButton) }
    private val gridView by lazy { findViewById<GridView>(R.id.gridView) }
    private val micButton by lazy { findViewById<ImageButton>(R.id.micButton) }
    private val photoModeButton by lazy { findViewById<MaterialButton>(R.id.photoModeButton) }
    private val qrModeButton by lazy { findViewById<MaterialButton>(R.id.qrModeButton) }
    private val recordChip by lazy { findViewById<Chip>(R.id.recordChip) }
    private val settingsButton by lazy { findViewById<ImageButton>(R.id.settingsButton) }
    private val shutterButton by lazy { findViewById<ImageButton>(R.id.shutterButton) }
    private val timerButton by lazy { findViewById<ImageButton>(R.id.timerButton) }
    private val timerChip by lazy { findViewById<Chip>(R.id.timerChip) }
    private val torchButton by lazy { findViewById<ImageButton>(R.id.torchButton) }
    private val videoModeButton by lazy { findViewById<MaterialButton>(R.id.videoModeButton) }
    private val viewFinder by lazy { findViewById<PreviewView>(R.id.viewFinder) }
    private val viewFinderFocus by lazy { findViewById<ImageView>(R.id.viewFinderFocus) }
    private val zoomLevel by lazy { findViewById<Slider>(R.id.zoomLevel) }

    private val keyguardManager by lazy { getSystemService(KeyguardManager::class.java) }
    private val locationManager by lazy { getSystemService(LocationManager::class.java) }

    private val imageAnalyzer by lazy { QrImageAnalyzer(this) }

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var extensionsManager: ExtensionsManager

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var cameraController: LifecycleCameraController

    private val cameraMode: CameraMode
        get() = sharedPreferences.lastCameraMode

    private lateinit var camera: PhysicalCamera

    private lateinit var audioConfig: AudioConfig

    private var aspectRatio: Int = AspectRatio.RATIO_4_3

    private var extensionMode = ExtensionMode.NONE
    private var supportedExtensionModes = listOf(extensionMode)

    private var isTakingPhoto: Boolean = false
    private var tookSomething: Boolean = false

    private var viewFinderTouchEvent: MotionEvent? = null

    private var recording: Recording? = null
    private val recordingLock = Mutex()
    private var recordingTime = 0L
        set(value) {
            field = value
            recordChip.text = TimeUtils.convertNanosToString(recordingTime)
        }

    private val sharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    private lateinit var cameraSoundsUtils: CameraSoundsUtils

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                MSG_HIDE_ZOOM_SLIDER -> {
                    zoomLevel.visibility = View.GONE
                }
                MSG_HIDE_FOCUS_RING -> {
                    viewFinderFocus.visibility = View.GONE
                }
            }
        }
    }

    private var location: Location? = null
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(it: Location) {
            if (location == null || location!!.accuracy >= it.accuracy) {
                location = it
            }
        }

        @SuppressLint("MissingPermission")
        fun register() {
            // Reset cached location
            location = null

            if (allLocationPermissionsGranted() && sharedPreferences.saveLocation) {
                // Request location updates
                locationManager.allProviders.forEach {
                    locationManager.requestLocationUpdates(it, 1000, 1f, this)
                }
            }
        }

        fun unregister() {
            // Remove updates
            locationManager.removeUpdates(this)

            // Reset cached location
            location = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hideStatusBars()

        setContentView(R.layout.activity_main)
        setShowWhenLocked(true)

        // Request camera permissions
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Initialize camera provider
        cameraProvider = ProcessCameraProvider.getInstance(this).get()

        // Initialize camera controller
        cameraController = LifecycleCameraController(this)

        // Get vendor extensions manager
        extensionsManager = ExtensionsManager.getInstanceAsync(this, cameraProvider).get()

        // Initialize sounds utils
        cameraSoundsUtils = CameraSoundsUtils(sharedPreferences)

        // Set top bar button callbacks
        aspectRatioButton.setOnClickListener { cycleAspectRatio() }
        effectButton.setOnClickListener { cyclePhotoEffects() }
        gridButton.setOnClickListener { cycleGridMode() }
        timerButton.setOnClickListener { toggleTimerMode() }
        torchButton.setOnClickListener { toggleTorchMode() }
        flashButton.setOnClickListener { cycleFlashMode() }
        micButton.setOnClickListener { toggleMicrophoneMode() }
        settingsButton.setOnClickListener { openSettings() }

        // Initialize camera mode highlight position
        (cameraModeHighlight.parent as View).doOnLayout {
            cameraModeHighlight.x = when (cameraMode) {
                CameraMode.QR -> qrModeButton.x
                CameraMode.PHOTO -> photoModeButton.x
                CameraMode.VIDEO -> videoModeButton.x
            }
        }

        // Attach CameraController to PreviewView
        viewFinder.controller = cameraController

        // Observe torch state
        cameraController.torchState.observe(this) {
            updateTorchModeIcon()
        }

        // Observe focus state
        cameraController.tapToFocusState.observe(this) {
            when (it) {
                CameraController.TAP_TO_FOCUS_STARTED -> {
                    viewFinderFocus.visibility = View.VISIBLE
                    handler.removeMessages(MSG_HIDE_FOCUS_RING)
                    ValueAnimator.ofInt(convertDpToPx(0), convertDpToPx(8)).apply {
                        addUpdateListener { anim ->
                            viewFinderFocus.setPadding(anim.animatedValue as Int)
                        }
                    }.start()
                }
                else -> {
                    handler.removeMessages(MSG_HIDE_FOCUS_RING)
                    ValueAnimator.ofInt(convertDpToPx(8), convertDpToPx(0)).apply {
                        addUpdateListener { anim ->
                            viewFinderFocus.setPadding(anim.animatedValue as Int)
                        }
                    }.start()

                    handler.sendMessageDelayed(handler.obtainMessage(MSG_HIDE_FOCUS_RING), 500)
                }
            }
        }

        // Observe manual focus
        viewFinder.setOnTouchListener { _, event ->
            val isSingleTouch = event.pointerCount == 1
            val isUpEvent = event.action == MotionEvent.ACTION_UP
            val notALongPress = (event.eventTime - event.downTime
                    < ViewConfiguration.getLongPressTimeout())
            if (isSingleTouch && isUpEvent && notALongPress) {
                // If the event is a click, invoke tap-to-focus and forward it to user's
                // OnClickListener#onClick.
                viewFinderTouchEvent = event
            }
            return@setOnTouchListener false
        }
        viewFinder.setOnClickListener { view ->
            viewFinderTouchEvent?.let {
                viewFinderFocus.x = it.x - (viewFinderFocus.width / 2)
                viewFinderFocus.y = it.y - (viewFinderFocus.height / 2)
            } ?: run {
                viewFinderFocus.x = (view.width - viewFinderFocus.width) / 2f
                viewFinderFocus.y = (view.height - viewFinderFocus.height) / 2f
            }
        }

        // Observe preview stream state
        viewFinder.previewStreamState.observe(this) {
            when (it) {
                PreviewView.StreamState.STREAMING -> {
                    // Show grid
                    gridView.alpha = 1f
                    gridView.previewView = viewFinder
                }
                else -> {}
            }
        }

        // Observe zoom state
        cameraController.zoomState.observe(this) {
            if (it.minZoomRatio == it.maxZoomRatio) {
                return@observe
            }

            zoomLevel.value = it.linearZoom
            zoomLevel.visibility = View.VISIBLE

            handler.removeMessages(MSG_HIDE_ZOOM_SLIDER)
            handler.sendMessageDelayed(handler.obtainMessage(MSG_HIDE_ZOOM_SLIDER), 2000)
        }

        zoomLevel.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                cameraController.setLinearZoom(value)
            }
        }
        zoomLevel.setLabelFormatter {
            "%.1fx".format(cameraController.zoomState.value?.zoomRatio)
        }

        // Set bottom bar button callbacks
        qrModeButton.setOnClickListener { changeCameraMode(CameraMode.QR) }
        photoModeButton.setOnClickListener { changeCameraMode(CameraMode.PHOTO) }
        videoModeButton.setOnClickListener { changeCameraMode(CameraMode.VIDEO) }

        flipCameraButton.setOnClickListener { flipCamera() }

        shutterButton.setOnClickListener {
            // Shutter animation
            ValueAnimator.ofInt(convertDpToPx(4), convertDpToPx(16), convertDpToPx(4)).apply {
                addUpdateListener {
                    shutterButton.setPadding(it.animatedValue as Int)
                }
            }.start()

            startTimerAndRun {
                when (cameraMode) {
                    CameraMode.PHOTO -> takePhoto()
                    CameraMode.VIDEO -> lifecycleScope.launch { captureVideo() }
                    else -> {}
                }
            }
        }

        galleryButton.setOnClickListener { openGallery() }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onResume() {
        super.onResume()

        // Set bright screen
        setBrightScreen(sharedPreferences.brightScreen)

        // Special case: we want to enable the gallery by default if
        // we have at least one saved Uri and we aren't locked
        updateGalleryButton(sharedPreferences.lastSavedUri, !keyguardManager.isKeyguardLocked)

        // Register location updates
        locationListener.register()

        // Re-bind the use cases
        bindCameraUseCases()
    }

    override fun onPause() {
        // Remove location and location updates
        locationListener.unregister()

        // Reset tookSomething state
        tookSomething = false

        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(
                    this, getString(R.string.app_permissions_toast), Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (cameraMode == CameraMode.VIDEO && shutterButton.isEnabled && event?.repeatCount == 1) {
                    shutterButton.performClick()
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (cameraMode != CameraMode.QR && shutterButton.isEnabled) {
                    shutterButton.performClick()
                }
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }

    private fun takePhoto() {
        // Bail out if a photo is already being taken
        if (isTakingPhoto) {
            return
        }

        isTakingPhoto = true
        shutterButton.isEnabled = false

        // Create output options object which contains file + metadata
        val outputOptions = StorageUtils.getPhotoMediaStoreOutputOptions(
            contentResolver,
            ImageCapture.Metadata().apply {
                location = this@MainActivity.location
            }
        )

        // Set up image capture listener, which is triggered after photo has
        // been taken
        cameraController.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(LOG_TAG, "Photo capture failed: ${exc.message}", exc)
                    isTakingPhoto = false
                    shutterButton.isEnabled = true
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    cameraSoundsUtils.playShutterClick()
                    viewFinder.foreground = ColorDrawable(Color.BLACK)
                    ValueAnimator.ofInt(0, 255, 0).apply {
                        addUpdateListener { anim ->
                            viewFinder.foreground.alpha = anim.animatedValue as Int
                        }
                    }.start()
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    sharedPreferences.lastSavedUri = output.savedUri
                    updateGalleryButton(output.savedUri)
                    Log.d(LOG_TAG, msg)
                    isTakingPhoto = false
                    shutterButton.isEnabled = true
                    tookSomething = true
                }
            }
        )
    }

    private suspend fun captureVideo() = recordingLock.withLock {
        if (cameraController.isRecording) {
            // Stop the current recording session.
            recording?.stop()
            return
        }

        // Create output options object which contains file + metadata
        val outputOptions = StorageUtils.getVideoMediaStoreOutputOptions(contentResolver, location)

        // Play shutter sound
        if (cameraSoundsUtils.playStartVideoRecording()) {
            // Delay startRecording() by 500ms to avoid recording shutter sound
            delay(500)
        }

        // Start recording
        recording = cameraController.startRecording(
            outputOptions,
            audioConfig,
            cameraExecutor
        ) {
            if (it is VideoRecordEvent.Status) {
                runOnUiThread {
                    recordingTime = it.recordingStats.recordedDurationNanos
                    recordChip.isVisible = true
                }
            } else if (it is VideoRecordEvent.Finalize) {
                runOnUiThread {
                    recordChip.isVisible = false
                }
                cameraSoundsUtils.playStopVideoRecording()
                val msg = "Video capture succeeded: ${it.outputResults.outputUri}"
                sharedPreferences.lastSavedUri = it.outputResults.outputUri
                updateGalleryButton(it.outputResults.outputUri)
                Log.d(LOG_TAG, msg)
                tookSomething = true
                recording = null
            }
        }
    }

    /**
     * Check if we can reinitialize the camera use cases
     */
    private fun canRestartCamera() = when (cameraMode) {
        // Disallow camera restart if we're taking a photo or if timer is running
        CameraMode.PHOTO -> !isTakingPhoto && !timerChip.isVisible
        // Disallow camera restart if a recording in progress or if timer is running
        CameraMode.VIDEO -> !cameraController.isRecording && !timerChip.isVisible
        // Otherwise, allow camera restart
        else -> true
    }

    /**
     * Rebind cameraProvider use cases
     */
    private fun bindCameraUseCases() {
        // Unbind previous use cases
        cameraController.unbind()

        isTakingPhoto = false

        // Hide grid until preview is ready
        gridView.alpha = 0f

        // Select front/back camera
        var cameraSelector = when (cameraMode) {
            CameraMode.QR -> CameraSelector.DEFAULT_BACK_CAMERA
            else -> when (sharedPreferences.lastCameraFacing) {
                CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                else -> CameraSelector.DEFAULT_BACK_CAMERA
            }
        }

        // Get the supported vendor extensions for the given camera selector
        supportedExtensionModes = extensionsManager.getSupportedModes(cameraSelector)

        // Get the user selected effect
        extensionMode = sharedPreferences.photoEffect
        if (!supportedExtensionModes.contains(extensionMode)) {
            extensionMode = ExtensionMode.NONE
        }

        // Get aspect ratio
        aspectRatio = sharedPreferences.aspectRatio
        val outputSize = CameraController.OutputSize(aspectRatio)

        // Initialize the use case we want and set its properties
        val cameraUseCases = when (cameraMode) {
            CameraMode.QR -> {
                cameraController.imageAnalysisTargetSize = outputSize
                cameraController.setImageAnalysisAnalyzer(cameraExecutor, imageAnalyzer)
                CameraController.IMAGE_ANALYSIS
            }
            CameraMode.PHOTO -> {
                cameraController.imageCaptureTargetSize = outputSize
                CameraController.IMAGE_CAPTURE
            }
            CameraMode.VIDEO -> {
                cameraController.videoCaptureTargetQuality = null // FIXME: video preview restart
                cameraController.videoCaptureTargetQuality = sharedPreferences.videoQuality
                CameraController.VIDEO_CAPTURE
            }
        }

        // Only photo mode supports vendor extensions for now
        if (cameraMode == CameraMode.PHOTO && supportedExtensionModes.contains(extensionMode)) {
            cameraSelector = extensionsManager.getExtensionEnabledCameraSelector(
                cameraSelector, extensionMode
            )
        }

        // Setup UI depending on camera mode
        when (cameraMode) {
            CameraMode.QR -> {
                timerButton.isVisible = false
                bottomButtonsLayout.isInvisible = true
            }
            CameraMode.PHOTO -> {
                timerButton.isVisible = true
                bottomButtonsLayout.isInvisible = false
            }
            CameraMode.VIDEO -> {
                timerButton.isVisible = true
                bottomButtonsLayout.isInvisible = false
            }
        }

        // Bind use cases to camera
        cameraController.cameraSelector = cameraSelector
        cameraController.setEnabledUseCases(cameraUseCases)

        // Restore settings that needs a rebind
        cameraController.imageCaptureMode = sharedPreferences.photoCaptureMode

        // Bind camera controller to lifecycle
        cameraController.bindToLifecycle(this)

        // Get a stable reference to CameraInfo
        // We can hardcode the first one in the filter as long as we use DEFAULT_*_CAMERA
        camera = PhysicalCamera(cameraSelector.filter(cameraProvider.availableCameraInfos)[0])

        // Restore settings that can be set on the fly
        setGridMode(sharedPreferences.lastGridMode)
        setFlashMode(sharedPreferences.photoFlashMode)
        setMicrophoneMode(sharedPreferences.lastMicMode)

        // Update icons from last state
        updateCameraModeButtons()
        updateTimerModeIcon()
        updateAspectRatioIcon()
        updatePhotoEffectIcon()
        updateGridIcon()
        updateTorchModeIcon()
        updateFlashModeIcon()
        updateMicrophoneModeIcon()
    }

    /**
     * Change the current camera mode and restarts the stream
     */
    private fun changeCameraMode(cameraMode: CameraMode) {
        if (!canRestartCamera()) {
            return
        }

        if (cameraMode == this.cameraMode) {
            return
        }

        sharedPreferences.lastCameraMode = cameraMode
        bindCameraUseCases()
    }

    /**
     * Cycle between cameras
     */
    private fun flipCamera() {
        if (!canRestartCamera()) {
            return
        }

        (flipCameraButton.drawable as AnimatedVectorDrawable).start()

        sharedPreferences.lastCameraFacing =
            when (cameraController.physicalCamera?.cameraFacing) {
                // We can definitely do it better
                CameraFacing.FRONT -> CameraFacing.BACK
                CameraFacing.BACK -> CameraFacing.FRONT
                else -> CameraFacing.BACK
            }

        bindCameraUseCases()
    }

    /**
     * Update the camera mode buttons reflecting the current mode
     */
    private fun updateCameraModeButtons() {
        qrModeButton.isEnabled = cameraMode != CameraMode.QR
        photoModeButton.isEnabled = cameraMode != CameraMode.PHOTO
        videoModeButton.isEnabled = cameraMode != CameraMode.VIDEO

        // Animate camera mode change
        (cameraModeHighlight.parent as View).doOnLayout {
            ValueAnimator.ofFloat(
                cameraModeHighlight.x, when (cameraMode) {
                    CameraMode.QR -> qrModeButton.x
                    CameraMode.PHOTO -> photoModeButton.x
                    CameraMode.VIDEO -> videoModeButton.x
                }
            ).apply {
                addUpdateListener {
                    cameraModeHighlight.x = it.animatedValue as Float
                }
            }.start()
        }
    }

    private fun cycleAspectRatio() {
        if (!canRestartCamera()) {
            return
        }

        sharedPreferences.aspectRatio = when (aspectRatio) {
            AspectRatio.RATIO_4_3 -> AspectRatio.RATIO_16_9
            AspectRatio.RATIO_16_9 -> AspectRatio.RATIO_4_3
            else -> AspectRatio.RATIO_4_3
        }

        bindCameraUseCases()
    }

    /**
     * Update the grid button icon based on the value set in grid view
     */
    private fun updateGridIcon() {
        gridButton.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                when (sharedPreferences.lastGridMode) {
                    GridMode.OFF -> R.drawable.ic_grid_off
                    GridMode.ON_3 -> R.drawable.ic_grid_on_3
                    GridMode.ON_4 -> R.drawable.ic_grid_on_4
                    GridMode.ON_GOLDENRATIO -> R.drawable.ic_grid_on_goldenratio
                }
            )
        )
    }

    /**
     * Set the specified grid mode, also updating the icon
     */
    private fun cycleGridMode() {
        sharedPreferences.lastGridMode = when (sharedPreferences.lastGridMode) {
            GridMode.OFF -> GridMode.ON_3
            GridMode.ON_3 -> GridMode.ON_4
            GridMode.ON_4 -> GridMode.ON_GOLDENRATIO
            GridMode.ON_GOLDENRATIO -> GridMode.OFF
        }
        setGridMode(sharedPreferences.lastGridMode)
    }

    private fun setGridMode(gridMode: GridMode) {
        gridView.mode = gridMode
        updateGridIcon()
    }

    /**
     * Update the timer mode button icon based on the value set in settings
     */
    private fun updateTimerModeIcon() {
        timerButton.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                when (sharedPreferences.timerMode) {
                    3 -> R.drawable.ic_timer_3
                    10 -> R.drawable.ic_timer_10
                    else -> R.drawable.ic_timer_off
                }
            )
        )
    }

    /**
     * Toggle timer mode
     */
    private fun toggleTimerMode() {
        sharedPreferences.timerMode = when (sharedPreferences.timerMode) {
            0 -> 3
            3 -> 10
            else -> 0
        }
        updateTimerModeIcon()
    }

    private fun updateAspectRatioIcon() {
        aspectRatioButton.isVisible = cameraMode != CameraMode.VIDEO
        aspectRatioButton.text = when (sharedPreferences.aspectRatio) {
            AspectRatio.RATIO_4_3 -> "4:3"
            AspectRatio.RATIO_16_9 -> "16:9"
            else -> throw Exception("Unknown aspect ratio ${sharedPreferences.aspectRatio}")
        }
    }

    /**
     * Update the torch mode button icon based on the value set in camera
     */
    private fun updateTorchModeIcon() {
        torchButton.isVisible = cameraController.cameraInfo?.hasFlashUnit() == true
        torchButton.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                when (cameraController.torchState.value) {
                    TorchState.OFF -> R.drawable.ic_torch_off
                    TorchState.ON -> R.drawable.ic_torch_on
                    else -> R.drawable.ic_torch_off
                }
            )
        )
    }

    /**
     * Toggle torch mode
     */
    private fun toggleTorchMode() {
        cameraController.enableTorch(cameraController.torchState.value != TorchState.ON)
    }

    /**
     * Update the flash mode button icon based on the value set in imageCapture
     */
    private fun updateFlashModeIcon() {
        flashButton.isVisible = cameraMode == CameraMode.PHOTO && camera.hasFlashUnit
        flashButton.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                when (cameraController.imageCaptureFlashMode) {
                    ImageCapture.FLASH_MODE_AUTO -> R.drawable.ic_flash_auto
                    ImageCapture.FLASH_MODE_ON -> R.drawable.ic_flash_on
                    ImageCapture.FLASH_MODE_OFF -> R.drawable.ic_flash_off
                    else -> R.drawable.ic_flash_off
                }
            )
        )
    }

    /**
     * Set the specified flash mode, saving the value to shared prefs and updating the icon
     */
    private fun setFlashMode(flashMode: Int) {
        cameraController.imageCaptureFlashMode = flashMode
        updateFlashModeIcon()

        sharedPreferences.photoFlashMode = flashMode
    }

    /**
     * Cycle flash mode between auto, on and off
     */
    private fun cycleFlashMode() {
        setFlashMode(
            when (cameraController.imageCaptureFlashMode) {
                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_AUTO
            }
        )
    }

    /**
     * Update the microphone mode button icon based on the value set in audioConfig
     */
    private fun updateMicrophoneModeIcon() {
        micButton.isVisible = cameraMode == CameraMode.VIDEO
        micButton.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                if (audioConfig.audioEnabled) R.drawable.ic_mic_on else R.drawable.ic_mic_off
            )
        )
    }

    /**
     * Toggles microphone during video recording
     */
    private fun toggleMicrophoneMode() {
        setMicrophoneMode(!audioConfig.audioEnabled)
    }

    /**
     * Set the specified microphone mode, saving the value to shared prefs and updating the icon
     */
    @SuppressLint("MissingPermission")
    private fun setMicrophoneMode(microphoneMode: Boolean) {
        if (!canRestartCamera()) {
            return
        }

        audioConfig = AudioConfig.create(microphoneMode)
        updateMicrophoneModeIcon()

        sharedPreferences.lastMicMode = microphoneMode
    }

    /**
     * Set a photo effect and restart the camera if required
     */
    private fun setExtensionMode(extensionMode: Int) {
        if (!canRestartCamera()) {
            return
        }

        if (extensionMode == this.extensionMode) {
            return
        }

        sharedPreferences.photoEffect = extensionMode

        bindCameraUseCases()
    }

    /**
     * Update the photo effect icon based on the current value of extensionMode
     */
    private fun updatePhotoEffectIcon() {
        effectButton.isVisible = cameraMode == CameraMode.PHOTO && supportedExtensionModes.size > 1
        effectButton.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                when (extensionMode) {
                    ExtensionMode.NONE -> R.drawable.ic_effect_none
                    ExtensionMode.BOKEH -> R.drawable.ic_effect_bokeh
                    ExtensionMode.HDR -> R.drawable.ic_effect_hdr
                    ExtensionMode.NIGHT -> R.drawable.ic_effect_night
                    ExtensionMode.FACE_RETOUCH -> R.drawable.ic_effect_face_retouch
                    ExtensionMode.AUTO -> R.drawable.ic_effect_auto
                    else -> R.drawable.ic_effect_none
                }
            )
        )
    }

    /**
     * Cycle between supported photo camera effects
     */
    private fun cyclePhotoEffects() {
        if (!canRestartCamera()) {
            return
        }

        setExtensionMode(
            if (extensionMode == supportedExtensionModes.last()) supportedExtensionModes.first()
            else supportedExtensionModes.indexOf(extensionMode) + 1
        )
    }

    private fun setBrightScreen(brightScreen: Boolean) {
        window.attributes = window.attributes.apply {
            screenBrightness =
                if (brightScreen) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun allLocationPermissionsGranted() = REQUIRED_PERMISSIONS_LOCATION.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateGalleryButton(uri: Uri?, enable: Boolean = true) {
        runOnUiThread {
            if (uri != null && enable) {
                getThumbnail(uri)?.let {
                    galleryButton.setPadding(0)
                    galleryButton.setImageBitmap(it)
                } ?: run {
                    galleryButton.setPadding(convertDpToPx(15))
                    galleryButton.setImageResource(R.drawable.ic_image)
                }
            } else if (keyguardManager.isKeyguardLocked) {
                galleryButton.setPadding(convertDpToPx(15))
                galleryButton.setImageResource(R.drawable.ic_lock)
            } else {
                galleryButton.setPadding(convertDpToPx(15))
                galleryButton.setImageResource(R.drawable.ic_image)
            }
        }
    }

    private fun dismissKeyguardAndRun(runnable: () -> Unit) {
        if (!keyguardManager.isKeyguardLocked) {
            runnable()
            return
        }

        keyguardManager.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    super.onDismissSucceeded()
                    runnable()
                }
            }
        )
    }

    private fun openGallery() {
        sharedPreferences.lastSavedUri.let { uri ->
            // If the Uri is null, attempt to launch non secure-gallery
            if (uri == null) {
                dismissKeyguardAndRun {
                    val intent = Intent().apply {
                        action = Intent.ACTION_VIEW
                        type = "image/*"
                    }
                    runCatching {
                        startActivity(intent)
                        return@dismissKeyguardAndRun
                    }
                }
                return
            }

            // This ensure we took at least one photo/video in the secure use-case
            if (tookSomething && keyguardManager.isKeyguardLocked) {
                val intent = Intent().apply {
                    action = MediaStore.ACTION_REVIEW_SECURE
                    data = uri
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                runCatching {
                    startActivity(intent)
                    return
                }
            }

            // Try to open the Uri in the non secure gallery
            dismissKeyguardAndRun {
                listOf(MediaStore.ACTION_REVIEW, Intent.ACTION_VIEW).forEach {
                    val intent = Intent().apply {
                        action = it
                        data = uri
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    runCatching {
                        startActivity(intent)
                        return@dismissKeyguardAndRun
                    }
                }
            }
        }
    }

    private fun openSettings() {
        if (!canRestartCamera()) {
            return
        }

        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun hideStatusBars() {
        val windowInsetsController = getInsetsController(window, window.decorView)
        // Configure the behavior of the hidden system bars
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Hide the status bar
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
    }

    private fun getThumbnail(uri: Uri?): Bitmap? {
        return try {
            uri?.let {
                val sizeInPx = convertDpToPx(75)
                contentResolver.loadThumbnail(it, Size(sizeInPx, sizeInPx), null)
            }
        } catch (exception: Exception) {
            Log.e(LOG_TAG, "${exception.message}")
            null
        }
    }

    private fun startTimerAndRun(runnable: () -> Unit) {
        if (sharedPreferences.timerMode <= 0 || !canRestartCamera()) {
            runnable()
            return
        }

        lifecycleScope.launch {
            shutterButton.isEnabled = false
            timerChip.isVisible = true

            for (i in sharedPreferences.timerMode downTo 1) {
                timerChip.text = "$i"
                delay(1000)
            }

            timerChip.isVisible = false
            shutterButton.isEnabled = true

            runnable()
        }
    }

    companion object {
        private const val LOG_TAG = "Selfie"

        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()
        internal val REQUIRED_PERMISSIONS_LOCATION =
            mutableListOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ).toTypedArray()

        private const val MSG_HIDE_ZOOM_SLIDER = 0
        private const val MSG_HIDE_FOCUS_RING = 1
    }
}