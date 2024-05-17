import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.util.Properties

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.dokka")
}

setupModuleForComposeMultiplatform()

android {
    namespace = "cafe.adriel.voyager.rxjava"
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(detectProject(rootProject,":voyager-core"))
            api(detectProject(rootProject,":voyager-screenmodel"))
//            api(projects.voyagerCore)
//            api(projects.voyagerScreenmodel)
            compileOnly(libs.rxjava)
        }
        jvmTest.dependencies {
            implementation(libs.junit.api)
            runtimeOnly(libs.junit.engine)
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
val currentName = project.name.replace(rootProject.name+"-","")
buildscript {
    dependencies {
        val dokkaVersion = libs.versions.dokka.get()
        classpath("org.jetbrains.dokka:dokka-base:$dokkaVersion")
    }
}
