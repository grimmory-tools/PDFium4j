package org.pdfium4j;

import org.pdfium4j.exception.PdfCorruptException;
import org.pdfium4j.model.PdfDiagnostic;
import org.pdfium4j.model.PdfProbeResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PDF handling with pathological test cases.
 * These tests verify that PDFium4j handles malformed, corrupted,
 * and edge-case PDFs gracefully without crashing.
 */
public class PathologicalPdfTest {

    @Test
    void testProbeEmptyFile() {
        byte[] empty = new byte[0];
        PdfProbeResult result = PdfDocument.probe(empty);
        
        assertFalse(result.isValid());
        assertEquals(PdfProbeResult.Status.UNREADABLE, result.status());
    }

    @Test
    void testProbeInvalidHeader() {
        byte[] notPdf = "This is not a PDF file".getBytes(StandardCharsets.UTF_8);
        PdfProbeResult result = PdfDocument.probe(notPdf);
        
        assertFalse(result.isValid());
        assertEquals(PdfProbeResult.Status.CORRUPT, result.status());
    }

    @Test
    void testProbeTruncatedPdf() {
        byte[] truncated = new byte[] {
                '%', 'P', 'D', 'F', '-', '1', '.', '4', '\n',
                '1', ' ', '0', ' ', 'o', 'b', 'j', '\n',
                '<', '<', '>', '>', '\n',
                'e', 'n', 'd', 'o', 'b', 'j'
        };
        
        PdfProbeResult result = PdfDocument.probe(truncated);
        assertNotNull(result);
        assertFalse(result.isValid() || result.status() == PdfProbeResult.Status.OK);
    }

    @Test
    void testDiagnoseCorruptPdf() throws IOException {
        Path tempFile = Files.createTempFile("corrupt-", ".pdf");
        try {
            Files.write(tempFile, "This is garbage data, not a PDF".getBytes());
            
            PdfDiagnostic diagnostic = PdfDocument.diagnose(tempFile);
            assertFalse(diagnostic.valid());
            assertFalse(diagnostic.warnings().isEmpty());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testOpenCorruptPdf() throws IOException {
        Path tempFile = Files.createTempFile("corrupt-open-", ".pdf");
        try {
            Files.write(tempFile, "Corrupted PDF content".getBytes());
            assertThrows(PdfCorruptException.class, () -> PdfDocument.open(tempFile));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Disabled("Requires encrypted PDF fixture")
    @Test
    void testPasswordProtectedHandling() {
    }

    @Test
    void testImageOnlyDetectionEmpty() {
        byte[] minimal = new byte[0];
        PdfProbeResult result = PdfDocument.probe(minimal);
        assertFalse(result.isValid());
    }

    @Disabled("Requires large-dimension PDF fixture")
    @Test
    void testLargeDimensionHandling() {
    }

    @Test
    void testBitFlipFuzzing() {
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

        java.util.Random random = new java.util.Random(42);
        for (int i = 0; i < 50; i++) {
            byte[] mutated = validPdf.clone();
            int flipPos = random.nextInt(mutated.length);
            int bitPos = random.nextInt(8);
            mutated[flipPos] ^= (byte) (1 << bitPos);

            try {
                PdfProbeResult result = PdfDocument.probe(mutated);
                assertNotNull(result);
            } catch (Exception e) {
                fail("Bit-flip mutation " + i + " threw exception: " + e.getMessage());
            }
        }
    }

    @Test
    void testProbeNullPath() {
        PdfProbeResult result = PdfDocument.probe((Path) null);
        assertNotNull(result);
        assertFalse(result.isValid());
        assertEquals(PdfProbeResult.Status.UNREADABLE, result.status());
    }

    @Test
    void testProbeNullData() {
        PdfProbeResult result = PdfDocument.probe((byte[]) null);
        assertNotNull(result);
        assertFalse(result.isValid());
        assertEquals(PdfProbeResult.Status.UNREADABLE, result.status());
    }

    @Test
    void testDiagnoseNullPath() {
        PdfDiagnostic diagnostic = PdfDocument.diagnose(null);
        assertNotNull(diagnostic);
        assertFalse(diagnostic.valid());
    }
}
