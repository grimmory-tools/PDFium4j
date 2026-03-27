package org.grimmory.pdfium4j.internal;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
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
    byte[] encoded = text.getBytes(StandardCharsets.UTF_16LE);
    MemorySegment seg = arena.allocate(encoded.length + 2L);
    MemorySegment.copy(encoded, 0, seg, ValueLayout.JAVA_BYTE, 0, encoded.length);
    seg.set(ValueLayout.JAVA_BYTE, encoded.length, (byte) 0);
    seg.set(ValueLayout.JAVA_BYTE, encoded.length + 1, (byte) 0);
    return seg;
  }

  /**
   * Decode a UTF-16LE buffer returned by PDFium into a Java String.
   *
   * @param seg the MemorySegment containing UTF-16LE data
   * @param byteLen total bytes in the buffer (including the 2-byte null terminator)
   */
  public static String fromWideString(MemorySegment seg, long byteLen) {
    if (byteLen <= 2) return "";
    byte[] data = seg.asSlice(0, byteLen - 2).toArray(ValueLayout.JAVA_BYTE);
    return new String(data, StandardCharsets.UTF_16LE);
  }

  /**
   * Decode a null-terminated UTF-8 buffer into a Java String.
   *
   * @param seg the MemorySegment containing the string
   * @param byteLen total bytes including the null terminator
   */
  public static String fromByteString(MemorySegment seg, long byteLen) {
    if (byteLen <= 1) return "";
    byte[] data = seg.asSlice(0, byteLen - 1).toArray(ValueLayout.JAVA_BYTE);
    return new String(data, StandardCharsets.UTF_8);
  }

  /** Convert a raw pointer (as MemorySegment) check for NULL. */
  public static boolean isNull(MemorySegment seg) {
    return seg == null || seg.equals(MemorySegment.NULL) || seg.address() == 0;
  }
}
