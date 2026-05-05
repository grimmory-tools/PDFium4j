import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Path

plugins {
    `java-library`
    `maven-publish`
    signing
    checkstyle
    pmd
    id("com.diffplug.spotless") version "8.4.0"
    id("com.github.spotbugs") version "6.5.1"
}

allprojects {
    group = "org.grimmory"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

configure<CheckstyleExtension> {
    toolVersion = "13.3.0"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    isShowViolations = true
}

configure<PmdExtension> {
    toolVersion = "7.22.0"
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
        required.set(false)
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

tasks.withType<Test> {
    useJUnitPlatform()
    dependsOn("extractPdfiumBinaries")
    classpath += files(layout.buildDirectory.dir("generated-natives"))
    jvmArgs(
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED"
    )
    filter {
        excludeTestsMatching("org.grimmory.pdfium4j.PathologicalPdfTest")
    }
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
        dependsOn("extractPdfiumBinaries")
    }
    mainClass.set("org.grimmory.pdfium4j.CorpusProcessor")
    classpath = sourceSets["test"].runtimeClasspath
    classpath += files(layout.buildDirectory.dir("generated-natives"))
    forwardSystemProperties(
        listOf(
            "corpus.dir",
            "corpus.outDir"
        )
    )
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
    forwardSystemProperties(
        listOf(
            "corpus.targetCount",
            "corpus.maxPages",
            "corpus.seed",
            "corpus.startIndex",
            "corpus.clean",
            "corpus.outDir"
        )
    )
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
        dependsOn("extractPdfiumBinaries")
    }
    mainClass.set("org.grimmory.pdfium4j.CorpusMetadataStressRunner")
    classpath = sourceSets["test"].runtimeClasspath
    classpath += files(layout.buildDirectory.dir("generated-natives"))
    forwardSystemProperties(
        listOf(
            "corpus.dir",
            "corpus.passes",
            "corpus.limit",
            "corpus.seed",
            "corpus.includeRegex",
            "corpus.failFast"
        )
    )
    doFirst {
        requireCorpusToolsEnabled(name)
        requireRunnerSource(name, "src/test/java/org/grimmory/pdfium4j/CorpusMetadataStressRunner.java")
        requireAbsolutePathProperty(name, "corpus.dir")
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}

// -- PDFium native binary download & bundling --
// Prebuilt binaries from https://github.com/bblanchon/pdfium-binaries
// Exclude stale empty natives dirs from src/main/resources (if present)
tasks.processResources { exclude("natives/**") }

val pdfiumVersion = findProperty("pdfiumVersion")?.toString() ?: "7749"

val pdfiumPlatforms = mapOf(
    "linux-x64"        to "linux-x64",
    "linux-arm64"      to "linux-arm64",
    "linux-musl-x64"   to "linux-musl-x64",
    "linux-musl-arm64" to "linux-musl-arm64",
    "darwin-x64"       to "mac-x64",
    "darwin-arm64"     to "mac-arm64",
    "windows-x64"      to "win-x64"
)

// When set (e.g. -PpdfiumPlatformFilter=linux-musl-x64), only that platform
// is downloaded and extracted. Useful in composite-build / container scenarios.
val platformFilter = findProperty("pdfiumPlatformFilter")?.toString()
val activePlatforms = if (platformFilter != null) {
    pdfiumPlatforms.filterKeys { it == platformFilter }
} else {
    pdfiumPlatforms
}

val pdfiumArchiveDir = layout.buildDirectory.dir("pdfium-archives")
val pdfiumNativesDir = layout.buildDirectory.dir("generated-natives")

val downloadPdfiumBinaries by tasks.registering {
    description = "Downloads prebuilt PDFium binaries for all supported platforms"
    outputs.dir(pdfiumArchiveDir)
    doLast {
        val dir = pdfiumArchiveDir.get().asFile
        dir.mkdirs()
        val base = "https://github.com/bblanchon/pdfium-binaries/releases/download/chromium/$pdfiumVersion"
        activePlatforms.values.forEach { remoteName ->
            val target = dir.resolve("pdfium-$remoteName.tgz")
            if (!target.exists()) {
                logger.lifecycle("Downloading pdfium-$remoteName.tgz …")
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
            }
        }
    }
}

val extractPdfiumBinaries by tasks.registering {
    description = "Extracts PDFium native libraries for bundling into the JAR"
    dependsOn(downloadPdfiumBinaries)
    outputs.dir(pdfiumNativesDir)
    // Capture project reference at configuration time to avoid deprecated Task.project at execution time
    val proj = project
    doLast {
        val nativesRoot = pdfiumNativesDir.get().asFile.resolve("natives")
        nativesRoot.deleteRecursively()
        activePlatforms.forEach { (localName, remoteName) ->
            val archive = pdfiumArchiveDir.get().asFile.resolve("pdfium-$remoteName.tgz")
            val platformDir = nativesRoot.resolve(localName)
            platformDir.mkdirs()
            val libFileName = when {
                localName.startsWith("linux")   -> "libpdfium.so"
                localName.startsWith("darwin")  -> "libpdfium.dylib"
                localName.startsWith("windows") -> "pdfium.dll"
                else -> error("Unknown platform: $localName")
            }
            proj.copy {
                from(proj.tarTree(proj.resources.gzip(archive))) {
                    include("lib/$libFileName", "bin/$libFileName")
                    eachFile { relativePath = RelativePath(true, name) }
                }
                into(platformDir)
                includeEmptyDirs = false
            }
            platformDir.resolve("native-libs.txt").writeText("$libFileName\n")
        }
    }
}

// Per-platform native JAR tasks, one classified JAR per supported OS/arch
val nativeJarTasks = pdfiumPlatforms.keys.map { localName ->
    val sanitized = localName.split("-").joinToString("") { it.replaceFirstChar(Char::uppercase) }
    tasks.register<Jar>("nativesJar$sanitized") {
        dependsOn(extractPdfiumBinaries)
        group = "build"
        description = "Packages $localName native library"
        archiveClassifier.set("natives-$localName")
        from(pdfiumNativesDir.map { it.dir("natives/$localName") }) { into("natives/$localName") }
    }
}

tasks.assemble {
    dependsOn(nativeJarTasks)
}

dependencies {
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.8")
    testCompileOnly("com.github.spotbugs:spotbugs-annotations:4.9.8")
    testImplementation("org.apache.pdfbox:pdfbox:3.0.7")
    testImplementation("org.apache.pdfbox:xmpbox:3.0.7")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// -- Maven Central publishing --

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
