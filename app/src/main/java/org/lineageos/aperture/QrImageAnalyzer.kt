/*
 * SPDX-FileCopyrightText: 2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.LinearLayoutCompat.LayoutParams
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.lineageos.aperture.ui.QrHighlightView

class QrImageAnalyzer(
    private val activity: Activity, private val qrHighlightView: QrHighlightView
) : ImageAnalysis.Analyzer {
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
    private val bottomSheetDialogCopy by lazy {
        bottomSheetDialog.findViewById<ImageButton>(R.id.copy)!!
    }
    private val bottomSheetDialogShare by lazy {
        bottomSheetDialog.findViewById<ImageButton>(R.id.share)!!
    }
    private val bottomSheetDialogActionsLayout by lazy {
        bottomSheetDialog.findViewById<LinearLayout>(R.id.actionsLayout)!!
    }

    private val barcodeScanningClient = BarcodeScanning.getClient()

    private val clipboardManager by lazy { activity.getSystemService(ClipboardManager::class.java) }
    private val keyguardManager by lazy { activity.getSystemService(KeyguardManager::class.java) }

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val image = imageProxy.image ?: return imageProxy.close()

        val inputImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
        barcodeScanningClient.process(
            inputImage
        ).addOnSuccessListener { barcodes ->
            barcodes.firstOrNull { it.rawValue != null }?.let {
                showQrDialog(it)
                qrHighlightView.points = it.cornerPoints?.let { cornerPoints ->
                    qrHighlightView.scalePoints(
                        cornerPoints, image, imageProxy.imageInfo.rotationDegrees
                    )
                }
            } ?: run {
                qrHighlightView.points = null
            }
        }.addOnCompleteListener {
            imageProxy.close()
        }
    }

    private fun showQrDialog(result: Barcode) {
        activity.runOnUiThread {
            if (bottomSheetDialog.isShowing) {
                return@runOnUiThread
            }

            // Classify message
            val span = SpannableString(result.rawValue)
            bottomSheetDialogData.text = span
            Thread {
                val textClassification = bottomSheetDialogData.textClassifier.classifyText(
                    span, 0, span.length, LocaleList.getDefault()
                )

                activity.runOnUiThread {
                    bottomSheetDialogData.text = textClassification.text
                    bottomSheetDialogActionsLayout.removeAllViews()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        textClassification.actions.isNotEmpty()
                    ) {
                        with(textClassification.actions[0]) {
                            bottomSheetDialogData.setOnClickListener { this.actionIntent.send() }
                            bottomSheetDialogTitle.text = this.title
                            this.icon.loadDrawableAsync(activity, {
                                bottomSheetDialogIcon.setImageDrawable(it)
                            }, Handler(Looper.getMainLooper()))
                        }
                        for (action in textClassification.actions.drop(1)) {
                            bottomSheetDialogActionsLayout.addView(inflateButton().apply {
                                setOnClickListener { action.actionIntent.send() }
                                text = action.title
                                action.icon.loadDrawableAsync(activity, {
                                    it.setBounds(0, 0, 15.px, 15.px)
                                    setCompoundDrawables(
                                        it, null, null, null
                                    )
                                }, Handler(Looper.getMainLooper()))
                            })
                        }
                    } else {
                        bottomSheetDialogData.setOnClickListener {}
                        bottomSheetDialogTitle.text = activity.resources.getText(R.string.qr_text)
                        bottomSheetDialogIcon.setImageDrawable(
                            AppCompatResources.getDrawable(activity, R.drawable.ic_qr_type_text)
                        )
                    }
                }
            }.start()

            // Make links clickable if not on locked keyguard
            bottomSheetDialogData.movementMethod =
                if (!keyguardManager.isKeyguardLocked) LinkMovementMethod.getInstance()
                else null

            // Set buttons
            bottomSheetDialogCopy.setOnClickListener {
                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText(
                        "", result.rawValue
                    )
                )
            }

            bottomSheetDialogShare.setOnClickListener {
                activity.startActivity(
                    Intent.createChooser(
                        Intent().apply {
                            action = Intent.ACTION_SEND
                            type = ClipDescription.MIMETYPE_TEXT_PLAIN
                            putExtra(
                                Intent.EXTRA_TEXT, result.rawValue
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

    @SuppressLint("InflateParams")
    private fun inflateButton(): MaterialButton {
        val button = activity.layoutInflater.inflate(
            R.layout.qr_bottom_sheet_action_button, null
        ) as MaterialButton
        return button.apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
    }
}
