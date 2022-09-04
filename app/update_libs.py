#!/usr/bin/env python3
#
# Copyright (C) 2022 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

from pathlib import Path
from requests import get
from shutil import rmtree
from typing import List
from xml.etree import ElementTree
from zipfile import ZipFile

# Make sure we're in the right directory
cwd = Path.cwd()
if cwd != Path(__file__).parent.resolve():
    raise Exception("Please run this script from the script folder")

from libs import (
    AOSP_ALIASES,
    AOSP_IGNORE,
    APP_NAME,
    DEFAULT_MIN_SDK_VERSION,
    DEFAULT_SDK_VERSION,
    DEPS,
    REPOSITORIES,
    UPDATE_GRADLE,
)

AAR_SOONG_MODULE_TEMPLATE = """\
android_library_import {{
    name: "{module.aosp_target_name}-nodeps",
    aars: ["{module.relative_library_path}/{module.aar_file_name}"],
    sdk_version: "{module.sdk_version}",
    min_sdk_version: "{module.min_sdk_version}",
    apex_available: [
        "//apex_available:platform",
        "//apex_available:anyapex",
    ],
    static_libs: [{deps}
    ],
}}

android_library {{
    name: "{module.aosp_target_name}",
    sdk_version: "{module.sdk_version}",
    min_sdk_version: "{module.min_sdk_version}",
    apex_available: [
        "//apex_available:platform",
        "//apex_available:anyapex",
    ],
    manifest: "{module.relative_library_path}/AndroidManifest.xml",
    static_libs: [
        "{module.aosp_target_name}-nodeps",{deps}
    ],
    java_version: "1.7",
}}
"""

JAR_SOONG_MODULE_TEMPLATE = """\
java_import {{
    name: "{module.aosp_target_name}-nodeps",
    jars: ["{module.relative_library_path}/{module.jar_file_name}"],
    sdk_version: "{module.sdk_version}",
    min_sdk_version: "{module.min_sdk_version}",
    apex_available: [
        "//apex_available:platform",
        "//apex_available:anyapex",
    ],
}}

java_library_static {{
    name: "{module.aosp_target_name}",
    sdk_version: "{module.sdk_version}",
    min_sdk_version: "{module.min_sdk_version}",
    apex_available: [
        "//apex_available:platform",
        "//apex_available:anyapex",
    ],
    static_libs: [
        "{module.aosp_target_name}-nodeps",{deps}
    ],
    java_version: "1.7",
}}
"""

class Library:
    def __init__(self, libs_path: Path, project_name: str, module_name: str, version: str, repo_name: str):
        self.libs_path = libs_path
        self.project_name = project_name
        self.module_name = module_name
        self.version = version
        self.repo_name = repo_name

        # [project, module]
        self.dependencies: List[List[str]] = []
        self.sdk_version = DEFAULT_SDK_VERSION
        self.min_sdk_version = DEFAULT_MIN_SDK_VERSION

        self.gradle_name = f"{self.project_name}:{self.module_name}:{self.version}"
        self.aosp_target_name = f"{self.project_name}_{self.module_name}"
        if self.aosp_target_name in AOSP_ALIASES:
            self.aosp_target_name = AOSP_ALIASES[self.aosp_target_name]
        elif self.repo_name != "sdk":
            self.aosp_target_name = f"{APP_NAME}_{self.aosp_target_name}"

        self.repo_url = REPOSITORIES[repo_name]

        self.module_url = f"{self.repo_url}/{self.project_name.replace('.', '/')}/{self.module_name}/{self.version}"

        self.relative_library_path = Path(*self.project_name.split('.')) / self.module_name / self.version
        self.library_path = self.libs_path / self.relative_library_path

        self.aar_file_name = f"{self.module_name}-{self.version}.aar"
        self.android_manifest_name = "AndroidManifest.xml"
        self.jar_file_name = f"{self.module_name}-{self.version}.jar"
        self.pom_file_name = f"{self.module_name}-{self.version}.pom"

        self.aar_file_path = self.library_path / self.aar_file_name
        self.android_manifest_path = self.library_path / self.android_manifest_name
        self.jar_file_path = self.library_path / self.jar_file_name
        self.pom_file_path = self.library_path / self.pom_file_name

    def download(self):
        if self.repo_name == "sdk":
            return

        # Create module dir
        module_dir = self.libs_path / self.library_path
        module_dir.mkdir(parents=True)

        # Download files
        aar_response = get(f"{self.module_url}/{self.aar_file_name}")
        if aar_response.status_code != 200:
            jar_response = get(f"{self.module_url}/{self.jar_file_name}")
            assert(jar_response.status_code == 200)
            self.jar_file_path.write_bytes(jar_response.content)
        else:
            self.aar_file_path.write_bytes(aar_response.content)

        pom_response = get(f"{self.module_url}/{self.pom_file_name}")
        assert(pom_response.status_code == 200)
        self.pom_file_path.write_bytes(pom_response.content)

        # Parse deps
        root = ElementTree.parse(self.pom_file_path).getroot()
        xmlns = "http://maven.apache.org/POM/4.0.0"
        for dependencies in root.findall(f".//{{{xmlns}}}dependencies"):
            for dependency in dependencies:
                group_id = dependency.find(f'.//{{{xmlns}}}groupId').text
                artifact_id = dependency.find(f'.//{{{xmlns}}}artifactId').text
                scope = dependency.find(f'.//{{{xmlns}}}scope')
                if scope is not None and scope.text != 'compile':
                    continue

                self.dependencies.append([group_id, artifact_id])

        # Extract AndroidManifest.xml if AAR
        if self.aar_file_path.exists():
            with ZipFile(self.aar_file_path) as aar:
                aar.extract("AndroidManifest.xml", self.library_path)

            # Parse minimum SDK version
            xmlns_android = "http://schemas.android.com/apk/res/android"
            root = ElementTree.parse(self.android_manifest_path).getroot()
            uses_sdk = root.find('uses-sdk')
            sdk_version = uses_sdk.attrib.get(f'{{{xmlns_android}}}targetSdkVersion', DEFAULT_SDK_VERSION)
            min_sdk_version = uses_sdk.attrib.get(f'{{{xmlns_android}}}minSdkVersion', DEFAULT_MIN_SDK_VERSION)
            self.sdk_version = int(sdk_version)
            self.min_sdk_version = int(min_sdk_version)
        else:
            self.sdk_version = DEFAULT_SDK_VERSION
            self.min_sdk_version = DEFAULT_MIN_SDK_VERSION

        # Delete unneeded files
        if self.pom_file_path.exists():
            self.pom_file_path.unlink()

    def get_library_path(self) -> Path:
        if self.aar_file_path.is_file():
            return self.aar_file_path
        elif self.jar_file_path.is_file():
            return self.jar_file_path

        raise Exception("No library file found")

    def get_soong_module(self, known_libraries: List["Library"] = None) -> str:
        if self.repo_name == "sdk":
            raise Exception("SDK modules don't need Soong module")

        dependencies = []
        for dependency in self.dependencies:
            # Convert dependency to prebuilts/sdk library names
            library_name = f"{dependency[0]}_{dependency[1]}"

            # Ignore library if it's in ignore list
            if library_name in AOSP_IGNORE:
                continue

            # Use alias if there
            library_name = AOSP_ALIASES.get(library_name, library_name)

            # Use proper library name if it's a known library
            for known_module in known_libraries or []:
                if known_module.project_name == dependency[0] and known_module.module_name == dependency[1]:
                    library_name = known_module.aosp_target_name
                    break

            dependencies.append(library_name)

        # Remove duplicates
        dependencies = list(dict.fromkeys(dependencies))

        dependencies_list_str = ("\n" + "\n".join([f'        "{dep}",' for dep in dependencies])) if dependencies else ""

        if self.aar_file_path.is_file():
            return AAR_SOONG_MODULE_TEMPLATE.format(module=self, deps=dependencies_list_str)
        elif self.jar_file_path.is_file():
            return JAR_SOONG_MODULE_TEMPLATE.format(module=self, deps=dependencies_list_str)

def main():
    # Clean up old libs dir
    libs_dir = cwd / "libs"
    if libs_dir.exists():
        rmtree(str(libs_dir))
    libs_dir.mkdir()

    libraries: List[Library] = []
    for dep in DEPS:
        print(f"Processing {dep}")
        library = Library(libs_dir, *dep)
        library.download()

        libraries.append(library)

    # Write Android.bp
    android_bp_path = cwd / "Android.bp"
    android_bp = android_bp_path.read_text().splitlines()

    soong_deps = []
    for library in libraries:
        soong_deps.append(AOSP_ALIASES.get(module.aosp_target_name, module.aosp_target_name) for module in libraries)

    new_android_bp = []
    in_deps_list = False
    for line in android_bp:
        if in_deps_list:
            if line == "    ],":
                in_deps_list = False
                new_android_bp.append(line)
            continue
        elif line == "    static_libs: [":
            in_deps_list = True
            new_android_bp.append(line)
            new_android_bp.append("        // DO NOT EDIT THIS LIST MANUALLY")
            new_android_bp.extend([f'        "{AOSP_ALIASES.get(library.aosp_target_name, library.aosp_target_name)}",' for library in libraries])
        else:
            new_android_bp.append(line)

    android_bp_path.write_text("\n".join(new_android_bp) + "\n")

    # Write libs/Android.bp
    android_bp_path = libs_dir / "Android.bp"
    android_bp_path.write_text("// DO NOT EDIT THIS FILE MANUALLY\n" + "\n".join([
        library.get_soong_module(libraries)
        for library in libraries
        if library.repo_name != "sdk"
    ]))

    # Write build.gradle/build.gradle.kts if needed
    if UPDATE_GRADLE:
        build_gradle_path = cwd / "build.gradle"
        build_gradle_kts_path = cwd / "build.gradle.kts"
        kts = build_gradle_kts_path.is_file()
        build_gradle = (build_gradle_kts_path if kts else build_gradle_path).read_text().splitlines()

        quote_char = '"' if kts else "'"

        gradle_deps = []
        for library in libraries:
            if library.repo_name == "sdk":
                gradle_deps.append(f"{quote_char}{library.gradle_name}{quote_char}")
            else:
                gradle_deps.append(f"files({quote_char}{library.get_library_path().relative_to(cwd)}{quote_char})")

        new_build_gradle = []
        in_deps_list = False
        for line in build_gradle:
            if in_deps_list:
                if line == "}":
                    in_deps_list = False
                    new_build_gradle.append(line)
                continue
            elif line == "dependencies {":
                in_deps_list = True
                new_build_gradle.append(line)
                new_build_gradle.append("    // DO NOT EDIT THIS LIST MANUALLY")
                new_build_gradle.extend([
                    (f"    implementation({gradle_dep})" if kts else f"    implementation {gradle_dep}")
                    for gradle_dep in gradle_deps
                ])
            else:
                new_build_gradle.append(line)

        (build_gradle_kts_path if kts else build_gradle_path).write_text("\n".join(new_build_gradle) + "\n")

if __name__ == "__main__":
    main()
