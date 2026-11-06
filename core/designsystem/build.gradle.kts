plugins {
    id("aura.android.library")
    id("aura.android.compose")
}

android {
    namespace = "com.aura.browser.core.designsystem"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
