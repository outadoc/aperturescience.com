plugins {
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.compose") version "2.4.0" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
}

allprojects {
    group = "com.aperturescience"
    version = "0.1.0"

    repositories {
        mavenCentral()
        google()
    }
}
