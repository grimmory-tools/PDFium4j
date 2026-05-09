package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShimXmpTest {

  private static final Path SAMPLE_PDF = Path.of("src/test/resources/minimal.pdf");

  @BeforeAll
  static void setup() {
    PdfiumLibrary.initialize();
  }

  @Test
  void testStandardMetadataUtf8() {
    try (PdfDocument doc = PdfDocument.open(SAMPLE_PDF)) {
      String title = "UTF-8 Title: ☕";
      doc.setMetadata(MetadataTag.TITLE, title);

      assertEquals(title, doc.metadata(MetadataTag.TITLE).orElse(""));
    }
  }
}
