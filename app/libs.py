#
# Copyright (C) 2022 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

from typing import Dict, List

APP_NAME = "Selfie"

# Decide whether to also update Gradle build config
UPDATE_GRADLE = False

REPOSITORIES: Dict[str, str] = {
    "sdk": "",

    "gmaven": "https://dl.google.com/android/maven2",
    "maven_central": "https://repo1.maven.org/maven2",
    "luk_camerax": "https://raw.githubusercontent.com/luk1337/camerax_selfie/master/.m2"
}

CAMERAX_VERSION = "1.2.0-beta02"

# [project, module, version, repo]
# sdk: not a local library, will use AOSP prebuilts/sdk one
# Note than for non-SDK libs you must manually specify its dependencies
DEPS: List[List[str]] = [
    # Core
    ["androidx.core", "core-ktx", "1.8.0", "sdk"],
    ["androidx.appcompat", "appcompat", "1.5.0", "sdk"],

    # Base
    ["androidx.constraintlayout", "constraintlayout", "2.1.4", "sdk"],
    ["androidx.lifecycle", "lifecycle-viewmodel", "2.5.1", "sdk"],
    ["androidx.lifecycle", "lifecycle-viewmodel-ktx", "2.5.1", "sdk"],
    ["androidx.preference", "preference", "1.2.0", "sdk"],
    ["com.google.android.material", "material", "1.7.0-beta01", "gmaven"],
    ["org.jetbrains.kotlinx", "kotlinx-coroutines-android", "1.6.4", "sdk"],
    ["org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.6.4", "sdk"],

    # CameraX
    ["androidx.camera", "camera-camera2", CAMERAX_VERSION, "luk_camerax"],
    ["androidx.camera", "camera-core", CAMERAX_VERSION, "luk_camerax"],
    ["androidx.camera", "camera-extensions", CAMERAX_VERSION, "luk_camerax"],
    ["androidx.camera", "camera-lifecycle", CAMERAX_VERSION, "luk_camerax"],
    ["androidx.camera", "camera-video", CAMERAX_VERSION, "luk_camerax"],
    ["androidx.camera", "camera-view", CAMERAX_VERSION, "luk_camerax"],

    # CameraX dependencies
    ["androidx.exifinterface", "exifinterface", "1.3.3", "sdk"],

    # Coil
    ["io.coil-kt", "coil", "2.2.0", "maven_central"],
    ["io.coil-kt", "coil-base", "2.2.0", "maven_central"],
    ["io.coil-kt", "coil-video", "2.2.0", "maven_central"],

    # Coil dependencies
    ["androidx.lifecycle", "lifecycle-common-java8", "2.5.1", "sdk"],
    ["com.squareup.okhttp3", "okhttp", "4.10.0", "maven_central"],
    ["com.squareup.okio", "okio", "3.2.0", "maven_central"],
    ["com.squareup.okio", "okio-jvm", "3.2.0", "maven_central"],
    ["org.jetbrains", "annotations", "13.0", "sdk"],

    # ZXing
    ["com.google.zxing", "core", "3.5.0", "maven_central"],
]

# Libs that are already in AOSP but not in prebuilts/sdk
AOSP_ALIASES: Dict[str, str] = {
    "androidx.constraintlayout_constraintlayout": "androidx-constraintlayout_constraintlayout",
    "com.google.auto.value_auto-value-annotations": "auto_value_annotations",
    "com.google.errorprone_error_prone_annotations": "error_prone_annotations",
    "com.google.guava_listenablefuture": "guava",
    "org.jetbrains_annotations": "kotlin-annotations",
    "org.jetbrains.kotlin_kotlin-stdlib": "kotlin-stdlib",
    "org.jetbrains.kotlin_kotlin-stdlib-jdk7": "kotlin-stdlib-jdk7",
    "org.jetbrains.kotlin_kotlin-stdlib-jdk8": "kotlin-stdlib-jdk8",
    "org.jetbrains.kotlinx_kotlinx-coroutines-android": "kotlinx-coroutines-android",
    "org.jetbrains.kotlinx_kotlinx-coroutines-core": "kotlinx-coroutines-core",
}

# Libs that should be ignored in AOSP (e.g. Kotlin stdlib)
AOSP_IGNORE: List[str] = [
    "org.jetbrains.kotlin_kotlin-stdlib-common",
]

# Default target SDK version
DEFAULT_SDK_VERSION = 31

# Default Android SDK version
DEFAULT_MIN_SDK_VERSION = 14
