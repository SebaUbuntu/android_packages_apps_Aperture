package org.lineageos.selfie

import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import org.lineageos.selfie.utils.CameraFacing

internal fun CameraController.cameraFacing(): CameraFacing {
    return if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
        CameraFacing.FRONT
    } else {
        CameraFacing.BACK
    }
}
