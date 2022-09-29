/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture

import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.OutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.video.AudioConfig
import androidx.core.util.Consumer
import org.lineageos.aperture.utils.PhysicalCamera
import java.util.concurrent.Executor

internal val CameraController.physicalCamera: PhysicalCamera?
    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    get() = cameraInfo?.let { PhysicalCamera(it) }

@androidx.camera.view.video.ExperimentalVideo
internal fun CameraController.startRecording (
    outputOptions: OutputOptions, audioConfig: AudioConfig, executor: Executor,
    listener: Consumer<VideoRecordEvent>
): Recording {
    if (outputOptions is FileDescriptorOutputOptions) {
        return startRecording(outputOptions, audioConfig, executor, listener)
    }
    if (outputOptions is FileOutputOptions) {
        return startRecording(outputOptions, audioConfig, executor, listener)
    }
    if (outputOptions is MediaStoreOutputOptions) {
        return startRecording(outputOptions, audioConfig, executor, listener)
    }

    throw Exception("Invalid outputOptions type")
}
