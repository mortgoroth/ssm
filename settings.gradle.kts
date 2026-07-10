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
        // Добавляем сервер JitPack
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

rootProject.name = "FreeSSM"
include(":app")
