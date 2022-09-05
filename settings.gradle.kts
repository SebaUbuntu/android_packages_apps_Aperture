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
        google()
        mavenCentral()
        maven("https://raw.githubusercontent.com/luk1337/camerax_selfie/6b2510d11a28eb5e32a0e17fea3f91bc37ae4316/.m2")
    }
}
rootProject.name = "Selfie"
include(":app")
