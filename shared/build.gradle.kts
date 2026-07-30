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
    // `PlanReader.read` is `suspend`, which is a language feature and needs no dependency. The
    // runtime is here for exactly one reason: GroqPlanReader moves its own socket off the calling
    // thread (`withContext(Dispatchers.IO)`) instead of trusting every future caller to remember to.
    // Still zero Android — coroutines-core is plain Kotlin, so `check-boundaries.sh` stays happy and
    // the iOS re-target stays a build-file change.
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.core)
}
