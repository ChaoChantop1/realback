plugins {
    alias(libs.plugins.kotlin.jvm)
}

// CONSTRAINT (M0 architecture rule, borrowed from uhabits):
// this module must have ZERO Android dependencies so it can be reused
// as-is under Kotlin Multiplatform when we target iOS in Phase 4.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
    }
}
