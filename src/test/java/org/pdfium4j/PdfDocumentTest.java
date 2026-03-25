package org.pdfium4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.pdfium4j.model.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PDFium4j. These tests require a PDFium native library to be available.
 * They are skipped if the library cannot be loaded.
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
            Thread worker = new Thread(() -> {
                try {
                    doc.pageCount();
                } catch (Throwable t) {
                    errorRef.set(t);
                }
            }, "pdfium-wrong-thread");

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
            Thread worker = new Thread(() -> {
                try {
                    doc.page(0);
                } catch (Throwable t) {
                    errorRef.set(t);
                }
            }, "pdfium-open-page-wrong-thread");

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

        PdfDocument doc = PdfDocument.open(testPdf);
        try {
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    doc.close();
                } catch (Throwable t) {
                    errorRef.set(t);
                }
            }, "pdfium-close-wrong-thread");

            worker.start();
            worker.join();

            assertNotNull(errorRef.get(), "Wrong-thread close should fail");
            assertInstanceOf(IllegalStateException.class, errorRef.get());
        } finally {
            doc.close();
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
            Thread worker = new Thread(() -> {
                try {
                    page.size();
                } catch (Throwable t) {
                    errorRef.set(t);
                }
            }, "pdfium-page-wrong-thread");

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
            Thread worker = new Thread(() -> {
                try {
                    page.close();
                } catch (Throwable t) {
                    errorRef.set(t);
                }
            }, "pdfium-page-close-wrong-thread");

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

        assertThrows(IllegalStateException.class, page::size,
                "Page handle should be invalid after owning document closes");
        assertDoesNotThrow(page::close,
                "Closing an already-invalidated page should be idempotent");
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
            assertTrue(rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270,
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
    void saveToBytes() throws IOException {
        Path testPdf = getTestPdf();
        if (testPdf == null) return;

        try (PdfDocument doc = PdfDocument.open(testPdf)) {
            byte[] saved = doc.saveToBytes();
            assertNotNull(saved);
            assertTrue(saved.length > 0, "Saved PDF should not be empty");
            // Verify it starts with %PDF
            String header = new String(saved, 0, Math.min(5, saved.length));
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
        PdfProbeResult result = PdfDocument.probe(new byte[]{0, 1, 2, 3, 4});
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
            assertTrue(version >= 10 && version <= 25,
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
            assertTrue(new String(saved, 0, 5).startsWith("%PDF"));
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
            doc.setMetadata(Map.of(
                    MetadataTag.TITLE, "Bulk Title",
                    MetadataTag.AUTHOR, "Bulk Author",
                    MetadataTag.SUBJECT, "Bulk Subject"
            ));
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
            assertTrue(doc.metadata(MetadataTag.TITLE).isEmpty(),
                    "Cleared title should read back as empty");
        }
    }

    private Path getTestPdf() {
        var url = getClass().getResource("/test.pdf");
        if (url != null) {
            return Path.of(url.getPath());
        }

        // Check JPDFium test resources
        Path jpdfiumTest = Path.of("/home/balazs/IdeaProjects/JPDFium/jpdfium/src/test/resources/pdfs/minimal.pdf");
        if (Files.exists(jpdfiumTest)) return jpdfiumTest;

        Path[] candidates = {
                Path.of("/home/balazs/IdeaProjects/JPDFium/jpdfium/src/test/resources/pdfs/general/minimal.pdf"),
                Path.of("/home/balazs/IdeaProjects/JPDFium/jpdfium/src/test/resources/pdfs/general/basic-text.pdf"),
                Path.of("/tmp/test.pdf"),
        };
        for (Path p : candidates) {
            if (Files.exists(p)) return p;
        }

        System.err.println("No test PDF found, skipping test");
        return null;
    }

    private Path getTestPdfWithText() {
        // Prefer a PDF that has text content
        Path[] candidates = {
                Path.of("/home/balazs/IdeaProjects/JPDFium/jpdfium/src/test/resources/pdfs/general/basic-text.pdf"),
                Path.of("/home/balazs/IdeaProjects/JPDFium/jpdfium/src/test/resources/pdfs/general/mozilla_tracemonkey.pdf"),
        };
        for (Path p : candidates) {
            if (Files.exists(p)) return p;
        }
        // Fall back to any test PDF
        return getTestPdf();
    }
}
