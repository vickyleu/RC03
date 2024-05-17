plugins {
    id("com.android.library")
    kotlin("android")
}

setupModuleForAndroidxCompose(
    withKotlinExplicitMode = false
)

android {
    namespace = "cafe.adriel.voyager.sample.multimodule.home"
}

dependencies {
    implementation(projects.voyagerNavigator)

    implementation(projects.samples.multiModule.navigation)

    implementation(libs.appCompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.material)
}
