package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.List;
import org.grimmory.pdfium4j.model.PdfStructureElement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShimStructureTreeTest {

  private static final Path MINIMAL_PDF = Path.of("src/test/resources/minimal.pdf");

  @BeforeAll
  static void setup() {
    PdfiumLibrary.initialize();
  }

  @Test
  void testStructureTreeRead() {
    try (PdfDocument doc = PdfDocument.open(MINIMAL_PDF)) {
      try (PdfPage page = doc.page(0)) {
        List<PdfStructureElement> tree = page.structureTree();
        assertNotNull(tree);
        // Minimal PDF might not have structure, but we check it doesn't crash
      }
    }
  }
}
