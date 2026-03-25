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

The library ships as a core API JAR plus per-platform native JARs.
Adding the core JAR on Linux will pull in the native libraries automatically.

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("org.grimmory:pdfium4j:0.1.0")
}
```

The `pdfium4j` artifact declares `pdfium4j-natives-linux-x64` as a runtime
dependency, so native libraries are included automatically on Linux x86_64.

When additional platforms are available, add only the one you need:

```kotlin
dependencies {
    implementation("org.grimmory:pdfium4j:0.1.0")
    // Pick your platform:
    runtimeOnly("org.grimmory:pdfium4j-natives-linux-x64:0.1.0")
    // runtimeOnly("org.grimmory:pdfium4j-natives-linux-arm64:0.1.0")
    // runtimeOnly("org.grimmory:pdfium4j-natives-darwin-arm64:0.1.0")
    // runtimeOnly("org.grimmory:pdfium4j-natives-windows-x64:0.1.0")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'org.grimmory:pdfium4j:0.1.0'
}
```

### Maven

```xml
<dependency>
    <groupId>org.grimmory</groupId>
    <artifactId>pdfium4j</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Runtime requirements

- Java 25+
- JVM flags:

```text
--enable-preview --enable-native-access=ALL-UNNAMED
```

## Native libraries

PDFium4j bundles prebuilt PDFium binaries inside per-platform native JARs.
When the native JAR is on the classpath, the library extracts the shared
libraries to a temp directory at startup and loads them automatically.

If classpath loading is unavailable, PDFium4j falls back to `System.loadLibrary("pdfium")`.

### Supported platforms

| Platform | Artifact |
|---|---|
| Linux x86_64 | `pdfium4j-natives-linux-x64` |
| Linux aarch64 | `pdfium4j-natives-linux-arm64` (planned) |
| macOS aarch64 | `pdfium4j-natives-darwin-arm64` (planned) |
| macOS x86_64 | `pdfium4j-natives-darwin-x64` (planned) |
| Windows x86_64 | `pdfium4j-natives-windows-x64` (planned) |

## Project structure

```text
pdfium4j/                     Root (core Java module)
  src/main/java/              FFM bindings and public API
  src/test/java/              Tests
pdfium4j-natives-linux-x64/   Native module for Linux x86_64
  src/main/resources/
    natives/linux-x64/        Prebuilt .so files + native-libs.txt
```

## Build

```bash
./gradlew build
```

## License

Apache License 2.0. See [LICENSE](LICENSE).
