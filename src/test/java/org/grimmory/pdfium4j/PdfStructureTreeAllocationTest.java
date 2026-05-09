package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.List;
import org.grimmory.pdfium4j.model.PdfStructureElement;
import org.grimmory.pdfium4j.util.AllocationTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PdfStructureTreeAllocationTest {

  private final NoAllocationAsserter asserter = new NoAllocationAsserter();
  private PdfDocument doc;
  private PdfPage page;

  /**
   * Allocation tolerance for complex structure tree extraction.
   *
   * <p>This API returns a List of PdfStructureElement objects, so allocations are expected. This
   * threshold ensures the implementation remains efficient.
   */
  private static final long EFFICIENCY_TOLERANCE = 128 * 1024;

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
    // Use a file that is likely to have structure (tagged PDF)
    Path source = findCorpusPdf();
    doc = PdfDocument.open(source);
    page = doc.page(0);

    // Warmup JIT
    for (int i = 0; i < 500; i++) {
      page.structureTree();
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
  void structureTreeReadingIsEfficient() {
    // Returns List<PdfStructureElement>, so some allocations are expected.
    asserter.startRecording();
    List<PdfStructureElement> tree = page.structureTree();
    assertNotNull(tree);
    asserter.assertNoAllocations(EFFICIENCY_TOLERANCE);
  }

  private static Path findCorpusPdf() {
    try {
      return AllocationTestUtils.getTestPdf(PdfStructureTreeAllocationTest.class);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to find test PDF", e);
    }
  }
}
