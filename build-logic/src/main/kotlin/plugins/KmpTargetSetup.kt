package plugins

import config.Config
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework

fun KotlinMultiplatformExtension.setupKmpTargets(
    onBinariesFramework: (Framework) -> Unit = {}
) {
    androidTarget {
        compilations.all {
            (this as? org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation)?.compilerOptions?.options?.jvmTarget?.set(
                org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(Config.javaVersion.toString())
            )
        }
    }

//    jvm("desktop")
//
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            onBinariesFramework(this)
        }
    }
}