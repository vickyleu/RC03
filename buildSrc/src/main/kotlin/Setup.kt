@file:OptIn(ExperimentalKotlinGradlePluginApi::class)
@file:Suppress("UnstableApiUsage", "DEPRECATION")

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

private fun BaseExtension.setupAndroid() {
    compileSdkVersion(34)
    defaultConfig {
        minSdk = 21
        targetSdk = 34

        versionCode = 1
        versionName = "1.0"
    }
}


fun DependencyHandler.detectProject(rootProject:Project, path:String): ProjectDependency{
    if(rootProject.name != "voyager"){
        return project(":voyager$path")
    }
    return project("$path")
}

fun KotlinDependencyHandler.detectProject(rootProject:Project,path:String): ProjectDependency{
    if(rootProject.name != "voyager"){
        return project(":voyager$path")
    }
    return project("$path")
}

fun Project.setupModuleForAndroidxCompose(
    withKotlinExplicitMode: Boolean = true,
) {
    val androidExtension: BaseExtension = extensions.findByType<LibraryExtension>()
        ?: extensions.findByType<com.android.build.gradle.AppExtension>()
        ?: error("Could not found Android application or library plugin applied on module $name")

    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    val composeCompilerVersion = libs.findVersion("composeCompiler").get().requiredVersion

    apply(plugin = "org.jetbrains.kotlin.plugin.compose")
    androidExtension.apply {
        setupAndroid()

        buildFeatures.apply {
            compose = true
        }

        composeOptions {
            kotlinCompilerExtensionVersion = composeCompilerVersion
        }

        compileOptions {
            sourceCompatibility = JavaVersion.toVersion(libs.findVersion("jvmTarget").get().requiredVersion)
            targetCompatibility = JavaVersion.toVersion(libs.findVersion("jvmTarget").get().requiredVersion)
        }

        testOptions {
            unitTests.all {
                it.useJUnitPlatform()
            }
        }
        @Suppress("DEPRECATION")
        (this as ExtensionAware).extensions.configure<KotlinJvmOptions> {
            configureKotlinJvmOptions(withKotlinExplicitMode,libs.findVersion("jvmTarget").get().requiredVersion)
        }
    }
}

fun Project.setupModuleForComposeMultiplatform(
    withKotlinExplicitMode: Boolean = true,
    fullyMultiplatform: Boolean = false,
    enableWasm: Boolean = true,
) {
    plugins.withType<org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper> {
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        extensions.configure<KotlinMultiplatformExtension> {
            if (withKotlinExplicitMode) {
                explicitApi()
            }
            sourceSets {
                all {
                    languageSettings.optIn("cafe.adriel.voyager.core.annotation.InternalVoyagerApi")
                    languageSettings.optIn("cafe.adriel.voyager.core.annotation.ExperimentalVoyagerApi")
                }
            }

            applyDefaultHierarchyTemplate {
                common {
                    if (fullyMultiplatform) {
                        group("commonWeb") {
                            withJs()
                            if (enableWasm) {
                                withWasm()
                            }
                        }
                    }
                    group("jvm") {
                        withCompilations {
                            it.target.targetName == "desktop" || it.target is KotlinAndroidTarget
                        }
                    }
                }
            }

            androidTarget {
                if (plugins.hasPlugin("com.vanniktech.maven.publish")) {
                    publishLibraryVariants("release")
                }
            }
            jvm("desktop")

            if (fullyMultiplatform) {
                js(IR) {
                    browser()
                }
                if (enableWasm) {
                    @OptIn(ExperimentalWasmDsl::class)
                    wasmJs { browser() }
                }
                macosX64()
                macosArm64()
                iosArm64()
                iosX64()
                iosSimulatorArm64()
            }
        }

        findAndroidExtension().apply {
            setupAndroid()
            sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
        }

        tasks.withType<KotlinCompile> {
            compilerOptions.configureKotlinJvmOptions(withKotlinExplicitMode,libs.findVersion("jvmTarget").get().requiredVersion)
        }
    }
}

private fun KotlinJvmOptions.configureKotlinJvmOptions(
    enableExplicitMode: Boolean,
    jvmVersion: String
) {
    jvmTarget = jvmVersion
    if (enableExplicitMode) freeCompilerArgs += ("-Xexplicit-api=strict")
    freeCompilerArgs += (
            listOf(
                "-opt-in=cafe.adriel.voyager.core.annotation.InternalVoyagerApi",
                "-opt-in=cafe.adriel.voyager.core.annotation.ExperimentalVoyagerApi"
            )
            )
}

private fun KotlinJvmCompilerOptions.configureKotlinJvmOptions(
    enableExplicitMode: Boolean,
    jvmVersion: String
) {
    jvmTarget.set(JvmTarget.fromTarget(jvmVersion))
    if (enableExplicitMode) freeCompilerArgs.add("-Xexplicit-api=strict")
    freeCompilerArgs.addAll(
        listOf(
            "-opt-in=cafe.adriel.voyager.core.annotation.InternalVoyagerApi",
            "-opt-in=cafe.adriel.voyager.core.annotation.ExperimentalVoyagerApi"
        )
    )
}

private fun Project.findAndroidExtension(): BaseExtension = extensions.findByType<LibraryExtension>()
    ?: extensions.findByType<com.android.build.gradle.AppExtension>()
    ?: error("Could not found Android application or library plugin applied on module $name")
