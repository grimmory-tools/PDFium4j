package org.grimmory.pdfium4j.internal;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Utility methods for Foreign Function &amp; Memory interop with PDFium.
 *
 * <p>PDFium primarily uses two string types: fpdfBYTESTRING (UTF-8) and fpdfWIDESTRING (UTF-16LE).
 * These helpers centralize the conversion logic to ensure consistency and minimize heap
 * allocations.
 */
public final class FfmHelper {

  public static final Linker LINKER = Linker.nativeLinker();
  public static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

  private static final StableValue<Map<String, MemoryLayout>> CANONICAL_LAYOUTS = StableValue.of();

  private static Map<String, MemoryLayout> getCanonicalLayoutsSafe() {
    try {
      return LINKER.canonicalLayouts();
    } catch (Throwable e) {
      InternalLogger.warn("Could not retrieve canonical layouts from Linker: " + e.getMessage());
      return Map.of();
    }
  }

  private static Map<String, MemoryLayout> layouts() {
    return CANONICAL_LAYOUTS.orElseSet(FfmHelper::getCanonicalLayoutsSafe);
  }

  public static final ValueLayout.OfInt C_INT =
      (ValueLayout.OfInt) layouts().getOrDefault("int", ValueLayout.JAVA_INT);

  public static final ValueLayout C_LONG =
      (ValueLayout) layouts().getOrDefault("long", detectCLongLayout());

  private static ValueLayout detectCLongLayout() {
    String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    if (os.contains("win")) {
      return ValueLayout.JAVA_INT; // Windows (MSVC) 'long' is 32-bit
    }
    return ValueLayout.JAVA_LONG; // Linux/macOS 64-bit 'long' is 64-bit
  }

  /** Read a platform-dependent C 'long' value from the given memory segment. */
  public static long readCLong(MemorySegment seg, long offset) {
    if (C_LONG instanceof ValueLayout.OfLong ofLong) {
      return seg.get(ofLong, offset);
    } else {
      return seg.get((ValueLayout.OfInt) C_LONG, offset);
    }
  }

  /** Write a platform-dependent C 'long' value to the given memory segment. */
  public static void writeCLong(MemorySegment seg, long offset, long value) {
    if (C_LONG instanceof ValueLayout.OfLong ofLong) {
      seg.set(ofLong, offset, value);
    } else {
      seg.set((ValueLayout.OfInt) C_LONG, offset, (int) value);
    }
  }

  public static final ValueLayout C_SIZE_T =
      (ValueLayout) layouts().getOrDefault("size_t", ValueLayout.JAVA_LONG);

  public static final ValueLayout.OfInt C_BOOL =
      (ValueLayout.OfInt) layouts().getOrDefault("int", ValueLayout.JAVA_INT);

  public static final AddressLayout C_POINTER = ValueLayout.ADDRESS;

  /** Standard options for non-critical downcalls. */
  static final Linker.Option[] NO_OPTIONS = Generators.noOptions();

  /**
   * Options for critical downcalls that do NOT access Java heap. Fastest for trivial native calls.
   */
  static final Linker.Option[] CRITICAL_OPTIONS = {Linker.Option.critical(false)};

  /** Options for critical downcalls that MAY access Java heap. Useful for certain optimizations. */
  static final Linker.Option[] HEAP_CRITICAL_OPTIONS = {Linker.Option.critical(true)};

  private FfmHelper() {}

  /** Encode a Java String to a null-terminated UTF-16LE MemorySegment (fpdfWIDESTRING). */
  public static MemorySegment toWideString(Arena arena, String text) {
    return arena.allocateFrom(text, StandardCharsets.UTF_16LE);
  }

  /**
   * Decode a UTF-16LE buffer returned by PDFium into a Java String.
   *
   * <p>We subtract 2 bytes from the total length because PDFium's widestrings include a 2-byte null
   * terminator that is not part of the Java String content.
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
    byte[] arr = ScratchBuffer.getByteArray(len);
    MemorySegment.copy(seg, JAVA_BYTE, 0, arr, 0, len);
    return new String(arr, 0, len, StandardCharsets.UTF_16LE);
  }

  /**
   * Normalize a reported wide-string byte length from native code to a safe decode length.
   *
   * <p>Returns 0 when the value cannot represent a non-empty UTF-16LE null-terminated string.
   */
  public static long normalizeWideByteLength(MemorySegment seg, long reported, long requested) {
    long available = seg.byteSize();
    if (reported <= 0 || requested <= 2 || available <= 2) return 0;
    long max = Math.min(requested, available);
    long bounded = Math.min(reported, max);
    if ((bounded & 1L) != 0) {
      bounded -= 1;
    }
    if (bounded <= 2) return 0;
    if (hasTerminator(seg, bounded)) return bounded;

    long withTerminator = bounded + 2;
    if (withTerminator <= max && hasTerminator(seg, withTerminator)) {
      return withTerminator;
    }
    return 0;
  }

  private static boolean hasTerminator(MemorySegment seg, long byteLen) {
    if (byteLen < 2) return false;
    long idx = byteLen - 2;
    return seg.get(JAVA_BYTE, idx) == 0 && seg.get(JAVA_BYTE, idx + 1) == 0;
  }

  /**
   * Writes a Java string as a null-terminated UTF-8 string into the given segment.
   *
   * <p>We use a manual ASCII loop as a zero-allocation fast-path for the majority of PDF keys. For
   * strings containing non-ASCII characters, we fall back to standard UTF-8 encoding.
   */
  public static MemorySegment writeUtf8String(MemorySegment seg, String s) {
    long required = utf8ByteLengthWithNull(s);
    if (seg.byteSize() < required) {
      throw new IllegalArgumentException("Segment too small for UTF-8 string");
    }

    int len = s.length();

    // Single-pass fast-path for ASCII
    for (int i = 0; i < len; i++) {
      char c = s.charAt(i);
      if (c > 127) {
        // Fallback for UTF-8 if non-ASCII detected
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        MemorySegment.copy(bytes, 0, seg, JAVA_BYTE, 0, bytes.length);
        seg.set(JAVA_BYTE, bytes.length, (byte) 0);
        return seg.asSlice(0, (long) bytes.length + 1);
      }
      seg.set(JAVA_BYTE, i, (byte) c);
    }

    seg.set(JAVA_BYTE, len, (byte) 0);
    return seg.asSlice(0, required);
  }

  /** Check for native null pointers. */
  public static boolean isNull(MemorySegment seg) {
    return seg == null || seg.equals(MemorySegment.NULL) || seg.address() == 0;
  }

  /** Encode a Java String to a null-terminated UTF-16LE MemorySegment. */
  public static MemorySegment writeWideString(Arena arena, String text) {
    return arena.allocateFrom(text, StandardCharsets.UTF_16LE);
  }

  /** Encode a Java String to a null-terminated UTF-16LE MemorySegment (fpdfWIDESTRING). */
  public static MemorySegment writeWideString(MemorySegment seg, String text) {
    long len = text.length();
    long required = len * 2 + 2;
    if (seg.byteSize() < required) {
      throw new IllegalArgumentException("Segment too small for UTF-16 string");
    }
    for (int i = 0; i < len; i++) {
      char c = text.charAt(i);
      seg.set(JAVA_BYTE, (long) i * 2, (byte) (c & 0xFF));
      seg.set(JAVA_BYTE, (long) i * 2 + 1, (byte) (c >> 8));
    }
    seg.set(JAVA_BYTE, len * 2, (byte) 0);
    seg.set(JAVA_BYTE, len * 2 + 1, (byte) 0);
    return seg.asSlice(0, required);
  }

  /** Encode a Java String to a null-terminated UTF-8 MemorySegment. */
  public static MemorySegment writeUtf8String(Arena arena, String text) {
    return arena.allocateFrom(text, StandardCharsets.UTF_8);
  }

  /** Decode an ASCII/UTF-8 buffer into a Java String. */
  public static String readAsciiString(MemorySegment seg, long byteLen) {
    if (byteLen <= 1) return "";
    long lenLong = byteLen - 1; // remove null terminator
    int len = (int) Math.min(lenLong, Integer.MAX_VALUE);
    byte[] arr = ScratchBuffer.getByteArray(len);
    MemorySegment.copy(seg, JAVA_BYTE, 0, arr, 0, len);
    return new String(arr, 0, len, StandardCharsets.UTF_8);
  }

  /** Decode a UTF-8 buffer into a Java String. */
  public static String fromUtf8String(MemorySegment seg, int byteLen) {
    if (byteLen <= 1) return "";
    int len = byteLen - 1;
    byte[] arr = ScratchBuffer.getByteArray(len);
    MemorySegment.copy(seg, JAVA_BYTE, 0, arr, 0, len);
    return new String(arr, 0, len, StandardCharsets.UTF_8);
  }

  /** Calculate the byte length of a string in UTF-8 including the null terminator. */
  public static long utf8ByteLengthWithNull(String s) {
    return utf8ByteLength(s) + 1L;
  }

  /** Calculate the byte length of a string in UTF-8. */
  public static long utf8ByteLength(String s) {
    int len = s.length();
    for (int i = 0; i < len; i++) {
      if (s.charAt(i) > 127) {
        return s.getBytes(StandardCharsets.UTF_8).length;
      }
    }
    return len;
  }

  /** Writes a Java string as a UTF-8 string into the given segment (no null terminator). */
  public static void writeUtf8StringNoNull(MemorySegment seg, String s) {
    long required = utf8ByteLength(s);
    if (seg.byteSize() < required) {
      throw new IllegalArgumentException("Segment too small for UTF-8 string");
    }

    int len = s.length();
    for (int i = 0; i < len; i++) {
      char c = s.charAt(i);
      if (c > 127) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        MemorySegment.copy(bytes, 0, seg, JAVA_BYTE, 0, bytes.length);
        return;
      }
      seg.set(JAVA_BYTE, i, (byte) c);
    }
  }
}
