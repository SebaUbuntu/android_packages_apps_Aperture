pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://raw.githubusercontent.com/luk1337/camerax_selfie/4532191e32b1d2c59753f5985878e245875d5df7/.m2")
        google()
        mavenCentral()
    }
}
rootProject.name = "Aperture"
include(":app")
