package org.lineageos.aperture

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.MediaStoreOutputOptions
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
import org.lineageos.aperture.utils.SharedPreferencesUtils
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var extensionsManager: ExtensionsManager

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var camera: Camera
    private lateinit var cameraFacing: CameraFacing

    private lateinit var cameraMode: CameraMode
    private var extensionMode: Int = ExtensionMode.NONE

    private var imageCapture: ImageCapture? = null
    private var isTakingPhoto: Boolean = false

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    @androidx.camera.core.ExperimentalZeroShutterLag
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.settingsButton.setOnClickListener { openSettings() }

        // Request camera permissions
        if (allPermissionsGranted()) {
            initCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        viewBinding.effectButton.setOnClickListener { cyclePhotoEffects() }
        viewBinding.flashButton.setOnClickListener { cycleFlashMode() }

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

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, STORAGE_DESTINATION)
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

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

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, STORAGE_DESTINATION)
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
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
    @androidx.camera.core.ExperimentalZeroShutterLag
    private fun bindCameraUseCases() {
        // Unbind previous use cases
        cameraProvider.unbindAll()

        imageCapture = null
        videoCapture = null

        // Get shared preferences
        val sharedPreferences = getSharedPreferences()

        // Select front/back camera
        cameraFacing = SharedPreferencesUtils.getLastCameraFacing(sharedPreferences)
        var cameraSelector = when (cameraFacing) {
            CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
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
                .setTargetRotation(viewBinding.root.display.rotation)
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

        // Update icons from last state
        updateCameraModeButtons()
        updatePhotoEffectIcon()
        updateFlashModeIcon()

        try {
            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, when (cameraMode) {
                    CameraMode.PHOTO -> imageCapture
                    CameraMode.VIDEO -> videoCapture
                })
        } catch(exc: Exception) {
            Log.e(LOG_TAG, "Use case binding failed", exc)
        }
    }

    /**
     * Change the current camera mode and restarts the stream
     */
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
    @androidx.camera.core.ExperimentalZeroShutterLag
    private fun flipCamera() {
        if (!canRestartCamera())
            return

        SharedPreferencesUtils.setLastCameraFacing(getSharedPreferences(), when(cameraFacing) {
            // We can definitely do it better
            CameraFacing.FRONT -> CameraFacing.BACK
            CameraFacing.BACK -> CameraFacing.FRONT
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
     * Update the flash mode button icon based on the value set in imageCapture
     */
    private fun updateFlashModeIcon() {
        val imageCapture = this.imageCapture ?: return

        viewBinding.flashButton.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                when (imageCapture.flashMode) {
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

    /**
     * Set a photo effect and restart the camera
     */
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
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    companion object {
        private const val LOG_TAG = "Aperture"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()

        private const val STORAGE_DESTINATION = "DCIM/Aperture"
    }
}