package org.lineageos.selfie.utils

import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager

object ExtensionModeExt {
    private val extensionModes =
        IntProgression.fromClosedRange(ExtensionMode.NONE, ExtensionMode.AUTO, 1)

    fun getSupportedModes(
        extensionsManager: ExtensionsManager,
        cameraSelector: CameraSelector
    ) = extensionModes.filter {
        extensionsManager.isExtensionAvailable(cameraSelector, it)
    }
}

