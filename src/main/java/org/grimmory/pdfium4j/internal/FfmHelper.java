package org.grimmory.pdfium4j.internal;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

/**
 * Utility methods for Foreign Function &amp; Memory interop with PDFium.
 *
 * <p>Handles the string types PDFium uses:
 *
 * <ul>
 *   <li>{@code char*} (FPDF_BYTESTRING) - UTF-8 byte strings
 *   <li>{@code FPDF_WIDESTRING} (UTF-16LE) - used by bookmarks, metadata values
 * </ul>
 *
 * <p>Also provides the double-call buffer pattern used by dozens of PDFium APIs.
 */
public final class FfmHelper {

  private FfmHelper() {}

  /** Encode a Java String to a null-terminated UTF-16LE MemorySegment (FPDF_WIDESTRING). */
  public static MemorySegment toWideString(Arena arena, String text) {
    return arena.allocateFrom(text, StandardCharsets.UTF_16LE);
  }

  /**
   * Decode a UTF-16LE buffer returned by PDFium into a Java String.
   *
   * @param seg the MemorySegment containing UTF-16LE data
   * @param byteLen total bytes in the buffer (including the 2-byte null terminator)
   */
  public static String fromWideString(MemorySegment seg, long byteLen) {
    if (byteLen <= 2) return "";
    long boundedByteLen = Math.min(byteLen, seg.byteSize());
    long lenLong = boundedByteLen - 2;
    if (lenLong <= 0) return "";
    if (lenLong > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Wide string length exceeds supported bounds: " + lenLong);
    }
    int len = (int) lenLong;
    byte[] arr = seg.asSlice(0, len).toArray(JAVA_BYTE);
    return new String(arr, StandardCharsets.UTF_16LE);
  }

  /** Convert a raw pointer (as MemorySegment) check for NULL. */
  public static boolean isNull(MemorySegment seg) {
    return seg == null || seg.equals(MemorySegment.NULL) || seg.address() == 0;
  }
}
