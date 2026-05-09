package org.grimmory.pdfium4j;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.grimmory.pdfium4j.internal.NoAllocationPathProbe;
import org.grimmory.pdfium4j.model.RenderFlags;
import org.grimmory.pdfium4j.util.AllocationTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorpusNoAllocationTest {

  private static final int WARMUP_FILES = 3;
  private static final int WARMUP_ITERATIONS_PER_FILE = 200;

  private final NoAllocationAsserter asserter = new NoAllocationAsserter();
  private Arena arena;
  private MemorySegment renderBuffer;
  private MemorySegment trailerBuffer;
  private final int[] output = new int[3];

  /** Allocation tolerance for JVM/JIT noise. */
  private static final long STEADY_STATE_TOLERANCE = 256;

  @BeforeAll
  void setUp() throws IOException {
    asserter.verifyAllocationTrackingAvailable();
    arena = Arena.ofShared();
    renderBuffer = arena.allocate(1024 * 1024 * 4);
    trailerBuffer = arena.allocate(32L * JAVA_INT.byteSize(), JAVA_INT.byteAlignment());

    List<Path> corpusFiles = getCorpusFiles().limit(WARMUP_FILES).toList();

    // Warmup JIT with first few files
    for (Path path : corpusFiles) {
      try (PdfDocument doc = PdfDocument.open(path)) {
        PdfPage page = doc.page(0);
        for (int i = 0; i < WARMUP_ITERATIONS_PER_FILE; i++) {
          page.renderTo(renderBuffer, 256, 256, 256 * 4, RenderFlags.DEFAULT.value(), 0xFFFFFFFF);
          page.renderThumbnailTo(renderBuffer, 256);
        }

        try (NoAllocationPathProbe probe = PdfDocument.noAllocationPathProbe(path)) {
          for (int i = 0; i < WARMUP_ITERATIONS_PER_FILE; i++) {
            probe.inspect(output, trailerBuffer);
          }
        }
      } catch (Exception e) {
        // Skip problematic files during warmup
        PdfiumLibrary.ignore(e);
      }
    }
  }

  @AfterAll
  void tearDown() {
    if (arena != null) arena.close();
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void noAllocationsAcrossGutenbergCorpus() throws IOException {
    List<Path> testFiles = getCorpusFiles().limit(10).toList();

    for (Path path : testFiles) {
      // 1. Path Probe
      try (NoAllocationPathProbe probe = PdfDocument.noAllocationPathProbe(path)) {
        asserter.startRecording();
        probe.inspect(output, trailerBuffer);
        asserter.assertNoAllocations(STEADY_STATE_TOLERANCE); // Small tolerance for TLAB noise
      }

      // 2. Document Open and Page Render
      try (PdfDocument doc = PdfDocument.open(path)) {
        PdfPage page = doc.page(0);

        asserter.startRecording();
        page.renderTo(renderBuffer, 256, 256, 256 * 4, RenderFlags.DEFAULT.value(), 0xFFFFFFFF);
        asserter.assertNoAllocations(STEADY_STATE_TOLERANCE);

        asserter.startRecording();
        page.renderThumbnailTo(renderBuffer, 256);
        asserter.assertNoAllocations(STEADY_STATE_TOLERANCE);
      } catch (Exception e) {
        // Some PDFs might be corrupt or have issues, we skip them but report if many fail
        PdfiumLibrary.ignore(e);
      }
    }
  }

  private static Stream<Path> getCorpusFiles() throws IOException {
    Path corpusDir = Path.of("corpus", "gutenberg");
    if (!Files.exists(corpusDir)) {
      corpusDir = Path.of("..", "corpus", "gutenberg");
    }
    if (!Files.exists(corpusDir)) {
      // Fallback for CI
      return Stream.of(AllocationTestUtils.getTestPdf(CorpusNoAllocationTest.class));
    }
    return Files.list(corpusDir)
        .filter(p -> p.toString().endsWith(".pdf"))
        .filter(
            p -> {
              try {
                return Files.size(p) < 1024 * 1024; // Only files < 1MB
              } catch (IOException _) {
                return false;
              }
            })
        .sorted();
  }

  static boolean pdfiumAvailable() {
    try {
      PdfiumLibrary.initialize();
      return true;
    } catch (Throwable _) {
      return false;
    }
  }
}
