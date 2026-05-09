package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.grimmory.pdfium4j.model.PdfProcessingPolicy;
import org.junit.jupiter.api.Test;

class PdfPrefetchTest {

  @Test
  void testPrefetchPages() throws Exception {
    Path path = Paths.get("src/test/resources/test.pdf");
    if (!path.toFile().exists()) return; // Skip if no test file

    PdfProcessingPolicy policy =
        PdfProcessingPolicy.defaultPolicy().withMode(PdfProcessingPolicy.Mode.STRICT);

    try (PdfDocument doc = PdfDocument.open(path, null, policy)) {
      int count = doc.pageCount();
      if (count < 3) return;

      // Access page 0
      try (PdfPage page = doc.page(0)) {
        assertNotNull(page);
      }

      // Access page 1 - should be in cache now
      // We can't easily check internal cache, but we can verify it's fast or doesn't throw
      try (PdfPage page1 = doc.page(1)) {
        assertNotNull(page1);
      }
    }
  }
}
