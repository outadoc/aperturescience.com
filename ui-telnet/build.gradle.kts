plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
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

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_21)
        localImageName.set("aperturescience-telnet")
        externalRegistry.set(
            io.ktor.plugin.features.DockerImageRegistry.externalRegistry(
                username = providers.environmentVariable("GHCR_USERNAME"),
                password = providers.environmentVariable("GHCR_PASSWORD"),
                project = provider { "aperturescience-telnet" },
                hostname = provider { "ghcr.io" },
                namespace = provider { "outadoc" },
            ),
        )
    }
}
