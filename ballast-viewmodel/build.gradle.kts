plugins {
    `copper-leaf-android`
    `copper-leaf-targets`
    `copper-leaf-base`
    `copper-leaf-version`
    `copper-leaf-lint`
    `copper-leaf-publish`
}

description = "Opinionated Application State Management framework for Kotlin Multiplatform"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":ballast-api"))
            }
        }
        val commonTest by getting {
            dependencies { }
        }
        val jvmMain by getting {
            dependencies { }
        }
        val androidMain by getting {
            dependencies {
                api(libs.androidx.lifecycle.common)
                api(libs.androidx.lifecycle.runtime)
                api(libs.androidx.lifecycle.viewmodel)
            }
        }
        val jsMain by getting {
            dependencies { }
        }
        val iosMain by getting {
            dependencies { }
        }
    }
}
