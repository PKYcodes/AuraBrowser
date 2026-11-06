plugins {
    id("aura.android.library")
    id("aura.android.compose")
    id("aura.android.hilt")
}

android {
    namespace = "com.aura.browser.feature.pip"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.mediaCommon)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
