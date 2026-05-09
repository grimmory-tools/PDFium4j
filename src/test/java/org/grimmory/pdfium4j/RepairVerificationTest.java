package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.grimmory.pdfium4j.model.PdfProcessingPolicy;
import org.junit.jupiter.api.Test;

public class RepairVerificationTest {

  @Test
  public void testRepairBug2004951() {
    Path corruptPath = Paths.get("corpus/mozilla-pdfjs/bug2004951.pdf");
    assumeTrue(Files.exists(corruptPath), "Corpus file required: " + corruptPath);

    PdfProcessingPolicy recover = PdfProcessingPolicy.defaultPolicy();
    try (PdfDocument doc = PdfDocument.open(corruptPath, null, recover)) {
      assertTrue(doc.pageCount() > 0, "Repaired document should have pages");
    }
  }
}
