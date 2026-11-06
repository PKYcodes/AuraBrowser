plugins {
    id("aura.android.library")
    id("aura.android.compose")
}

android {
    namespace = "com.aura.browser.navigation"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
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
    api(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
}
