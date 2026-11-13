plugins {
    id("aura.android.library")
    id("aura.android.hilt")
}

android {
    namespace = "com.aura.browser.core.network"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
