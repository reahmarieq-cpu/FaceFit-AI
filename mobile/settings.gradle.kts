pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "facefit"
include(":app")

// Redirect build outputs outside OneDrive to prevent Windows/OneDrive file locking on temporary zip-cache files
val localAppData = System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir")
val buildCacheDir = File(localAppData, "FaceFitAI-build")

gradle.beforeProject {
    layout.buildDirectory.set(File(buildCacheDir, if (path == ":") "root" else name))
}
