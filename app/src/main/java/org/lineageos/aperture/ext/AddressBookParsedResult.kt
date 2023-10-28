/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.ext

import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.ContactsContract
import android.view.textclassifier.TextClassification
import android.view.textclassifier.TextClassifier
import com.google.zxing.client.result.AddressBookParsedResult
import org.lineageos.aperture.R

fun AddressBookParsedResult.createIntent() = Intent(
    Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI
).apply {
    names.firstOrNull()?.let {
        putExtra(ContactsContract.Intents.Insert.NAME, it)
    }

    pronunciation?.let {
        putExtra(ContactsContract.Intents.Insert.PHONETIC_NAME, it)
    }

    phoneNumbers?.let { phoneNumbers ->
        val phoneTypes = phoneTypes ?: arrayOf()

        for ((i, keys) in listOf(
            listOf(
                ContactsContract.Intents.Insert.PHONE,
                ContactsContract.Intents.Insert.PHONE_TYPE,
            ),
            listOf(
                ContactsContract.Intents.Insert.SECONDARY_PHONE,
                ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE,
            ),
            listOf(
                ContactsContract.Intents.Insert.TERTIARY_PHONE,
                ContactsContract.Intents.Insert.TERTIARY_PHONE_TYPE,
            ),
        ).withIndex()) {
            phoneNumbers.getOrNull(i)?.let { phone ->
                putExtra(keys.first(), phone)
                phoneTypes.getOrNull(i)?.let {
                    putExtra(keys.last(), it)
                }
            }
        }
    }

    emails?.let { emails ->
        val emailTypes = emailTypes ?: arrayOf()

        for ((i, keys) in listOf(
            listOf(
                ContactsContract.Intents.Insert.EMAIL,
                ContactsContract.Intents.Insert.EMAIL_TYPE,
            ),
            listOf(
                ContactsContract.Intents.Insert.SECONDARY_EMAIL,
                ContactsContract.Intents.Insert.SECONDARY_EMAIL_TYPE,
            ),
            listOf(
                ContactsContract.Intents.Insert.TERTIARY_EMAIL,
                ContactsContract.Intents.Insert.TERTIARY_EMAIL_TYPE,
            ),
        ).withIndex()) {
            emails.getOrNull(i)?.let { phone ->
                putExtra(keys.first(), phone)
                emailTypes.getOrNull(i)?.let {
                    putExtra(keys.last(), it)
                }
            }
        }
    }

    instantMessenger?.let {
        putExtra(ContactsContract.Intents.Insert.IM_HANDLE, it)
    }

    note?.let {
        putExtra(ContactsContract.Intents.Insert.NOTES, it)
    }

    addresses?.let { emails ->
        val addressTypes = addressTypes ?: arrayOf()

        for ((i, keys) in listOf(
            listOf(
                ContactsContract.Intents.Insert.POSTAL,
                ContactsContract.Intents.Insert.POSTAL_TYPE,
            ),
        ).withIndex()) {
            emails.getOrNull(i)?.let { phone ->
                putExtra(keys.first(), phone)
                addressTypes.getOrNull(i)?.let {
                    putExtra(keys.last(), it)
                }
            }
        }
    }

    org?.let {
        putExtra(ContactsContract.Intents.Insert.COMPANY, it)
    }
}

fun AddressBookParsedResult.createTextClassification(
    context: Context
) = TextClassification.Builder()
    .setText(title ?: names.firstOrNull() ?: "")
    .setEntityType(TextClassifier.TYPE_OTHER, 1.0f)
    .apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            addAction(
                RemoteAction::class.build(
                    context,
                    R.drawable.ic_contact_phone,
                    R.string.qr_address_title,
                    R.string.qr_address_content_description,
                    createIntent()
                )
            )
        }
    }
    .build()
