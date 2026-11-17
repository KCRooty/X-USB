plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "21"
            }
        }
        withJava()
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":common"))
                implementation(compose.desktop.currentOs)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
    }
}

// Copia el classpath de ejecución para lanzar las pruebas del backend fuera de Gradle (p. ej. en WSL).
tasks.register<Copy>("copyTestRuntime") {
    val comp = kotlin.jvm().compilations["test"]
    dependsOn("compileTestKotlinJvm")
    from(comp.runtimeDependencyFiles)
    from(comp.output.allOutputs)
    into(layout.buildDirectory.dir("testRuntime"))
}

compose.desktop {
    application {
        mainClass = "com.xusb.desktop.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "X-USB"
            packageVersion = "1.0.0"
        }
    }
}
