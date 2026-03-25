plugins {
    `java-library`
    `maven-publish`
    signing
}

allprojects {
    group = "org.grimmory"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    publishing {
        repositories {
            maven {
                name = "SonatypeCentral"
                url = uri("https://central.sonatype.com/repository/maven-releases/")
                credentials {
                    username = findProperty("sonatypeUsername") as String?
                        ?: System.getenv("SONATYPE_USERNAME")
                    password = findProperty("sonatypePassword") as String?
                        ?: System.getenv("SONATYPE_PASSWORD")
                }
            }
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf(
        "--enable-preview"
    ))
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs(
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED"
    )
    filter {
        excludeTestsMatching("org.pdfium4j.PathologicalPdfTest")
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

dependencies {
    // First release bundles Linux x64 unconditionally; future releases will leave platform selection to consumers
    runtimeOnly(project(":pdfium4j-natives-linux-x64"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// -- Maven Central publishing --

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

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

    repositories {
        maven {
            name = "SonatypeCentral"
            url = uri("https://central.sonatype.com/repository/maven-releases/")
            credentials {
                username = findProperty("sonatypeUsername") as String?
                    ?: System.getenv("SONATYPE_USERNAME")
                password = findProperty("sonatypePassword") as String?
                    ?: System.getenv("SONATYPE_PASSWORD")
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
