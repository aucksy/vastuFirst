// rules — versioned rule JSON dataset + loader + validation. Pure Kotlin/JVM, ZERO Android.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":shared"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
}
