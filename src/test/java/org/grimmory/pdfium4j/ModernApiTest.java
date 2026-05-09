package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.grimmory.pdfium4j.model.PdfAttachment;
import org.grimmory.pdfium4j.model.PdfStructureElement;
import org.grimmory.pdfium4j.model.RenderResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModernApiTest {

  private static final Path SAMPLE_PDF = Path.of("src/test/resources/minimal.pdf");

  @BeforeAll
  static void setup() {
    // Test Skia initialization
    PdfiumLibrary.setRendererType(1); // Skia
    PdfiumLibrary.initialize();
  }

  @Test
  void testSkiaRendering() {
    try (PdfDocument doc = PdfDocument.open(SAMPLE_PDF)) {
      try (PdfPage page = doc.page(0)) {
        RenderResult result = page.render(72);
        assertNotNull(result);
        assertTrue(result.width() > 0);
        assertTrue(result.height() > 0);
      }
    }
  }

  @Test
  void testStructureTree() {
    try (PdfDocument doc = PdfDocument.open(SAMPLE_PDF)) {
      try (PdfPage page = doc.page(0)) {
        List<PdfStructureElement> elements = page.structureTree();
        assertNotNull(elements);
        // Even if empty, it shouldn't crash
        System.out.println("Structure elements found: " + elements.size());
      }
    }
  }

  @Test
  void testAttachments() {
    try (PdfDocument doc = PdfDocument.open(SAMPLE_PDF)) {
      List<PdfAttachment> attachments = doc.attachments();
      assertNotNull(attachments);
      System.out.println("Attachments found: " + attachments.size());
    }
  }

  @Test
  void testThumbnail() {
    try (PdfDocument doc = PdfDocument.open(SAMPLE_PDF)) {
      try (PdfPage page = doc.page(0)) {
        page.getThumbnail()
            .ifPresent(
                thumb -> {
                  assertTrue(thumb.width() > 0);
                  System.out.println("Thumbnail found: " + thumb.width() + "x" + thumb.height());
                });
      }
    }
  }

  @Test
  void test64BitLoading() {
    // Small file but tests the 64-bit API path
    byte[] data = new byte[1024]; // Just a dummy, will fail format check but tests the entry point
    try {
      PdfDocument.open(data);
    } catch (Exception e) {
      // Expected failure due to bad format, but ensures FFM mapping works
      assertTrue(
          e.getMessage().contains("Failed to open document")
              || e.getMessage().contains("corruption"));
    }
  }
}
