plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    id(libs.plugins.android.application.get().pluginId) apply false
//    alias(libs.plugins.android.application) apply false
    id(libs.plugins.android.library.get().pluginId) apply false
//    alias(libs.plugins.android.library) apply false
    id(libs.plugins.kotlin.multiplatform.get().pluginId) apply false

    alias(libs.plugins.jetbrains.compose) apply false
//    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
}