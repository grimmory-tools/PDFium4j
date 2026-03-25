package org.pdfium4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class KoReaderChecksumTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsEmptyForMissingPath() {
        Path missing = tempDir.resolve("missing.pdf");
        assertTrue(KoReaderChecksum.calculate(missing).isEmpty());
    }

    @Test
    void returnsEmptyForNullBytes() {
        assertTrue(KoReaderChecksum.calculate((byte[]) null).isEmpty());
    }

    @Test
    void emptyBytesMatchMd5OfEmpty() {
        String checksum = KoReaderChecksum.calculate(new byte[0]).orElseThrow();
        assertEquals(md5Hex(new byte[0]), checksum);
    }

    @Test
    void sameContentMatchesForBytesAndPath() throws IOException {
        byte[] content = ("pdfium4j-checksum-".repeat(4000)).getBytes(StandardCharsets.UTF_8);
        Path file = tempDir.resolve("book.pdf");
        Files.write(file, content);

        String fromBytes = KoReaderChecksum.calculate(content).orElseThrow();
        String fromPath = KoReaderChecksum.calculate(file).orElseThrow();

        assertEquals(fromBytes, fromPath);
    }

    @Test
    void differentContentProducesDifferentChecksum() {
        byte[] a = "A".repeat(3000).getBytes(StandardCharsets.UTF_8);
        byte[] b = "B".repeat(3000).getBytes(StandardCharsets.UTF_8);

        assertNotEquals(KoReaderChecksum.calculate(a).orElseThrow(),
                KoReaderChecksum.calculate(b).orElseThrow());
    }

    @Test
    void usesKoreaderSamplePositions() {
        byte[] sampleFile = createSamplePositionFile();
        String checksum = KoReaderChecksum.calculate(sampleFile).orElseThrow();

        assertEquals("2674126f0e2399f2e79453a1e49ebb74", checksum);
    }

    @Test
    void pdfDocumentConvenienceMethodsDelegateToKoreaderChecksum() throws IOException {
        byte[] content = ("pdf-document-wrapper-".repeat(2500)).getBytes(StandardCharsets.UTF_8);
        Path file = tempDir.resolve("doc.pdf");
        Files.write(file, content);

        assertEquals(KoReaderChecksum.calculate(content), PdfDocument.koReaderPartialMd5(content));
        assertEquals(KoReaderChecksum.calculate(file), PdfDocument.koReaderPartialMd5(file));
    }

    private static byte[] createSamplePositionFile() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("ZERO".repeat(256).getBytes(StandardCharsets.UTF_8));
        out.writeBytes("1K__".repeat(256).getBytes(StandardCharsets.UTF_8));
        out.writeBytes(new byte[2048]);
        out.writeBytes("4K__".repeat(256).getBytes(StandardCharsets.UTF_8));
        out.writeBytes(repeatByte(0x11, 16384 - 5120));
        out.writeBytes("16K_".repeat(256).getBytes(StandardCharsets.UTF_8));
        out.writeBytes(repeatByte(0x22, 65536 - 17408));
        out.writeBytes("64K_".repeat(256).getBytes(StandardCharsets.UTF_8));
        out.writeBytes(repeatByte(0xFF, 1024 * 100));
        return out.toByteArray();
    }

    private static byte[] repeatByte(int value, int count) {
        byte[] bytes = new byte[count];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static String md5Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
