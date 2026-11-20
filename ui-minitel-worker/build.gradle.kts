plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// The Minitel service on Cloudflare Workers - see README.md. `npm run build` (or
// `:ui-minitel-worker:jsNodeProductionLibraryDistribution`) compiles it into
// build/dist/js/productionLibrary/, which worker/index.mjs imports.
kotlin {
    js {
        outputModuleName = "ui-minitel-worker"
        nodejs()
        useEsModules()
        binaries.library()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":ui-minitel-core"))
            implementation(libs.minipavi.core.cloudflare)
        }
    }
}
