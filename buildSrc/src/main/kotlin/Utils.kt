import org.gradle.api.Project
import org.gradle.api.Task
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.BufferedWriter
import java.io.File
import java.io.StringReader
import java.nio.file.Paths
import java.util.Properties
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible

fun Project.getBuildRelativeDir(): String {
    return this.layout.buildDirectory.get().asFile.let {
        val parent = this.projectDir.parentFile
        val relative = it.relativeTo(parent)
        return@let relative.path
    }
}

//fun Project.getRootPodPlatform(): String {
//    val rootPod = project.projectDir.resolve("../iosApp/Podfile")
//    val platform = rootPod.readText().lines().find {
//        !it.startsWith("#") && it.contains(Regex("platform\\s*:ios\\s*,\\s*'\\d+\\.\\d+'"))
//    }?.split(",").orEmpty().lastOrNull()?.replace("\'", "")?.replace(" ", "") ?: "12.0"
//    return platform
//}

fun Task.updatePodspecFile(
    project: Project, rootDir: File, outputFile: File, configProperties: Properties,iosDeploymentTarget:String
) {
    val taskBuilder = CocoapodsAppender.TaskBuilder(
        this, project.layout.buildDirectory.get().asFile, project.projectDir, project.rootDir
    )
    project.xcodeCheck(taskBuilder.isBuildDirChanged, configProperties)
    val projectLayout = project.layout.buildDirectory
    val framework = projectLayout.dir("cocoapods").map { it.dir("framework") }
    val composeResources = projectLayout.dir("compose/ios").map { it.dir(project.name) }
        .map { it.dir("compose-resources") }
    val frameworkDir = framework.get().asFile.relativeTo(outputFile.parentFile)
    val composeResourcesDir = composeResources.get().asFile.relativeTo(outputFile.parentFile)

    val currentPath = project.projectDir
    val frameworkIncludeDir =
        (frameworkDir.resolve("${project.name}.framework")).invariantSeparatorsPath
    val currentPathNormalized = currentPath.normalize()
    val composeResourcesRelativelyPath = composeResourcesDir.invariantSeparatorsPath
    val composeResourcesPath =
        Paths.get(File(currentPathNormalized.absolutePath + "/" + composeResourcesRelativelyPath).absolutePath)
    val frameworkPath =
        Paths.get(File(currentPathNormalized.absolutePath + "/" + frameworkIncludeDir).absolutePath)
    // 规范化路径
    val frameworkPathNormalized = frameworkPath.normalize()
    val composeResourcesPathNormalized = composeResourcesPath.normalize()
    doFirst {
        if (taskBuilder.isBuildDirChanged) {
            if (!frameworkPathNormalized.startsWith(Paths.get(currentPathNormalized.absolutePath))) {// 当前framework路径路径不是项目的子路径
                if (!frameworkPathNormalized.exists()) return@doFirst
                val frameworkLinkDir = currentPath.resolve("framework")
                if (!frameworkLinkDir.exists()) {
                    frameworkLinkDir.mkdirs()
//                    frameworkLinkDir.createDirectory()
                }
                currentPath.resolve("framework/${frameworkPathNormalized.name}").apply {
                    if (exists()) {
                        delete()
                    }
                }
                ProcessBuilder().directory(frameworkLinkDir).command(
                        "ln",
                        "-s",
                        frameworkPathNormalized.absolutePathString(),
                        "./${frameworkPathNormalized.name}"
                    ).start().apply {
                        waitFor()
                    }.inputStream.bufferedReader().readText()

                if (composeResourcesPathNormalized.exists()) {
                    currentPath.resolve("framework/${composeResourcesPathNormalized.name}").apply {
                        if (exists()) {
                            delete()
                        }
                    }
                    ProcessBuilder().directory(frameworkLinkDir).command(
                            "ln",
                            "-s",
                            composeResourcesPathNormalized.absolutePathString(),
                            "./${composeResourcesPathNormalized.name}"
                        ).start().apply {
                            waitFor()
                        }.inputStream.bufferedReader().readText()
                }
            }
        }
    }
    doLast {
        if (taskBuilder.isBuildDirChanged) {
            outputFile.apply {
                if (!exists()) {
                    createNewFile()
                }
                this::class.declaredMemberFunctions.find { it.name == "generate" }?.apply {
                    isAccessible = true
                    call(this@updatePodspecFile)
                }
            }
            taskBuilder.withClosure { podBuilder ->
                    podBuilder.xcodeKotlinCheck(this)
                        .relinkGradle(project.projectDir, taskBuilder.podSpecDir).replace(
                            "spec.vendored_frameworks",
                            "spec.vendored_frameworks      = 'framework/${project.name}.framework'".prependIndent(
                                "\t"
                            )
                        )
                        // podspec中设置消除警告
                        .append(
                            "spec.ios.deployment_target",
                            "spec.compiler_flags = '-w'".prependIndent("\t")
                        ).replace(
                            "spec.resources = ['$composeResourcesRelativelyPath']",
//                            "".prependIndent("\t")
                            "spec.resources = ['framework/compose-resources']".prependIndent("\t")
                        )
                    val builder = CocoapodsAppender.Builder(rootDir.resolve("iosApp/Podfile"))
                    builder/*.rewriteSymroot(
                        project.layout.buildDirectory.get().asFile,
                        projectDir,
                        rollback = !taskBuilder.isBuildDirChanged
                    )*/.deploymentTarget(
                            this, target = iosDeploymentTarget, swiftTarget = "5.0"
                        ).inhibitAllWarnings().sharedPodRelink(
                            taskBuilder.podSpecDir,
                            !taskBuilder.isBuildDirChanged,
                            sharedModuleName = project.name
                        ).build()
                }.relinkPodspec(outputFile).build()
        }/*taskBuilder
            .withClosure { podBuilder ->
                podBuilder.xcodeKotlinCheck(podSpec)
                    .relinkGradle(project.projectDir,taskBuilder.podSpecDir)
            }
            .relinkPodspec(podSpec.outputFile)
            .build()*/
    }
}

fun Project.xcodeCheck(buildDirChanged: Boolean, configProperties: Properties) {
    getBuildRelativeDir().apply {
        val dir = this
        // 在这里获取.idea/workspace.xml文件,然后修改里面的配置
        try {
            val workspaceFile = rootDir.resolve(".idea/workspace.xml")
            if (workspaceFile.exists()) {
                workspaceFile.bufferedReader().useLines { lines ->
                    val content = lines.joinToString("\n")
                    // 使用 XML 解析器解析 XML 内容
                    val documentBuilderFactory = DocumentBuilderFactory.newInstance()
                    val documentBuilder = documentBuilderFactory.newDocumentBuilder()
                    val document = documentBuilder.parse(InputSource(StringReader(content)))
                    // 获取根节点
                    val rootElement = document.documentElement
                    // 遍历子节点，查找需要的节点
                    for (i in 0 until rootElement.childNodes.length) {
                        val childNode = rootElement.childNodes.item(i)
                        if (childNode is Element && childNode.tagName == "component" && childNode.getAttribute(
                                "name"
                            ) == "RunManager"
                        ) {
                            // 在 RunManager 节点下查找配置节点
                            val configurations = childNode.getElementsByTagName("configuration")
                            val configName = "iOS Application.iosApp"
                            val execTargetId = "00008110-00122DA63622801E"
                            val xcodeProject = "${rootDir.absolutePath}/iosApp/iosApp.xcworkspace"
                            // 根据现有配置进行判断和修改
                            if (configurations.length == 1) {
                                val node = configurations.item(0) as Element
                                if (node.getAttribute("XCODE_PROJECT")
                                        .contains(".xcworkspace") && node.getAttribute("XCODE_PROJECT") == xcodeProject
                                ) return@useLines
                                // 只有一个配置时，添加新的配置节点
                                val newConfigNode = document.createElement("configuration")
                                newConfigNode.setAttribute("name", configName)
                                newConfigNode.setAttribute("type", "KmmRunConfiguration")
                                newConfigNode.setAttribute("factoryName", "iOS Application")
                                newConfigNode.setAttribute("nameIsGenerated", "true")
                                newConfigNode.setAttribute("CONFIG_VERSION", "1")
                                newConfigNode.setAttribute("EXEC_TARGET_ID", execTargetId)
                                newConfigNode.setAttribute("XCODE_PROJECT", xcodeProject)
                                newConfigNode.setAttribute("XCODE_CONFIGURATION", "Debug")
                                newConfigNode.setAttribute("XCODE_SCHEME", "iosApp")
                                if (buildDirChanged) {
                                    newConfigNode.setAttribute("SYMROOT", "$dir/cocoapods/iosApp")
                                    newConfigNode.setAttribute("OBJROOT", "$dir/cocoapods/iosApp")
                                }
                                val methodNode = document.createElement("method")
                                methodNode.setAttribute("v", "2")
                                val optionNode = document.createElement("option")
                                optionNode.setAttribute(
                                    "name", "com.jetbrains.kmm.ios.BuildIOSAppTask"
                                )
                                optionNode.setAttribute("enabled", "true")
                                methodNode.appendChild(optionNode)
                                newConfigNode.appendChild(methodNode)
                                // 添加到 RunManager 节点下
                                childNode.appendChild(newConfigNode)
                                // 判断 list 节点是否存在，不存在则添加
                                if (childNode.getElementsByTagName("list").length == 0) {
                                    val listNode = document.createElement("list")
                                    val itemNode = document.createElement("item")
                                    itemNode.setAttribute("itemvalue", configName)
                                    listNode.appendChild(itemNode)
                                    childNode.appendChild(listNode)
                                } else {
                                    // 如果存在 list 节点，判断是否包含新配置，不包含则添加
                                    val listNode =
                                        childNode.getElementsByTagName("list").item(0) as Element
                                    val items = listNode.getElementsByTagName("item")
                                    var hasNewConfig = false
                                    for (j in 0 until items.length) {
                                        val item = items.item(j) as Element
                                        if (item.getAttribute("itemvalue") == configName) {
                                            hasNewConfig = true
                                            break
                                        }
                                    }
                                    if (!hasNewConfig) {
                                        val itemNode = document.createElement("item")
                                        itemNode.setAttribute("itemvalue", configName)
                                        listNode.appendChild(itemNode)
                                    }
                                }
                            } else {
                                // 多个配置时的逻辑
                                // 遍历 configurations 判断是否有 iOS 配置，进行修改或添加新配置
                                val oldIosConfigNodes = mutableListOf<Element>()
                                for (j in 0 until configurations.length) {
                                    val configNode = configurations.item(j) as Element
                                    if (configNode.getAttribute("name")
                                            .contains("iOS Application")
                                    ) {
                                        oldIosConfigNodes.add(configNode)
                                        break
                                    }
                                }
                                // 判断如果有iOS配置，并且XCODE_PROJECT是xcodeproj文件，则删除原有配置
                                if (oldIosConfigNodes.isNotEmpty()) {
                                    if (oldIosConfigNodes.firstNotNullOf {
                                            it.getAttribute("XCODE_PROJECT")
                                                .contains("iosApp.xcodeproj")
                                        }) {
                                        for (oldIosConfigNode in oldIosConfigNodes) {
                                            childNode.removeChild(oldIosConfigNode)
                                        }
                                    } else {
                                        if (oldIosConfigNodes.firstNotNullOf {
                                                it.getAttribute("XCODE_PROJECT")
                                                    .contains(".xcworkspace") && it.getAttribute("XCODE_PROJECT") == xcodeProject
                                            }) {
                                            return@useLines
                                        }
                                    }
                                }

                                // 删除原有配置并添加新的配置节点
                                for (oldIosConfigNode in oldIosConfigNodes) {
                                    childNode.removeChild(oldIosConfigNode)
                                }
                                // 同时删除 list 节点下的 iOS 配置
                                val listNode =
                                    if (childNode.getElementsByTagName("list").length == 0) {
                                        val ln = document.createElement("list")
                                        childNode.appendChild(ln)
                                        ln
                                    } else {
                                        val ln = childNode.getElementsByTagName("list")
                                            .item(0) as Element
                                        val items = ln.getElementsByTagName("item")
                                        for (j in 0 until items.length) {
                                            try {
                                                val item = (items.item(j) as? Element) ?: continue
                                                if (item.getAttribute("itemvalue")
                                                        .contains("iOS Application")
                                                ) {
                                                    ln.removeChild(item)
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        ln
                                    }

                                // 添加新的 iOS 配置
                                val newConfigNode = document.createElement("configuration")
                                newConfigNode.setAttribute("name", configName)
                                newConfigNode.setAttribute("type", "KmmRunConfiguration")
                                newConfigNode.setAttribute("factoryName", "iOS Application")
                                newConfigNode.setAttribute("nameIsGenerated", "true")
                                newConfigNode.setAttribute("CONFIG_VERSION", "1")
                                newConfigNode.setAttribute("EXEC_TARGET_ID", execTargetId)
                                newConfigNode.setAttribute("XCODE_PROJECT", xcodeProject)
                                newConfigNode.setAttribute("XCODE_CONFIGURATION", "Debug")
                                newConfigNode.setAttribute("XCODE_SCHEME", "iosApp")
                                if (buildDirChanged) {
                                    newConfigNode.setAttribute("SYMROOT", "$dir/cocoapods/iosApp")
                                    newConfigNode.setAttribute("OBJROOT", "$dir/cocoapods/iosApp")
                                }
                                val methodNode = document.createElement("method")
                                methodNode.setAttribute("v", "2")
                                val optionNode = document.createElement("option")
                                optionNode.setAttribute(
                                    "name", "com.jetbrains.kmm.ios.BuildIOSAppTask"
                                )
                                optionNode.setAttribute("enabled", "true")
                                methodNode.appendChild(optionNode)
                                newConfigNode.appendChild(methodNode)
                                childNode.appendChild(newConfigNode)
                                // 添加到 list 节点下
                                val itemNode = document.createElement("item")
                                itemNode.setAttribute("itemvalue", configName)
                                listNode.appendChild(itemNode)
                            }
                            break  // 遍历到第一个 RunManager 节点后退出循环
                        }
                    }
                    // 保存修改后的 XML 内容到文件中
                    val transformerFactory = TransformerFactory.newInstance()
                    val transformer = transformerFactory.newTransformer()
                    val source = DOMSource(document)
                    val result = StreamResult(workspaceFile)
                    transformer.transform(source, result)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }


        project.file("../iosApp/Configuration/Config.xcconfig").apply {
            updateXcodeConfigFile(
                // $(SRCROOT) xcode项目根路径
                // $(CONFIGURATION) Debug/Release
                // $(SDK_NAME) iphonesimulator17.2/iphoneos17.2
                {
                    if (buildDirChanged) "PODS_BUILD_DIR" to "$dir/cocoapods/iosApp"
                    else "PODS_BUILD_DIR" to ""
                },
                {
                    if (buildDirChanged) "SYMROOT" to "$dir/cocoapods/iosApp"
                    else "SYMROOT" to ""
                },
                {
                    if (buildDirChanged) "OBJROOT" to "$dir/cocoapods/iosApp"
                    else "OBJROOT" to ""
                },
                { "BUNDLE_ID" to configProperties.getProperty("BundleId") },
                { "TEAM_ID" to configProperties.getProperty("TeamId") },
                { "APP_NAME" to configProperties.getProperty("AppName") })
        }
    }
    processPlistFiles(project.projectDir.parentFile)
}


fun Task.syntheticXCodeprojsTarget(project: Project,iosDeploymentTarget:String) {
    doFirst {
        val xcodeprojFiles = listOf(
            "Pods/Pods.xcodeproj", "synthetic.xcodeproj"
        )
        val outside = project.layout.buildDirectory.asFile.get().resolve("cocoapods/synthetic/ios/")
        // outside 文件夹下包含Pods/Pods.xcodeproj和synthetic.xcodeproj
        // 替换文件中所有行中IPHONEOS_DEPLOYMENT_TARGET = xx.x;的值
        // 解析出rootPod中的platform版本号
        val platform = iosDeploymentTarget
        xcodeprojFiles.forEach { xcodeproj ->
            val file = outside.resolve(xcodeproj).resolve("project.pbxproj")
            if (file.exists()) {
                // project.pbxproj文件内容
                val origin = file.readText(charset = Charsets.UTF_8)
                // 使用 正则表达式替换
                val replace = origin.replace(
                    Regex("IPHONEOS_DEPLOYMENT_TARGET = [\\d.]+;"),
                    "IPHONEOS_DEPLOYMENT_TARGET = $platform;"
                )
                if (origin != replace) {
                    file.writeText(replace)
                }
            } else {
                throw RuntimeException("文件不存在 ${file.absolutePath}")
            }
        }
    }
}

fun Project.creatingIosNativePodspecExport(
    nativePath: String,
    podspecName: String,
    iosDeploymentTarget: String
) {
    val rootProject = file(nativePath)
    if (!rootProject.exists()) return
    val podspec = rootProject.resolve("$podspecName.podspec")
    val podfile = rootProject.resolve("Podfile")
    val modules = rootProject.resolve("Modules")
    // 生成rootProject目录下的Modules文件夹
    if (!modules.exists()) {
        modules.mkdirs()
    }
    //因为原有项目中已经放了Podfile文件,所以这里要把Podfile文件拿出来解析后,写入Podfile的依赖到当前podspec中
    // 1.解析Podfile文件的所有依赖
    val dependencies = mutableMapOf<String, String>()
    val sources = arrayListOf<String>()
    if (podfile.exists()) {
        val podfileContent = podfile.useLines {
            // 忽略掉空行,忽略掉注释行,每行可能都存在大量空格,不能简单只是判断#号开头
            it.filter { line ->
                line.replace(" ", "").isNotBlank() && !line.replace(" ", "").startsWith("#")
            }.joinToString("\n")
        }
        val regex =
            """pod\s+(['"])([^'"]+)\1\s*(?:,\s*['"]([^'"]+)['"])?(?:\s*,\s*(?::git|:path)\s*=>\s*(?:['"])([^'"]+)(?:['"]))?\s*(?:#.*)?"""
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(podfileContent)

        val regexSourceType = """pod\s+['"]([^'"]+)['"][^']*:(git|path)\s*=>\s*['"]([^'"]+)['"]"""
        val patternSourceType = Pattern.compile(regexSourceType)
        val matcherSourceType = patternSourceType.matcher(podfileContent)

        var index = 0
        while (matcher.find()) {
            val name = matcher.group(2).removeSurrounding(" ")
            val versionOrUrl = (when {
                matcher.groupCount() > 3 -> {// 直接版本号或者URL
                    matcher.group(3)?.removeSurrounding(" ")?.trim() ?: ""
                }

                matcher.groupCount() > 4 -> {// 直接版本号或者URL
                    matcher.group(4)?.removeSurrounding(" ")?.trim() ?: ""
                }

                matcher.groupCount() > 5 -> { // 版本范围
                    matcher.group(5)?.removeSurrounding(" ")?.trim() ?: ""
                }

                matcher.groupCount() > 6 -> { // URL
                    ":${matcher.group(6)?.removeSurrounding(" ")?.trim() ?: ""}"
                }

                else -> ""
            }).removeSurrounding(" ")
            //  mother fucker , isBlank can't use ????
            if (!versionOrUrl.isNotBlank()) {
                matcherSourceType.find(index)
                val sourceType = matcherSourceType.group(2)?.removeSurrounding(" ")?.trim() ?: ""
                when (sourceType) {
                    "git", "path" -> {
                        // 这里不需要判断,因为在podspec中不支持写git和path
                        /*versionOrUrl = ":$sourceType=> \"${matcherSourceType.group(3)?.removeSurrounding(" ")?.trim()?:""}\"".apply {
                            println("versionOrUrl:$this======")

                        }*/
                    }

                    else -> Unit
                }
            }
            index++
            dependencies[name] = versionOrUrl
        }
        val patternSource = Pattern.compile("source\\s*'(.+)'")
        val matcherSource = patternSource.matcher(podfileContent)
        if (matcherSource.find()) {
            val group = matcherSource.group(1)
            sources.add(group)
        }
    }
    // 2.先判断podspec文件是否存在,不存在就创建一个
    if (!podspec.exists()) {
        podspec.createNewFile()
    }
    // 特殊的依赖在这里定义,比如下面这个cipher的版本号
    val forceReplaceDependencies = mapOf(
        "WCDBOptimizedSQLCipher" to "1.4.2",
    )
    // 3.将platform,还有依赖写入到podspec文件中
    val pchImports =
        rootProject.resolve("$podspecName/UoocOnline.pch").bufferedReader().useLines { lines ->
            lines.mapNotNull {
                val line = it.trim()
                if (line.startsWith("#import") && !line.contains("#import \"$podspecName-Swift.h\"")
                // && !line.contains("#import \"AppDelegate.h\"")
                ) {
                    "\t" + line/*.replace("#import", "@import")
                    .replace("/", ".")
                    .replace("\"", "")
                    .replace("<", "")
                    .replace(">", "")
                    .replace(".h", "")*/
                } else {
                    null
                }
            }.toList()
                // 排序,HNNetworking.h必须在最前面
                .sortedBy {
                    when {
                        it.contains("HNNetworking.h") -> 0
                        it.contains("UoocOnline-Bridging-Header.h") -> 1
                        else -> 2
                    }
                }.joinToString("\n")
        }.trimIndent()
    podspec.writeText(
        specGenerator(
            podspec, iosDeploymentTarget, podspecName, pchImports, dependencies, forceReplaceDependencies
        )
    )
    // 再生成一个LICENSE文件
    val license = rootProject.resolve("LICENSE")
    if (!license.exists()) {
        license.createNewFile()
    }
    // 写入MIT完整版
    license.writeText(
        """
         MIT License
         Copyright (c) 2024 Uooc Group
         Permission is hereby granted, free of charge, to any person obtaining a copy
         of this software and associated documentation files (the "Software"), to deal
         in the Software without restriction, including without limitation the rights
         to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
         copies of the Software, and to permit persons to whom the Software is
         furnished to do so, subject to the following conditions:
         The above copyright notice and this permission notice shall be included in all
         copies or substantial portions of the Software.
         THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
         IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
         FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
         AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
         LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
         OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
         SOFTWARE.
        """.trimIndent()
    )
    // modules文件夹下创建一个modulemap文件
    val modulemap = modules.resolve("$podspecName-rewrite.modulemap")
    if (!modulemap.exists()) {
        modulemap.createNewFile()
    }
    modulemap.writeText(
        """
        framework module $podspecName {
            umbrella header "$podspecName-rewrite-umbrella.h"
            export *
            module * { export * }
        }
        """.trimIndent()
    )
    // 创建一个umbrella头文件
    val umbrella = modules.resolve("$podspecName-rewrite-umbrella.h")
    if (!umbrella.exists()) {
        umbrella.createNewFile()
    }
    val umbrellaHeader = mutableListOf<String>()
    // 先查找rootProject中的${podspecName}/AllClassFiles文件夹下的${podspecName}-Bridging-Header.h 文件
    rootProject.resolve("$podspecName/AllClassFiles").walk().forEach { it ->
        if (it.name == "${podspecName}-Bridging-Header.h") {
            it.useLines { line ->
                line.toList().mapNotNull {
                    //判断是#开头的就是import
                    val lineShort =it.removeSurrounding(" ").removeSurrounding("\t")
                    if (lineShort.startsWith("#")) {
                        lineShort
                    } else {
                        null
                    }
                }.forEach {
                        umbrellaHeader.add(it)
                }
            }
        }
    }

    val bridgingHeader =
        rootProject.resolve("$podspecName/AllClassFiles/**/${podspecName}-Bridging-Header.h")
    if (bridgingHeader.exists()) {
        // 读取文件内容
        bridgingHeader.useLines { line ->
            line.toList().mapNotNull {
                //判断是#开头的就是import
                if (it.removeSurrounding(" ").startsWith("#")) {
                    it
                } else {
                    null
                }
            }.forEach {
                    umbrellaHeader.add(it)
                }
        }
    }
    // 生成一个空的umbrella头文件
    umbrella.writeText(
        """
#ifdef __OBJC__
    #import <UIKit/UIKit.h>
#else
    #ifndef FOUNDATION_EXPORT
        #if defined(__cplusplus)
            #define FOUNDATION_EXPORT extern "C"
        #else
            #define FOUNDATION_EXPORT extern
        #endif
    #endif
#endif
${"\n" + umbrellaHeader.joinToString("\n")}
FOUNDATION_EXPORT double ${podspecName}VersionNumber;
FOUNDATION_EXPORT const unsigned char ${podspecName}VersionString[];
    """.trimIndent()
    )

    // 最后生成一个${podspecName}.h文件
    val podHeader = modules.resolve("$podspecName.h")
    if (!podHeader.exists()) {
        podHeader.createNewFile()
    }
    podHeader.writeText(
        """
        //auto-generate by gradle script
        #define ${podspecName}VersionNumber 1.0.0
        """.trimIndent()
    )
}

fun Task.syntheticPodfileGen(podfile: File) {
    doLast {
        val builder = CocoapodsAppender.Builder(podfile)
        // modify dependencies deployment target
        builder.deploymentTarget(this, swiftTarget = "5.0").openStaticLinkage().inhibitAllWarnings()
            .build()
    }
}

fun toPodsVersion(
    forceReplaceDependencies: Map<String, String>, it: Map.Entry<String, String>
) = if (forceReplaceDependencies.containsKey(it.key)) {
    ", '${forceReplaceDependencies[it.key]}'"
} else {
    //  mother fucker , isBlank can't use ????
    if (!it.value.isNotBlank()) {
        ""
    } else {
        when {
            it.value.startsWith(":git") -> {
                ", ${it.value.replace(" ", "")}"
            }

            it.value.startsWith(":path") -> {
                ", ${it.value.replace(" ", "")}"
            }

            else -> {
                ", '${it.value.replace(" ", "")}'"
            }
        }
    }
}

fun specGenerator(
    podspec: File,
    platform: String,
    podspecName: String,
    pchImports: String,
    dependencies: MutableMap<String, String>,
    forceReplaceDependencies: Map<String, String>
) = """Pod::Spec.new do |s|
          s.name = '${podspec.nameWithoutExtension}'
          s.version = '1.0.0'
          s.summary = '#{s.name}'
          s.homepage = 'http://www.example.com'
          s.license = { :type => 'MIT', :file => 'LICENSE' }
          s.author = { 'Uooc' => 'vicky leu' }
          s.source  = { :path=> '.'}
          s.ios.deployment_target    = '${platform}'
          s.static_framework = false
          # 源代码,所有
          s.source_files = [
            '$podspecName/AllClassFiles/**/*.{h,m,swift}',
            '$podspecName/PLV/**/*.{h,m,swift}',
            'Modules/*.{h}',
            '$podspecName/AppDelegate.{h,m}',
          ]
          s.public_header_files = [
            '$podspecName/AllClassFiles/Common/**/*{Controller.h}',
            '$podspecName/PLV/**/*{Controller.h}',
            '$podspecName/AllClassFiles/Network/*.{h}',
            'Modules/*.{h}',
            '$podspecName/AllClassFiles/**/UOCResetBaseURLObject.h',
            '$podspecName/AllClassFiles/**/UOCCustomNavView.h',
            '$podspecName/AllClassFiles/**/UOCNoDataView.h',
            '$podspecName/AllClassFiles/**/UIView+Toast.h',
            '$podspecName/AppDelegate.{h}',
          ]
          # 禁止所有警告
          s.compiler_flags = '-w'
          
          s.header_dir = "$podspecName"
          s.preserve_paths = [
                'Modules/*.h',
                'Modules/*.modulemap'
          ]
          # 排除文件
          s.exclude_files = [
            '$podspecName/*.{plist}',
            '$podspecName/**/$podspecName-Bridging-Header.h',
          ]
          s.module_map = "Modules/$podspecName-rewrite.modulemap"
          # 图片资源
          s.resource_bundles = {
            '$podspecName' => ${
    listOf(
        "*.{xcassets}",
        "Lottie*/**/*.{json,png}",
        "*.{lproj}",
        "*.{html,mp3}",
        "Plv/**/Resources/*.{bundle}"
    ).map { "'$podspecName/${it}'" }
}
          }
          s.requires_arc            = true
          # 公共头文件导进组件.pch文件中
          s.prefix_header_contents =  <<-PCH
                #ifndef PCH_FILE_IMPORTED_SUCCESSFULLY
                    #define PCH_FILE_IMPORTED_SUCCESSFULLY
                #endif
                
                #ifndef ISDEBUG
                    #define ISDEBUG NO
                #endif
                
                #import "UOCBaseViewController.h"
                #if __has_include(<$podspecName/$podspecName-Swift.h>)
                    #import <$podspecName/$podspecName-Swift.h>
                #else
                    #import "$podspecName-Swift.h"    
                #endif
                ${"\n" + pchImports.prependIndent("\t\t\t")}
          PCH
          s.pod_target_xcconfig = { 'GCC_PREPROCESSOR_DEFINITIONS' => '${'$'}(inherited)',
            "BUILD_LIBRARY_FOR_DISTRIBUTION" => "YES",
            "DEFINES_MODULE" => "YES",
            "SWIFT_INCLUDE_PATHS" => "${'$'}(SRCROOT)/$podspecName/Modules"
          }
          # don't use header_mappings_dir,this will cause the header file to be copied to the root directory of the project
          # https://github.com/CocoaPods/CocoaPods/issues/11145
          # s.header_mappings_dir = "Modules"
       
          # 依赖系统Frameworks
          s.ios.frameworks = 'Foundation', 'UIKit', 'CoreFoundation', 'CoreTelephony', 'QuartzCore', 'CoreData'
          # 依赖系统动态.tdb
          s.libraries = 'z', 'sqlite3', 'c++'
          s.swift_version = '5.0'
          # 依赖
          ${
    dependencies.map {
        "s.dependency '${it.key}' ${toPodsVersion(forceReplaceDependencies, it)}"
    }.joinToString("\n          ")
}
end""".trimIndent()

// 更新Xcode配置文件
fun File.updateXcodeConfigFile(vararg callable: Map<String, String>.() -> Pair<String, String>?) {
    val xcconfigProperties = mutableMapOf<String, String>()
    val properties = Properties()
    var isChanged = false
    if (exists()) {
        reader(Charsets.UTF_8).use { reader ->
            properties.load(reader)
            properties.forEach { (key, value) ->
                if (key.toString().isNotBlank()) {
                    xcconfigProperties[key.toString()] = value.toString()
                }
            }
        }
    } else {
        createNewFile()
    }
    callable.mapNotNull { it(xcconfigProperties) }.forEach {
        val (key, value) = it
        if (xcconfigProperties[key] != value) {
            xcconfigProperties[key] = value
            isChanged = true
        }
    }
    if (xcconfigProperties.keys.isNotEmpty() && isChanged) {
        bufferedWriter(Charsets.UTF_8).use { writer ->
            writeProperties(writer, xcconfigProperties, null)
        }
    }
}

// 写入属性文件
@Suppress("SameParameterValue")
private fun writeProperties(
    writer: BufferedWriter, properties: Map<String, String>, comments: String? = null
) {
    comments?.let {
        writer.write("# $it")
        writer.newLine()
    }
    properties.asSequence().sortedBy { it.key }.toList().onEachIndexed { index, entry ->
            writer.write("${entry.key}=${entry.value}")
            if (index < properties.size - 1) {
                writer.newLine()
            }
        }
    writer.flush()
}

private fun queryPlistFilesAndModuleName(dir: File): Pair<String, String>? {
    val listFiles = dir.listFiles()
    listFiles?.forEach {
        if (it.isDirectory) {
            if (it.name.endsWith(".xcodeproj")) {
                val pbxproj = it.resolve("project.pbxproj")
                if (pbxproj.exists()) {
                    return Pair(it.nameWithoutExtension, pbxproj.absolutePath)
                }
            } else {
                val pair = queryPlistFilesAndModuleName(it)
                if (pair != null) {
                    return pair
                }
            }
        }
    }
    return null
}

//需要检查所有的plist文件,UILaunchStoryboardName是否存在,如果不存在就要添加一个UILaunchStoryboardName的空key,如果是存在其他Launch设置就忽略
// 因为iOS设备没有设置启动屏幕的话,屏幕尺寸是错误的,所以需要检查一下,如果已经设置了就不需要再设置了
fun processPlistFiles(dir: File) {
    val pair = queryPlistFilesAndModuleName(dir) ?: return
    val moduleName = pair.first
    val pbxproj = File(pair.second)
    if (pbxproj.exists()) {
        val plists = getAllPlistFiles(pbxproj)
        plists.filter { it == "Pods" }.forEach {
            val plist = File(dir, "$moduleName/${it}")
//            throw RuntimeException("moduleName:$moduleName it:$it")
            if (plist.exists()) {
                val originContent = plist.readText()
                var content = originContent
                // <key>UILaunchStoryboardName</key> 可能是<key>UILaunchStoryboardName*</key>的形式,所以需要使用正则表达式来匹配,
                // content直接contains的话有可能其他value里写了key,所以要移除掉
                // 空格,然后再匹配,避免有错误的匹配
                if (content.replace(" ", "").contains("<key>UILaunchStoryboardName</key>")) {
                    return
                }
                // UILaunchScreen,UILaunchImageName,UILaunchImages 都需要判断所对应的内容是否是空的,不能只判断key是否存在,因为key存在,但是value
                // 是空的话屏幕依然不会生效的,只有UILaunchStoryboardName空的可以自动计算出屏幕尺寸
                if (content.replace(" ", "").contains("<key>UILaunchScreen</key>")) {
                    // 正则判断内容是否是空的
                    val pattern =
                        Pattern.compile("<key>UILaunchScreen</key>[\\s\\S]*?<string>(.*?)</string>")
                    val matcher = pattern.matcher(content)
                    if (matcher.find()) {
                        val group = matcher.group(1)
                        if (group.isNotBlank()) {
                            return
                        }
                    }
                    // 如果走到这里,说明放了个空的key,那么就需要删除掉,删除的时候还是要用正则表达式,因为可能有空格
                    val patternDelete =
                        Pattern.compile("<key>UILaunchScreen</key>[\\s\\S]*?<string>(.*?)</string>")
                    val matcherDelete = patternDelete.matcher(content)
                    if (matcherDelete.find()) {
                        val group = matcherDelete.group()
                        val newContent = content.replace(group, "")
                        content = newContent
                    }
                }
                if (content.replace(" ", "").contains("<key>UILaunchImageName</key>")) {
                    val pattern =
                        Pattern.compile("<key>UILaunchImageName</key>[\\s\\S]*?<string>(.*?)</string>")
                    val matcher = pattern.matcher(content)
                    if (matcher.find()) {
                        val group = matcher.group(1)
                        if (group.isNotBlank()) {
                            return
                        }
                    }
                    val patternDelete =
                        Pattern.compile("<key>UILaunchImageName</key>[\\s\\S]*?<string>(.*?)</string>")
                    val matcherDelete = patternDelete.matcher(content)
                    if (matcherDelete.find()) {
                        val group = matcherDelete.group()
                        val newContent = content.replace(group, "")
                        content = newContent
                    }
                }
                if (content.replace(" ", "").contains("<key>UILaunchImages</key>")) {
                    // UILaunchImages的内容是数组,所以需要判断是否是空的,不是空的话就不需要处理
                    val pattern =
                        Pattern.compile("<key>UILaunchImages</key>[\\s\\S]*?<array>(.*?)</array>")
                    val matcher = pattern.matcher(content)
                    if (matcher.find()) {
                        val group = matcher.group(1)
                        if (group.isNotBlank()) {
                            println("UILaunchImages太复杂,无法判断,请手动处理")
                            return
                        }
                    }
                    val patternDelete =
                        Pattern.compile("<key>UILaunchImages</key>[\\s\\S]*?<array>(.*?)</array>")
                    val matcherDelete = patternDelete.matcher(content)
                    if (matcherDelete.find()) {
                        val group = matcherDelete.group()
                        val newContent = content.replace(group, "")
                        content = newContent
                    }
                }

                val patternSbn = Pattern.compile("<key>UILaunchStoryboardName[\\s\\S]*?</key>")
                val matcherSbn = patternSbn.matcher(content)
                if (matcherSbn.find()) {
                    return
                }
                val pattern = Pattern.compile("<plist version=\"1.0\">[\\s\\S]*?<dict>")
                val matcher = pattern.matcher(content)
                if (matcher.find()) {
                    val group = matcher.group()
                    val newContent = content.replace(
                        group,
                        "$group\n    <key>UILaunchStoryboardName</key>\n    <string></string>"
                    )
                    content = newContent
                }
                if (originContent != content) {
                    plist.writeText(content)
                }
            } else {
                throw RuntimeException("$moduleName/${it} file not found")
            }
        }
    }
}

private fun getAllPlistFiles(pbxproj: File): List<String> {
    val content = pbxproj.readText()
    val pattern =
        Pattern.compile("isa = XCBuildConfiguration;[\\s\\S]*?INFOPLIST_FILE = (.*?);[\\s\\S]*?name = (.*?);")
    val matcher = pattern.matcher(content)
    val result = mutableMapOf<String, String>()
    while (matcher.find()) {
        //plist需要取出前后的引号,必须是前后,不能是中间
        val plist = matcher.group(1).trim('\"').trim('\'')
        val name = matcher.group(2).trim('\"').trim('\'')
        if (!result.containsKey(name)) {
            val value = if (plist.contains("\$(CONFIGURATION)")) {
                plist.replace("\$(CONFIGURATION)", name)
            } else {
                plist
            }
            result[name] = value
        }
    }
    // 最后做个检查,如果value中有相同的值,就保留一个,没有则全部保留
    val map = mutableMapOf<String, String>()
    result.forEach { (key, value) ->
        if (!map.containsValue(value)) {
            map[key] = value
        }
    }
    return map.values.toList()
}