rootProject.name = "vastufirst"

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

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

// Pure logic modules — zero Android, iOS-ready (Product PRD §3.1, Impl PRD §3.1).
include(":shared")
include(":rules")
include(":engine")
include(":data")
// UI + platform.
include(":designsystem")
include(":app")
