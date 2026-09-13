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
// The same engine compiled to JavaScript for the admin panel. Compiles the OTHER modules' source
// files; owns almost none of its own, so there is no second scorer to drift. Never depended on by
// :app — it exists only to produce a browser bundle.
include(":enginejs")
// UI + platform.
include(":designsystem")
include(":app")
