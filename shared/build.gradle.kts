// shared — DTOs, result types, enums (Zone, Verdict, …). Pure Kotlin/JVM, ZERO Android.
// Product PRD §3.1: kotlin("jvm") only, no Android plugin. The iOS move later is a
// build-file change (jvm → multiplatform), not a rewrite — no android.* can leak in.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    testImplementation(libs.kotlin.test)
    // `PlanReader.read` is `suspend` (a language feature, no dependency); runBlocking in the scan
    // tests is the only thing that needs the coroutines runtime, so it is test-only.
    testImplementation(libs.kotlinx.coroutines.core)
}
