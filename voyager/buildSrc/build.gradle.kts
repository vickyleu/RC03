plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
//    implementation(libs.plugin.kotlin)
    implementation("com.squareup:javapoet:1.13.0") // https://github.com/google/dagger/issues/3068
}
