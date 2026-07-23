// data — repositories, persistence (SQLDelight), Supabase client (Phase 4+).
// Pure Kotlin/JVM, ZERO Android. The Android SQLDelight driver lands in :app, not here:
// this module declares the schema + repositories and consumes an injected SqlDriver, so the
// iOS port (Phase 5) supplies a NativeSqliteDriver with no change to this code.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(17)
}

sqldelight {
    databases {
        create("VastuDatabase") {
            packageName.set("com.vastufirst.data.db")
        }
    }
}

dependencies {
    api(project(":shared"))
    implementation(project(":rules"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.coroutines)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.turbine)
}
