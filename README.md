# PDFium4j

Compact Java PDF engine on top of Java 25 FFM.

## What it does

- Open PDF from file path or byte array
- Probe and diagnose unreadable, corrupt, or password protected files
- Render pages to RGBA buffers at custom DPI
- Render bounded previews and thumbnails with memory caps
- Extract plain text and per character bounds
- Read page size, page count, labels, rotation, permissions, encryption state
- Read and write metadata from Info dictionary
- Extract raw XMP and parse structured XMP metadata
- Generate KOReader-compatible partial MD5 checksums for lightweight file identity
- Read bookmarks and links
- Read page annotations
- Delete pages, insert blank pages, import pages, save documents
- Save to path, byte array, or OutputStream

## Reliability and safety

- No JNI bridge
- Direct FFM calls with explicit native resource ownership
- Deterministic close semantics for document and page handles
- Thread confinement for document and page operations
- Strict and recover policy modes
- Hard limits for document bytes, render pixels, and render worker count
- Typed exceptions with error code mapping

## Quick start

```java
import java.nio.file.Path;

import org.pdfium4j.PdfDocument;
import org.pdfium4j.PdfPage;
import org.pdfium4j.model.PdfProcessingPolicy;
import org.pdfium4j.model.RenderResult;

PdfProcessingPolicy policy = PdfProcessingPolicy.defaultPolicy()
    .withMaxDocumentBytes(512L * 1024 * 1024)
    .withMaxRenderPixels(40_000_000L)
    .withMaxParallelRenderThreads(8);

try (PdfDocument doc = PdfDocument.open(Path.of("book.pdf"), null, policy)) {
  try (PdfPage page = doc.page(0)) {
    RenderResult cover = page.renderThumbnail(512);
    String text = page.extractText();
  }

  var diagnostics = PdfDocument.diagnose(Path.of("book.pdf"));
  var checksum = PdfDocument.koReaderPartialMd5(Path.of("book.pdf"));
  var xmp = doc.xmpMetadataString();
  doc.save(Path.of("output.pdf"));
}
```

## Installation

The library ships as a core API JAR plus per-platform native classifier JARs.
Add the core dependency and the classifier matching your target platform.

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("org.grimmory:pdfium4j:0.1.0")
    runtimeOnly("org.grimmory:pdfium4j:0.1.0:natives-linux-x64")
}
```

#### Auto-detect platform

```kotlin
val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch")

val pdfiumNatives = when {
    "linux" in osName && osArch == "amd64"    -> "natives-linux-x64"
    "linux" in osName && osArch == "aarch64"  -> "natives-linux-arm64"
    "mac" in osName   && osArch == "aarch64"  -> "natives-darwin-arm64"
    "mac" in osName   && osArch == "x86_64"   -> "natives-darwin-x64"
    "windows" in osName && osArch == "amd64"  -> "natives-windows-x64"
    else -> error("Unsupported platform: $osName/$osArch")
}

dependencies {
    implementation("org.grimmory:pdfium4j:0.1.0")
    runtimeOnly("org.grimmory:pdfium4j:0.1.0:$pdfiumNatives")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'org.grimmory:pdfium4j:0.1.0'
    runtimeOnly 'org.grimmory:pdfium4j:0.1.0:natives-linux-x64'
}
```

### Maven

```xml
<dependency>
    <groupId>org.grimmory</groupId>
    <artifactId>pdfium4j</artifactId>
    <version>0.1.0</version>
</dependency>
<dependency>
    <groupId>org.grimmory</groupId>
    <artifactId>pdfium4j</artifactId>
    <version>0.1.0</version>
    <classifier>natives-linux-x64</classifier>
</dependency>
```

### Available classifiers

| Platform | Classifier |
|---|---|
| Linux x86_64 | `natives-linux-x64` |
| Linux aarch64 | `natives-linux-arm64` |
| macOS aarch64 | `natives-darwin-arm64` |
| macOS x86_64 | `natives-darwin-x64` |
| Windows x86_64 | `natives-windows-x64` |

## Runtime requirements

- Java 25+
- JVM flags:

```text
--enable-preview --enable-native-access=ALL-UNNAMED
```

## Native libraries

PDFium4j ships prebuilt PDFium binaries as classified JAR artifacts.
When the correct native classifier JAR is on the classpath, the library
extracts the shared library to a temp directory at startup and loads it
automatically.

If classpath loading is unavailable, PDFium4j falls back to `System.loadLibrary("pdfium")`.

### Supported platforms

| Platform | Classifier |
|---|---|
| Linux x86_64 | `natives-linux-x64` |
| Linux aarch64 | `natives-linux-arm64` |
| macOS aarch64 | `natives-darwin-arm64` |
| macOS x86_64 | `natives-darwin-x64` |
| Windows x86_64 | `natives-windows-x64` |

## Project structure

```text
pdfium4j/                     Root (Java module)
  src/main/java/              FFM bindings and public API
  src/test/java/              Tests
  build/generated-natives/    Downloaded PDFium binaries (build-time)
```

## Build

```bash
./gradlew build
```

## License

Apache License 2.0. See [LICENSE](LICENSE).
