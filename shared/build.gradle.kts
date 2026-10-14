// shared — DTOs, result types, enums (Zone, Verdict, …), the plan reader's mapper, the grid editor's
// maths, Control Room updates. Pure Kotlin, ZERO Android — no android.* can leak in.
//
// ⭐ MULTIPLATFORM since the iPhone plan's stage 1 (14 Oct 2026) — see rules/build.gradle.kts for the
// layout. src/main/kotlin is common code; JVM-only pieces (the plan reader's internet call) live in
// src/jvmMain, their iPhone twins in src/iosMain.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

// The reader's recipe and the recorded plan readings, compiled in for the iPhone (buildSrc).
val bundleShared = tasks.register<BundleTextTask>("bundleSharedForIos") {
    sourceDir.set(layout.projectDirectory.dir("src/main/resources"))
    packageName.set("com.vastufirst.shared.scan")
    mapName.set("bundledSharedFiles")
    outputDir.set(layout.buildDirectory.dir("generated/bundled/iosMain"))
}
// The test fixtures' text files (the signed Control Room reply, the public key), for iPhone tests.
val bundleSharedTest = tasks.register<BundleTextTask>("bundleSharedTestForIos") {
    sourceDir.set(layout.projectDirectory.dir("src/test/resources"))
    packageName.set("com.vastufirst.shared")
    mapName.set("bundledSharedTestFiles")
    outputDir.set(layout.buildDirectory.dir("generated/bundled/iosTest"))
}

kotlin {
    jvmToolchain(17)
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            kotlin.srcDir("src/test/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        jvmMain {
            resources.srcDir("src/main/resources")
        }
        jvmTest {
            resources.srcDir("src/test/resources")
        }
        iosMain {
            kotlin.srcDir(bundleShared.map { it.outputDir })
        }
        iosTest {
            kotlin.srcDir(bundleSharedTest.map { it.outputDir })
        }
    }
}
