package org.grimmory.pdfium4j;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * KOReader-compatible partial MD5 checksum implementation.
 *
 * <p>Samples 1024-byte windows at offsets derived from LuaJIT bit shifts: 0, 1K, 4K, 16K, 64K,
 * 256K, 1M, 4M, 16M, 64M, 256M, 1G.
 *
 * <p>Uses Modern Java 25 MemorySegment for high-performance streaming access.
 */
public final class KoReaderChecksum {

  private static final int SAMPLE_SIZE = 1024;
  private static final int STEP = 1024;

  private KoReaderChecksum() {}

  /**
   * Calculate checksum for a file path using memory-mapped I/O.
   *
   * @param path file to sample
   * @return checksum hex string, or empty if input is invalid/unreadable
   */
  public static Optional<String> calculate(Path path) {
    if (path == null) return Optional.empty();

    try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
      long fileSize = fc.size();

      // Use shared arena for potential parallel access or scoped lifetime
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment segment = fc.map(FileChannel.MapMode.READ_ONLY, 0, fileSize, arena);
        return Optional.of(calculateFromSegment(segment));
      }
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  /**
   * Calculate checksum for in-memory bytes using {@link MemorySegment}.
   *
   * @param data document bytes
   * @return checksum hex string, or empty for null input
   */
  public static Optional<String> calculate(byte[] data) {
    if (data == null || data.length == 0) return Optional.empty();
    return Optional.of(calculateFromSegment(MemorySegment.ofArray(data)));
  }

  private static String calculateFromSegment(MemorySegment segment) {
    MessageDigest md5 = createMd5();
    long byteSize = segment.byteSize();

    for (int i = -1; i <= 10; i++) {
      long position = samplePosition(i);
      if (position >= byteSize) break;

      long len = Math.min(SAMPLE_SIZE, byteSize - position);
      if (len <= 0) break;

      // Modern Java 25: Efficiently update MD5 from MemorySegment
      md5.update(segment.asSlice(position, len).asByteBuffer());
    }

    return HexFormat.of().formatHex(md5.digest());
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
