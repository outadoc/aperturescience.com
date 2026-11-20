plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.aperturescience.terminal.minitel.MainKt")
}

dependencies {
    implementation(project(":ui-minitel-core"))
    implementation(libs.minipavi.core.ktor)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    runtimeOnly(libs.logback.classic)
}

base {
    archivesName.set("aperturescience-minitel")
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_21)
        localImageName.set("aperturescience-minitel")
        externalRegistry.set(
            io.ktor.plugin.features.DockerImageRegistry.externalRegistry(
                username = providers.environmentVariable("GHCR_USERNAME"),
                password = providers.environmentVariable("GHCR_PASSWORD"),
                project = provider { "aperturescience-minitel" },
                hostname = provider { "ghcr.io" },
                namespace = provider { "outadoc" },
            ),
        )
    }
}
