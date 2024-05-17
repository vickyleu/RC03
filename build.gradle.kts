import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import java.util.concurrent.Executors

buildscript {
    dependencies {
//        classpath(libs.plugin.hilt)
//        classpath(libs.plugin.ktlint)
////        classpath(libs.plugin.maven)
//        classpath(libs.plugin.multiplatform.compose)
//        classpath(libs.plugin.dokka)
//        classpath(libs.plugin.compose.compiler)
//        classpath(libs.plugin.atomicfu)
        val dokkaVersion = libs.versions.dokka.get()
        classpath("org.jetbrains.dokka:dokka-base:$dokkaVersion")
    }
}
plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    id(libs.plugins.android.application.get().pluginId) apply false
//    alias(libs.plugins.android.application) apply false
    id(libs.plugins.android.library.get().pluginId) apply false
//    alias(libs.plugins.android.library) apply false
    id(libs.plugins.kotlin.multiplatform.get().pluginId) apply false


    alias(libs.plugins.kotlin.atomicfu).apply(false)
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.dokka)
    id("maven-publish")
}

val username = properties["your.github.packages.username"]
allprojects {
    val voyagerRoot = File("${rootDir.absolutePath}${File.separatorChar}${"voyager"}")
    if(project.projectDir.absolutePath.contains(voyagerRoot.absolutePath)){
        val properties = Properties().apply {
            runCatching { rootProject.file("local.properties") }
                .getOrNull()
                .takeIf { it?.exists() ?: false }
                ?.reader()
                ?.use(::load)
        }
        val environment: Map<String, String?> = System.getenv()
        val myExtra = mutableMapOf<String, Any>()
        myExtra["githubToken"] = properties["github.token"] as? String
            ?: environment["GITHUB_TOKEN"] ?: ""

        val libs = rootDir.resolve("gradle/libs.versions.toml")
        val map = hashMapOf<String, String>()
        libs.useLines {
            it.forEach { line ->
                if (line.contains("=") && line.replace(" ", "").startsWith("#").not()) {
                    val (key, value) = line.split("=")
                    map[key.replace(" ", "").removeSurrounding("\"")] =
                        value.replace(" ", "").removeSurrounding("\"")
                }
            }
        }
        val jvmTarget = map["jvmTarget"] ?: "11"
        val mavenAuthor = username
        val mavenGroup = "com.$mavenAuthor.${voyagerRoot.name}.example"
        val currentName = project.name.replace("${voyagerRoot.name}-", "")

        apply(plugin = "org.jetbrains.dokka")
        apply(plugin = "maven-publish")
        val mGroup = mavenGroup
        val mVersion = "1.0.0"
        afterEvaluate {
            if (project.name.startsWith(voyagerRoot.name).not()) {
                return@afterEvaluate
            }
            tasks.withType<PublishToMavenRepository> {
                val isMac = DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX
                onlyIf {
                    isMac.also {
                        if (!isMac) logger.error(
                            """
                    Publishing the library requires macOS to be able to generate iOS artifacts.
                    Run the task on a mac or use the project GitHub workflows for publication and release.
                """
                        )
                    }
                }
            }
            if (project.extensions.findByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class) != null){
                val kotlin =
                    project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class)
                kotlin.apply {
                    @OptIn(ExperimentalKotlinGradlePluginApi::class)
                    this.compilerOptions {
                        freeCompilerArgs = listOf(
                            "-Xexpect-actual-classes", // remove warnings for expect classes
                            "-Xskip-prerelease-check",
                            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
                        )
                    }
                    this.jvmToolchain {
                        languageVersion.set(JavaLanguageVersion.of(jvmTarget.toInt()))
                    }
                    this.targets.withType<KotlinNativeTarget> {
                        binaries.all {
                            freeCompilerArgs += "-Xdisable-phases=VerifyBitcode"
                        }
                    }
                }
            }
            afterEvaluate {
                val javadocJar by   tasks.registering(Jar::class) {
                    dependsOn(tasks.dokkaHtml)
                    from(tasks.dokkaHtml.flatMap(org.jetbrains.dokka.gradle.DokkaTask::outputDirectory))
                    archiveClassifier = "javadoc"
                }
                publishing {
                    val projectName = voyagerRoot.name
                    repositories {
                        maven {
                            name = "GitHubPackages"
                            url = uri("https://maven.pkg.github.com/$mavenAuthor/${projectName}")
                            credentials {
                                username = "$mavenAuthor"
                                password = myExtra["githubToken"]?.toString()
                            }
                        }
                    }
                    publications.withType<MavenPublication> {
                        artifact(javadocJar) // Required a workaround. See below
                        version = mVersion
                        groupId = mGroup
                        if (artifactId.startsWith("${voyagerRoot.name}-${currentName}")) {
                            artifactId =
                                artifactId.replace("${voyagerRoot.name}-${currentName}", currentName)
                        }
                        pom {
                            url = "https://github.com/$mavenAuthor/${projectName}"
                            name = projectName
                            description = """
                Visit the project on GitHub to learn more.
            """.trimIndent()
                            inceptionYear = "2024"
                            licenses {
                                license {
                                    name = "Apache-2.0 License"
                                    url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                                }
                            }
                            developers {
                                developer {
                                    id = "adrielcafe"
                                    name = "Adriel Cafe"
                                    email = ""
                                    roles = listOf("Mobile Developer")
                                    timezone = "GMT+8"
                                }
                            }
                            contributors {
                                // contributor {}
                            }
                            scm {
                                tag = "HEAD"
                                url = "https://github.com/$mavenAuthor/${projectName}"
                                connection = "scm:git:github.com/$mavenAuthor/${projectName}.git"
                                developerConnection =
                                    "scm:git:ssh://github.com/$mavenAuthor/${projectName}.git"
                            }
                            issueManagement {
                                system = "GitHub"
                                url = "https://github.com/$mavenAuthor/${projectName}/issues"
                            }
                            ciManagement {
                                system = "GitHub Actions"
                                url = "https://github.com/$mavenAuthor/${projectName}/actions"
                            }
                        }
                    }
                }
            }
            tasks.dokkaHtml {
                // outputDirectory = layout.buildDirectory.get().resolve("dokka")
                offlineMode = false
                moduleName = currentName
                dokkaSourceSets {
                    configureEach {
                        reportUndocumented = true
                        noAndroidSdkLink = false
                        noStdlibLink = false
                        noJdkLink = false
                        jdkVersion = jvmTarget.toInt()
                    }
                }
            }
            tasks.withType(AbstractPublishToMaven::class).configureEach {
                dependsOn(tasks.withType(Sign::class))
            }
        }
    }
}


tasks.register("deletePackages") {

    val libs = rootDir.resolve("gradle/libs.versions.toml")
    val map = hashMapOf<String, String>()
    libs.useLines {
        it.forEach { line ->
            if (line.contains("=") && line.startsWith("#").not()) {
                val (key, value) = line.split("=")
                map[key
                    .replace(" ", "").removeSurrounding("\"")] =
                    value
                        .replace(" ", "").removeSurrounding("\"")
            }
        }
    }
    val voyagerRoot = File("${rootDir.absolutePath}${File.separatorChar}${"voyager"}")
    val rootProjectName = voyagerRoot.name

    val mavenAuthor = username
    val mavenGroup = "com.$mavenAuthor.$rootProjectName.example"

    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "maven-publish")

    group = "publishing"
    description = "Delete all packages in the GitHub Packages registry"

    val keyword = "${mavenGroup}"
    println("keyword: $keyword")
    val properties = Properties().apply {
        runCatching { rootProject.file("local.properties") }
            .getOrNull()
            .takeIf { it?.exists() ?: false }
            ?.reader()
            ?.use(::load)
    }
// For information about signing.* properties,
// see comments on signing { ... } block below
    val environment: Map<String, String?> = System.getenv()
    val myExtra = mutableMapOf<String, Any>()
    myExtra["githubToken"] = properties["github.token"] as? String
        ?: environment["GITHUB_TOKEN"] ?: ""
    val headers = mapOf(
        "Accept" to "application/vnd.github.v3+json",
        "Authorization" to "Bearer ${myExtra["githubToken"]}",
        "X-GitHub-Api-Version" to "2022-11-28"
    )
    doLast {
        runBlocking {
            val executor = Executors.newFixedThreadPool(10)
            val scope = CoroutineScope(executor.asCoroutineDispatcher())
            val fetchJobs = packageTypes.flatMap { packageType ->
                visibilityTypes.map { visibility ->
                    scope.async {
                        fetchPackages(packageType, visibility, headers)
                    }
                }
            }
            fetchJobs.awaitAll().forEach { packages ->
                allPackages.addAll(packages)
            }
            val deleteJobs = allPackages.filter { pkg ->
                val packageName = pkg["name"] as String
                packageName.contains(keyword)
            }.map { pkg ->
                val packageType = pkg["package_type"] as String
                val packageName = pkg["name"] as String
                scope.async {
                    deletePackage(packageType, packageName, headers)
                }
            }
            try {
                deleteJobs.awaitAll()
                executor.shutdown()
            } catch (e: Exception) {
                println("删除包失败: ${e.message}")
            }
        }
    }
}


val packageTypes = listOf("npm", "maven", "docker", "container")
val visibilityTypes = listOf("public", "private", "internal")
val allPackages = mutableListOf<Map<String, Any>>()

fun fetchPackages(packageType: String, visibility: String, headers: Map<String, String>): List<Map<String, Any>> {
    val packages = mutableListOf<Map<String, Any>>()
    var page = 1

    while (true) {
        val url =
            URL("https://api.github.com/user/packages?package_type=$packageType&visibility=$visibility&page=$page&per_page=100")
        val connection = url.openConnection() as HttpURLConnection

        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }

        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val batch: List<Map<String, Any>> = jacksonObjectMapper().readValue(response)
            if (batch.isEmpty()) break
            packages.addAll(batch)
            page++
        } else {
            println("获取$packageType ($visibility) 包列表失败，错误代码: ${connection.responseCode} ${connection.responseMessage}")
            println(connection.inputStream.bufferedReader().use { it.readText() })
            break
        }
    }

    return packages
}

fun deletePackage(packageType: String, packageName: String, headers: Map<String, String>) {
    val url = URL("https://api.github.com/user/packages/$packageType/$packageName")
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "DELETE"
    headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }

    if (connection.responseCode == 204 || connection.responseCode == 200) {
        println("$packageName 删除成功")
    } else {
        println("$packageName 删除失败，错误代码: ${connection.responseCode}")
        println(connection.inputStream.bufferedReader().use { it.readText() })
    }
}
