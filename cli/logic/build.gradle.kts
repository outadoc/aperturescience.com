plugins {
    kotlin("jvm")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // kotlinx-coroutines-test's TestScope/runTest/advanceUntilIdle are marked experimental;
        // this is a test-only dependency of this module, so opting in module-wide is fine.
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

tasks.test {
    useJUnitPlatform()
}
