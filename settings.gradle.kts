pluginManagement {
    includeBuild("build-logic")
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.mozilla.org/maven2/")
            content {
                includeGroupByRegex("org\\.mozilla\\..*")
            }
        }
    }
}

rootProject.name = "AuraBrowser"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Application & Navigation
include(":app")
include(":navigation")

// Core Modules
include(":core:common")
include(":core:designsystem")
include(":core:database")
include(":core:datastore")
include(":core:media-common")
include(":core:network")
include(":core:permissions")

// Feature Modules
include(":feature:home")
include(":feature:browser")
include(":feature:tabs")
include(":feature:bookmarks")
include(":feature:history")
include(":feature:downloads")
include(":feature:youtube")
include(":feature:music")
include(":feature:video")
include(":feature:pip")
include(":feature:extensions")
include(":feature:filemanager")
include(":feature:privacy")
include(":feature:settings")
