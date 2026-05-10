package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.grimmory.pdfium4j.util.AllocationTestUtils;
import org.junit.jupiter.api.Test;

public class MetadataIntegrityTest {

  @Test
  public void testMetadataPreservationAndRoundTrip() throws IOException {
    Path source = findCorpusPdf();

    // 1. Read existing metadata
    Map<String, String> originalMeta;
    try (PdfDocument doc = PdfDocument.open(source)) {
      originalMeta = doc.metadata();
      System.out.println("Original Metadata: " + originalMeta);
    }

    // 2. Modify one field and save
    byte[] modifiedPdf;
    try (PdfDocument doc = PdfDocument.open(source)) {
      doc.setMetadata(MetadataTag.TITLE, "New Integrity Title");
      doc.setMetadata(MetadataTag.AUTHOR, "New Integrity Author");

      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      doc.save(bos);
      modifiedPdf = bos.toByteArray();
    }

    // 3. Re-read and verify
    try (PdfDocument doc = PdfDocument.open(modifiedPdf)) {
      Map<String, String> newMeta = doc.metadata();
      System.out.println("New Metadata: " + newMeta);

      // Verify new fields
      assertEquals("New Integrity Title", doc.metadata(MetadataTag.TITLE).orElse(null));
      assertEquals("New Integrity Author", doc.metadata(MetadataTag.AUTHOR).orElse(null));

      // Verify preservation of old fields (that were not overwritten)
      originalMeta.forEach(
          (key, value) -> {
            if (!key.equals(MetadataTag.TITLE.name()) && !key.equals(MetadataTag.AUTHOR.name())) {
              assertEquals(value, newMeta.get(key), "Field " + key + " should be preserved");
            }
          });
    }
  }

  @Test
  public void testNoDuplicateMetadataKeys() throws IOException {
    Path source = findCorpusPdf();
    try (PdfDocument doc = PdfDocument.open(source)) {
      // Set via tag
      doc.setMetadata(MetadataTag.TITLE, "Title via Tag");
      // Set via raw key (case-insensitive)
      doc.setMetadata("Title", "Title via String");

      Map<String, String> meta = doc.metadata();
      // Should NOT contain both "TITLE" and "Title"
      // Based on our logic, it should contain "TITLE" mapping to "Title via String"
      // because setMetadata("Title", ...) now routes to pendingMetadata
      assertEquals(
          1,
          meta.keySet().stream().filter(k -> k.equalsIgnoreCase("title")).count(),
          "Should not have duplicate Title keys (e.g. TITLE and Title)");
      assertEquals("Title via String", doc.metadata(MetadataTag.TITLE).orElse(null));
    }
  }

  private static Path findCorpusPdf() throws IOException {
    return AllocationTestUtils.getTestPdf(MetadataIntegrityTest.class);
  }
}
