plugins {
    id("com.android.library")
    kotlin("android")
}

setupModuleForAndroidxCompose(
    withKotlinExplicitMode = false
)

android {
    namespace = "cafe.adriel.voyager.sample.multimodule.posts"
}

dependencies {
    implementation(projects.voyagerScreenmodel)
    implementation(projects.voyagerNavigator)

    implementation(projects.samples.multiModule.navigation)

    implementation(libs.appCompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.material)
}
