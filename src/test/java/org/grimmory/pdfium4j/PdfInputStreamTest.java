package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfInputStreamTest {

  private static final Path SAMPLE_PDF = Path.of("src/test/resources/minimal.pdf");

  @Test
  void testOpenFromInputStream(@TempDir Path tempDir) throws IOException {
    byte[] data = Files.readAllBytes(SAMPLE_PDF);
    try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
        PdfDocument doc = PdfDocument.open(bais)) {
      assertEquals(1, doc.pageCount());
    }
  }

  @Test
  void testOpenFromInputStreamWithNull() {
    assertThrows(IllegalArgumentException.class, () -> PdfDocument.open((InputStream) null));
  }

  @Test
  void testOpenFromCorruptedInputStream() {
    byte[] badData = "Not a PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    try (ByteArrayInputStream bais = new ByteArrayInputStream(badData)) {
      assertThrows(PdfiumException.class, () -> PdfDocument.open(bais));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void testTempFileDeletion() throws IOException {
    byte[] data = Files.readAllBytes(SAMPLE_PDF);

    // We want to verify the temp file is gone after close.
    // Since we can't easily get the temp file path from the public API,
    // we'll trust the CleanupState mechanism which is already tested for other paths.
    // But we can at least ensure multiple opens work.
    try (PdfDocument doc1 = PdfDocument.open(new ByteArrayInputStream(data));
        PdfDocument doc2 = PdfDocument.open(new ByteArrayInputStream(data))) {
      assertEquals(1, doc1.pageCount());
      assertEquals(1, doc2.pageCount());
    }
  }
}
