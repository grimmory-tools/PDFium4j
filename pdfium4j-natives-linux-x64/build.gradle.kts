plugins {
    `java-library`
    `maven-publish`
    signing
}

description = "PDFium native libraries for Linux x86_64"

// No Java sources in this module - just native resources
sourceSets {
    main {
        java.setSrcDirs(emptyList<String>())
    }
}

tasks.withType<Javadoc> {
    enabled = false
}

// Maven Central requires javadoc and sources JARs, even if empty
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier = "javadoc"
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier = "sources"
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(javadocJar)
            artifact(sourcesJar)

            pom {
                name = "PDFium4j Natives - Linux x86_64"
                description = "Prebuilt PDFium native libraries for Linux x86_64"
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
