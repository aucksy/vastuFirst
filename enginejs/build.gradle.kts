import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

// enginejs — the SAME scoring engine, compiled to JavaScript for the admin panel.
//
// ⭐ This module contains almost no source of its own. It points its source directories at
// `shared`, `rules` and `engine` and compiles those very files for the browser, so the admin
// panel's "what does this rule change do to a score?" preview runs the code the phone runs.
// There is deliberately no second implementation to drift — if a file here stops compiling,
// that is the point: somebody put something JVM-only into the scoring path.
//
// Three things are held out, and only three:
//   * shared/scan/**   — the plan reader; it speaks java.net and is not part of scoring.
//   * shared/editor/** — the guided-grid editor; phone UI support, not part of scoring.
//   * rules/RuleSetResources.kt — reads the dataset off the JVM classpath. Replaced here by a
//     JavaScript version that refuses, because the panel always hands the rules in.
//
// The existing modules stay `kotlin("jvm")` and are NOT touched by this. `:app` resolves them
// exactly as before, and check-boundaries.sh keeps passing.
//
// ⚠ Compiling is not proof. `tools/verify-enginejs.mjs` runs the built bundle under Node against
// the real rule set and the anchor home, and fails if it does not answer 31 — the same number the
// JVM tests pin. A build that compiles and scores differently is the failure this whole module
// could otherwise introduce silently.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    js(IR) {
        moduleName = "vastuengine"
        browser {
            // The engine's real tests are the JVM ones in :engine. This target exists to produce a
            // bundle, not to host a second test suite that would drift from them.
            testTask { enabled = false }
        }
        binaries.executable()
        compilerOptions {
            // UMD so the same file works three ways with no build step in the panel: a plain
            // <script> tag (which is all a strict content policy allows), a Node require() for the
            // verification below, and a bundler if one is ever added.
            moduleKind.set(JsModuleKind.MODULE_UMD)
        }
    }

    sourceSets {
        val jsMain by getting {
            kotlin.srcDir("$rootDir/shared/src/main/kotlin")
            kotlin.srcDir("$rootDir/rules/src/main/kotlin")
            kotlin.srcDir("$rootDir/engine/src/main/kotlin")

            kotlin.exclude("**/com/vastufirst/shared/scan/**")
            kotlin.exclude("**/com/vastufirst/shared/editor/**")
            // Taking a signed rule set off the network: java.net and java.security, and only a
            // phone ever does it. The panel is where rules come FROM.
            kotlin.exclude("**/com/vastufirst/shared/update/**")
            kotlin.exclude("**/com/vastufirst/rules/RuleSetResources.kt")

            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.js.ExperimentalJsExport")
    }
}

/**
 * Collect everything the admin panel needs into one folder, so the panel never reaches into this
 * repository's layout and nothing it uses can be edited in two places.
 */
val engineBundle by tasks.registering(Copy::class) {
    group = "vastufirst"
    description = "The JavaScript engine, the live rule set and the preview homes, in one folder."
    dependsOn(tasks.named("jsBrowserDistribution"))

    into(layout.buildDirectory.dir("engine-bundle"))

    from(layout.buildDirectory.dir("dist/js/productionExecutable")) {
        include("*.js")
        into("engine")
    }
    from("$rootDir/rules/src/main/resources/ruleset") {
        include("*.json")
        into("ruleset")
    }
    from("$rootDir/engine/src/test/resources/preview") {
        include("*.json")
        into("homes")
    }
}
