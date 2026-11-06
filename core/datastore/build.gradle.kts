plugins {
    id("aura.android.library")
    id("aura.android.hilt")
}

android {
    namespace = "com.aura.browser.core.datastore"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
