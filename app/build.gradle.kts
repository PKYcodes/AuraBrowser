import java.util.Properties

plugins {
    id("aura.android.application")
    id("aura.android.hilt")
}

// Release signing: provide keystore.properties (git-ignored) with storeFile, storePassword,
// keyAlias and keyPassword. Without it, release builds are produced unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.aura.browser"

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.aura.browser"
        versionCode = 1
        versionName = "1.0.0"
    }

    packaging {
        jniLibs {
            // Compress Mozilla GeckoView's libxul.so inside the APK (~672 MB -> ~75 MB)
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            ndk {
                // arm64 only keeps the debug APK small (GeckoView is ~70 MB per ABI).
                // Emulator on a PC? Build with: gradle :app:assembleDebug -PwithX86=true
                abiFilters += if (providers.gradleProperty("withX86").isPresent) {
                    listOf("arm64-v8a", "x86_64")
                } else {
                    listOf("arm64-v8a")
                }
            }
        }
        release {
            ndk {
                // Phones only: GeckoView's native engine is large, so ship a single ABI.
                abiFilters += listOf("arm64-v8a")
            }
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            isShrinkResources = true
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
