plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    jvm("desktop") {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.9.0")
                implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
                implementation("com.github.topjohnwu.libsu:core:5.3.0")
                implementation("com.github.topjohnwu.libsu:io:5.3.0")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "com.xusb.common"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
