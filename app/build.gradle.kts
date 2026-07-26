// app — Android entry point. Depends on all modules. Android-only dependencies
// (SQLDelight Android driver, compass sensor, share intent, later Razorpay) live HERE and
// nowhere else (Impl PRD §2). The pure modules below stay Android-free.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Screenshot/render harness (UI-POLISH.md §6): registers record/verify/compareRoborazzi*
    // tasks on the JVM unit-test task. Runs headless — no emulator — on the cloud runner.
    alias(libs.plugins.roborazzi)
}

kotlin {
    androidTarget()

    sourceSets {
        androidMain.dependencies {
            implementation(project(":designsystem"))
            implementation(project(":shared"))
            implementation(project(":rules"))
            implementation(project(":engine"))
            implementation(project(":data"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)          // un-themed primitives only (ripple, text-field internals)

            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.koin.core)
            implementation(libs.koin.android)
            implementation(libs.koin.compose)          // koinInject in the launch decider
            implementation(libs.koin.androidx.compose)

            implementation(libs.sqldelight.android.driver)
        }

        // JVM render harness — screenshot + measurement + accessibility, no emulator (UI-POLISH §6).
        // Lives in the android unit-test source set so it runs as a plain `testDebugUnitTest`.
        androidUnitTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.junit.rule)
            implementation(libs.roborazzi.accessibility.check)
            // Version-matched Compose UI-test rule (createComposeRule / captureRoboImage host).
            // compose.uiTest is flagged experimental by the JB Compose plugin; opt in explicitly.
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
    }
}

android {
    namespace = "com.vastufirst.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.vastufirst.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 22
        versionName = "0.3.10"  // Plot keys report a refusal (buzz) instead of failing silently; door spoken as "north"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Required by Robolectric so the render harness can resolve app resources, and so
    // src/androidUnitTest/resources/robolectric.properties (sdk = 35) is honoured (UI-POLISH §6.1).
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// The render test writes screenshots and the L1 measurement manifests as side effects Gradle does
// not track. If Gradle serves the test task from up-to-date/cache, those files are never produced
// and the L1 gate fails with "MISSING manifest dir". Force the test to run every time — a ~1 min
// cost that guarantees fresh goldens + manifests on every CI run.
tasks.withType<Test>().configureEach {
    outputs.upToDateWhen { false }
}

dependencies {
    // Merges the ComponentActivity manifest entry into the debug variant so runComposeUiTest (the
    // L1 semantics walk) can launch under Robolectric. Must be debugImplementation — the unit test
    // uses the debug variant's merged manifest.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Goldens live OUTSIDE build/ so CI can commit them back on first record (the Now-in-Android
// bootstrap — UI-POLISH §6.3; build/ is git-ignored, so goldens kept there could never persist).
//
// ⚠ The golden LOCATION is decided by the ABSOLUTE path each test passes to captureRoboImage (see
// RenderHarness.goldenPath), NOT by any Gradle setting. Roborazzi's `roborazzi { outputDir }`
// extension does NOT redirect the golden path — it resolves a relative path against the test JVM's
// working dir (the module root). We verified this in CI: an unqualified "editor/x.png" landed at
// app/editor/x.png. So there is deliberately no roborazzi{} block here; the path is owned in Kotlin.
