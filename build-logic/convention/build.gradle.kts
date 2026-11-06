import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.aura.browser.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "aura.android.application"
            implementationClass = "AuraAndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "aura.android.library"
            implementationClass = "AuraAndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "aura.android.compose"
            implementationClass = "AuraAndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "aura.android.hilt"
            implementationClass = "AuraAndroidHiltConventionPlugin"
        }
    }
}
