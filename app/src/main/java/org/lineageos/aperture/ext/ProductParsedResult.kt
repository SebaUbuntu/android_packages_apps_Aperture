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
import com.google.zxing.client.result.ProductParsedResult
import org.lineageos.aperture.R

fun ProductParsedResult.createIntent() = Intent(
    Intent.ACTION_VIEW, Uri.parse("https://www.barcodelookup.com/${productID}")
)

fun ProductParsedResult.createTextClassification(context: Context) = TextClassification.Builder()
    .setText(productID)
    .setEntityType(TextClassifier.TYPE_OTHER, 1.0f)
    .apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            addAction(
                RemoteAction::class.build(
                    context,
                    R.drawable.ic_shopping_cart,
                    R.string.qr_product_title,
                    R.string.qr_product_content_description,
                    createIntent()
                )
            )
        }
    }
    .build()
