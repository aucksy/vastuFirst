// engine — zone maths, 81-pada grid, 32-pada door, rule evaluation, scoring, defects.
// Pure Kotlin/JVM, ZERO Android. This is the product; screens are how people reach it.
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":shared"))
    api(project(":rules"))
    implementation(libs.kotlinx.datetime)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.reflect)   // FacingNeutralityTest: static field-absence guard (§0.4)
}
