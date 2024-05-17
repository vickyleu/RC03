@file:Suppress("UnstableApiUsage")

rootProject.name = "KotlinProject"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        val properties = java.util.Properties().apply {
            runCatching { rootProject.projectDir.resolve("local.properties") }
                .getOrNull()
                .takeIf { it?.exists() ?: false }
                ?.reader()
                ?.use(::load)
        }
        val environment: Map<String, String?> = System.getenv()
        extra["githubToken"] = properties["github.token"] as? String
            ?: environment["GITHUB_TOKEN"] ?: ""
        maven {
            url = uri("https://maven.pkg.github.com/vickyleu/voyager")
            credentials {
                username = "vickyleu"
                password = extra["githubToken"]?.toString()
            }
            content {
                excludeGroupByRegex("(?!com|cn).github.(?!vickyleu).*")
            }
        }
    }
}

include(":composeApp")


include(":voyager")
project(":voyager").projectDir = file("./voyager")

include("voyager:voyager-core")
include("voyager:voyager-navigator")
include("voyager:voyager-screenmodel")
include("voyager:voyager-tab-navigator")
include("voyager:voyager-transitions")
include("voyager:voyager-bottom-sheet-navigator")
