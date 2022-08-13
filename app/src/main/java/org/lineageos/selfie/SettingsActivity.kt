/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.selfie

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import org.lineageos.selfie.utils.CameraSoundsUtils

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private val customStorageLocation by lazy { findPreference<SwitchPreference>("custom_storage_location") }
        private val saveLocation by lazy { findPreference<SwitchPreference>("save_location") }
        private val shutterSound by lazy { findPreference<SwitchPreference>("shutter_sound") }

        private val requestLocationPermissions = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            if (it.any { permission -> !permission.value }) {
                saveLocation?.isChecked = false
                Toast.makeText(
                    requireContext(), getString(R.string.save_location_toast), Toast.LENGTH_SHORT
                ).show()
            }
        }

        private val openDocumentTree = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) {
            if (it != null) {
                context?.contentResolver?.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            customStorageLocation?.isChecked = it != null
            customStorageLocation?.sharedPreferences?.customStorageLocation = it
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            customStorageLocation?.apply {
                isChecked = sharedPreferences?.customStorageLocation != null
                onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { _, newValue ->
                        if (newValue as Boolean) {
                            openDocumentTree.launch(null)
                        } else {
                            sharedPreferences?.customStorageLocation = null
                        }
                        true
                    }
            }
            saveLocation?.let {
                // Reset location back to off if permissions aren't granted
                it.isChecked = it.isChecked && allLocationPermissionsGranted()
                it.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { _, newValue ->
                        if (newValue as Boolean) {
                            requestLocationPermissions.launch(
                                MainActivity.REQUIRED_PERMISSIONS_LOCATION
                            )
                        }
                        true
                    }
            }
            shutterSound?.isEnabled = !CameraSoundsUtils.mustPlaySounds
        }

        private fun allLocationPermissionsGranted() =
            MainActivity.REQUIRED_PERMISSIONS_LOCATION.all {
                ContextCompat.checkSelfPermission(
                    requireContext(), it
                ) == PackageManager.PERMISSION_GRANTED
            }
    }
}