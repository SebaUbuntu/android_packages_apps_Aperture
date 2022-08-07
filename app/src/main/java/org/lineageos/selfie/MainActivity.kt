package org.lineageos.selfie

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.TorchState
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.OnVideoSavedCallback
import androidx.camera.view.video.OutputFileResults
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat.getInsetsController
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import org.lineageos.selfie.ui.GridView
import org.lineageos.selfie.utils.CameraFacing
import org.lineageos.selfie.utils.CameraMode
import org.lineageos.selfie.utils.GridMode
import org.lineageos.selfie.utils.StorageUtils
import org.lineageos.selfie.utils.TimeUtils
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {
    private val effectButton by lazy { findViewById<ImageView>(R.id.effectButton) }
    private val flashButton by lazy { findViewById<ImageView>(R.id.flashButton) }
    private val flipCameraButton by lazy { findViewById<ImageView>(R.id.flipCameraButton) }
    private val gridButton by lazy { findViewById<ImageView>(R.id.gridButton) }
    private val gridView by lazy { findViewById<GridView>(R.id.gridView) }
    private val mainLayout by lazy { findViewById<ConstraintLayout>(R.id.mainLayout) }
    private val photoModeButton by lazy { findViewById<ImageView>(R.id.photoModeButton) }
    private val recordChip by lazy { findViewById<Chip>(R.id.recordChip) }
    private val settingsButton by lazy { findViewById<ImageView>(R.id.settingsButton) }
    private val shutterButton by lazy { findViewById<ImageView>(R.id.shutterButton) }
    private val galleryButton by lazy { findViewById<ImageView>(R.id.galleryButton) }
    private val torchButton by lazy { findViewById<ImageView>(R.id.torchButton) }
    private val videoModeButton by lazy { findViewById<ImageView>(R.id.videoModeButton) }
    private val viewFinder by lazy { findViewById<PreviewView>(R.id.viewFinder) }
    private val viewFinderFocus by lazy { findViewById<ImageView>(R.id.viewFinderFocus) }
    private val zoomLevel by lazy { findViewById<Slider>(R.id.zoomLevel) }

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var extensionsManager: ExtensionsManager

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var cameraController: LifecycleCameraController

    private lateinit var cameraMode: CameraMode

    private var extensionMode = ExtensionMode.NONE
    private var extensionModeIndex = 0
    private var supportedExtensionModes = listOf(extensionMode)

    private var isTakingPhoto: Boolean = false

    private var viewFinderTouchEvent: MotionEvent? = null

    private var recordingTime = 0L
        set(value) {
            field = value
            recordChip.text = TimeUtils.convertSecondsToString(recordingTime)
        }
    private lateinit var recordingTimer: Timer

    private val sharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

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

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    @androidx.camera.core.ExperimentalZeroShutterLag
    @androidx.camera.view.video.ExperimentalVideo
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()

        setContentView(R.layout.activity_main)
        setShowWhenLocked(true)

        // Request camera permissions
        if (allPermissionsGranted()) {
            initCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        effectButton.setOnClickListener { cyclePhotoEffects() }
        gridButton.setOnClickListener { toggleGrid() }
        torchButton.setOnClickListener { toggleTorchMode() }
        flashButton.setOnClickListener { cycleFlashMode() }
        settingsButton.setOnClickListener { openSettings() }

        zoomLevel.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                cameraController.setZoomRatio(value)
            }
        }

        photoModeButton.setOnClickListener { changeCameraMode(CameraMode.PHOTO) }
        videoModeButton.setOnClickListener { changeCameraMode(CameraMode.VIDEO) }

        flipCameraButton.setOnClickListener { flipCamera() }

        shutterButton.setOnClickListener {
            when (cameraMode) {
                CameraMode.PHOTO -> takePhoto()
                CameraMode.VIDEO -> captureVideo()
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    @androidx.camera.core.ExperimentalZeroShutterLag
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                initCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun takePhoto() {
        // Bail out if a photo is already being taken
        if (isTakingPhoto)
            return

        isTakingPhoto = true
        shutterButton.isEnabled = false

        // Create output options object which contains file + metadata
        val outputOptions = StorageUtils.getPhotoMediaStoreOutputOptions(contentResolver)

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
                    mainLayout.foreground = ColorDrawable(Color.WHITE)
                    val colorFade: ObjectAnimator = ObjectAnimator.ofInt(
                        mainLayout.foreground,
                        "alpha",
                        255,
                        0,
                    )
                    colorFade.duration = 500
                    colorFade.start()
                    val intent = Intent().apply {
                        action = MediaStore.ACTION_REVIEW
                        data = output.savedUri
                    }
                    galleryButton.setOnClickListener { startActivity(intent) }
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Log.d(LOG_TAG, msg)
                    isTakingPhoto = false
                    shutterButton.isEnabled = true
                }
            }
        )
    }

    @androidx.camera.view.video.ExperimentalVideo
    private fun captureVideo() {
        if (cameraController.isRecording) {
            // Stop the current recording session.
            cameraController.stopRecording()
            return
        }

        // Create output options object which contains file + metadata
        val outputOptions = StorageUtils.getVideoMediaStoreOutputOptions(contentResolver)

        // Start recording
        cameraController.startRecording(
            outputOptions,
            cameraExecutor,
            object : OnVideoSavedCallback {
                override fun onVideoSaved(output: OutputFileResults) {
                    stopRecordingTimer()
                    val intent = Intent().apply {
                        action = MediaStore.ACTION_REVIEW
                        data = output.savedUri
                    }
                    galleryButton.setOnClickListener { startActivity(intent) }
                    val msg = "Video capture succeeded: ${output.savedUri}"
                    Log.d(LOG_TAG, msg)
                }

                override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                    stopRecordingTimer()
                    Log.e(LOG_TAG, "Video capture ends with error: $message")
                }
            }
        )

        startRecordingTimer()
    }

    /**
     * Check if we can reinitialize the camera use cases
     */
    @androidx.camera.view.video.ExperimentalVideo
    private fun canRestartCamera(): Boolean {
        if (cameraMode == CameraMode.PHOTO) {
            // Check if we're taking a photo
            if (isTakingPhoto)
                return false
        } else if (cameraMode == CameraMode.VIDEO) {
            // Check for a recording in progress
            if (cameraController.isRecording)
                return false
        }

        return true
    }

    /**
     * Prepare CameraProvider and other one-time init objects, must only be called from onCreate
     */
    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    @androidx.camera.core.ExperimentalZeroShutterLag
    private fun initCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            // Initialize camera controller
            cameraController = LifecycleCameraController(this)

            // Get vendor extensions manager
            extensionsManager =
                ExtensionsManager.getInstanceAsync(this, cameraProvider).get()

            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Rebind cameraProvider use cases
     */
    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    @androidx.camera.core.ExperimentalZeroShutterLag
    @androidx.camera.view.video.ExperimentalVideo
    private fun bindCameraUseCases() {
        // Unbind previous use cases
        cameraController.unbind()

        isTakingPhoto = false

        // Select front/back camera
        var cameraSelector = when (sharedPreferences.getLastCameraFacing()) {
            CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Get the supported vendor extensions for the given camera selector
        supportedExtensionModes = extensionsManager.getSupportedModes(cameraSelector)

        // Get the user selected effect
        extensionMode = sharedPreferences.getPhotoEffect()
        if (!supportedExtensionModes.contains(extensionMode))
            extensionMode = ExtensionMode.NONE
        extensionModeIndex = supportedExtensionModes.indexOf(extensionMode)

        // Initialize the use case we want
        cameraMode = sharedPreferences.getLastCameraMode()
        val cameraUseCases = when (cameraMode) {
            CameraMode.PHOTO -> CameraController.IMAGE_CAPTURE
            CameraMode.VIDEO -> CameraController.VIDEO_CAPTURE
        }

        // Only photo mode supports vendor extensions for now
        if (cameraMode == CameraMode.PHOTO) {
            // Select the extension
            if (supportedExtensionModes.contains(extensionMode)) {
                cameraSelector = extensionsManager.getExtensionEnabledCameraSelector(
                    cameraSelector, extensionMode
                )
            } else {
                val msg = "Extension $extensionMode is not supported"
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                    .show()
                Log.e(LOG_TAG, msg)
            }
        }

        // Bind use cases to camera
        cameraController.cameraSelector = cameraSelector
        cameraController.setEnabledUseCases(cameraUseCases)

        // Attach CameraController to PreviewView
        viewFinder.controller = cameraController

        // Restore settings that needs a rebind
        cameraController.imageCaptureMode = sharedPreferences.getPhotoCaptureMode()

        // Bind camera controller to lifecycle
        cameraController.bindToLifecycle(this)

        // Restore settings that can be set on the fly
        setGridMode(sharedPreferences.getLastGridMode())
        setFlashMode(sharedPreferences.getPhotoFlashMode())

        // Observe focus state
        cameraController.tapToFocusState.observe(this) {
            when (it) {
                CameraController.TAP_TO_FOCUS_STARTED -> {
                    viewFinderFocus.visibility = View.VISIBLE
                    handler.removeMessages(MSG_HIDE_FOCUS_RING)
                }
                else -> {
                    handler.removeMessages(MSG_HIDE_FOCUS_RING)
                    handler.sendMessageDelayed(handler.obtainMessage(MSG_HIDE_FOCUS_RING), 500)
                }
            }
        }

        viewFinder.setOnTouchListener { view, event ->
            val isSingleTouch = event.pointerCount == 1
            val isUpEvent = event.action == MotionEvent.ACTION_UP
            val notALongPress = (event.eventTime - event.downTime
                    < ViewConfiguration.getLongPressTimeout())
            if (isSingleTouch && isUpEvent && notALongPress) {
                // If the event is a click, invoke tap-to-focus and forward it to user's
                // OnClickListener#onClick.
                viewFinderTouchEvent = event
                view.performClick()
                // A click has been detected and forwarded. Consume the event so onClick won't be
                // invoked twice.
                return@setOnTouchListener true
            }
            return@setOnTouchListener false
        }
        viewFinder.setOnClickListener { view ->
            viewFinderTouchEvent?.let {
                viewFinderFocus.x = it.x - (viewFinderFocus.width / 2)
                viewFinderFocus.y = it.y + (viewFinderFocus.height / 2)
            } ?: run {
                viewFinderFocus.x = (view.width - viewFinderFocus.width) / 2f
                viewFinderFocus.y = (view.height - viewFinderFocus.height) / 2f
            }
        }

        // Observe zoom state
        cameraController.zoomState.observe(this) {
            zoomLevel.valueFrom = it.minZoomRatio
            zoomLevel.valueTo = it.maxZoomRatio
            zoomLevel.value = it.zoomRatio
            zoomLevel.visibility = View.VISIBLE

            handler.removeMessages(MSG_HIDE_ZOOM_SLIDER)
            handler.sendMessageDelayed(handler.obtainMessage(MSG_HIDE_ZOOM_SLIDER), 2000)
        }

        // Observe torch state
        cameraController.torchState.observe(this) {
            updateTorchModeIcon()
        }

        // Set grid mode from last state
        setGridMode(sharedPreferences.getLastGridMode())

        // Update icons from last state
        updateCameraModeButtons()
        toggleRecordingChipVisibility()
        updatePhotoEffectIcon()
        updateGridIcon()
        updateFlashModeIcon()
    }

    /**
     * Change the current camera mode and restarts the stream
     */
    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    @androidx.camera.core.ExperimentalZeroShutterLag
    @androidx.camera.view.video.ExperimentalVideo
    private fun changeCameraMode(cameraMode: CameraMode) {
        if (!canRestartCamera())
            return

        if (cameraMode == this.cameraMode)
            return

        sharedPreferences.setLastCameraMode(cameraMode)
        bindCameraUseCases()
    }

    /**
     * Cycle between cameras
     */
    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    @androidx.camera.core.ExperimentalZeroShutterLag
    @androidx.camera.view.video.ExperimentalVideo
    private fun flipCamera() {
        if (!canRestartCamera())
            return

        sharedPreferences.setLastCameraFacing(
            when (cameraController.physicalCamera()?.getCameraFacing()) {
                // We can definitely do it better
                CameraFacing.FRONT -> CameraFacing.BACK
                CameraFacing.BACK -> CameraFacing.FRONT
                else -> CameraFacing.BACK
            }
        )

        bindCameraUseCases()
    }

    /**
     * Update the camera mode buttons reflecting the current mode
     */
    private fun updateCameraModeButtons() {
        photoModeButton.isEnabled = cameraMode != CameraMode.PHOTO
        videoModeButton.isEnabled = cameraMode != CameraMode.VIDEO
    }

    /**
     * Update the grid button icon based on the value set in grid view
     */
    private fun updateGridIcon() {
        gridButton.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                when (gridView.visibility) {
                    View.VISIBLE -> R.drawable.ic_grid_on
                    View.INVISIBLE -> R.drawable.ic_grid_off
                    else -> R.drawable.ic_grid_off
                }
            )
        )
    }

    /**
     * Set the specified grid mode, also updating the icon
     */
    private fun setGridMode(value: GridMode) {
        gridView.visibility = when (value) {
            GridMode.OFF -> View.INVISIBLE
            GridMode.ON_3 -> View.VISIBLE
        }
        updateGridIcon()

        sharedPreferences.setLastGridMode(value)
    }

    /**
     * Toggle grid
     */
    private fun toggleGrid() {
        setGridMode(
            when (gridView.visibility) {
                View.VISIBLE -> GridMode.OFF
                View.INVISIBLE -> GridMode.ON_3
                else -> GridMode.ON_3
            }
        )
    }

    /**
     * Update the torch mode button icon based on the value set in camera
     */
    private fun updateTorchModeIcon() {
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
        cameraController.enableTorch(
            when (cameraController.torchState.value) {
                TorchState.OFF -> true
                TorchState.ON -> false
                else -> false
            }
        )
    }

    /**
     * Update the flash mode button icon based on the value set in imageCapture
     */
    private fun updateFlashModeIcon() {
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

        sharedPreferences.setPhotoFlashMode(flashMode)
    }

    /**
     * Cycle flash mode between auto, on and off
     */
    private fun cycleFlashMode() {
        setFlashMode(
            if (cameraController.imageCaptureFlashMode >= ImageCapture.FLASH_MODE_OFF) ImageCapture.FLASH_MODE_AUTO
            else cameraController.imageCaptureFlashMode + 1
        )
    }

    @androidx.camera.view.video.ExperimentalVideo
    private fun toggleRecordingChipVisibility() {
        recordChip.visibility = if (cameraController.isRecording) View.VISIBLE else
            View.GONE
    }

    @androidx.camera.view.video.ExperimentalVideo
    private fun startRecordingTimer() {
        recordingTime = 0
        toggleRecordingChipVisibility()

        recordingTimer = Timer("${hashCode()}", false)
        recordingTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    recordingTime++
                }
            }
        }, 1000, 1000)
    }

    @androidx.camera.view.video.ExperimentalVideo
    private fun stopRecordingTimer() {
        recordingTimer.cancel()
        runOnUiThread {
            toggleRecordingChipVisibility()
        }
    }

    /**
     * Set a photo effect and restart the camera if required
     */
    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    @androidx.camera.core.ExperimentalZeroShutterLag
    @androidx.camera.view.video.ExperimentalVideo
    private fun setExtensionMode(extensionMode: Int) {
        if (!canRestartCamera())
            return

        if (extensionMode == this.extensionMode)
            return

        sharedPreferences.setPhotoEffect(extensionMode)

        bindCameraUseCases()
    }

    /**
     * Update the photo effect icon based on the current value of extensionMode
     */
    private fun updatePhotoEffectIcon() {
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
    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    @androidx.camera.core.ExperimentalZeroShutterLag
    @androidx.camera.view.video.ExperimentalVideo
    private fun cyclePhotoEffects() {
        if (!canRestartCamera())
            return

        if (supportedExtensionModes.size - 1 == extensionModeIndex) {
            extensionModeIndex = 0
        } else {
            extensionModeIndex++
        }

        setExtensionMode(
            supportedExtensionModes[extensionModeIndex]
        )
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    @androidx.camera.view.video.ExperimentalVideo
    private fun openSettings() {
        if (!canRestartCamera())
            return

        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun hideSystemBars() {
        val windowInsetsController = getInsetsController(window, window.decorView)
        // Configure the behavior of the hidden system bars
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Hide both the status bar and the navigation bar
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        private const val LOG_TAG = "Selfie"

        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()

        private const val MSG_HIDE_ZOOM_SLIDER = 0
        private const val MSG_HIDE_FOCUS_RING = 1
    }
}