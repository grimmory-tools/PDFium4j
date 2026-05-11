package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.List;
import org.grimmory.pdfium4j.model.TextCharInfo;
import org.grimmory.pdfium4j.util.AllocationTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PdfPageTextAllocationTest {

  private final NoAllocationAsserter asserter = new NoAllocationAsserter();
  private PdfDocument doc;
  private PdfPage page;

  /**
   * Allocation tolerance for bulk text extraction.
   *
   * <p>This API returns a List of TextCharInfo objects and internal Strings, so allocations are
   * expected. 256KB is a tight ceiling for a single page of text elements.
   */
  private static final long EFFICIENCY_TOLERANCE = 256 * 1024;

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

    // Find a page with text
    for (int i = 0; i < doc.pageCount(); i++) {
      PdfPage p = doc.page(i);
      if (p.charCount() > 0) {
        page = p;
        break;
      }
    }
    if (page == null) {
      Assumptions.assumeTrue(false, "No page with text found in corpus PDF");
    }

    // Warmup JIT for bulk extraction
    for (int i = 0; i < 500; i++) {
      page.extractTextWithBounds();
    }
  }

  @AfterAll
  void tearDown() {
    if (page != null) {
      page.close();
    }
    if (doc != null) {
      doc.close();
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void extractTextWithBoundsDoesNotAllocateExcessively() {
    // Note: This API returns List<PdfTextWithBounds> and String, which ARE heap allocations.
    // However, the NATIVE interaction and BUFFER handling should be optimized.
    // This test specifically checks that we don't allocate millions of bytes for a single page.
    asserter.startRecording();
    List<TextCharInfo> text = page.extractTextWithBounds();
    assertFalse(text.isEmpty(), "Should have extracted some text");
    // We expect some allocations for the result objects, but not massive amounts.
    // 256KB is a very generous threshold for a single page of text objects.
    asserter.assertNoAllocations(EFFICIENCY_TOLERANCE); // 1MB for all the objects
  }

  private static Path findCorpusPdf() {
    try {
      return AllocationTestUtils.getTestPdf(PdfPageTextAllocationTest.class);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to find test PDF", e);
    }
  }
}
