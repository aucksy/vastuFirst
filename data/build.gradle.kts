// data — repositories, persistence (SQLDelight in Phase 2), Supabase client (Phase 2+).
// Pure Kotlin/JVM, ZERO Android. The Android SQLDelight driver lands in :app, not here.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":shared"))
    implementation(project(":rules"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.turbine)
}
