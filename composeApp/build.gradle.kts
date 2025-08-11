import config.Config
import plugins.setupKmpTargets

plugins {
    id("android-application-setup")
    id("detekt-setup")
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrainsCompose)
}

android {
    namespace = Config.applicationId + ".sample"
}

kotlin {
    setupKmpTargets(
        onBinariesFramework = {
            it.baseName = "ComposeApp"
            it.isStatic = true
        }
    )

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
                implementation(libs.koin.android)
        }

        commonMain.dependencies {
            implementation(projects.recordCore)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.materialKolor)
            implementation(libs.mokoPermissions)
            
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation)

            //KOIN
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeVM)
        }
    }
}

android {
    dependencies {
        debugImplementation(libs.compose.ui.tooling)
    }
}
