plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    jvmToolchain(21)

    jvm {
        mainRun {
            mainClass.set("com.aperturescience.terminal.minitel.MainKt")
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":logic"))
            implementation(libs.minipavi.core)
            implementation(libs.minipavi.videotex)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

base {
    archivesName.set("aperturescience-minitel")
}
