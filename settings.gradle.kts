pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android (el reconeixement de text de les plaques) només és a JitPack
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "Wakker"
include(":app")
