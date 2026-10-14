// engine — zone maths, 81-pada grid, 32-pada door, rule evaluation, scoring, defects.
// Pure Kotlin, ZERO Android. This is the product; screens are how people reach it.
// ⭐ MULTIPLATFORM since the iPhone plan's stage 1 (14 Oct 2026) — see rules/build.gradle.kts for the
// layout. The engine needs no platform code at all; only three tests read the JVM classpath or use
// reflection, and they live in src/jvmTest. Sample01Test (the home that must score 31) is common and
// runs on the iPhone simulator too.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
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
                api(project(":rules"))
                implementation(libs.kotlinx.datetime)
            }
        }
        commonTest {
            kotlin.srcDir("src/test/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        jvmTest {
            resources.srcDir("src/test/resources")
            dependencies {
                implementation(libs.kotlin.reflect)   // FacingNeutralityTest: static field-absence guard (§0.4)
            }
        }
    }
}
