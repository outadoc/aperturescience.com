plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    application
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":logic"))
    implementation("com.jakewharton.mosaic:mosaic-runtime:0.18.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.aperturescience.terminal.ui.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

base {
    archivesName.set("aperturescience-terminal")
}
