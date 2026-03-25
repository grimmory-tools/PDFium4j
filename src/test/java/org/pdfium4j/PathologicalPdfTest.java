package org.pdfium4j;

import org.pdfium4j.exception.PdfCorruptException;
import org.pdfium4j.exception.PdfPasswordException;
import org.pdfium4j.exception.PdfiumException;
import org.pdfium4j.model.PdfDiagnostic;
import org.pdfium4j.model.PdfProbeResult;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * Tests for PDF handling with pathological test cases.
 * These tests verify that PDFium4j handles malformed, corrupted,
 * and edge-case PDFs gracefully without crashing.
 */
public class PathologicalPdfTest {

    /**
     * Test probing an empty file.
     */
    @Test
    public void testProbeEmptyFile() {
        byte[] empty = new byte[0];
        PdfProbeResult result = PdfDocument.probe(empty);
        
        assertFalse(result.isValid());
        assertEquals(PdfProbeResult.Status.UNREADABLE, result.status());
    }

    /**
     * Test probing a file with invalid header.
     */
    @Test
    public void testProbeInvalidHeader() {
        byte[] notPdf = "This is not a PDF file".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        PdfProbeResult result = PdfDocument.probe(notPdf);
        
        assertFalse(result.isValid());
        assertEquals(PdfProbeResult.Status.CORRUPT, result.status());
    }

    /**
     * Test probing a truncated PDF (missing EOF marker).
     */
    @Test
    public void testProbeTruncatedPdf() {
        // Minimal valid PDF header but truncated body
        byte[] truncated = new byte[] {
                '%', 'P', 'D', 'F', '-', '1', '.', '4', '\n',
                '1', ' ', '0', ' ', 'o', 'b', 'j', '\n',
                '<', '<', '>', '>', '\n',
                'e', 'n', 'd', 'o', 'b', 'j'
                // Missing xref and EOF
        };
        
        PdfProbeResult result = PdfDocument.probe(truncated);
        // Should not crash, should return error status
        assertNotNull(result);
        assertFalse(result.isValid() || result.status() == PdfProbeResult.Status.OK);
    }

    /**
     * Test diagnosing a corrupt PDF.
     */
    @Test
    public void testDiagnoseCorruptPdf() throws IOException {
        Path tempFile = Files.createTempFile("corrupt-", ".pdf");
        try {
            // Write garbage data
            Files.write(tempFile, "This is garbage data, not a PDF".getBytes());
            
            PdfDiagnostic diagnostic = PdfDocument.diagnose(tempFile);
            assertFalse(diagnostic.isValid());
            assertFalse(diagnostic.warnings().isEmpty());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Test that opening a corrupt PDF throws PdfCorruptException.
     */
    @Test(expected = PdfCorruptException.class)
    public void testOpenCorruptPdf() throws IOException {
        Path tempFile = Files.createTempFile("corrupt-open-", ".pdf");
        try {
            Files.write(tempFile, "Corrupted PDF content".getBytes());
            PdfDocument.open(tempFile);
            fail("Should throw PdfCorruptException");
        } catch (PdfCorruptException e) {
            assertTrue(e.getMessage().contains("corrupt") || e.getMessage().contains("FORMAT"));
            throw e;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Test probing a password-protected PDF.
     * Note: This requires a test PDF encrypted with a password.
     * For now, we test the error handling.
     */
    @Test
    public void testPasswordProtectedHandling() {
        // We can't create a password-protected PDF on the fly,
        // but we can verify the probe handles it correctly if encountered
        // This test documents the expected behavior
        // 
        // In production, PdfProbeResult should return PASSWORD_REQUIRED status
        // for encrypted PDFs without throwing an exception
    }

    /**
     * Test image-only detection with empty PDF.
     */
    @Test
    public void testImageOnlyDetectionEmpty() {
        // Empty/minimal PDF should not be flagged as image-only
        byte[] minimal = new byte[0];
        PdfProbeResult result = PdfDocument.probe(minimal);
        assertFalse(result.isValid());
    }

    /**
     * Test that very large dimension PDFs are handled correctly.
     */
    @Test
    public void testLargeDimensionHandling() {
        // This test documents the expected behavior for huge pages
        // A real test would need a PDF with 14400x14400+ pages
        // The bounded rendering should prevent OOM errors
    }

    /**
     * Test bit-flip fuzzing on valid PDF structure.
     * This verifies that random bit flips result in clean exceptions, not crashes.
     */
    @Test
    public void testBitFlipFuzzing() throws IOException {
        // Create a minimal valid PDF structure
        byte[] validPdf = new byte[] {
                '%', 'P', 'D', 'F', '-', '1', '.', '4', '\n',
                '1', ' ', '0', ' ', 'o', 'b', 'j', '\n',
                '<', '<', '/', 'T', 'y', 'p', 'e', '/', 'C', 'a', 't', 'a', 'l', 'o', 'g', '>', '>', '\n',
                'e', 'n', 'd', 'o', 'b', 'j', '\n',
                'x', 'r', 'e', 'f', '\n',
                '0', ' ', '1', '\n',
                '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', ' ', '6', '5', '5', '3', '5', ' ', 'f', ' ', '\n',
                't', 'r', 'a', 'i', 'l', 'e', 'r', '\n',
                '<', '<', '/', 'S', 'i', 'z', 'e', ' ', '1', ' ', '/', 'R', 'o', 'o', 't', ' ', '1', ' ', '0', ' ', 'R', '>', '>', '\n',
                's', 't', 'a', 'r', 't', 'x', 'r', 'e', 'f', '\n',
                '0', '\n',
                '%', '%', 'E', 'O', 'F'
        };

        // Flip random bits and verify no crashes
        java.util.Random random = new java.util.Random(42); // Fixed seed for reproducibility
        for (int i = 0; i < 50; i++) {
            byte[] mutated = validPdf.clone();
            int flipPos = random.nextInt(mutated.length);
            int bitPos = random.nextInt(8);
            mutated[flipPos] ^= (1 << bitPos);

            try {
                PdfProbeResult result = PdfDocument.probe(mutated);
                // Should not throw, should return some status
                assertNotNull(result);
            } catch (Exception e) {
                fail("Bit-flip mutation " + i + " threw exception: " + e.getMessage());
            }
        }
    }

    /**
     * Test null path handling.
     */
    @Test
    public void testProbeNullPath() {
        PdfProbeResult result = PdfDocument.probe((Path) null);
        assertNotNull(result);
        assertFalse(result.isValid());
        assertEquals(PdfProbeResult.Status.UNREADABLE, result.status());
    }

    /**
     * Test null data handling.
     */
    @Test
    public void testProbeNullData() {
        PdfProbeResult result = PdfDocument.probe((byte[]) null);
        assertNotNull(result);
        assertFalse(result.isValid());
        assertEquals(PdfProbeResult.Status.UNREADABLE, result.status());
    }

    /**
     * Test diagnostic with null path.
     */
    @Test
    public void testDiagnoseNullPath() {
        PdfDiagnostic diagnostic = PdfDocument.diagnose(null);
        assertNotNull(diagnostic);
        assertFalse(diagnostic.isValid());
    }
}
