// rules — versioned rule JSON dataset + loader + validation. Pure Kotlin, ZERO Android.
//
// ⭐ MULTIPLATFORM since the iPhone plan's stage 1 (14 Oct 2026): `jvm()` serves the Android app and
// the JVM tests exactly as `kotlin("jvm")` did; the two iOS targets build only on a Mac (CI's iOS
// workflow). The folders did NOT move: src/main/kotlin is the common code and src/test/kotlin the
// common tests, so every tool and script that names those paths keeps working. Only what is truly
// JVM-only lives in src/jvmMain / src/jvmTest, and its iPhone twin in src/iosMain.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

// The rule files, compiled in for the iPhone (see buildSrc/BundleTextTask). Same folder the JVM reads.
val bundleRules = tasks.register<BundleTextTask>("bundleRulesForIos") {
    sourceDir.set(layout.projectDirectory.dir("src/main/resources"))
    packageName.set("com.vastufirst.rules")
    mapName.set("bundledRuleFiles")
    outputDir.set(layout.buildDirectory.dir("generated/bundled/iosMain"))
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
                api(project(":shared"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            kotlin.srcDir("src/test/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        jvmMain {
            resources.srcDir("src/main/resources")
        }
        iosMain {
            kotlin.srcDir(bundleRules.map { it.outputDir })
        }
    }
}
