plugins {
    `java-library`
    id("me.champeau.jmh") version "0.7.3"
}
 
dependencies {
    implementation(project(":"))
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}
 
jmh {
    jmhVersion.set("1.37")
    duplicateClassesStrategy.set(DuplicatesStrategy.EXCLUDE)
    jvmArgs.addAll(listOf(
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED"
    ))
}
 
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
