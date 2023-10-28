/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.ext

import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.textclassifier.TextClassification
import android.view.textclassifier.TextClassifier
import com.google.zxing.client.result.TelParsedResult
import org.lineageos.aperture.R

fun TelParsedResult.createIntent() = Intent(Intent.ACTION_SENDTO, Uri.parse(telURI))

fun TelParsedResult.createTextClassification(context: Context) = TextClassification.Builder()
    .setText(number)
    .setEntityType(TextClassifier.TYPE_PHONE, 1.0f)
    .apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            addAction(
                RemoteAction::class.build(
                    context,
                    R.drawable.ic_phone,
                    R.string.qr_tel_title,
                    R.string.qr_tel_content_description,
                    createIntent()
                )
            )
        }
    }
    .build()
