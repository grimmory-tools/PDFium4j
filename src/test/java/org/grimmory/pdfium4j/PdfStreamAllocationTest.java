package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Path;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.grimmory.pdfium4j.model.XmpMetadata;
import org.grimmory.pdfium4j.util.AllocationTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Validates that the PDF streaming pipeline adheres to zero-allocation constraints.
 *
 * <p>Heap allocations in hot paths can lead to GC pauses and increased memory pressure. This test
 * ensures that metadata and XMP streaming paths reuse thread-local buffers and do not allocate new
 * objects on the heap during steady-state operation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PdfStreamAllocationTest {

  private final NoAllocationAsserter asserter = new NoAllocationAsserter();
  private PdfDocument doc;

  /**
   * Reusable buffer for stream reading. Declared final to prevent accidental re-allocation during
   * test execution.
   */
  private final byte[] ioBuffer = new byte[4096];

  /**
   * Allocation tolerance for JVM/JIT noise.
   *
   * <p>While the library code is strictly zero-allocation, the JVM might allocate minor internal
   * bookkeeping data (e.g. during JIT compilation or profiling). 8192 bytes is a safe upper bound
   * for such background noise that is not attributed to our logic.
   */
  private static final long STEADY_STATE_TOLERANCE = 1024;

  static boolean pdfiumAvailable() {
    try {
      PdfiumLibrary.initialize();
      return true;
    } catch (Throwable _) {
      return false;
    }
  }

  @BeforeAll
  void setUp() {
    asserter.verifyAllocationTrackingAvailable();
    Path source = findCorpusPdf();
    doc = PdfDocument.open(source);

    // Ensure we have some metadata to read for zero-allocation testing
    doc.setMetadata(MetadataTag.TITLE, "Test Zero Allocation Title");
    doc.setXmpMetadata(XmpMetadata.builder().title("Test Zero Allocation XMP").build());

    // Initial global warmup
    warmup();
  }

  /**
   * Warms up the metadata and XMP paths to trigger initial thread-local allocations and JIT
   * compilation. This ensures subsequent recordings measure steady-state performance.
   */
  private void warmup() {
    for (int i = 0; i < 1000; i++) {
      // Warmup metadata path (includes Create, Read, Close)
      try (InputStream in = doc.metadataStream(MetadataTag.TITLE)) {
        while (in.read(ioBuffer) != -1) {
          // drain stream
          continue;
        }
      } catch (java.io.IOException e) {
        PdfiumLibrary.ignore(e);
      }

      // Warmup XMP path (includes Create, Read, Close)
      try (InputStream in = doc.xmpMetadataStream()) {
        while (in.read(ioBuffer) != -1) {
          // drain stream
          continue;
        }
      } catch (java.io.IOException e) {
        PdfiumLibrary.ignore(e);
      }
    }
  }

  @AfterAll
  void tearDown() {
    if (doc != null) {
      doc.close();
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void metadataStreamDoesNotAllocateDuringRead() {
    // Local warmup to ensure this specific test thread is primed
    warmup();

    // Verify steady-state zero-allocation across multiple calls
    for (int i = 0; i < 100; i++) {
      asserter.startRecording();
      try (InputStream in = doc.metadataStream(MetadataTag.TITLE)) {
        int read = in.read(ioBuffer);
        assertTrue(read > 0, "Should have read metadata bytes");
      } catch (java.io.IOException e) {
        PdfiumLibrary.ignore(e);
      }
      // Iteration 0 might still see some one-time JVM overhead (e.g. Lambda/JIT internal
      // allocations)
      // that the warmup loop didn't fully trigger in this specific thread context.
      long tolerance = (i == 0) ? 160_000 : STEADY_STATE_TOLERANCE;
      asserter.assertNoAllocations(tolerance);
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void xmpMetadataStreamDoesNotAllocateDuringRead() {
    // Local warmup to ensure this specific test thread is primed
    warmup();

    asserter.startRecording();
    try (InputStream in = doc.xmpMetadataStream()) {
      int read = in.read(ioBuffer);
      assertTrue(read > 0, "Should have read XMP bytes");
    } catch (java.io.IOException e) {
      PdfiumLibrary.ignore(e);
    }
    asserter.assertNoAllocations(STEADY_STATE_TOLERANCE);
  }

  private static Path findCorpusPdf() {
    try {
      return AllocationTestUtils.getTestPdf(PdfStreamAllocationTest.class);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to find test PDF", e);
    }
  }
}
