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

            // The ₹699 checkout. Present in every build; reached only when payments are
            // switched on (see paymentsEnabled above), because the code that touches it is
            // behind an interface whose other implementation talks to no store at all.
            implementation(libs.play.billing)
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
            // ⭐ For the unfinished-home tests (v0.6.6). The rule "nothing comes back unless the user
            // asks for it" lives in a ViewModel with a debounced autosave, so proving it needs a
            // controllable Main dispatcher and virtual time — the alternative is a claim in a comment.
            implementation(libs.kotlinx.coroutines.test)
            // Version-matched Compose UI-test rule (createComposeRule / captureRoboImage host).
            // compose.uiTest is flagged experimental by the JB Compose plugin; opt in explicitly.
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
    }
}

// The plan-reading key, supplied by the build environment and never by the repository.
//
// ⭐ It is read from a GitHub Actions secret in CI and from a git-ignored `.env` locally, so it is
// never committed — and when it is ABSENT the app says so plainly instead of pretending to read a
// plan. That honesty is the whole point of threading it this far: a build that cannot read plans and
// looks identical to one that can is exactly the build that got handed over on 30 July.
//
// This is the interim arrangement (§6.2 option A) for private test builds. A key inside an APK is
// extractable, which is acceptable for a free-tier key with no money attached and NOT acceptable for
// the Play Store — the reader sits behind an interface precisely so moving to a server proxy is a
// one-file change later.
// Since 4 Aug 2026 the reader speaks to OpenRouter (see reader-config.json for why, and
// tools/scan-eval/READER-CANDIDATES.md for the measurements). ⚠ No GROQ_API_KEY fallback: the key
// check reads only OPENROUTER_API_KEY and green-skips when it is absent, so the old fallback baked
// a dead Groq key into the APK from a stale environment and nothing ever failed loud — every scan
// then died with "we couldn't read your plan". A keyless build refuses honestly instead.
val planReaderKey: String = (project.findProperty("planReaderKey") as String?)
    ?: System.getenv("OPENROUTER_API_KEY")
    ?: ""

// ⭐⭐ PAYMENTS: BUILT IN FULL, SHIPPED SWITCHED OFF.
//
// The checkout is complete and goes live by setting this to true — there is no key to paste, because
// Google Play Billing identifies the app by its own signature and package name. Nothing secret ever
// enters the APK. (The plan originally said Razorpay; Play does not permit a third-party gateway for
// digital content sold and consumed inside a Play Store app. See app/.../billing/Billing.kt.)
//
// ⚠ WITH THIS OFF, THE APP MUST SAY SO. It does — the unlock screen's own words come from
// `billingNotice()`, which reads this flag and states plainly that no payment is taken. A screen
// that LOOKS like it charges and does not is the one thing this feature must never do.
val paymentsEnabled: Boolean =
    ((project.findProperty("paymentsEnabled") as String?) ?: System.getenv("PAYMENTS_ENABLED"))
        ?.equals("true", ignoreCase = true) ?: false

// ⭐ THE PLAY STORE SIGNING KEY — supplied by the build environment, never committed.
//
// Absent (every ordinary build, and every CI run) the release variant falls back to the committed
// test key, so `assembleRelease` still runs end to end and R8 is exercised on every push. Present —
// a base64 keystore in a GitHub secret — the release is signed with the owner's real key. The two
// are told apart deliberately: see the signature check script, which fails a build whose APK is
// signed by something unexpected.
val releaseStoreFile: String? =
    (project.findProperty("releaseStoreFile") as String?) ?: System.getenv("RELEASE_STORE_FILE")
val releaseStorePassword: String? =
    (project.findProperty("releaseStorePassword") as String?) ?: System.getenv("RELEASE_STORE_PASSWORD")
val releaseKeyAlias: String? =
    (project.findProperty("releaseKeyAlias") as String?) ?: System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? =
    (project.findProperty("releaseKeyPassword") as String?) ?: System.getenv("RELEASE_KEY_PASSWORD")

android {
    namespace = "com.vastufirst.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.vastufirst.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // ⚠ These lagged the tags: v0.9.0 shipped still calling itself 0.8.1, so an installed build
        // could not be told apart from the one before it on the phone. Bump BOTH with every tag.
        versionCode = 59
        versionName = "0.17.0"

        // Escaped rather than interpolated raw: this string is pasted into generated Kotlin, so a
        // stray quote or backslash in a key would produce a file that does not compile.
        val escapedKey = planReaderKey.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "PLAN_READER_KEY", "\"$escapedKey\"")
        buildConfigField("boolean", "PAYMENTS_ENABLED", paymentsEnabled.toString())
    }

    buildFeatures {
        // Off by default in AGP 8; the generated BuildConfig is how the key reaches Kotlin.
        buildConfig = true
    }

    // ⭐⭐ A STABLE SIGNING KEY, AND IT IS NOT A SECRET.
    //
    // THE BUG THIS FIXES: with no signing config, AGP signs a debug build with the keystore at
    // ~/.android/debug.keystore — and a fresh GitHub Actions runner does not have one, so AGP
    // GENERATES A RANDOM KEY on every single build. Android refuses to install an update whose
    // signature differs from the installed app, so every new test build had to be UNINSTALLED first,
    // and uninstalling erases the app's data. Every home the owner had drawn was destroyed by every
    // release. Measured, not guessed: v0.3.15 and v0.3.16 ship certificates with different SHA-256s.
    //
    // WHY COMMITTING THIS FILE DOES NOT BREAK "no secrets in the repo": it is a self-signed test
    // certificate with the conventional debug password, holding no account, no service and no store
    // identity. Its only power is to sign a build that Android will accept as an update to another
    // build signed by the same key, which is precisely the behaviour we want. It is the same trust
    // level as the debug keystore Android Studio writes into every developer's home directory.
    //
    // ⚠ THE PLAY STORE KEY IS A DIFFERENT KEY AND MUST NEVER LIVE HERE. That one is created at store
    // setup, kept by the owner, and supplied to the build as a secret. Changing to it costs one final
    // reinstall for the two sideloaded test phones — and nothing at all for real users, who will be
    // installing from the Play Store rather than updating a sideloaded APK.
    //
    // scripts/check-apk-signature.py fails the build if a produced APK is ever signed by anything
    // other than this key, because the symptom otherwise only shows up on a phone, days later, as
    // lost data.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("signing/vastufirst-debug.keystore")
            storeType = "pkcs12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Created ONLY when the environment supplies a real key. Declaring it unconditionally with
        // null paths makes every ordinary build fail at configuration time, which would make the
        // project un-buildable for anyone without the store key — including CI.
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            // ⭐ SHRUNK AND OBFUSCATED. Smaller download, faster start, and the rule data and scoring
            // weights — which ARE the product — stop being readable by unzipping the APK. See
            // proguard-rules.pro for exactly what must survive and why; the serializers in
            // particular, because losing them means saved homes that will not open.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The owner's key when the environment has one, the committed test key otherwise — so
            // `assembleRelease` always runs and R8 is exercised on every single push, rather than
            // first meeting the shrinker on the day of the store upload.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        getByName("debug") {
            // Left unshrunk on purpose: the test builds the owner installs must carry readable stack
            // traces, and the render harness runs against this variant.
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

    // ⚠⚠ MEMORY, AND IT IS NOT A TUNING KNOB — the build died without it (16 Aug 2026).
    //
    // A full golden re-record renders every screen at eight configurations, and each capture holds a
    // full-size bitmap. Run in one JVM with the default heap, that walked the hosted runner into the
    // ground: the job did not fail a test, it "lost communication with the server" after 49 minutes
    // with the record step still marked running — the runner process itself was starved and killed.
    // Nothing in the log says "out of memory", which is what makes this expensive to diagnose twice.
    //
    // ONE fork at a time, an explicit ceiling that leaves the runner room to breathe alongside the
    // Gradle and Kotlin daemons, and a fresh JVM every 40 classes so the bitmaps a finished class was
    // holding are actually given back rather than merely eligible.
    maxParallelForks = 1
    maxHeapSize = "2g"
    forkEvery = 40
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
