// designsystem — VastuTheme, tokens, components, icons. Compose Multiplatform.
// commonMain holds all UI code as pure-common Compose, so the iOS target added in Phase 5
// is a one-line target declaration, not a rewrite. Phase 0 ships android + jvm targets only
// (no Kotlin/Native yet) to keep the cloud build fast and green.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)   // bundled OFL fonts (Marcellus / DM Sans / DM Mono)
        }
    }
}

// Bundled fonts live in commonMain/composeResources/font and generate a typed `Res` accessor.
// (Default generateResClass = auto emits the class because the module has resources + the
// components.resources dependency.)
compose.resources {
    packageOfResClass = "com.vastufirst.designsystem.generated.resources"
}

android {
    namespace = "com.vastufirst.designsystem"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
