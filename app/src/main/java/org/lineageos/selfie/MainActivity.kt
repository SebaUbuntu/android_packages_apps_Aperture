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
import android.util.Log
import android.view.View
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
import androidx.camera.view.video.OnVideoSavedCallback
import androidx.camera.view.video.OutputFileResults
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.lineageos.selfie.databinding.ActivityMainBinding
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
    private lateinit var viewBinding: ActivityMainBinding

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var extensionsManager: ExtensionsManager

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var cameraController: LifecycleCameraController

    private lateinit var cameraMode: CameraMode
    private var extensionMode: Int = ExtensionMode.NONE

    private var isTakingPhoto: Boolean = false

    private var recordingTime = 0L
        set(value) {
            field = value
            viewBinding.recordChip.text = TimeUtils.convertSecondsToString(recordingTime)
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
                    viewBinding.zoomLevel.visibility = View.GONE
                }
            }
        }
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    @androidx.camera.core.ExperimentalZeroShutterLag
    @androidx.camera.view.video.ExperimentalVideo
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            initCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        viewBinding.effectButton.setOnClickListener { cyclePhotoEffects() }
        viewBinding.gridButton.setOnClickListener { toggleGrid() }
        viewBinding.torchButton.setOnClickListener { toggleTorchMode() }
        viewBinding.flashButton.setOnClickListener { cycleFlashMode() }
        viewBinding.settingsButton.setOnClickListener { openSettings() }

        viewBinding.zoomLevel.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                cameraController.setZoomRatio(value)
            }
        }

        viewBinding.photoModeButton.setOnClickListener { changeCameraMode(CameraMode.PHOTO) }
        viewBinding.videoModeButton.setOnClickListener { changeCameraMode(CameraMode.VIDEO) }

        viewBinding.flipCameraButton.setOnClickListener { flipCamera() }

        viewBinding.shutterButton.setOnClickListener {
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
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                initCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun takePhoto() {
        // Bail out if a photo is already being taken
        if (isTakingPhoto)
            return

        isTakingPhoto = true
        viewBinding.shutterButton.isEnabled = false

        // Create output options object which contains file + metadata
        val outputOptions = StorageUtils.getPhotoMediaStoreOutputOptions(contentResolver)

        // Set camera usecases
        cameraController.setEnabledUseCases(CameraController.IMAGE_CAPTURE)

        // Set up image capture listener, which is triggered after photo has
        // been taken
        cameraController.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(LOG_TAG, "Photo capture failed: ${exc.message}", exc)
                    isTakingPhoto = false
                    viewBinding.shutterButton.isEnabled = true
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults) {
                    viewBinding.root.foreground = ColorDrawable(Color.WHITE)
                    val colorFade: ObjectAnimator = ObjectAnimator.ofInt(
                        viewBinding.root.foreground,
                        "alpha",
                        255,
                        0,
                    )
                    colorFade.duration = 500
                    colorFade.start()
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(LOG_TAG, msg)
                    isTakingPhoto = false
                    viewBinding.shutterButton.isEnabled = true
                }
            }
        )
    }

    @androidx.camera.view.video.ExperimentalVideo
    private fun captureVideo() {
        if (cameraController.isRecording) {
            // Stop the current recording session.
            cameraController.stopRecording()
            cameraController.setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            return
        }

        // Create output options object which contains file + metadata
        val outputOptions = StorageUtils.getVideoMediaStoreOutputOptions(contentResolver)

        // Set camera usecases
        cameraController.setEnabledUseCases(CameraController.VIDEO_CAPTURE)

        // Start recording
        cameraController.startRecording(
                outputOptions,
                cameraExecutor,
                object : OnVideoSavedCallback {
                    override fun onVideoSaved(outputFileResults: OutputFileResults) {
                        stopRecordingTimer()
                        val msg = "Video capture succeeded: ${outputFileResults.savedUri}"
                        runOnUiThread {
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        }
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
        cameraProvider.unbindAll()

        isTakingPhoto = false

        // Select front/back camera
        var cameraSelector = when (sharedPreferences.getLastCameraFacing()) {
            CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Get the user selected effect
        extensionMode = sharedPreferences.getPhotoEffect()

        // Initialize the use case we want
        cameraMode = sharedPreferences.getLastCameraMode()
        if (cameraMode == CameraMode.PHOTO) {
            // Select the extension
            if (extensionsManager.isExtensionAvailable(cameraSelector, extensionMode)) {
                cameraSelector = extensionsManager.getExtensionEnabledCameraSelector(
                    cameraSelector, extensionMode)
            } else {
                val msg = "Extension $extensionMode is not supported"
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                    .show()
                Log.e(LOG_TAG, msg)
            }
        }

        // Bind use cases to camera
        cameraController = LifecycleCameraController(this)
        cameraController.cameraSelector = cameraSelector
        cameraController.imageCaptureMode = sharedPreferences.getPhotoCaptureMode()
        cameraController.bindToLifecycle(this)
        viewBinding.viewFinder.controller = cameraController

        // Observe zoom state
        cameraController.zoomState.observe(this) {
            viewBinding.zoomLevel.valueFrom = it.minZoomRatio
            viewBinding.zoomLevel.valueTo = it.maxZoomRatio
            viewBinding.zoomLevel.value = it.zoomRatio
            viewBinding.zoomLevel.visibility = View.VISIBLE

            handler.removeMessages(MSG_HIDE_ZOOM_SLIDER)
            handler.sendMessageDelayed(handler.obtainMessage(MSG_HIDE_ZOOM_SLIDER), 2000)
        }

        // Set grid mode from last state
        setGridMode(sharedPreferences.getLastGridMode())

        // Update icons from last state
        updateCameraModeButtons()
        toggleRecordingChipVisibility()
        updatePhotoEffectIcon()
        updateGridIcon()
        updateTorchModeIcon()
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
            when(cameraController.cameraFacing()) {
                // We can definitely do it better
                CameraFacing.FRONT -> CameraFacing.BACK
                CameraFacing.BACK -> CameraFacing.FRONT
                else -> CameraFacing.BACK
            })

        bindCameraUseCases()
    }

    /**
     * Update the camera mode buttons reflecting the current mode
     */
    private fun updateCameraModeButtons() {
        viewBinding.photoModeButton.isEnabled = cameraMode != CameraMode.PHOTO
        viewBinding.videoModeButton.isEnabled = cameraMode != CameraMode.VIDEO
    }

    /**
     * Update the grid button icon based on the value set in grid view
     */
    private fun updateGridIcon() {
        viewBinding.gridButton.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                when (viewBinding.gridView.visibility) {
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
        viewBinding.gridView.visibility = when (value) {
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
        setGridMode(when (viewBinding.gridView.visibility) {
            View.VISIBLE -> GridMode.OFF
            View.INVISIBLE -> GridMode.ON_3
            else -> GridMode.ON_3
        })
    }

    /**
     * Update the torch mode button icon based on the value set in camera
     */
    private fun updateTorchModeIcon() {
        viewBinding.torchButton.setImageDrawable(
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
     * Set the specified torch mode, also updating the icon
     */
    private fun setTorchMode(value: Boolean) {
        cameraController.enableTorch(value)
        updateTorchModeIcon()
    }

    /**
     * Toggle torch mode
     */
    private fun toggleTorchMode() {
        setTorchMode(when (cameraController.torchState.value) {
            TorchState.OFF -> true
            TorchState.ON -> false
            else -> false
        })
    }

    /**
     * Update the flash mode button icon based on the value set in imageCapture
     */
    private fun updateFlashModeIcon() {
        viewBinding.flashButton.setImageDrawable(
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
            else cameraController.imageCaptureFlashMode + 1)
    }

    @androidx.camera.view.video.ExperimentalVideo
    private fun toggleRecordingChipVisibility() {
        viewBinding.recordChip.visibility = if (cameraController.isRecording) View.VISIBLE else
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
     * Set a photo effect and restart the camera
     */
    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    @androidx.camera.core.ExperimentalZeroShutterLag
    @androidx.camera.view.video.ExperimentalVideo
    private fun setExtensionMode(extensionMode: Int) {
        if (!canRestartCamera())
            return

        sharedPreferences.setPhotoEffect(extensionMode)

        bindCameraUseCases()
    }

    /**
     * Update the photo effect icon based on the current value of extensionMode
     */
    private fun updatePhotoEffectIcon() {
        viewBinding.effectButton.setImageDrawable(
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

        setExtensionMode(
            if (extensionMode >= ExtensionMode.AUTO) ExtensionMode.NONE
            else extensionMode + 1)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    @androidx.camera.view.video.ExperimentalVideo
    private fun openSettings() {
        if (!canRestartCamera())
            return

        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    companion object {
        private const val LOG_TAG = "Selfie"

        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()

        private const val MSG_HIDE_ZOOM_SLIDER = 0
    }
}