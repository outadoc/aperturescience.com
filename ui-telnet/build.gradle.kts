plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.aperturescience.terminal.telnet.MainKt")
}

dependencies {
    implementation(project(":logic"))
    implementation(libs.ktor.network)
    runtimeOnly(libs.logback.classic)
    testImplementation(libs.kotlin.test)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

base {
    archivesName.set("aperturescience-telnet")
}
