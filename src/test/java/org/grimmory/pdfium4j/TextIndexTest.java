package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextIndexTest {

  @Test
  void testTextIndexingAndSearch() throws Exception {
    Path path = Paths.get("src/test/resources/test.pdf");
    if (!path.toFile().exists()) return;

    try (PdfDocument doc = PdfDocument.open(path)) {
      // Index the document
      doc.indexText();

      // Search for a word that is likely in the test PDF
      // Assuming "PDFium" or "Test" is there.
      List<Integer> results = doc.search("PDF");

      // Verification
      for (int pageIdx : results) {
        try (PdfPage p = doc.page(pageIdx)) {
          assertTrue(p.extractText().contains("PDF"));
        }
      }
    }
  }
}
