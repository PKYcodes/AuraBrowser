plugins {
    id("aura.android.library")
    id("aura.android.hilt")
}

android {
    namespace = "com.aura.browser.core.permissions"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
}
