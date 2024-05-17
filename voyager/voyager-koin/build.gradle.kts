import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.util.Properties

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

setupModuleForComposeMultiplatform(
    fullyMultiplatform = true,
    enableWasm = false // https://github.com/InsertKoinIO/koin/issues/1634
)
compose {
    kotlinCompilerPlugin = "org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable:${libs.versions.kotlincompose.compiler.get()}"
}
android {
    namespace = "cafe.adriel.voyager.koin"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(detectProject(rootProject,":voyager-core"))
            api(detectProject(rootProject,":voyager-screenmodel"))
            api(detectProject(rootProject,":voyager-navigator"))
//            api(projects.voyagerCore)
//            api(projects.voyagerScreenmodel)
//            api(projects.voyagerNavigator)

            compileOnly(compose.runtime)
            compileOnly(compose.runtimeSaveable)

            implementation(libs.kotlinx.coroutines.core)
        }
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
    commonMainImplementation(libs.koin.compose) {
        exclude("org.jetbrains.compose.runtime")
    }
}
