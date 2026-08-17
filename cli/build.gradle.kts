plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.ktlint)
}

allprojects {
    group = "com.aperturescience"
    version = "0.1.0"

    repositories {
        mavenCentral()
        google()
        // outadoc/minipavi-kotlin (ui-minitel's Minitel/Vidéotex dependency) is only published
        // via JitPack, not Maven Central.
        maven { url = uri("https://jitpack.io") }
    }

    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
