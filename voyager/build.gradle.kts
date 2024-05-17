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

val javaVersion = JavaVersion.toVersion(libs.versions.jvmTarget.get())
check(JavaVersion.current().isCompatibleWith(javaVersion)) {
    "This project needs to be run with Java ${javaVersion.getMajorVersion()} or higher (found: ${JavaVersion.current()})."
}


//plugins {
//    alias(libs.plugins.binaryCompatibilityValidator)
//}
plugins {
//    classpath(libs.plugin.hilt)
//    classpath(libs.plugin.ktlint)
////        classpath(libs.plugin.maven)
//    classpath(libs.plugin.multiplatform.compose)
//    classpath(libs.plugin.dokka)
//    classpath(libs.plugin.compose.compiler)
//    classpath(libs.plugin.atomicfu)
    alias(libs.plugins.hilt).apply(false)
    alias(libs.plugins.ktlint).apply(false)
    alias(libs.plugins.jetbrains.compose).apply(false)
    //trick: for the same plugin versions in all sub-modules
    alias(libs.plugins.dokka)//.apply(false)
    id("maven-publish")
    // kotlin 2.0.0 with compose needs this
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.kotlin.atomicfu).apply(false)
}



allprojects {
    if (tasks.findByName("testClasses") == null) {
        try {
            tasks.register("testClasses")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("0.47.1")
        @Suppress("DEPRECATION")
        disabledRules.set(setOf("filename"))
    }
}


//apiValidation {
//    ignoredProjects.addAll(listOf(
//        /*samples*/"android",
//        /*samples*/"multiplatform",
//        /*samples/multi-modulo*/"app",
//        /*samples/multi-modulo*/"feature-home",
//        /*samples/multi-modulo*/"feature-posts",
//        /*samples/multi-modulo*/"navigation",
//    ))
//    nonPublicMarkers.addAll(listOf(
//        "cafe.adriel.voyager.core.annotation.InternalVoyagerApi",
//        "cafe.adriel.voyager.core.annotation.ExperimentalVoyagerApi"
//    ))
//}




allprojects {
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
    val rootProjectName = rootDir.name
    val mavenAuthor = "vickyleu"
    val mavenGroup = "com.$mavenAuthor.$rootProjectName"
    val currentName = project.name.replace("$rootProjectName-", "")

    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "maven-publish")
    val mGroup = mavenGroup
    val mVersion = "1.0.2"
    afterEvaluate {
        if (project.name.startsWith(rootProjectName).not()) {
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
                val projectName = rootProjectName
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
                    if (artifactId.startsWith("${rootProjectName}-${currentName}")) {
                        artifactId =
                            artifactId.replace("${rootProjectName}-${currentName}", currentName)
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
            // See the buildscript block above and also
            // https://github.com/Kotlin/dokka/issues/2406
//    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
////        customAssets = listOf(file("../asset/logo-icon.svg"))
////        customStyleSheets = listOf(file("../asset/logo-styles.css"))
//        separateInheritedMembers = true
//    }

            dokkaSourceSets {
                configureEach {
                    reportUndocumented = true
                    noAndroidSdkLink = false
                    noStdlibLink = false
                    noJdkLink = false
                    jdkVersion = jvmTarget.toInt()
                    // sourceLink {
                    //     // Unix based directory relative path to the root of the project (where you execute gradle respectively).
                    //     // localDirectory.set(file("src/main/kotlin"))
                    //     // URL showing where the source code can be accessed through the web browser
                    //     // remoteUrl = uri("https://github.com/mahozad/${project.name}/blob/main/${project.name}/src/main/kotlin").toURL()
                    //     // Suffix which is used to append the line number to the URL. Use #L for GitHub
                    //     remoteLineSuffix = "#L"
                    // }
                }
            }
        }


// TODO: Remove after https://youtrack.jetbrains.com/issue/KT-46466 is fixed
//  Thanks to KSoup repository for this code snippet
        tasks.withType(AbstractPublishToMaven::class).configureEach {
            dependsOn(tasks.withType(Sign::class))
        }

// * Uses signing.* properties defined in gradle.properties in ~/.gradle/ or project root
// * Can also pass from command line like below
// * ./gradlew task -Psigning.secretKeyRingFile=... -Psigning.password=... -Psigning.keyId=...
// * See https://docs.gradle.org/current/userguide/signing_plugin.html
// * and https://stackoverflow.com/a/67115705
        /*signing {
            sign(publishing.publications)
        }*/

        /*apply(plugin = "org.jetbrains.dokka")
        val dokkaHtml by tasks.getting(org.jetbrains.dokka.gradle.DokkaTask::class)
        val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
            dependsOn(dokkaHtml)
            archiveClassifier.set("javadoc")
            from(dokkaHtml.outputDirectory)
        }*/
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

    val isUseForkConfig = map["isUseForkConfig"]?.toBooleanStrict() ?: false
    val rootProjectName = rootDir.name

    val mavenAuthor = if (isUseForkConfig) "vickyleu" else "Arnaud Giuliani"
    val mavenGroup = if (isUseForkConfig) "com.$mavenAuthor.$rootProjectName" else "io.insert-koin"

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
