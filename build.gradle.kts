import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Path
import java.security.MessageDigest

plugins {
    `java-library`
    `maven-publish`
    signing
    checkstyle
    pmd
    id("com.diffplug.spotless") version "8.4.0"
    id("com.github.spotbugs") version "6.5.4"
}

allprojects {
    group = "org.grimmory"
    version = "1.1.0"

    repositories {
        mavenCentral()
    }
}

configure<CheckstyleExtension> {
    toolVersion = "13.4.2"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    isShowViolations = true
}

configure<PmdExtension> {
    toolVersion = "7.23.0"
    isConsoleOutput = true
    rulesMinimumPriority.set(5)
    ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
    ruleSets = emptyList()
}

val enableCorpusTools = providers.gradleProperty("enableCorpusTools")
    .map { value ->
        when (value.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw GradleException("enableCorpusTools must be 'true' or 'false'")
        }
    }
    .orElse(false)

val corpusToolTestSources = listOf(
    "org/grimmory/pdfium4j/CorpusMetadataStressRunner.java",
    "org/grimmory/pdfium4j/CorpusProcessor.java",
    "org/grimmory/pdfium4j/PdfBoxCorpusGenerator.java"
)
val corpusToolTestSourcePaths = corpusToolTestSources.map { "src/test/java/$it" }

extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension>("spotless") {
    format("misc") {
        target("*.md", "*.kts", "*.gradle.kts", "**/*.yml", "**/*.yaml", "**/.gitignore")
        targetExclude("**/build/**", "**/.gradle/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
    java {
        target("src/*/java/**/*.java")
        targetExclude("**/build/**")
        if (!enableCorpusTools.get()) {
            targetExclude(corpusToolTestSourcePaths)
        }
        googleJavaFormat("1.35.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
    reports.create("html") {
        required.set(true)
    }
    reports.create("xml") {
        required.set(true)
    }
}

tasks.withType<Checkstyle>().configureEach {
    exclude("**/internal/*Bindings.java")
}

tasks.withType<Pmd>().configureEach {
    exclude("**/internal/*Bindings.java")
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withJavadocJar()
    withSourcesJar()
}

if (!enableCorpusTools.get()) {
    sourceSets.named("test") {
        java {
            corpusToolTestSources.forEach { exclude(it) }
        }
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf(
        "--enable-preview"
    ))
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}

// -- PDFium native binary download & bundling --

val pdfiumVersion = findProperty("pdfiumVersion")?.toString() ?: "7834"

val pdfiumPlatforms = mapOf(
    "linux-x64"        to "linux-x64",
    "linux-arm64"      to "linux-arm64",
    "linux-musl-x64"   to "linux-musl-x64",
    "linux-musl-arm64" to "linux-musl-arm64",
    "darwin-x64"       to "mac-x64",
    "darwin-arm64"     to "mac-arm64",
    "windows-x64"      to "win-x64"
)

val platformFilter = findProperty("pdfiumPlatformFilter")?.toString()
val activePlatforms = if (platformFilter != null) {
    pdfiumPlatforms.filterKeys { it == platformFilter }
} else {
    pdfiumPlatforms
}

// Optional directory containing pre-built shim artifacts (one sub-dir per platform named native-dir-<platform>).
// When set, buildShim will copy shims from here instead of compiling for non-host-compatible platforms.
val prebuiltShimsDir = findProperty("prebuiltShimsDir")?.toString()?.let { project.file(it) }

val pdfiumArchiveDir = layout.buildDirectory.dir("pdfium-archives")
val pdfiumNativesDir = layout.buildDirectory.dir("generated-natives")

val hostOs = System.getProperty("os.name").lowercase()
val hostArch = System.getProperty("os.arch").lowercase()
val hostPlatform = when {
    hostOs.contains("mac") && (hostArch == "aarch64" || hostArch == "arm64") -> "darwin-arm64"
    hostOs.contains("mac") && (hostArch == "x86_64" || hostArch == "amd64") -> "darwin-x64"
    hostOs.contains("linux") && (hostArch == "aarch64" || hostArch == "arm64") -> "linux-arm64"
    hostOs.contains("linux") && (hostArch == "x86_64" || hostArch == "amd64") -> "linux-x64"
    hostOs.contains("windows") && (hostArch == "x86_64" || hostArch == "amd64") -> "windows-x64"
    else -> null
}

val pdfiumHashes = mapOf(
    "7825" to mapOf(
        "linux-x64"        to "ae0e276bcdf276dca2746adb4780f79949620e5c655973ca252a3994bc516a13",
        "linux-arm64"      to "b063f5244586f5e0c025cd4d74dd10f75bbb41e28bcdc1032349ca27814a06cf",
        "linux-musl-x64"   to "c0d70bea47c93b055d6a9334c248f6c7957df130c5478fc0277c55ee98ade4bb",
        "linux-musl-arm64" to "5e4cc22df55498cb2f094f479869a66575b317bbc0e35d633c1e7e99b783f3d3",
        "mac-x64"          to "1e2f0a38bd7a8c369b0a1655a527c6b5491086fe3a45d1d82432e9229ac9b40c",
        "mac-arm64"        to "0e9692fa2063f5b5e6f6129680fe618f47efb9d728dd02e9db9b8999e386c84e",
        "win-x64"          to "eefb48c845ab22f0945151093ce8fd611a33687796728051f9a1b2b341e1b980"
    ),
    "7834" to mapOf(
        "linux-x64"        to "e10b18234af3e988b3021547786e574b8905a24511067f14773f29c9cac12365",
        "linux-arm64"      to "5381c1e7436dc6811ba86f4444fdcaccadd90fdb2a06f12ee81bfba96689ee36",
        "linux-musl-x64"   to "b6c5c8f0ff24fc09bf19f3572620294938bd4a35efd97630bec1669984a407c3",
        "linux-musl-arm64" to "1737a6f0d26f16ec46bb82ad4a31cfaf92ba7709686121306be4d0245f867d20",
        "mac-x64"          to "fcfed5eaf8fe9a761577d626dff651227600a52fc5f933c461447564361bb036",
        "mac-arm64"        to "2b733774416de02482281c0abc7589b08dc908896ecef2bfc31a85c5b5ffd572",
        "win-x64"          to "0abfacf8aacc919f98eff2c3efa2927c3dc9faf07e31f22558a1f1cf93809612"
    )
)

val downloadPdfiumBinaries by tasks.registering {
    description = "Downloads prebuilt PDFium binaries for all supported platforms"
    outputs.dir(pdfiumArchiveDir)
    doLast {
        val dir = pdfiumArchiveDir.get().asFile
        dir.mkdirs()

        val hashesForVersion = pdfiumHashes[pdfiumVersion]
            ?: error("No hashes defined for PDFium version $pdfiumVersion. Please update pdfiumHashes in build.gradle.kts")

        // Security check: ensure all supported platforms have hashes for this version
        val missingHashes = pdfiumPlatforms.values.toSet() - hashesForVersion.keys
        if (missingHashes.isNotEmpty()) {
            error("Missing hashes for version $pdfiumVersion: $missingHashes")
        }

        val base = "https://github.com/bblanchon/pdfium-binaries/releases/download/chromium/$pdfiumVersion"
        activePlatforms.forEach { (localName, remoteName) ->
            val target = dir.resolve("pdfium-$remoteName.tgz")
            val expectedHash = hashesForVersion[remoteName] ?: error("No hash for platform $remoteName")

            if (target.exists()) {
                val actualHash = calculateSha256(target)
                if (actualHash == expectedHash) {
                    logger.lifecycle("Skipping $remoteName (already downloaded and verified)")
                    return@forEach
                } else {
                    logger.warn("Hash mismatch for existing $remoteName; redownloading...")
                    target.delete()
                }
            }

            val conn = URI("$base/pdfium-$remoteName.tgz").toURL()
                .openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connect()
            check(conn.responseCode == 200) {
                "Download failed: HTTP ${conn.responseCode} for pdfium-$remoteName.tgz"
            }
            conn.inputStream.use { inp ->
                target.outputStream().buffered().use { out -> inp.copyTo(out) }
            }

            val actualHash = calculateSha256(target)
            if (actualHash != expectedHash) {
                target.delete()
                error("SHA-256 verification failed for pdfium-$remoteName.tgz!\nExpected: $expectedHash\nActual:   $actualHash")
            }
            logger.lifecycle("Verified pdfium-$remoteName.tgz")
        }
    }
}

fun calculateSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { inp ->
        val buffer = ByteArray(8192)
        var read: Int
        while (inp.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val extractPdfiumBinaries by tasks.registering {
    description = "Extracts PDFium native libraries for bundling into the JAR"
    dependsOn(downloadPdfiumBinaries)
    inputs.property("activePlatforms", activePlatforms.keys)
    inputs.property("zlibRoot", System.getenv("ZLIB_ROOT") ?: "")
    inputs.property("jpegRoot", System.getenv("JPEG_ROOT") ?: "")
    outputs.dir(pdfiumNativesDir)
    val proj = project
    doLast {
        val nativesRoot = pdfiumNativesDir.get().asFile.resolve("natives")
        activePlatforms.forEach { (localName, remoteName) ->
            val archive = pdfiumArchiveDir.get().asFile.resolve("pdfium-$remoteName.tgz")
            val platformDir = nativesRoot.resolve(localName)
            if (platformDir.exists()) {
                platformDir.deleteRecursively()
            }
            platformDir.mkdirs()

            // Copy PDFium binaries
            proj.copy {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                from(proj.tarTree(proj.resources.gzip(archive))) {
                    include("**/*pdfium*.so*", "**/*pdfium*.dylib*", "**/*pdfium*.dll", "**/*pdfium*.lib")
                    eachFile { relativePath = RelativePath(true, name) }
                }
                into(platformDir)
                includeEmptyDirs = false
            }

            // Copy extra dependencies if roots are provided (CI environment)
            val zlibRoot = System.getenv("ZLIB_ROOT")
            val jpegRoot = System.getenv("JPEG_ROOT")

            listOfNotNull(zlibRoot, jpegRoot).map { proj.file(it) }.filter { it.exists() }.forEach { root ->
                proj.copy {
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                    from(root) {
                        include("bin/*.dll", "lib/*.so*", "lib/*.dylib*")
                        eachFile { relativePath = RelativePath(true, name) }
                    }
                    into(platformDir)
                    includeEmptyDirs = false
                }
            }
        }
    }
}

val pdfiumHeadersDir = layout.buildDirectory.dir("pdfium-headers")

val extractPdfiumHeaders by tasks.registering {
    description = "Extracts PDFium headers for building the shim"
    dependsOn(downloadPdfiumBinaries)
    inputs.property("activePlatforms", activePlatforms.keys)
    outputs.dir(pdfiumHeadersDir)
    val proj = project
    doLast {
        val headersDir = pdfiumHeadersDir.get().asFile
        headersDir.deleteRecursively()
        headersDir.mkdirs()
        val platform = activePlatforms.values.first()
        val archive = pdfiumArchiveDir.get().asFile.resolve("pdfium-$platform.tgz")
        proj.copy {
            from(proj.tarTree(proj.resources.gzip(archive))) {
                include("**/include/**/*.h")
                eachFile {
                    // Preserve directory structure after 'include/'
                    val includeIdx = relativePath.segments.indexOf("include")
                    if (includeIdx != -1) {
                        val newSegments = relativePath.segments.drop(includeIdx + 1).toTypedArray()
                        relativePath = RelativePath(true, *newSegments)
                    } else {
                        relativePath = RelativePath(true, name)
                    }
                }
            }
            into(headersDir)
            includeEmptyDirs = false
        }
    }
}

val buildShim by tasks.registering {
    description = "Builds the C++ shim library"
    dependsOn(extractPdfiumHeaders, extractPdfiumBinaries)

    val shimDir = project.file("shim")
    val buildDir = layout.buildDirectory.dir("shim-build")
    val proj = project

    inputs.dir(shimDir)
    inputs.property("activePlatforms", activePlatforms.keys)
    outputs.dir(buildDir)
    outputs.upToDateWhen { false }

    doLast {
        val hostIsArm64 = hostArch == "aarch64" || hostArch == "arm64"
        val hostIsX64 = hostArch == "x86_64" || hostArch == "amd64"

        activePlatforms.keys.forEach { platform ->
            val platformIsArm64 = platform.endsWith("arm64")
            val platformIsX64 = platform.endsWith("x64")
            val libDir = pdfiumNativesDir.get().asFile.resolve("natives/$platform")
            val shimLibName = when {
                platform.startsWith("linux")   -> "pdfium4j_shim.so"
                platform.startsWith("darwin")  -> "pdfium4j_shim.dylib"
                platform.startsWith("windows") -> "pdfium4j_shim.dll"
                else -> error("Unknown platform: $platform")
            }

            val isHostCompatible = when {
                platform.startsWith("darwin") && hostOs.contains("mac") ->
                    (hostIsArm64 && platformIsArm64) || (hostIsX64 && platformIsX64)
                platform.startsWith("linux") && hostOs.contains("linux") ->
                    (hostIsArm64 && platformIsArm64) || (hostIsX64 && platformIsX64)
                platform.startsWith("windows") && hostOs.contains("windows") ->
                    hostIsX64 && platformIsX64
                else -> false
            }

            if (!isHostCompatible) {
                val prebuilt = prebuiltShimsDir
                if (prebuilt != null) {
                    val shimFile = prebuilt.resolve("native-dir-$platform/$shimLibName")
                    if (shimFile.exists()) {
                        logger.lifecycle("[$platform] Restoring pre-built shim from ${shimFile.absolutePath}")
                        proj.copy { from(shimFile); into(libDir) }
                    } else {
                        logger.warn("[$platform] Pre-built shim not found at ${shimFile.absolutePath}; skipping")
                    }
                } else {
                    logger.warn("[$platform] Platform not compatible with host OS $hostOs; skipping shim build")
                }
                return@forEach
            }
            val platformBuildDir = buildDir.get().asFile.resolve(platform)
            platformBuildDir.mkdirs()

            val pdfiumRoot = layout.buildDirectory.dir("pdfium-env-$platform").get().asFile
            pdfiumRoot.mkdirs()

            proj.copy {
                from(pdfiumHeadersDir) { into("include") }
                from(libDir) {
                    if (platform.startsWith("windows")) {
                        into("bin")
                    } else {
                        into("lib")
                    }
                }
                into(pdfiumRoot)
            }

            val isWindows = platform.startsWith("windows")
            val cmakeConfigCmd = mutableListOf("cmake", "-S", shimDir.absolutePath, "-B", ".", "-DPDFIUM_ROOT=${pdfiumRoot.absolutePath}")

            if (!isWindows) {
                cmakeConfigCmd.addAll(listOf("-G", "Unix Makefiles"))
            }

            if (platform.startsWith("darwin")) {
                val arch = if (platform.endsWith("arm64")) "arm64" else "x86_64"
                cmakeConfigCmd.add("-DCMAKE_OSX_ARCHITECTURES=$arch")
            }

            fun runCommand(cmd: List<String>, stage: String) {
                logger.lifecycle("[$platform] Executing: ${cmd.joinToString(" ")}")
                val process = ProcessBuilder(cmd)
                    .directory(platformBuildDir)
                    .redirectErrorStream(true)
                    .start()

                val output = process.inputStream.bufferedReader().readText()
                if (process.waitFor() != 0) {
                    logger.error("[$platform] $stage output:\n$output")
                    error("[$platform] $stage failed. Check the logs above for details.")
                }
            }

            runCommand(cmakeConfigCmd, "CMake configuration")
            runCommand(listOf("cmake", "--build", ".", "--config", "Release"), "CMake build")

            val builtLib = platformBuildDir.walkTopDown().find { it.name == shimLibName }
                ?: error("Native shim NOT FOUND after build: $shimLibName in $platformBuildDir")

            if (platform.startsWith("darwin")) {
                logger.lifecycle("[$platform] Fixing dylib linkage with install_name_tool")
                // Fix the shim's own ID
                runCommand(listOf("install_name_tool", "-id", "@rpath/$shimLibName", builtLib.absolutePath), "Fixing shim ID")
                // Fix the reference to libpdfium.dylib
                // We use both the relative name and the likely absolute path name if CMake embedded it
                runCommand(listOf("install_name_tool", "-change", "libpdfium.dylib", "@loader_path/libpdfium.dylib", builtLib.absolutePath), "Fixing pdfium reference (short)")
                runCommand(listOf("install_name_tool", "-change", "./libpdfium.dylib", "@loader_path/libpdfium.dylib", builtLib.absolutePath), "Fixing pdfium reference (dot)")

                // Fix references to libjpeg variants if they were linked
                runCommand(listOf("install_name_tool", "-change", "libjpeg.dylib", "@loader_path/libjpeg.dylib", builtLib.absolutePath), "Fixing jpeg reference")
                runCommand(listOf("install_name_tool", "-change", "libjpeg.8.dylib", "@loader_path/libjpeg.8.dylib", builtLib.absolutePath), "Fixing jpeg reference (v8)")
                runCommand(listOf("install_name_tool", "-change", "libjpeg.9.dylib", "@loader_path/libjpeg.9.dylib", builtLib.absolutePath), "Fixing jpeg reference (v9)")

                // Also fix libpdfium.dylib ID in its extraction dir if it exists there
                val pdfiumLib = libDir.resolve("libpdfium.dylib")
                if (pdfiumLib.exists()) {
                    runCommand(listOf("install_name_tool", "-id", "@rpath/libpdfium.dylib", pdfiumLib.absolutePath), "Fixing pdfium ID")
                }
            }

            logger.lifecycle("[$platform] Found built shim at: ${builtLib.absolutePath}")
            proj.copy {
                from(builtLib)
                into(libDir)
            }
        }
    }
}

val generateNativeIndex by tasks.registering {
    description = "Generates native-libs.txt index files for all platforms"
    dependsOn(extractPdfiumBinaries, buildShim)
    inputs.property("activePlatforms", activePlatforms.keys)
    val nativesRoot = pdfiumNativesDir.map { it.dir("natives") }
    inputs.dir(nativesRoot)
    outputs.dir(nativesRoot)

    doLast {
        nativesRoot.get().asFile.listFiles()?.filter { it.isDirectory }?.forEach { platformDir ->
            val libs = platformDir.listFiles()?.filter {
                val name = it.name
                name.endsWith(".so") || name.endsWith(".dylib") || name.endsWith(".dll")
            }?.map { it.name }?.sortedBy {
                when {
                    it.contains("pdfium") && !it.contains("shim") -> 0
                    it.contains("zlib") || it.contains("jpeg") || it.contains("z") -> 1
                    it.contains("shim") -> 2
                    else -> 3
                }
            }
            if (libs != null && libs.isNotEmpty()) {
                platformDir.resolve("native-libs.txt").writeText(libs.joinToString("\n") + "\n")
            }
        }
    }
}

val nativeJarTasks = activePlatforms.keys.map { localName ->
    val sanitized = localName.split("-").joinToString("") { it.replaceFirstChar(Char::uppercase) }
    tasks.register<Jar>("nativesJar$sanitized") {
        dependsOn(generateNativeIndex)
        group = "build"
        description = "Packages $localName native library"
        archiveClassifier.set("natives-$localName")

        onlyIf("Native shim available for $localName") {
            val dir = pdfiumNativesDir.get().asFile.resolve("natives/$localName")
            val hasShim = dir.listFiles()?.any { it.name.contains("shim") } ?: false
            if (!hasShim) {
                logger.lifecycle("Skipping nativesJar$sanitized: shim not built for $localName " +
                    "(use -PpdfiumPlatformFilter=$localName or supply -PprebuiltShimsDir to include it)")
            }
            hasShim
        }

        from(pdfiumNativesDir.map { it.dir("natives/$localName") }) { into("natives/$localName") }
        from("shim/vendor/qpdf/LICENSE.txt") { into("natives/$localName"); rename { "QPDF-LICENSE.txt" } }
        from("shim/vendor/qpdf/NOTICE.md") { into("natives/$localName"); rename { "QPDF-NOTICE.md" } }
    }
}

tasks.register("verifyNativeJars") {
    description = "Verifies the integrity of the generated native JARs"
    dependsOn(nativeJarTasks)
    doLast {
        activePlatforms.keys.forEach { platform ->
            val sanitized = platform.split("-").joinToString("") { it.replaceFirstChar(Char::uppercase) }
            val jarTask = tasks.named<Jar>("nativesJar$sanitized").get()
            val jarFile = jarTask.archiveFile.get().asFile

            if (!jarFile.exists()) {
                logger.lifecycle("Skipped verification for $platform: jar was not built (shim unavailable on this host)")
                return@forEach
            }

            val entries = mutableSetOf<String>()
            project.zipTree(jarFile).visit {
                if (!this.isDirectory) {
                    entries.add(this.path)
                }
            }

            val expectedPdfium = when {
                platform.startsWith("linux")   -> "natives/$platform/libpdfium.so"
                platform.startsWith("darwin")  -> "natives/$platform/libpdfium.dylib"
                platform.startsWith("windows") -> "natives/$platform/pdfium.dll"
                else -> error("Unknown platform type for verification: $platform")
            }
            val expectedShim = when {
                platform.startsWith("linux")   -> "natives/$platform/pdfium4j_shim.so"
                platform.startsWith("darwin")  -> "natives/$platform/pdfium4j_shim.dylib"
                platform.startsWith("windows") -> "natives/$platform/pdfium4j_shim.dll"
                else -> error("Unknown platform type for verification: $platform")
            }

            if (!entries.contains(expectedPdfium)) {
                throw GradleException("Integrity check failed: Missing $expectedPdfium in ${jarFile.name}")
            }
            if (!entries.contains(expectedShim)) {
                throw GradleException("Integrity check failed: Missing $expectedShim in ${jarFile.name}")
            }
            if (!entries.contains("natives/$platform/QPDF-LICENSE.txt")) {
                throw GradleException("Integrity check failed: Missing QPDF-LICENSE.txt in ${jarFile.name}")
            }
            if (!entries.contains("natives/$platform/QPDF-NOTICE.md")) {
                throw GradleException("Integrity check failed: Missing QPDF-NOTICE.md in ${jarFile.name}")
            }
            logger.lifecycle("Successfully verified integrity of ${jarFile.name} (contains PDFium + Shim + QPDF License)")
        }
    }
}

tasks.assemble {
    dependsOn(nativeJarTasks)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
    dependsOn(generateNativeIndex)
    classpath += files(layout.buildDirectory.dir("generated-natives"))
    jvmArgs(
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED"
    )
    if (platformFilter != null) {
        systemProperty("pdfium4j.platform", platformFilter)
    }
    System.getProperty("corpus.xmp.limit")?.let {
        systemProperty("corpus.xmp.limit", it)
    }
    System.getProperty("corpus.path")?.let {
        systemProperty("corpus.path", it)
    }
    System.getProperty("sourcePdf")?.let {
        systemProperty("sourcePdf", it)
    }
    System.getProperty("corpus.limit")?.let {
        systemProperty("corpus.limit", it)
    }
    filter {
        excludeTestsMatching("org.grimmory.pdfium4j.PathologicalPdfTest")
    }
}

tasks.named<Test>("test") {
    exclude("**/*AllocationTest.class")
}

tasks.register<Test>("allocationTests") {
    group = "verification"
    description = "Runs allocation assertions for native save hot paths"
    dependsOn(generateNativeIndex)
    useJUnitPlatform()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath + files(layout.buildDirectory.dir("generated-natives"))
    include("**/*AllocationTest.class")
    jvmArgs(
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED"
    )
}

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("-enable-preview", true)
        source = "25"
        addStringOption("Xdoclint:none", "-quiet")
    }
    isFailOnError = false
}

tasks.withType<JavaExec> {
    jvmArgs(
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED"
    )
}

fun requireCorpusToolsEnabled(taskName: String) {
    if (!enableCorpusTools.get()) {
        throw GradleException(
            "$taskName is disabled by default. Re-run with -PenableCorpusTools=true and explicit absolute corpus paths."
        )
    }
}

fun requireRunnerSource(taskName: String, relativePath: String) {
    if (!project.file(relativePath).exists()) {
        throw GradleException("$taskName entrypoint source is missing from this checkout: $relativePath")
    }
}

fun requireAbsolutePathProperty(taskName: String, key: String) {
    val value = System.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: throw GradleException("$taskName requires -D$key=/absolute/path")
    if (!Path.of(value).isAbsolute) {
        throw GradleException("$taskName requires -D$key to be an absolute path, got: $value")
    }
}

fun JavaExec.forwardSystemProperties(keys: List<String>) {
    keys.forEach { key ->
        System.getProperty(key)?.let { value -> systemProperty(key, value) }
    }
}

tasks.register<JavaExec>("runCorpusProcessor") {
    group = "application"
    description = "Runs the CorpusProcessor to write metadata to PDFs"
    if (enableCorpusTools.get()) {
        dependsOn("buildShim")
    }
    mainClass.set("org.grimmory.pdfium4j.CorpusProcessor")
    classpath = sourceSets["test"].runtimeClasspath
    classpath += files(layout.buildDirectory.dir("generated-natives"))
    forwardSystemProperties(listOf("corpus.dir", "corpus.outDir"))
    doFirst {
        requireCorpusToolsEnabled(name)
        requireRunnerSource(name, "src/test/java/org/grimmory/pdfium4j/CorpusProcessor.java")
        requireAbsolutePathProperty(name, "corpus.dir")
        requireAbsolutePathProperty(name, "corpus.outDir")
    }
}

tasks.register<JavaExec>("runPdfBoxCorpusGenerator") {
    group = "application"
    description = "Generates a synthetic PDF corpus using PDFBox (test scope only)"
    mainClass.set("org.grimmory.pdfium4j.PdfBoxCorpusGenerator")
    classpath = sourceSets["test"].runtimeClasspath
    forwardSystemProperties(listOf("corpus.targetCount", "corpus.maxPages", "corpus.seed", "corpus.startIndex", "corpus.clean", "corpus.outDir"))
    doFirst {
        requireCorpusToolsEnabled(name)
        requireRunnerSource(name, "src/test/java/org/grimmory/pdfium4j/PdfBoxCorpusGenerator.java")
        requireAbsolutePathProperty(name, "corpus.outDir")
    }
}

tasks.register<Exec>("runPdfJsIngestion") {
    group = "application"
    description = "Ingests Mozilla's pdf.js test corpus"
    commandLine("python3", "scripts/ingest_pdfjs.py")
}

tasks.register<JavaExec>("runCorpusMetadataStress") {
    group = "application"
    description = "Runs metadata save stress validation against corpus PDFs"
    if (enableCorpusTools.get()) {
        dependsOn("buildShim")
    }
    mainClass.set("org.grimmory.pdfium4j.CorpusMetadataStressRunner")
    classpath = sourceSets["test"].runtimeClasspath
    classpath += files(layout.buildDirectory.dir("generated-natives"))
    forwardSystemProperties(listOf("corpus.dir", "corpus.passes", "corpus.limit", "corpus.seed", "corpus.includeRegex", "corpus.failFast"))
    doFirst {
        requireCorpusToolsEnabled(name)
        requireRunnerSource(name, "src/test/java/org/grimmory/pdfium4j/CorpusMetadataStressRunner.java")
        requireAbsolutePathProperty(name, "corpus.dir")
    }
}

dependencies {
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.8")
    testCompileOnly("com.github.spotbugs:spotbugs-annotations:4.9.8")
    testImplementation("org.apache.pdfbox:pdfbox:3.0.7")
    testImplementation("org.apache.pdfbox:xmpbox:3.0.7")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            nativeJarTasks.forEach { jarTask ->
                artifact(jarTask)
            }
            pom {
                name = "PDFium4j"
                description = "Lightweight Java FFM wrapper around Google's PDFium PDF engine"
                url = "https://github.com/grimmory-tools/PDFium4j"
                inceptionYear = "2025"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
                developers {
                    developer {
                        id = "grimmory-tools"
                        name = "Grimmory Tools"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/grimmory-tools/PDFium4j.git"
                    developerConnection = "scm:git:ssh://github.com/grimmory-tools/PDFium4j.git"
                    url = "https://github.com/grimmory-tools/PDFium4j"
                }
            }
        }
    }
}

signing {
    val signingKey = findProperty("signingKey") as String? ?: System.getenv("GPG_PRIVATE_KEY")
    val signingPassword = findProperty("signingPassword") as String? ?: System.getenv("GPG_PASSPHRASE")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}
