/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.selfie.utils

import android.content.res.Resources
import android.content.SharedPreferences
import android.media.MediaActionSound
import org.lineageos.selfie.shutterSound

class CameraSoundsUtils(private val sharedPreferences: SharedPreferences) {
    private val mediaActionSound = MediaActionSound().apply {
        // Preload all sounds to reduce latency
        load(MediaActionSound.SHUTTER_CLICK)
        load(MediaActionSound.START_VIDEO_RECORDING)
        load(MediaActionSound.STOP_VIDEO_RECORDING)
    }

    fun playShutterClick() {
        if (sharedPreferences.shutterSound || mustPlaySounds) {
            mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
        }
    }

    fun playStartVideoRecording(): Boolean {
        if (sharedPreferences.shutterSound || mustPlaySounds) {
            mediaActionSound.play(MediaActionSound.START_VIDEO_RECORDING)
            return true
        }
        return false
    }

    fun playStopVideoRecording() {
        if (sharedPreferences.shutterSound || mustPlaySounds) {
            mediaActionSound.play(MediaActionSound.STOP_VIDEO_RECORDING)
        }
    }

    companion object {
        val mustPlaySounds: Boolean
            get() {
                val resources = Resources.getSystem()
                val id = resources.getIdentifier("config_camera_sound_forced", "bool", "android")
                return id > 0 && resources.getBoolean(id)
            }
    }
}
