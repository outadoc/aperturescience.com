@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)
    jvm()
    linuxX64()
    wasmJs {
        // d8 (V8's standalone CLI shell), not a browser - this target has no DOM dependency at
        // all, so wasmJsTest can run the whole suite headlessly instead of needing a browser.
        d8()
        // Also required (even though this module never touches the DOM itself) for ui-web's
        // webpack/npm tooling to treat this project as JS/Wasm-usable at all: without a
        // browser()/nodejs() sub-target here, ui-web's wasmJsBrowserDistribution fails with
        // ":logic is not configured for JS usage" trying to resolve it as an npm dependency.
        // Its own browser test task is disabled, though: it'd run via Karma+headless Chrome,
        // which isn't installed here (and would be redundant anyway - d8() above already runs
        // this module's whole suite, and it has no DOM-dependent behavior a browser would
        // exercise differently).
        browser {
            testTask {
                enabled = false
            }
        }
    }

    compilerOptions {
        // kotlinx-coroutines-test's TestScope/runTest/advanceUntilIdle are marked experimental;
        // this is a test-only dependency of this module, so opting in module-wide is fine.
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: TerminalEngine's public surface (liveLine/exitRequested's
            // StateFlow<T>, boot()'s CoroutineScope parameter) exposes kotlinx.coroutines types
            // directly, so consumers like ui-terminal/ui-web need this on their own compile
            // classpath too - implementation would keep it a private detail they can't see.
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
