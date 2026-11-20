plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
}

// The Minitel service's turn logic, shared by its two hosts: ui-minitel (Ktor on the JVM, Docker
// image) and ui-minitel-worker (Kotlin/JS on Cloudflare Workers).
kotlin {
    jvmToolchain(21)
    jvm()
    js {
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":logic"))
            api(libs.minipavi.core)
            api(libs.minipavi.videotex)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
