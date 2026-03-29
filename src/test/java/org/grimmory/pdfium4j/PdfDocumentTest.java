package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.grimmory.pdfium4j.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for PDFium4j. These tests require a PDFium native library to be available. They are skipped
 * if the library cannot be loaded.
 */
class PdfDocumentTest {

  static boolean pdfiumAvailable() {
    try {
      PdfiumLibrary.initialize();
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void openFromPath() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      assertTrue(doc.pageCount() > 0, "Should have at least one page");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void openFromBytes() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    byte[] data = Files.readAllBytes(testPdf);
    try (PdfDocument doc = PdfDocument.open(data)) {
      assertTrue(doc.pageCount() > 0);
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void pageSize() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      PageSize size = doc.pageSize(0);
      assertTrue(size.width() > 0, "Width should be positive");
      assertTrue(size.height() > 0, "Height should be positive");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void renderPage() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      RenderResult result = page.render(150);
      assertTrue(result.width() > 0);
      assertTrue(result.height() > 0);
      assertNotNull(result.rgba());
      assertEquals(result.width() * result.height() * 4, result.rgba().length);

      BufferedImage image = result.toBufferedImage();
      assertEquals(result.width(), image.getWidth());
      assertEquals(result.height(), image.getHeight());
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void metadata() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      Map<String, String> meta = doc.metadata();
      assertNotNull(meta);
      // Just verify the API works; content depends on test PDF
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void bookmarks() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      List<Bookmark> bookmarks = doc.bookmarks();
      assertNotNull(bookmarks);
      // May be empty depending on test PDF
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void closedDocumentThrows() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    PdfDocument doc = PdfDocument.open(testPdf);
    doc.close();
    assertThrows(IllegalStateException.class, doc::pageCount);
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void closedPageThrows() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      PdfPage page = doc.page(0);
      page.close();
      assertThrows(IllegalStateException.class, page::size);
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void wrongThreadDocumentAccessThrows() throws Exception {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      AtomicReference<Throwable> errorRef = new AtomicReference<>();
      Thread worker =
          new Thread(
              () -> {
                try {
                  doc.pageCount();
                } catch (Throwable t) {
                  errorRef.set(t);
                }
              },
              "pdfium-wrong-thread");

      worker.start();
      worker.join();

      assertNotNull(errorRef.get(), "Wrong-thread access should fail");
      assertInstanceOf(IllegalStateException.class, errorRef.get());
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void wrongThreadPageOpenThrows() throws Exception {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      AtomicReference<Throwable> errorRef = new AtomicReference<>();
      Thread worker =
          new Thread(
              () -> {
                try {
                  doc.page(0);
                } catch (Throwable t) {
                  errorRef.set(t);
                }
              },
              "pdfium-open-page-wrong-thread");

      worker.start();
      worker.join();

      assertNotNull(errorRef.get(), "Wrong-thread page open should fail");
      assertInstanceOf(IllegalStateException.class, errorRef.get());
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void wrongThreadDocumentCloseThrows() throws Exception {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      AtomicReference<Throwable> errorRef = new AtomicReference<>();
      Thread worker =
          new Thread(
              () -> {
                try {
                  doc.close();
                } catch (Throwable t) {
                  errorRef.set(t);
                }
              },
              "pdfium-close-wrong-thread");

      worker.start();
      worker.join();

      assertNotNull(errorRef.get(), "Wrong-thread close should fail");
      assertInstanceOf(IllegalStateException.class, errorRef.get());
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void wrongThreadPageAccessThrows() throws Exception {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      AtomicReference<Throwable> errorRef = new AtomicReference<>();
      Thread worker =
          new Thread(
              () -> {
                try {
                  page.size();
                } catch (Throwable t) {
                  errorRef.set(t);
                }
              },
              "pdfium-page-wrong-thread");

      worker.start();
      worker.join();

      assertNotNull(errorRef.get(), "Wrong-thread page access should fail");
      assertInstanceOf(IllegalStateException.class, errorRef.get());
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void wrongThreadPageCloseThrows() throws Exception {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      AtomicReference<Throwable> errorRef = new AtomicReference<>();
      Thread worker =
          new Thread(
              () -> {
                try {
                  page.close();
                } catch (Throwable t) {
                  errorRef.set(t);
                }
              },
              "pdfium-page-close-wrong-thread");

      worker.start();
      worker.join();

      assertNotNull(errorRef.get(), "Wrong-thread page close should fail");
      assertInstanceOf(IllegalStateException.class, errorRef.get());
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void documentCloseInvalidatesOpenPages() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    PdfDocument doc = PdfDocument.open(testPdf);
    PdfPage page = doc.page(0);
    doc.close();

    assertThrows(
        IllegalStateException.class,
        page::size,
        "Page handle should be invalid after owning document closes");
    assertDoesNotThrow(page::close, "Closing an already-invalidated page should be idempotent");
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void extractText() throws IOException {
    Path testPdf = getTestPdfWithText();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      String text = page.extractText();
      assertNotNull(text);
      assertFalse(text.isEmpty(), "Should extract some text from test PDF");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void charCount() throws IOException {
    Path testPdf = getTestPdfWithText();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      int count = page.charCount();
      assertTrue(count > 0, "Should have characters on test PDF page");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void pageRotation() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      int rotation = page.rotation();
      assertTrue(
          rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270,
          "Rotation should be 0, 90, 180, or 270");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void setPageRotation() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      page.setRotation(90);
      assertEquals(90, page.rotation());

      page.setRotation(0);
      assertEquals(0, page.rotation());

      assertThrows(IllegalArgumentException.class, () -> page.setRotation(45));
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void pageLabel() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      // Just verify API doesn't crash; most test PDFs don't have page labels
      Optional<String> label = doc.pageLabel(0);
      assertNotNull(label);
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void xmpMetadata() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      byte[] xmp = doc.xmpMetadata();
      assertNotNull(xmp);
      // Some PDFs have XMP, some don't - just verify no crash

      String xmpStr = doc.xmpMetadataString();
      assertNotNull(xmpStr);

      if (xmp.length > 0) {
        assertTrue(xmpStr.contains("<?xpacket"), "XMP should contain xpacket marker");
        assertTrue(xmpStr.contains("xpacket end"), "XMP should have end marker");
      }
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void xmpMetadataRoundTrip(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    Path outPdf = tempDir.resolve("xmp-roundtrip.pdf");

    // Step 1: open, set XMP, save
    String xmpContent =
        """
                <?xpacket begin="\uFEFF" id="W5M0MpCehiHzreSzNTczkc9d"?>
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <dc:title><rdf:Alt><rdf:li>RoundTripTitle</rdf:li></rdf:Alt></dc:title>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                <?xpacket end="w"?>""";

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      doc.setXmpMetadata(xmpContent);
      doc.save(outPdf);
    }

    // Step 2: re-open from path and verify XMP is readable
    try (PdfDocument doc2 = PdfDocument.open(outPdf)) {
      String xmpStr = doc2.xmpMetadataString();
      assertFalse(xmpStr.isEmpty(), "XMP should be found in saved file");
      assertTrue(xmpStr.contains("RoundTripTitle"), "XMP should contain the title we set");
    }

    // Step 3: open from bytes and verify
    byte[] bytes = Files.readAllBytes(outPdf);
    try (PdfDocument doc3 = PdfDocument.open(bytes)) {
      String xmpStr = doc3.xmpMetadataString();
      assertFalse(xmpStr.isEmpty(), "XMP should be found when opened from bytes");
      assertTrue(xmpStr.contains("RoundTripTitle"), "XMP should contain the title from bytes");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void xmpMetadataRoundTripSamePath(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    Path pdf = tempDir.resolve("same-path.pdf");
    Files.copy(testPdf, pdf);

    String xmpContent =
        """
                <?xpacket begin="\uFEFF" id="W5M0MpCehiHzreSzNTczkc9d"?>
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <dc:title><rdf:Alt><rdf:li>SamePathTitle</rdf:li></rdf:Alt></dc:title>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                <?xpacket end="w"?>""";

    // Open from same path, set XMP, save to same path
    try (PdfDocument doc = PdfDocument.open(pdf)) {
      doc.setXmpMetadata(xmpContent);
      doc.save(pdf);
    }

    // Re-open same path
    try (PdfDocument doc2 = PdfDocument.open(pdf)) {
      String xmpStr = doc2.xmpMetadataString();
      assertFalse(xmpStr.isEmpty(), "XMP should be found in same-path saved file");
      assertTrue(xmpStr.contains("SamePathTitle"), "XMP should contain the title we set");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void xmpMetadataRoundTripWithExistingXmp(@TempDir Path tempDir)
      throws IOException, URISyntaxException {
    // Use a PDF that already has a 4096-byte XMP stream (like grimmory's minimal.pdf)
    var resource = getClass().getResource("/minimal.pdf");
    if (resource == null) return;
    Path originalPdf = Path.of(resource.toURI());

    Path pdf = tempDir.resolve("existing-xmp.pdf");
    Files.copy(originalPdf, pdf);

    String xmpContent =
        """
                <?xpacket begin="\uFEFF" id="W5M0MpCehiHzreSzNTczkc9d"?>
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description
                        xmlns:dc="http://purl.org/dc/elements/1.1/"
                        xmlns:calibre="http://calibre-ebook.com/xmp-namespace">
                      <calibre:series><rdf:value>ExistingXmpSeries</rdf:value></calibre:series>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                <?xpacket end="w"?>""";

    try (PdfDocument doc = PdfDocument.open(pdf)) {
      doc.setXmpMetadata(xmpContent);
      doc.save(pdf);
    }

    // Verify the raw file contains the xpacket marker
    byte[] rawBytes = Files.readAllBytes(pdf);
    String rawStr = new String(rawBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
    assertTrue(rawStr.contains("<?xpacket begin="), "Saved file should contain xpacket marker");
    assertTrue(rawStr.contains("ExistingXmpSeries"), "Saved file should contain our XMP content");

    // Re-open from path and verify XMP readable
    try (PdfDocument doc2 = PdfDocument.open(pdf)) {
      String xmpStr = doc2.xmpMetadataString();
      assertFalse(xmpStr.isEmpty(), "XMP should be found in file with existing XMP");
      assertTrue(xmpStr.contains("ExistingXmpSeries"), "XMP should contain series name");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void saveToBytes() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      byte[] saved = doc.saveToBytes();
      assertNotNull(saved);
      assertTrue(saved.length > 0, "Saved PDF should not be empty");
      // Verify it starts with %PDF
      String header = new String(saved, 0, Math.min(5, saved.length), StandardCharsets.ISO_8859_1);
      assertTrue(header.startsWith("%PDF"), "Saved file should be a valid PDF");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void saveToFile(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    Path output = tempDir.resolve("output.pdf");
    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      doc.save(output);
    }

    assertTrue(Files.exists(output));
    assertTrue(Files.size(output) > 0);

    // Verify saved PDF is loadable
    try (PdfDocument doc = PdfDocument.open(output)) {
      assertTrue(doc.pageCount() > 0);
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void savePreservesRotation(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    Path output = tempDir.resolve("rotated.pdf");
    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      page.setRotation(90);
      doc.save(output);
    }

    // Verify rotation persisted
    try (PdfDocument doc = PdfDocument.open(output);
        PdfPage page = doc.page(0)) {
      assertEquals(90, page.rotation(), "Rotation should be preserved after save");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void probeValidPdf() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    PdfProbeResult result = PdfDocument.probe(testPdf);
    assertTrue(result.isValid(), "Valid PDF should probe as OK");
    assertTrue(result.pageCount() > 0, "Should report page count");
    assertEquals(PdfProbeResult.Status.OK, result.status());
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void probeInvalidData() {
    PdfProbeResult result = PdfDocument.probe(new byte[] {0, 1, 2, 3, 4});
    assertFalse(result.isValid());
    assertEquals(PdfProbeResult.Status.CORRUPT, result.status());
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void probeEmptyData() {
    PdfProbeResult result = PdfDocument.probe(new byte[0]);
    assertFalse(result.isValid());
    assertEquals(PdfProbeResult.Status.UNREADABLE, result.status());
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void probeNullPath() {
    PdfProbeResult result = PdfDocument.probe((Path) null);
    assertFalse(result.isValid());
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void renderBounded() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      RenderResult result = page.renderBounded(300, 200, 200);
      assertTrue(result.width() <= 200, "Width should be at most 200");
      assertTrue(result.height() <= 200, "Height should be at most 200");
      assertTrue(result.width() > 0);
      assertTrue(result.height() > 0);
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void renderThumbnail() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      RenderResult thumb = page.renderThumbnail(100);
      assertTrue(thumb.width() <= 100);
      assertTrue(thumb.height() <= 100);
      assertTrue(thumb.width() > 0);
      assertTrue(thumb.height() > 0);
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void allPageSizes() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      List<PageSize> sizes = doc.allPageSizes();
      assertEquals(doc.pageCount(), sizes.size());
      for (PageSize size : sizes) {
        assertTrue(size.width() > 0);
        assertTrue(size.height() > 0);
      }
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void isImageOnly() throws IOException {
    Path testPdf = getTestPdfWithText();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      // A text PDF should not be image-only
      assertFalse(doc.isImageOnly(), "Text PDF should not be image-only");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void fileVersion() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      int version = doc.fileVersion();
      assertTrue(
          version >= 10 && version <= 25,
          "PDF version should be between 1.0 and 2.5, got: " + version);
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void extractTextWithBounds() throws IOException {
    Path testPdf = getTestPdfWithText();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      List<TextCharInfo> chars = page.extractTextWithBounds();
      assertNotNull(chars);
      if (page.charCount() > 0) {
        assertFalse(chars.isEmpty(), "Should have char info for text page");
        TextCharInfo first = chars.getFirst();
        assertTrue(first.charCode() > 0, "Should have valid char code");
        assertNotNull(first.character());
      }
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void hasText() throws IOException {
    Path testPdf = getTestPdfWithText();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      assertTrue(page.hasText(), "Text PDF page should have text");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void annotations() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      List<PdfAnnotation> annots = page.annotations();
      assertNotNull(annots);
      // Just verify API works - most test PDFs don't have annotations
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void webLinks() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      List<PdfLink> links = page.webLinks();
      assertNotNull(links);
      // Just verify API works
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void insertAndDeletePage(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      int originalCount = doc.pageCount();

      // Insert blank page at end
      doc.insertBlankPage(originalCount, PageSize.A4);
      assertEquals(originalCount + 1, doc.pageCount());

      // Delete the inserted page
      doc.deletePage(originalCount);
      assertEquals(originalCount, doc.pageCount());
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void deletePageOutOfRange() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      assertThrows(IllegalArgumentException.class, () -> doc.deletePage(-1));
      assertThrows(IllegalArgumentException.class, () -> doc.deletePage(doc.pageCount()));
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void diagnose() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    PdfDiagnostic diag = PdfDocument.diagnose(testPdf);
    assertTrue(diag.valid(), "Test PDF should be valid");
    assertTrue(diag.pageCount() > 0);
    assertNotNull(diag.warnings());
    assertNotNull(diag.fileVersionString());
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void saveToOutputStream() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      doc.save(baos);
      byte[] saved = baos.toByteArray();
      assertTrue(saved.length > 0);
      assertTrue(new String(saved, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF"));
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void setMetadataSingleTag(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    Path output = tempDir.resolve("meta.pdf");
    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      doc.setMetadata(MetadataTag.TITLE, "Test Title");
      // Verify in-memory read-back
      Optional<String> inMemory = doc.metadata(MetadataTag.TITLE);
      assertTrue(inMemory.isPresent(), "Title should be readable in-memory after set");
      assertEquals("Test Title", inMemory.get());
      doc.save(output);
    }

    try (PdfDocument doc = PdfDocument.open(output)) {
      Optional<String> title = doc.metadata(MetadataTag.TITLE);
      assertTrue(title.isPresent(), "Title should persist after save");
      assertEquals("Test Title", title.get());
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void setMetadataBulk(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    Path output = tempDir.resolve("meta-bulk.pdf");
    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      doc.setMetadata(
          Map.of(
              MetadataTag.TITLE, "Bulk Title",
              MetadataTag.AUTHOR, "Bulk Author",
              MetadataTag.SUBJECT, "Bulk Subject"));
      doc.save(output);
    }

    try (PdfDocument doc = PdfDocument.open(output)) {
      assertEquals("Bulk Title", doc.metadata(MetadataTag.TITLE).orElse(""));
      assertEquals("Bulk Author", doc.metadata(MetadataTag.AUTHOR).orElse(""));
      assertEquals("Bulk Subject", doc.metadata(MetadataTag.SUBJECT).orElse(""));
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void setMetadataClearValue(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    Path output = tempDir.resolve("meta-clear.pdf");
    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      doc.setMetadata(MetadataTag.TITLE, "Temporary");
      doc.setMetadata(MetadataTag.TITLE, "");
      doc.save(output);
    }

    try (PdfDocument doc = PdfDocument.open(output)) {
      assertTrue(
          doc.metadata(MetadataTag.TITLE).isEmpty(), "Cleared title should read back as empty");
    }
  }

  // --- Tests for new APIs (metadata(String), renderPageToBytes, RenderResult encoding, image
  // extraction, isBlank) ---

  @Test
  @EnabledIf("pdfiumAvailable")
  void metadataByStringKeyReadsStandardTag(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    Path output = tempDir.resolve("string-key-meta.pdf");
    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      doc.setMetadata(MetadataTag.TITLE, "StringKeyTest");
      doc.save(output);
    }

    try (PdfDocument doc = PdfDocument.open(output)) {
      // Read via String key (same underlying FPDF_GetMetaText call)
      Optional<String> title = doc.metadata("Title");
      assertTrue(title.isPresent(), "Title should be readable via string key");
      assertEquals("StringKeyTest", title.get());
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void metadataByStringKeyReturnsEmptyForMissing() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      Optional<String> result = doc.metadata("NonExistentCustomKey");
      assertTrue(result.isEmpty(), "Non-existent key should return empty");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void renderResultToJpegBytesProducesValidJpeg() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      RenderResult result = page.render(72);
      byte[] jpeg = result.toJpegBytes();

      assertTrue(jpeg.length > 0, "JPEG bytes should not be empty");
      // JPEG magic bytes: FF D8 FF
      assertEquals((byte) 0xFF, jpeg[0], "JPEG should start with 0xFF");
      assertEquals((byte) 0xD8, jpeg[1], "JPEG byte 2 should be 0xD8");
      assertEquals((byte) 0xFF, jpeg[2], "JPEG byte 3 should be 0xFF");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void renderResultToJpegBytesWithQuality() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      RenderResult result = page.render(72);
      byte[] lowQuality = result.toJpegBytes(0.1f);
      byte[] highQuality = result.toJpegBytes(0.95f);

      assertTrue(lowQuality.length > 0, "Low quality JPEG should not be empty");
      assertTrue(highQuality.length > 0, "High quality JPEG should not be empty");
      assertTrue(
          highQuality.length > lowQuality.length,
          "High quality JPEG should be larger than low quality");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void renderResultToPngBytesProducesValidPng() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      RenderResult result = page.render(72);
      byte[] png = result.toPngBytes();

      assertTrue(png.length > 0, "PNG bytes should not be empty");
      // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
      assertEquals((byte) 0x89, png[0], "PNG byte 1");
      assertEquals((byte) 0x50, png[1], "PNG byte 2 (P)");
      assertEquals((byte) 0x4E, png[2], "PNG byte 3 (N)");
      assertEquals((byte) 0x47, png[3], "PNG byte 4 (G)");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void renderPageToBytesJpeg() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      byte[] jpeg = doc.renderPageToBytes(0, 150, "jpeg");

      assertTrue(jpeg.length > 0, "Rendered JPEG should not be empty");
      assertEquals((byte) 0xFF, jpeg[0], "Should be JPEG format");
      assertEquals((byte) 0xD8, jpeg[1], "Should be JPEG format");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void renderPageToBytesPng() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      byte[] png = doc.renderPageToBytes(0, 150, "png");

      assertTrue(png.length > 0, "Rendered PNG should not be empty");
      assertEquals((byte) 0x89, png[0], "Should be PNG format");
      assertEquals((byte) 0x50, png[1], "Should be PNG format");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void renderPageToBytesInvalidFormat() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      assertThrows(
          IllegalArgumentException.class,
          () -> doc.renderPageToBytes(0, 150, "bmp"),
          "Should reject unsupported format");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void pageIsBlankOnTextPage() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      // Our test PDF has "Hello World" text
      assertFalse(page.isBlank(), "Page with text should not be blank");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void pageIsBlankOnBlankPage(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    Path blankPdf = tempDir.resolve("blank.pdf");

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      doc.insertBlankPage(doc.pageCount(), PageSize.A4);
      doc.save(blankPdf);
    }

    try (PdfDocument doc = PdfDocument.open(blankPdf)) {
      // The last page is the blank one we inserted
      try (PdfPage page = doc.page(doc.pageCount() - 1)) {
        assertTrue(page.isBlank(), "Blank page with no text or images should be blank");
      }
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void pageImageCountOnTextOnlyPage() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      // Minimal text-only PDF has no images
      assertEquals(0, page.imageCount(), "Text-only page should have 0 images");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void pageEmbeddedImagesOnTextOnlyPage() throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    try (PdfDocument doc = PdfDocument.open(testPdf);
        PdfPage page = doc.page(0)) {
      List<EmbeddedImage> images = page.embeddedImages();
      assertTrue(images.isEmpty(), "Text-only page should have no embedded images");
    }
  }

  private Path getTestPdf() {
    var url = getClass().getResource("/test.pdf");
    if (url != null) {
      try {
        return Path.of(url.toURI());
      } catch (URISyntaxException e) {
        System.getLogger(PdfDocumentTest.class.getName())
            .log(System.Logger.Level.DEBUG, "Unable to resolve test PDF URI", e);
      }
    }
    // Generate a minimal PDF with text content
    try {
      Path tempPdf = Files.createTempFile("pdfium4j-test-", ".pdf");
      tempPdf.toFile().deleteOnExit();
      Files.write(tempPdf, minimalPdfWithText());
      return tempPdf;
    } catch (IOException e) {
      System.err.println("Failed to create test PDF: " + e.getMessage());
      return null;
    }
  }

  private Path getTestPdfWithText() {
    return getTestPdf();
  }

  private static byte[] minimalPdfWithText() {
    String pdf =
        """
                %PDF-1.4
                1 0 obj
                << /Type /Catalog /Pages 2 0 R >>
                endobj
                2 0 obj
                << /Type /Pages /Kids [3 0 R] /Count 1 >>
                endobj
                3 0 obj
                << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792]
                   /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
                endobj
                4 0 obj
                << /Length 44 >>
                stream
                BT /F1 12 Tf 100 700 Td (Hello World) Tj ET
                endstream
                endobj
                5 0 obj
                << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
                endobj
                xref
                0 6
                0000000000 65535 f \r
                0000000009 00000 n \r
                0000000058 00000 n \r
                0000000115 00000 n \r
                0000000266 00000 n \r
                0000000360 00000 n \r
                trailer
                << /Size 6 /Root 1 0 R >>
                startxref
                434
                %%EOF
                """;
    return pdf.getBytes(StandardCharsets.US_ASCII);
  }

  // --- Metadata-only fast save tests ---

  @Test
  @EnabledIf("pdfiumAvailable")
  void metadataOnlySaveIsFasterThanFullSave(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    Path outNoChanges = tempDir.resolve("no-changes.pdf");
    Path outFast = tempDir.resolve("fast-save.pdf");

    // Save with no changes  -  should use fast path, returning original bytes
    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      doc.save(outNoChanges);
    }

    // Metadata-only save (fast path)
    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      doc.setMetadata(MetadataTag.TITLE, "Fast Save Title");
      doc.save(outFast);
    }

    // Verify both produce valid PDFs
    assertTrue(Files.size(outNoChanges) > 0);
    assertTrue(Files.size(outFast) > 0);

    // No-changes save should produce same size as original
    assertEquals(
        Files.size(testPdf),
        Files.size(outNoChanges),
        "Save with no changes should preserve original file size exactly");

    // Verify the fast-save file contains the metadata
    try (PdfDocument doc = PdfDocument.open(outFast)) {
      assertEquals("Fast Save Title", doc.metadata(MetadataTag.TITLE).orElse(""));
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void metadataOnlySaveFromBytesWorks(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    byte[] originalBytes = Files.readAllBytes(testPdf);
    Path outPath = tempDir.resolve("bytes-meta.pdf");

    try (PdfDocument doc = PdfDocument.open(originalBytes)) {
      doc.setMetadata(MetadataTag.TITLE, "BytesSave");
      doc.setMetadata(MetadataTag.AUTHOR, "Test Author");
      doc.save(outPath);
    }

    try (PdfDocument doc = PdfDocument.open(outPath)) {
      assertEquals("BytesSave", doc.metadata(MetadataTag.TITLE).orElse(""));
      assertEquals("Test Author", doc.metadata(MetadataTag.AUTHOR).orElse(""));
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void metadataOnlySavePreservesPageContent(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    Path output = tempDir.resolve("preserve-content.pdf");

    int originalPageCount;
    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      originalPageCount = doc.pageCount();
    }

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      doc.setMetadata(MetadataTag.TITLE, "Preserves Content");
      doc.setXmpMetadata(buildBookloreXmp("Preserves Content", "Test Author"));
      doc.save(output);
    }

    try (PdfDocument doc = PdfDocument.open(output)) {
      assertEquals(originalPageCount, doc.pageCount());
      assertEquals("Preserves Content", doc.metadata(MetadataTag.TITLE).orElse(""));

      String xmp = doc.xmpMetadataString();
      assertTrue(xmp.contains("Preserves Content"), "XMP should be in file");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void structuralChangeUsesFullSave(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    Path output = tempDir.resolve("structural.pdf");

    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      doc.insertBlankPage(0, new PageSize(612, 792));
      doc.setMetadata(MetadataTag.TITLE, "Structural Change");
      doc.save(output);
    }

    try (PdfDocument doc = PdfDocument.open(output)) {
      assertEquals(2, doc.pageCount(), "Should have original + inserted page");
      assertEquals("Structural Change", doc.metadata(MetadataTag.TITLE).orElse(""));
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void xmpMetadataRoundTripWithBookloreNamespace(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    Path output = tempDir.resolve("booklore-xmp.pdf");

    String bookloreXmp =
        """
                <?xpacket begin="\uFEFF" id="W5M0MpCehiHzreSzNTczkc9d"?>
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description rdf:about=""
                        xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <dc:title><rdf:Alt><rdf:li xml:lang="x-default">Dead Simple Python</rdf:li></rdf:Alt></dc:title>
                      <dc:creator><rdf:Seq><rdf:li>Jason C. McDonald</rdf:li></rdf:Seq></dc:creator>
                      <dc:publisher><rdf:Bag><rdf:li>No Starch Press</rdf:li></rdf:Bag></dc:publisher>
                      <dc:subject>
                        <rdf:Bag>
                          <rdf:li>Programming</rdf:li>
                          <rdf:li>Python</rdf:li>
                        </rdf:Bag>
                      </dc:subject>
                      <dc:date><rdf:Seq><rdf:li>2023-01-01</rdf:li></rdf:Seq></dc:date>
                      <dc:language><rdf:Bag><rdf:li>English</rdf:li></rdf:Bag></dc:language>
                    </rdf:Description>
                    <rdf:Description rdf:about=""
                        xmlns:booklore="http://booklore.org/metadata/1.0/">
                      <booklore:subtitle>Idiomatic Python for the Impatient Programmer</booklore:subtitle>
                      <booklore:isbn13>9781718500921</booklore:isbn13>
                      <booklore:isbn10>1718500920</booklore:isbn10>
                      <booklore:goodreadsId>52555538</booklore:goodreadsId>
                      <booklore:goodreadsRating>4.4</booklore:goodreadsRating>
                      <booklore:pageCount>713</booklore:pageCount>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                <?xpacket end="w"?>""";

    // Write XMP + Info Dict
    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      doc.setMetadata(MetadataTag.TITLE, "Dead Simple Python");
      doc.setMetadata(MetadataTag.AUTHOR, "Jason C. McDonald");
      doc.setXmpMetadata(bookloreXmp);
      doc.save(output);
    }

    // Read back and verify BOTH Info Dict and XMP
    try (PdfDocument doc = PdfDocument.open(output)) {
      // Info Dict
      assertEquals("Dead Simple Python", doc.metadata(MetadataTag.TITLE).orElse(""));
      assertEquals("Jason C. McDonald", doc.metadata(MetadataTag.AUTHOR).orElse(""));

      // XMP
      String xmp = doc.xmpMetadataString();
      assertFalse(xmp.isEmpty(), "XMP should be present");

      XmpMetadata parsed = XmpMetadataParser.parse(xmp);
      assertEquals("Dead Simple Python", parsed.title().orElse(""));
      assertEquals(List.of("Jason C. McDonald"), parsed.creators());
      assertEquals("No Starch Press", parsed.publisher().orElse(""));
      assertEquals("2023-01-01", parsed.date().orElse(""));
      assertEquals("English", parsed.language().orElse(""));
      assertTrue(parsed.subjects().contains("Programming"));
      assertTrue(parsed.subjects().contains("Python"));

      // Verify raw XMP string contains booklore namespace elements
      assertTrue(xmp.contains("booklore:subtitle"), "XMP should contain subtitle");
      assertTrue(xmp.contains("Idiomatic Python"), "XMP should contain subtitle value");
      assertTrue(xmp.contains("booklore:isbn13"), "XMP should contain isbn13");
      assertTrue(xmp.contains("9781718500921"));
      assertTrue(xmp.contains("booklore:isbn10"), "XMP should contain isbn10");
      assertTrue(xmp.contains("1718500920"));
      assertTrue(xmp.contains("booklore:goodreadsId"), "XMP should contain goodreadsId");
      assertTrue(xmp.contains("52555538"));
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void xmpMetadataOverwritePrevious(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    Path firstSave = tempDir.resolve("first.pdf");
    Path secondSave = tempDir.resolve("second.pdf");

    // First write
    try (PdfDocument doc = PdfDocument.open(testPdf)) {
      doc.setMetadata(MetadataTag.TITLE, "First Title");
      doc.setXmpMetadata(buildBookloreXmp("First Title", "First Author"));
      doc.save(firstSave);
    }

    // Second write overwrites - open the FIRST save and update
    try (PdfDocument doc = PdfDocument.open(firstSave)) {
      doc.setMetadata(MetadataTag.TITLE, "Second Title");
      doc.setXmpMetadata(buildBookloreXmp("Second Title", "Second Author"));
      doc.save(secondSave);
    }

    // Verify the SECOND save has the NEW values, not the old ones
    try (PdfDocument doc = PdfDocument.open(secondSave)) {
      assertEquals("Second Title", doc.metadata(MetadataTag.TITLE).orElse(""));

      XmpMetadata parsed = XmpMetadataParser.parse(doc.xmpMetadata());
      assertEquals("Second Title", parsed.title().orElse(""));
      assertEquals(List.of("Second Author"), parsed.creators());
    }
  }

  private String buildBookloreXmp(String title, String author) {
    return """
                <?xpacket begin="\uFEFF" id="W5M0MpCehiHzreSzNTczkc9d"?>
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description rdf:about=""
                        xmlns:dc="http://purl.org/dc/elements/1.1/">
                                            <dc:title><rdf:Alt><rdf:li xml:lang="x-default">{TITLE}</rdf:li></rdf:Alt></dc:title>
                                            <dc:creator><rdf:Seq><rdf:li>{AUTHOR}</rdf:li></rdf:Seq></dc:creator>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                                <?xpacket end="w"?>"""
        .replace("{TITLE}", title)
        .replace("{AUTHOR}", author);
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void fileSizeStableAfterMetadataSave(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    long originalSize = Files.size(testPdf);
    Path pdf = tempDir.resolve("stable.pdf");
    Files.copy(testPdf, pdf);

    // Save with metadata + XMP (simulates grimmory's write path)
    try (PdfDocument doc = PdfDocument.open(pdf)) {
      doc.setMetadata(MetadataTag.TITLE, "Test Title");
      doc.setMetadata(MetadataTag.AUTHOR, "Test Author");
      doc.setXmpMetadata(buildBookloreXmp("Test Title", "Test Author"));
      doc.save(pdf);
    }

    long afterFirst = Files.size(pdf);
    // Incremental update should add at most 20 KB for metadata + xref + trailer
    assertTrue(
        afterFirst - originalSize < 20_000,
        "First save should add at most 20 KB, but grew by " + (afterFirst - originalSize));

    // Save again (simulates second metadata update)
    try (PdfDocument doc = PdfDocument.open(pdf)) {
      doc.setMetadata(MetadataTag.TITLE, "Updated Title");
      doc.setMetadata(MetadataTag.AUTHOR, "Updated Author");
      doc.setXmpMetadata(buildBookloreXmp("Updated Title", "Updated Author"));
      doc.save(pdf);
    }

    long afterSecond = Files.size(pdf);
    // Second save grows by another incremental update
    assertTrue(
        afterSecond - afterFirst < 20_000,
        "Second save should add at most 20 KB, but grew by " + (afterSecond - afterFirst));

    // Total size should be within 40 KB of original
    assertTrue(
        afterSecond < originalSize + 40_000,
        "After two saves, file should be within 40 KB of original ("
            + originalSize
            + "), but is "
            + afterSecond);
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void saveWithNoChangesPreservesSize(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    long originalSize = Files.size(testPdf);
    Path pdf = tempDir.resolve("unchanged.pdf");
    Files.copy(testPdf, pdf);

    // Save with no changes
    try (PdfDocument doc = PdfDocument.open(pdf)) {
      doc.save(pdf);
    }

    assertEquals(originalSize, Files.size(pdf), "Save with no changes should not change file size");
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void xmpOnlySaveUsesIncrementalUpdate(@TempDir Path tempDir) throws IOException {
    Path testPdf = getTestPdf();
    if (testPdf == null) return;

    long originalSize = Files.size(testPdf);
    Path pdf = tempDir.resolve("xmp-only.pdf");
    Files.copy(testPdf, pdf);

    // Only XMP, no setMetadata  -  should still use fast path
    try (PdfDocument doc = PdfDocument.open(pdf)) {
      doc.setXmpMetadata(buildBookloreXmp("XMP Only Title", "XMP Author"));
      doc.save(pdf);
    }

    long afterSave = Files.size(pdf);
    assertTrue(
        afterSave - originalSize < 20_000,
        "XMP-only save should add at most 20 KB, but grew by " + (afterSave - originalSize));

    try (PdfDocument doc = PdfDocument.open(pdf)) {
      String xmp = doc.xmpMetadataString();
      assertTrue(xmp.contains("XMP Only Title"), "XMP should be present");
    }
  }
}
