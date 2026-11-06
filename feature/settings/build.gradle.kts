plugins {
    id("aura.android.library")
    id("aura.android.compose")
    id("aura.android.hilt")
}

android {
    namespace = "com.aura.browser.feature.settings"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.datastore)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
}
