/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.HybridBinarizer

class QrImageAnalyzer(private val activity: Activity) : ImageAnalysis.Analyzer {
    private val dialog by lazy {
        AlertDialog.Builder(activity)
            .setTitle(R.string.qr_title)
            .create()
    }
    private val reader by lazy { MultiFormatReader() }

    private val clipboardManager by lazy { activity.getSystemService(ClipboardManager::class.java) }
    private val keyguardManager by lazy { activity.getSystemService(KeyguardManager::class.java) }

    override fun analyze(image: ImageProxy) {
        val binaryBitmap = BinaryBitmap(HybridBinarizer(image.planarYUVLuminanceSource))

        runCatching {
            showQrDialog(reader.decodeWithState(binaryBitmap).text)
        }

        image.close()
    }

    private fun showQrDialog(message: String) {
        activity.runOnUiThread {
            if (dialog.isShowing) {
                return@runOnUiThread
            }

            // Linkify message
            SpannableString(message).let {
                Linkify.addLinks(it, Linkify.ALL)
                dialog.setMessage(it)
            }

            // Set buttons
            dialog.setButton(
                DialogInterface.BUTTON_NEUTRAL,
                activity.getString(R.string.qr_copy)
            ) { _, _ ->
                clipboardManager.setPrimaryClip(ClipData.newPlainText("", message))
            }

            dialog.setButton(
                DialogInterface.BUTTON_POSITIVE,
                activity.getString(android.R.string.ok),
            ) { _, _ ->
                // Do nothing.
            }

            // Show dialog
            dialog.show()

            // Make links clickable if keyguard is unlocked
            dialog.findViewById<TextView?>(android.R.id.message)?.movementMethod =
                if (!keyguardManager.isKeyguardLocked) LinkMovementMethod.getInstance()
                else null
        }
    }
}
