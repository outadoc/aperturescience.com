plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.shadow)
}

kotlin {
    jvmToolchain(21)

    jvm {
        // Replaces the plain `kotlin("jvm")` + `application` plugin combo (incompatible with
        // Kotlin Multiplatform): this is the KMP/JVM binaries DSL's equivalent of
        // `application { mainClass.set(...) }`, and it's also what the shadow plugin's KMP
        // support reads its `shadowJar.mainClass` convention from.
        mainRun {
            mainClass.set("com.aperturescience.terminal.ui.MainKt")
        }
    }
    linuxX64 {
        binaries {
            executable {
                entryPoint = "com.aperturescience.terminal.ui.main"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":logic"))
            implementation(libs.mosaic.tty)
            implementation(libs.mosaic.terminal)
            implementation(libs.mosaic.tty.terminal)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

base {
    archivesName.set("aperturescience-terminal")
}
