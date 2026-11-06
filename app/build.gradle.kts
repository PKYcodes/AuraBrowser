plugins {
    id("aura.android.application")
    id("aura.android.hilt")
}

android {
    namespace = "com.aura.browser"

    defaultConfig {
        applicationId = "com.aura.browser"
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(projects.navigation)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.mediaCommon)
    implementation(projects.core.network)
    implementation(projects.core.permissions)

    implementation(projects.feature.home)
    implementation(projects.feature.browser)
    implementation(projects.feature.tabs)
    implementation(projects.feature.bookmarks)
    implementation(projects.feature.history)
    implementation(projects.feature.downloads)
    implementation(projects.feature.youtube)
    implementation(projects.feature.music)
    implementation(projects.feature.video)
    implementation(projects.feature.pip)
    implementation(projects.feature.extensions)
    implementation(projects.feature.filemanager)
    implementation(projects.feature.privacy)
    implementation(projects.feature.settings)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
