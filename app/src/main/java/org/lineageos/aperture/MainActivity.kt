package org.lineageos.aperture

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.preference.PreferenceManager
import org.lineageos.aperture.databinding.ActivityMainBinding
import org.lineageos.aperture.utils.CameraFacing
import org.lineageos.aperture.utils.CameraMode
import org.lineageos.aperture.utils.GridMode
import org.lineageos.aperture.utils.PhysicalCamera
import org.lineageos.aperture.utils.SharedPreferencesUtils
import org.lineageos.aperture.utils.StorageUtils
import org.lineageos.aperture.utils.TimeUtils
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var extensionsManager: ExtensionsManager

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var camera: Camera
    private lateinit var physicalCamera: PhysicalCamera

    private lateinit var cameraMode: CameraMode
    private var extensionMode: Int = ExtensionMode.NONE

    private var imageCapture: ImageCapture? = null
    private var isTakingPhoto: Boolean = false

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private val orientationEventListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) {
                    return
                }

                val rotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                imageCapture?.targetRotation = rotation
                videoCapture?.targetRotation = rotation
            }
        }
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    @androidx.camera.core.ExperimentalZeroShutterLag
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

    override fun onStart() {
        super.onStart()
        orientationEventListener.enable()
    }

    override fun onPause() {
        super.onPause()
        orientationEventListener.disable()
    }

    override fun onResume() {
        super.onResume()
        orientationEventListener.enable()
    }

    override fun onStop() {
        super.onStop()
        orientationEventListener.disable()
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
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Bail out if a photo is already being taken
        if (isTakingPhoto)
            return

        isTakingPhoto = true
        viewBinding.shutterButton.isEnabled = false

        // Create output options object which contains file + metadata
        val outputOptions = StorageUtils.getPhotoMediaStoreOutputOptions(contentResolver)

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
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

    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        viewBinding.shutterButton.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

        // Create output options object which contains file + metadata
        val outputOptions = StorageUtils.getVideoMediaStoreOutputOptions(contentResolver)

        recording = videoCapture.output
            .prepareRecording(this, outputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@MainActivity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        viewBinding.shutterButton.isEnabled = true
                    }
                    is VideoRecordEvent.Status -> {
                        viewBinding.recordChip.text = TimeUtils.convertNanosToString(
                            recordEvent.recordingStats.recordedDurationNanos)
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(LOG_TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(LOG_TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        viewBinding.recordChip.text = getString(R.string.record_chip_default_text)
                        viewBinding.shutterButton.isEnabled = true
                    }
                }
            }
    }

    /**
     * Check if we can reinitialize the camera use cases
     */
    private fun canRestartCamera(): Boolean {
        if (cameraMode == CameraMode.PHOTO) {
            // If imageCapture is null, we definitely need a restart
            if (imageCapture == null)
                return true

            // Check if we're taking a photo
            if (isTakingPhoto)
                return false
        } else if (cameraMode == CameraMode.VIDEO) {
            // If videoCapture is null, we definitely need a restart
            if (videoCapture == null)
                return true

            // Check for a recording in progress
            val curRecording = recording
            if (curRecording != null)
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
    private fun bindCameraUseCases() {
        // Unbind previous use cases
        cameraProvider.unbindAll()

        imageCapture = null
        isTakingPhoto = false

        videoCapture = null
        recording = null

        // Get shared preferences
        val sharedPreferences = getSharedPreferences()

        // Select front/back camera
        var cameraSelector = when (SharedPreferencesUtils.getLastCameraFacing(sharedPreferences)) {
            CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Get the user selected effect
        extensionMode = SharedPreferencesUtils.getPhotoEffect(sharedPreferences)

        // Preview
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            }

        // Initialize the use case we want
        cameraMode = SharedPreferencesUtils.getLastCameraMode(sharedPreferences)
        if (cameraMode == CameraMode.PHOTO) {
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(SharedPreferencesUtils.getPhotoCaptureMode(sharedPreferences))
                .setFlashMode(SharedPreferencesUtils.getPhotoFlashMode(sharedPreferences))
                .build()

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
        } else if (cameraMode == CameraMode.VIDEO) {
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        SharedPreferencesUtils.getVideoQuality(sharedPreferences),
                        FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                    )
                )
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
        }

        // Bind use cases to camera
        camera = cameraProvider.bindToLifecycle(
            this, cameraSelector, preview, when (cameraMode) {
                CameraMode.PHOTO -> imageCapture
                CameraMode.VIDEO -> videoCapture
            })
        physicalCamera = PhysicalCamera(camera.cameraInfo)

        // Set grid mode from last state
        setGridMode(SharedPreferencesUtils.getLastGridMode(sharedPreferences))

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
    private fun changeCameraMode(cameraMode: CameraMode) {
        if (!canRestartCamera())
            return

        if (cameraMode == this.cameraMode)
            return

        SharedPreferencesUtils.setLastCameraMode(getSharedPreferences(), cameraMode)
        bindCameraUseCases()
    }

    /**
     * Cycle between cameras
     */
    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    @androidx.camera.core.ExperimentalZeroShutterLag
    private fun flipCamera() {
        if (!canRestartCamera())
            return

        SharedPreferencesUtils.setLastCameraFacing(
            getSharedPreferences(), when(physicalCamera.getCameraFacing()) {
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

        SharedPreferencesUtils.setLastGridMode(getSharedPreferences(), value)
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
                when (camera.cameraInfo.torchState.value) {
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
        camera.cameraControl.enableTorch(value).get()
        updateTorchModeIcon()
    }

    /**
     * Toggle torch mode
     */
    private fun toggleTorchMode() {
        setTorchMode(when (camera.cameraInfo.torchState.value) {
            TorchState.OFF -> true
            TorchState.ON -> false
            else -> false
        })
    }

    /**
     * Update the flash mode button icon based on the value set in imageCapture
     */
    private fun updateFlashModeIcon() {
        val flashMode = imageCapture?.flashMode ?: ImageCapture.FLASH_MODE_OFF

        viewBinding.flashButton.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                when (flashMode) {
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
        val imageCapture = this.imageCapture ?: return

        imageCapture.flashMode = flashMode
        updateFlashModeIcon()

        SharedPreferencesUtils.setPhotoFlashMode(getSharedPreferences(), flashMode)
    }

    /**
     * Cycle flash mode between auto, on and off
     */
    private fun cycleFlashMode() {
        val imageCapture = this.imageCapture ?: return

        setFlashMode(
            if (imageCapture.flashMode >= ImageCapture.FLASH_MODE_OFF) ImageCapture.FLASH_MODE_AUTO
            else imageCapture.flashMode + 1)
    }

    private fun toggleRecordingChipVisibility() {
        viewBinding.recordChip.visibility = if (cameraMode == CameraMode.VIDEO) View.VISIBLE else
            View.GONE
    }

    /**
     * Set a photo effect and restart the camera
     */
    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    @androidx.camera.core.ExperimentalZeroShutterLag
    private fun setExtensionMode(extensionMode: Int) {
        if (!canRestartCamera())
            return

        SharedPreferencesUtils.setPhotoEffect(getSharedPreferences(), extensionMode)

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

    private fun getSharedPreferences(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(this)
    }

    private fun openSettings() {
        if (!canRestartCamera())
            return

        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    companion object {
        private const val LOG_TAG = "Aperture"

        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()
    }
}