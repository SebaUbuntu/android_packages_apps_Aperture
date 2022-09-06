import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.XmlParser

plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    compileSdk = 33

    defaultConfig {
        applicationId = "org.lineageos.selfie.dev"
        minSdk = 31
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        named("release") {
            // Enables code shrinking, obfuscation, and optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true

            // Includes the default ProGuard rules files.
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.appcompat:appcompat:1.5.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    implementation("androidx.preference:preference:1.2.0")
    implementation("com.google.android.material:material:1.8.0-alpha01")

    // CameraX core library using the camera2 implementation
    val cameraxVersion = "1.2.0-beta02"
    // The following line is optional, as the core library is included indirectly by camera-camera2
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    // If you want to additionally use the CameraX Lifecycle library
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    // If you want to additionally use the CameraX VideoCapture library
    implementation("androidx.camera:camera-video:${cameraxVersion}")
    // If you want to additionally use the CameraX View class
    implementation("androidx.camera:camera-view:${cameraxVersion}")
    // If you want to additionally use the CameraX Extensions library
    implementation("androidx.camera:camera-extensions:${cameraxVersion}")

    // ZXing
    implementation("com.google.zxing:core:3.5.0")

    // Coil
    implementation("io.coil-kt:coil:2.2.0")
    implementation("io.coil-kt:coil-video:2.2.0")
}

tasks.register("generateBp") {
    val project = project(":app")
    val configuration = project.configurations["debugRuntimeClasspath"]

    val libsBase = File("${project.projectDir.absolutePath}/libs")
    libsBase.deleteRecursively()

    val moduleString = { it: ModuleVersionIdentifier -> "${it.group}:${it.name}:${it.version}" }
    val modulePath =
        { it: ModuleVersionIdentifier -> "${it.group.replace(".", "/")}/${it.name}/${it.version}" }

    val spaces = { it: Int ->
        var ret = ""
        for (i in it downTo 1) {
            ret += ' '
        }
        ret
    }

    val moduleName = { it: Any ->
        when (it) {
            is ModuleVersionIdentifier -> {
                "${rootProject.name}_${it.group}_${it.name}"
            }
            is String -> {
                if (it.contains(":")) {
                    val (group, artifactId) = it.split(":")
                    "${rootProject.name}_${group}_${artifactId}"
                } else {
                    "${rootProject.name}_${it}"
                }
            }
            else -> {
                throw Exception("Invalid `it` type")
            }
        }
    }

    val moduleNameAosp = { it: String ->
        when (it) {
            "androidx.constraintlayout:constraintlayout" -> "androidx-constraintlayout_constraintlayout"
            "com.google.auto.value:auto-value-annotations" -> "auto_value_annotations"
            "com.google.guava:listenablefuture" -> "guava"
            "org.jetbrains.kotlin:kotlin-stdlib" -> "kotlin-stdlib"
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8" -> "kotlin-stdlib-jdk8"
            "org.jetbrains.kotlinx:kotlinx-coroutines-android" -> "kotlinx-coroutines-android"
            else -> it.replace(":", "_")
        }
    }

    val isAvailableInAosp = { group: String, artifactId: String ->
        when {
            group.startsWith("androidx") -> {
                // We provide our own androidx.camera & lifecycle-common
                !group.startsWith("androidx.camera") && artifactId != "lifecycle-common"
            }
            group.startsWith("org.jetbrains") -> {
                // kotlin-android-extensions-runtime & kotlin-parcelize-runtime aren't in AOSP
                !artifactId.startsWith("kotlin-android-extensions-runtime") &&
                        !artifactId.startsWith("kotlin-parcelize-runtime")
            }
            group == "com.google.auto.value" -> true
            group == "com.google.guava" -> true
            group == "junit" -> true
            else -> false
        }
    }

    // Update app/Android.bp
    File("${project.projectDir.absolutePath}/Android.bp").let { file ->
        // Read dependencies
        val dependencies = "${spaces(8)}// DO NOT EDIT THIS SECTION MANUALLY\n".plus(
            configuration.allDependencies.joinToString("\n") {
                if (isAvailableInAosp(it.group!!, it.name)) {
                    "${spaces(8)}\"${moduleNameAosp("${it.group}:${it.name}")}\","
                } else {
                    "${spaces(8)}\"${moduleName("${it.group}:${it.name}")}\","
                }
            }
        )

        // Replace existing dependencies with newly generated ones
        file.writeText(
            file.readText().replace(
                "static_libs: \\[.*?\\]".toRegex(RegexOption.DOT_MATCHES_ALL),
                "static_libs: [%s]".format("\n$dependencies\n${spaces(4)}")
            )
        )
    }

    // Update app/libs
    configuration.resolvedConfiguration.resolvedArtifacts.sortedBy {
        moduleString(it.moduleVersion.id)
    }.distinctBy {
        moduleString(it.moduleVersion.id)
    }.forEach {
        val id = it.moduleVersion.id

        // Skip modules that are available in AOSP
        if (isAvailableInAosp(id.group, it.name)) {
            return@forEach
        }

        // Get file path
        val dirPath = "${libsBase}/${modulePath(id)}"
        val filePath = "${dirPath}/${it.file.name}"

        // Copy artifact to app/libs
        it.file.copyTo(File(filePath))

        // Parse dependencies
        val dependencies =
            it.file.parentFile.parentFile.walk().first { file -> file.extension == "pom" }
                .let { file ->
                    val ret = mutableListOf<String>()

                    val pom = XmlParser().parse(file)
                    val dependencies = (pom["dependencies"] as NodeList).firstOrNull() as Node?

                    dependencies?.children()?.forEach { node ->
                        val dependency = node as Node
                        ret.add(
                            "${
                                (dependency.get("groupId") as NodeList).text()
                            }:${
                                (dependency.get("artifactId") as NodeList).text()
                            }"
                        )
                    }

                    ret
                }

        var targetSdkVersion = 31
        var minSdkVersion = 14

        // Extract AndroidManifest.xml for AARs
        if (it.file.extension == "aar") {
            copy {
                from(zipTree(filePath).matching { include("/AndroidManifest.xml") }.singleFile)
                into(dirPath)
            }

            val androidManifest = XmlParser().parse(File("${dirPath}/AndroidManifest.xml"))

            val usesSdk = (androidManifest["uses-sdk"] as NodeList).first() as Node
            targetSdkVersion = (usesSdk.get("@targetSdkVersion") as Int?) ?: targetSdkVersion
            minSdkVersion = (usesSdk.get("@minSdkVersion") as Int?) ?: minSdkVersion
        }

        // Write Android.bp
        File("$libsBase/Android.bp").let { file ->
            // Add autogenerated header if file is empty
            if (file.length() == 0L) {
                file.writeText("// DO NOT EDIT THIS FILE MANUALLY")
            }

            val formatDeps = { addNoDeps: Boolean ->
                val deps = dependencies.filter { dep ->
                    when {
                        configuration.resolvedConfiguration.resolvedArtifacts.firstOrNull { artifact ->
                            dep == "${artifact.moduleVersion.id.group}:${artifact.moduleVersion.id.name}"
                        } == null -> {
                            val moduleName = if (addNoDeps) {
                                moduleName(id)
                            } else {
                                "${moduleName(id)}-nodeps"
                            }
                            println("$moduleName: Skipping $dep because it's not in resolvedArtifacts")
                            false
                        }
                        dep == "org.jetbrains.kotlin:kotlin-stdlib-common" -> false
                        else -> true
                    }

                }.toMutableList()

                if (addNoDeps) {
                    // Add -nodeps dependency for android_library/java_library_static
                    deps.add(0, "${id.group}_${id.name}-nodeps")
                }

                var ret = ""

                if (deps.isNotEmpty()) {
                    deps.forEach { dep ->
                        ret += if (dep.contains(":")) {
                            val (group, artifactId) = dep.split(":")
                            if (isAvailableInAosp(group, artifactId)) {
                                "\n${spaces(8)}\"${moduleNameAosp(dep)}\","
                            } else {
                                "\n${spaces(8)}\"${moduleName(dep)}\","
                            }
                        } else {
                            "\n${spaces(8)}\"${moduleName(dep)}\","
                        }
                    }
                    ret += "\n${spaces(4)}"
                }

                ret
            }

            when (it.extension) {
                "aar" -> {
                    file.appendText(
                        """

                            android_library_import {
                                name: "${moduleName(id)}-nodeps",
                                aars: ["${modulePath(id)}/${it.file.name}"],
                                sdk_version: "$targetSdkVersion",
                                min_sdk_version: "$minSdkVersion",
                                apex_available: [
                                    "//apex_available:platform",
                                    "//apex_available:anyapex",
                                ],
                                static_libs: [%s],
                            }

                            android_library {
                                name: "${moduleName(id)}",
                                sdk_version: "$targetSdkVersion",
                                min_sdk_version: "$minSdkVersion",
                                apex_available: [
                                    "//apex_available:platform",
                                    "//apex_available:anyapex",
                                ],
                                manifest: "${modulePath(id)}/AndroidManifest.xml",
                                static_libs: [%s],
                                java_version: "1.7",
                            }

                        """.trimIndent().format(formatDeps(false), formatDeps(true))
                    )
                }
                "jar" -> {
                    file.appendText(
                        """

                            java_import {
                                name: "${moduleName(id)}-nodeps",
                                jars: ["${modulePath(id)}/${it.file.name}"],
                                sdk_version: "$targetSdkVersion",
                                min_sdk_version: "$minSdkVersion",
                                apex_available: [
                                    "//apex_available:platform",
                                    "//apex_available:anyapex",
                                ],
                            }

                            java_library_static {
                                name: "${moduleName(id)}",
                                sdk_version: "$targetSdkVersion",
                                min_sdk_version: "$minSdkVersion",
                                apex_available: [
                                    "//apex_available:platform",
                                    "//apex_available:anyapex",
                                ],
                                static_libs: [%s],
                                java_version: "1.7",
                            }

                        """.trimIndent().format(formatDeps(true))
                    )
                }
                else -> throw Exception("Unknown file extension: ${it.extension}")
            }
        }
    }
}
