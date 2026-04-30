package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.grimmory.pdfium4j.exception.PdfiumRenderException;
import org.grimmory.pdfium4j.model.RenderResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class PdfPageRenderSafeTest {

  private static final Path TEST_PDF = Paths.get("src/test/resources/minimal.pdf");

  @BeforeAll
  static void setup() {
    PdfiumLibrary.initialize();
  }

  static boolean isPdfiumAvailable() {
    try {
      PdfiumLibrary.initialize();
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  @Test
  @EnabledIf("isPdfiumAvailable")
  void testRenderSafeThrowsWhenLimitExceeded() {
    try (PdfDocument doc = PdfDocument.open(TEST_PDF)) {
      try (PdfPage page = doc.page(0)) {
        // A standard render should be around 1-2 MB.
        // Set an extremely low limit (1 KB) to trigger the exception.
        assertThrows(PdfiumRenderException.class, () -> page.renderSafe(150, 1024));
      }
    }
  }

  @Test
  @EnabledIf("isPdfiumAvailable")
  void testRenderSafePassesWhenLimitSufficient() {
    try (PdfDocument doc = PdfDocument.open(TEST_PDF)) {
      try (PdfPage page = doc.page(0)) {
        // Set a generous limit (100 MB).
        RenderResult result = page.renderSafe(72, 100 * 1024 * 1024);
        assertNotNull(result);
        assertTrue(result.width() > 0);
        assertTrue(result.height() > 0);
      }
    }
  }
}
