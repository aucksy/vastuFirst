// data — repositories, persistence (SQLDelight), Supabase client (Phase 4+).
// Pure Kotlin, ZERO Android. The Android SQLDelight driver lands in :app, not here:
// this module declares the schema + repositories and consumes an injected SqlDriver, so the
// iOS port supplies a NativeSqliteDriver with no change to this code.
//
// ⭐ MULTIPLATFORM since the iPhone plan's stage 1 (14 Oct 2026) — see rules/build.gradle.kts for the
// layout. The schema stays in src/main/sqldelight. PersistenceTest needs the JVM SQLite driver, so it
// lives in src/jvmTest; the iPhone's database is proven in stage 3, when the app supplies its driver.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
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
                implementation(project(":rules"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
            }
        }
        commonTest {
            kotlin.srcDir("src/test/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.turbine)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
    }
}

sqldelight {
    databases {
        create("VastuDatabase") {
            packageName.set("com.vastufirst.data.db")
            // The schema did not move with the multiplatform switch (src/commonMain/sqldelight is the
            // plugin's default): one folder, so the migration files keep their history.
            srcDirs.setFrom("src/main/sqldelight")
        }
    }
}
