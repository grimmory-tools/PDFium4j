package org.pdfium4j;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * KOReader-compatible partial MD5 checksum implementation.
 *
 * <p>Samples 1024-byte windows at offsets derived from LuaJIT bit shifts:
 * 0, 1K, 4K, 16K, 64K, 256K, 1M, 4M, 16M, 64M, 256M, 1G.</p>
 */
public final class KoReaderChecksum {

    private static final int SAMPLE_SIZE = 1024;
    private static final int STEP = 1024;

    private KoReaderChecksum() {
    }

    /**
     * Calculate checksum for a file path.
     *
     * @param path file to sample
     * @return checksum hex string, or empty if input is invalid/unreadable
     */
    public static Optional<String> calculate(Path path) {
        if (path == null || !Files.exists(path)) {
            return Optional.empty();
        }

        MessageDigest md5 = createMd5();
        byte[] sampleBuffer = new byte[SAMPLE_SIZE];

        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            for (int i = -1; i <= 10; i++) {
                long position = samplePosition(i);
                raf.seek(position);
                int read = raf.read(sampleBuffer);
                if (read <= 0) {
                    break;
                }
                md5.update(sampleBuffer, 0, read);
            }
            return Optional.of(HexFormat.of().formatHex(md5.digest()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Calculate checksum for in-memory bytes.
     *
     * @param data document bytes
     * @return checksum hex string, or empty for null input
     */
    public static Optional<String> calculate(byte[] data) {
        if (data == null) {
            return Optional.empty();
        }

        MessageDigest md5 = createMd5();

        for (int i = -1; i <= 10; i++) {
            long position = samplePosition(i);
            if (position >= data.length) {
                break;
            }
            int len = (int) Math.min(SAMPLE_SIZE, data.length - position);
            if (len <= 0) {
                break;
            }
            md5.update(data, (int) position, len);
        }

        return Optional.of(HexFormat.of().formatHex(md5.digest()));
    }

    private static long samplePosition(int i) {
        int shiftCount = 2 * i;
        int maskedShift = shiftCount & 0x1F;
        long shifted = ((long) STEP) << maskedShift;
        return shifted & 0xFFFFFFFFL;
    }

    private static MessageDigest createMd5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm is not available", e);
        }
    }
}
