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
import com.google.zxing.client.result.SMSParsedResult
import org.lineageos.aperture.R

fun SMSParsedResult.createIntent() = Intent(Intent.ACTION_SENDTO, Uri.parse(smsuri))

fun SMSParsedResult.createTextClassification(context: Context) = TextClassification.Builder()
    .setText(numbers.first())
    .setEntityType(TextClassifier.TYPE_OTHER, 1.0f)
    .apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            addAction(
                RemoteAction::class.build(
                    context,
                    R.drawable.ic_sms,
                    R.string.qr_sms_title,
                    R.string.qr_sms_content_description,
                    createIntent()
                )
            )
        }
    }
    .build()
