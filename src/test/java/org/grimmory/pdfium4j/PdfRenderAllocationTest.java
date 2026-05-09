package org.grimmory.pdfium4j;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import org.grimmory.pdfium4j.model.RenderFlags;
import org.grimmory.pdfium4j.util.AllocationTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PdfRenderAllocationTest {

  private static final int WARMUP_ITERATIONS = 1000;

  private final NoAllocationAsserter asserter = new NoAllocationAsserter();
  private PdfDocument doc;
  private PdfPage page;
  private Arena arena;
  private MemorySegment renderBuffer;

  /** Allocation tolerance for JVM/JIT noise. Reduced for Java 25 FFM. */
  private static final long STEADY_STATE_TOLERANCE = 128;

  @BeforeAll
  void setUp() {
    asserter.verifyAllocationTrackingAvailable();
    arena = Arena.ofShared();

    Path source = findCorpusPdf();
    doc = PdfDocument.open(source);
    page = doc.page(0);

    // Allocate a buffer large enough for thumbnail rendering
    renderBuffer = arena.allocate(1024 * 1024 * 4);

    // Warmup
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      page.renderTo(renderBuffer, 256, 256, 256 * 4, RenderFlags.DEFAULT.value(), 0xFFFFFFFF);
      page.renderThumbnailTo(renderBuffer, 256);
    }
  }

  @AfterAll
  void tearDown() {
    if (page != null) page.close();
    if (doc != null) doc.close();
    if (arena != null) arena.close();
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void renderThumbnailToSegmentDoesNotAllocateAfterWarmup() {
    asserter.startRecording();
    page.renderThumbnailTo(renderBuffer, 256);
    asserter.assertNoAllocations(STEADY_STATE_TOLERANCE);
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void renderToSegmentDoesNotAllocateAfterWarmup() {
    asserter.startRecording();
    page.renderTo(renderBuffer, 256, 256, 256 * 4, RenderFlags.DEFAULT.value(), 0xFFFFFFFF);
    asserter.assertNoAllocations(STEADY_STATE_TOLERANCE);
  }

  static boolean pdfiumAvailable() {
    try {
      PdfiumLibrary.initialize();
      return true;
    } catch (Throwable _) {
      return false;
    }
  }

  private static Path findCorpusPdf() {
    try {
      return AllocationTestUtils.getTestPdf(PdfRenderAllocationTest.class);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to find test PDF", e);
    }
  }
}
