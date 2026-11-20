plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.ktlint)
}

allprojects {
    group = "com.aperturescience"
    version = "0.1.0"

    repositories {
        mavenCentral()

        // To test a local build of outadoc/minipavi-kotlin (its `publishToMavenLocal` task), run
        // Gradle with -Pminipavi.mavenLocal=true.
        if (providers.gradleProperty("minipavi.mavenLocal").orNull == "true") {
            mavenLocal {
                mavenContent {
                    includeGroup("fr.outadoc.minipavi")
                }
            }
        }

        // outadoc/minipavi-kotlin (ui-minitel's Minitel/Vidéotex dependency) is published to
        // GitHub Packages, which requires authentication even for public packages. Provide a token
        // with the `read:packages` scope, either through the `gpr.user` and `gpr.key` Gradle
        // properties (in ~/.gradle/gradle.properties), or through the GITHUB_ACTOR and
        // GITHUB_TOKEN environment variables (as on GitHub Actions).
        maven {
            name = "MiniPaviGitHubPackages"
            url = uri("https://maven.pkg.github.com/outadoc/minipavi-kotlin")
            credentials {
                username =
                    providers
                        .gradleProperty("gpr.user")
                        .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                        .orNull
                password =
                    providers
                        .gradleProperty("gpr.key")
                        .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                        .orNull
            }
            mavenContent {
                includeGroup("fr.outadoc.minipavi")
            }
        }
    }

    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
