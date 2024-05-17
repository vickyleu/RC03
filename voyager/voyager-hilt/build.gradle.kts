plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("kapt")
}

setupModuleForAndroidxCompose()

android {
    namespace = "cafe.adriel.voyager.hilt"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

kapt {
    correctErrorTypes = true
}

kotlin {
    @Suppress("OPT_IN_USAGE")
    compilerOptions {
        freeCompilerArgs = listOf(
            "-Xexpect-actual-classes", // remove warnings for expect classes
            "-Xskip-prerelease-check",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
        )
    }
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmTarget.get()))
    }
}
dependencies {
    api(detectProject(rootProject, ":voyager-screenmodel"))
    api(detectProject(rootProject, ":voyager-navigator"))
//    api(projects.voyagerScreenmodel)
//    api(projects.voyagerNavigator)

    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.lifecycle.savedState)
    implementation(libs.lifecycle.viewModelKtx)
    implementation(libs.hilt.android)
    implementation(libs.lifecycle.viewModelCompose)
    kapt(libs.hilt.compiler)

    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.junit.api)
}
