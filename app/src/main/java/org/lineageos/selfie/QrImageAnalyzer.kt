/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.selfie

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer

class QrImageAnalyzer(private val activity: Activity) : ImageAnalysis.Analyzer {
    private val bottomSheetDialog by lazy {
        BottomSheetDialog(activity).apply {
            setContentView(R.layout.qr_bottom_sheet_dialog)
        }
    }
    private val bottomSheetDialogTitle by lazy {
        bottomSheetDialog.findViewById<TextView>(R.id.title)!!
    }
    private val bottomSheetDialogData by lazy {
        bottomSheetDialog.findViewById<TextView>(R.id.data)!!
    }
    private val bottomSheetDialogIcon by lazy {
        bottomSheetDialog.findViewById<ImageView>(R.id.icon)!!
    }

    private val reader by lazy { MultiFormatReader() }

    private val clipboardManager by lazy { activity.getSystemService(ClipboardManager::class.java) }
    private val keyguardManager by lazy { activity.getSystemService(KeyguardManager::class.java) }

    override fun analyze(image: ImageProxy) {
        val source = image.planarYUVLuminanceSource

        val result = runCatching {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
        }.getOrNull() ?: runCatching {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert())))
        }.getOrNull()

        result?.let {
            showQrDialog(it)
        }

        reader.reset()
        image.close()
    }

    private fun showQrDialog(result: Result) {
        activity.runOnUiThread {
            if (bottomSheetDialog.isShowing) {
                return@runOnUiThread
            }

            // Classify message
            val span = SpannableString(result.text)
            bottomSheetDialogData.text = span
            Thread {
                val textClassification = bottomSheetDialogData.textClassifier.classifyText(
                    span, 0, span.length, LocaleList.getDefault()
                )

                activity.runOnUiThread {
                    bottomSheetDialogData.text = textClassification.text
                    if (textClassification.actions.isNotEmpty()) {
                        with(textClassification.actions[0]) {
                            bottomSheetDialogData.setOnClickListener { this.actionIntent.send() }
                            bottomSheetDialogTitle.text = this.title
                            this.icon.loadDrawableAsync(activity, {
                                bottomSheetDialogIcon.setImageDrawable(it)
                            }, Handler(Looper.getMainLooper()))
                        }
                    } else {
                        bottomSheetDialogData.setOnClickListener {}
                        bottomSheetDialogTitle.text =
                            activity.resources.getText(R.string.qr_type_text)
                        bottomSheetDialogIcon.setImageDrawable(
                            AppCompatResources.getDrawable(activity, R.drawable.ic_qr_type_text))
                    }
                }
            }.start()

            // Make links clickable if not on locked keyguard
            bottomSheetDialogData.movementMethod =
                if (!keyguardManager.isKeyguardLocked) LinkMovementMethod.getInstance()
                else null

            // Set buttons
            bottomSheetDialog.findViewById<ImageButton>(R.id.copy)?.setOnClickListener {
                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText(
                        "", result.text
                    )
                )
            }

            bottomSheetDialog.findViewById<ImageButton>(R.id.share)?.setOnClickListener {
                activity.startActivity(
                    Intent.createChooser(
                        Intent().apply {
                            action = Intent.ACTION_SEND
                            type = ClipDescription.MIMETYPE_TEXT_PLAIN
                            putExtra(
                                Intent.EXTRA_TEXT, result.text
                            )
                        },
                        activity.getString(androidx.transition.R.string.abc_shareactionprovider_share_with)
                    )
                )
            }

            // Show dialog
            bottomSheetDialog.show()
        }
    }
}
