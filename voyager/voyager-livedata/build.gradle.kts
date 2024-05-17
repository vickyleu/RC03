import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.util.Properties

plugins {
    id("com.android.library")
    kotlin("android")
}

setupModuleForAndroidxCompose()

android {
    namespace = "cafe.adriel.voyager.livedata"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
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
    api(detectProject(rootProject,":voyager-core"))
    api(detectProject(rootProject,":voyager-screenmodel"))
    implementation(libs.compose.runtimeLiveData)

    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.junit.api)
}
