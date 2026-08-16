plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.compose") version "2.4.0"
    application
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.aperturescience"
version = "0.1.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("com.jakewharton.mosaic:mosaic-runtime:0.18.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.aperturescience.terminal.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
