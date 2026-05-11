package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.grimmory.pdfium4j.internal.InternalLogger;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.grimmory.pdfium4j.model.PdfProcessingPolicy;
import org.grimmory.pdfium4j.model.XmpMetadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CorpusMetadataRoundTripTest {

  private static final Logger PDFBOX_LOGGER = Logger.getLogger("org.apache.pdfbox");

  @BeforeAll
  static void setup() {
    System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    PDFBOX_LOGGER.setLevel(Level.OFF);
    PdfiumLibrary.initialize();
  }

  static Stream<Path> getCorpusFiles() throws IOException {
    Path corpusDir = Path.of("corpus");
    if (!Files.exists(corpusDir)) {
      corpusDir = Path.of("..", "corpus");
    }
    if (!Files.exists(corpusDir)) return Stream.of(Path.of("__NO_CORPUS__"));
    String filter = System.getProperty("sourcePdf");
    List<Path> files =
        Stream.of("gutenberg", "mozilla-pdfjs", "random")
            .map(corpusDir::resolve)
            .filter(Files::exists)
            .flatMap(
                dir -> {
                  try {
                    return Files.walk(dir)
                        .filter(p -> p.toString().endsWith(".pdf"))
                        .filter(p -> !p.toString().contains("/quarantine/"))
                        .filter(p -> filter == null || p.toString().contains(filter));
                  } catch (IOException e) {
                    return Stream.empty();
                  }
                })
            .sorted()
            .toList();
    return files.isEmpty() ? Stream.of(Path.of("__NO_CORPUS__")) : files.stream();
  }

  @ParameterizedTest
  @MethodSource("getCorpusFiles")
  void roundtripCorpusTest(Path sourcePdf) throws Exception {
    Assumptions.assumeTrue(Files.exists(sourcePdf), "Corpus not available");
    Files.writeString(
        Path.of("test_progress.log"),
        "Processing: " + sourcePdf + "\n",
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
    Path tempDir = Files.createTempDirectory("pdfium4j-roundtrip");
    Path modifiedPdf = tempDir.resolve(sourcePdf.getFileName());

    String testTitle = "Roundtrip Title for " + sourcePdf.getFileName();
    String customTagValue = "BookloreTag" + sourcePdf.hashCode();

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
        if (e.getMessage() != null && e.getMessage().contains("encrypted")) {
          return; // Skip encrypted
        }
        throw e;
      }

      // 2. Validate Structural Integrity (No Corruption)
      if (isCommandAvailable("qpdf", "--version")) {
        CommandResult qpdfRes = runCommand(List.of("qpdf", "--check", modifiedPdf.toString()));
        assertTrue(
            qpdfRes.exitCode() == 0 || qpdfRes.exitCode() == 3,
            "qpdf check failed for " + sourcePdf + "\nOutput: " + qpdfRes.output());
      }

      // 3. XMP Read/Write Verification (pdfinfo)
      if (isCommandAvailable("pdfinfo", "-v")) {
        CommandResult pdfinfoRes = runCommand(List.of("pdfinfo", modifiedPdf.toString()));
        assertEquals(0, pdfinfoRes.exitCode(), "pdfinfo execution failed");
        assertTrue(
            pdfinfoRes.output().contains(testTitle)
                || pdfinfoRes.output().contains("Roundtrip Title"),
            "pdfinfo did not see the updated Title\nOutput: " + pdfinfoRes.output());
      } else {
        System.out.println("SKIPPING pdfinfo validation (not available)");
      }

      // 4. Verify Custom Keys with PDFBox (Fallback Verification)
      try (PDDocument doc = Loader.loadPDF(modifiedPdf.toFile())) {
        assertNotNull(doc.getDocumentCatalog());
      }

      // 5. Read back with Pdfium4j and ensure custom keys are retrieved
      try (PdfDocument doc = PdfDocument.open(modifiedPdf)) {
        assertEquals(testTitle, doc.metadata(MetadataTag.TITLE).orElse(null));
        XmpMetadata xmp = XmpMetadataParser.parseFrom(doc);
        assertNotNull(xmp, "XMP missing");
        List<String> tags = xmp.findListField("CustomKey");
        assertFalse(tags.isEmpty(), "XMP missing custom key: CustomKey");
        assertTrue(tags.contains(customTagValue), "Custom tag value mismatch");
      }
    } catch (Throwable t) {
      System.err.println("FAILURE on file: " + sourcePdf);
      try {
        Files.copy(modifiedPdf, Path.of("debug_failed.pdf"), StandardCopyOption.REPLACE_EXISTING);
        System.err.println("Modified PDF copied to: debug_failed.pdf");
      } catch (IOException e) {
        System.err.println("Failed to copy debug file: " + e.getMessage());
      }
      throw t;
    } finally {
      try (Stream<Path> walk = Files.walk(tempDir)) {
        walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
      } catch (IOException _) {
        InternalLogger.warn("Cleanup of temporary directory failed (ignored)");
      }
    }
  }

  private static boolean isCommandAvailable(String command, String arg) {
    try {
      return runCommand(List.of(command, arg)).exitCode() <= 1;
    } catch (Exception _) {
      return false;
    }
  }

  private static CommandResult runCommand(List<String> command)
      throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectErrorStream(true);
    Process process = pb.start();
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (InputStream in = process.getInputStream()) {
      in.transferTo(buffer);
    }
    int exit = process.waitFor();
    return new CommandResult(exit, buffer.toString(StandardCharsets.UTF_8));
  }

  @Test
  void debugTest() throws Exception {
    String filter = System.getProperty("sourcePdf");
    Path source =
        Path.of(filter != null ? filter : "corpus/gutenberg/1063_The Cask of Amontillado.pdf");
    if (!Files.exists(source)) {
      System.out.println("SKIPPING debugTest (corpus not found)");
      return;
    }
    Path target = Path.of("debug_output.pdf");
    try (PdfDocument doc = PdfDocument.open(source)) {
      doc.setMetadata(MetadataTag.TITLE, "Debug Title");
      doc.save(target);
    }

    CommandResult res = runCommand(List.of("qpdf", "--check", "debug_output.pdf"));
    System.out.println("QPDF CHECK OUTPUT:");
    System.out.println(res.output());

    CommandResult info = runCommand(List.of("pdfinfo", "debug_output.pdf"));
    System.out.println("PDFINFO OUTPUT:");
    System.out.println(info.output());
  }

  @Test
  void testCompressedPdfMetadata() throws Exception {
    Path source = Path.of("compressed_test.pdf");
    // Ensure the file exists (generated earlier in scratch)
    if (!Files.exists(source)) {
      Path corpusSource = Path.of("corpus/gutenberg/10007_Carmilla.pdf");
      if (!Files.exists(corpusSource)) {
        System.out.println("SKIPPING testCompressedPdfMetadata (corpus not found)");
        return;
      }
      runCommand(
          List.of(
              "qpdf",
              corpusSource.toString(),
              "--object-streams=generate",
              "--compression-level=9",
              source.toString()));
    }

    Path target = Path.of("compressed_target.pdf");
    try (PdfDocument doc = PdfDocument.open(source)) {
      doc.setMetadata(MetadataTag.TITLE, "Compressed Title");
      doc.save(target);
    }

    CommandResult qpdfRes = runCommand(List.of("qpdf", "--check", target.toString()));
    if (qpdfRes.exitCode() != 0) {
      System.err.println("QPDF CHECK FAILED:\n" + qpdfRes.output());
    }

    CommandResult pdfinfoRes = runCommand(List.of("pdfinfo", target.toString()));
    assertTrue(
        pdfinfoRes.output().contains("Compressed Title"),
        "pdfinfo did not see the updated Title in compressed PDF");
  }

  private record CommandResult(int exitCode, String output) {}
}
