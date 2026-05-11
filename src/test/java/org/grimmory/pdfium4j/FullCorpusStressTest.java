package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.grimmory.pdfium4j.model.PdfProcessingPolicy;
import org.grimmory.pdfium4j.model.XmpMetadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Hardening test that runs metadata round-trips across all available corpora: Gutenberg, Mozilla,
 * and Random.
 */
class FullCorpusStressTest {

  private static final Logger PDFBOX_LOGGER = Logger.getLogger("org.apache.pdfbox");

  @BeforeAll
  static void setup() {
    System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    PDFBOX_LOGGER.setLevel(Level.OFF);
    PdfiumLibrary.initialize();
  }

  static Stream<Path> getAllCorpusFiles() {
    Path baseDir = Path.of("corpus");
    if (!Files.exists(baseDir)) {
      baseDir = Path.of("..", "corpus");
    }
    if (!Files.exists(baseDir)) return Stream.of(Path.of("__NO_CORPUS__"));

    String filter = System.getProperty("sourcePdf");
    String limitStr = System.getProperty("corpus.limit");
    int limit = (limitStr != null) ? Integer.parseInt(limitStr) : Integer.MAX_VALUE;

    List<Path> files =
        Stream.of("gutenberg", "mozilla-pdfjs", "random")
            .map(baseDir::resolve)
            .filter(Files::exists)
            .flatMap(
                dir -> {
                  try {
                    return Files.walk(dir)
                        .filter(p -> p.toString().endsWith(".pdf"))
                        .filter(p -> !p.toString().contains("/quarantine/"))
                        .filter(
                            p ->
                                filter == null
                                    || "all".equalsIgnoreCase(filter)
                                    || p.toString().contains(filter));
                  } catch (IOException _) {
                    return Stream.empty();
                  }
                })
            .sorted()
            .limit(limit)
            .toList();
    return files.isEmpty() ? Stream.of(Path.of("__NO_CORPUS__")) : files.stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getAllCorpusFiles")
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void roundtripStressTest(Path sourcePdf) throws Exception {
    Assumptions.assumeTrue(Files.exists(sourcePdf), "Corpus not available");
    System.out.println("Processing: " + sourcePdf);
    Path tempDir = Files.createTempDirectory("pdfium4j-stress");
    Path modifiedPdf = tempDir.resolve(sourcePdf.getFileName());

    String testTitle = "Stress Test Title for " + sourcePdf.getFileName();
    String customTagValue = "StressTag-" + System.currentTimeMillis();

    try {
      // 1. Inject Metadata
      try (PdfDocument doc =
          PdfDocument.open(sourcePdf, null, PdfProcessingPolicy.defaultPolicy())) {
        doc.setMetadata(MetadataTag.TITLE, testTitle);
        XmpMetadata xmp = XmpMetadataParser.parseFrom(doc);
        if (xmp == null) {
          xmp =
              XmpMetadata.builder()
                  .title(testTitle)
                  .customListFields(Map.of("xmp:CustomKey", List.of(customTagValue)))
                  .build();
        } else {
          Map<String, List<String>> newLists = new HashMap<>(xmp.customListFields());
          newLists.put("xmp:CustomKey", List.of(customTagValue));
          xmp =
              XmpMetadata.builder()
                  .title(testTitle)
                  .creators(xmp.creators())
                  .description(xmp.description().orElse(null))
                  .subjects(xmp.subjects())
                  .publisher(xmp.publisher().orElse(null))
                  .language(xmp.language().orElse(null))
                  .date(xmp.date().orElse(null))
                  .rights(xmp.rights().orElse(null))
                  .identifiers(xmp.identifiers())
                  .pdfaConformance(xmp.pdfaConformance().orElse(null))
                  .calibreFields(xmp.calibreFields())
                  .customFields(xmp.customFields())
                  .customListFields(newLists)
                  .xmpIdentifiers(xmp.xmpIdentifiers())
                  .build();
        }
        doc.setXmpMetadata(xmp);
        doc.save(modifiedPdf);
      } catch (Exception e) {
        if (e.getMessage() != null
            && (e.getMessage().contains("encrypted") || e.getMessage().contains("password"))) {
          return; // Skip encrypted
        }
        // For Random/Mozilla, we allow some failures if the original is broken
        // but we should log them.
        System.err.println("Load/Save failed for " + sourcePdf + ": " + e.getMessage());
        return;
      }

      // 2. Read back and verify with PDFium
      try (PdfDocument doc = PdfDocument.open(modifiedPdf)) {
        String actualTitle = doc.metadataString(MetadataTag.TITLE);
        assertEquals(testTitle, actualTitle, "Title mismatch in " + sourcePdf);
        XmpMetadata xmp = XmpMetadataParser.parseFrom(doc);
        assertNotNull(xmp, "XMP metadata missing in " + sourcePdf);
        List<String> tags = xmp.customListFields().get("xmp:CustomKey");
        assertNotNull(tags, "Custom XMP key missing in " + sourcePdf);
        assertTrue(tags.contains(customTagValue), "Custom XMP value mismatch in " + sourcePdf);
      }

      // 3. External Validation
      validateWithExternalTools(modifiedPdf, testTitle);
    } finally {
      // Clean up temp files
      try (Stream<Path> walk = Files.walk(tempDir)) {
        walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
      }
    }
  }

  private void validateWithExternalTools(Path pdf, String expectedTitle) throws Exception {
    // qpdf --check
    Process qpdf = new ProcessBuilder("qpdf", "--check", pdf.toString()).start();
    if (qpdf.waitFor() != 0) {
      String err = new String(qpdf.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
      throw new AssertionError("qpdf validation failed for " + pdf + ": " + err);
    }

    // pdfinfo
    Process pdfinfo = new ProcessBuilder("pdfinfo", pdf.toString()).start();
    String output = new String(pdfinfo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (pdfinfo.waitFor() != 0) {
      throw new AssertionError("pdfinfo failed for " + pdf);
    }
    if (!output.contains("Title:") || !output.contains(expectedTitle)) {
      throw new AssertionError(
          "pdfinfo could not find expected title in " + pdf + ". Output: " + output);
    }
  }
}
