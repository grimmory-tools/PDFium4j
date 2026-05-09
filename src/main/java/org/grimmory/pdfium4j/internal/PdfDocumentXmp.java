package org.grimmory.pdfium4j.internal;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.grimmory.pdfium4j.PdfiumLibrary;

/** Internal helper for XMP metadata extraction and fallback scanning. */
public final class PdfDocumentXmp {

  private static final byte[] XMP_START = "<?xpacket begin".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] XMP_END = "<?xpacket end".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] XMP_TERM = "?>".getBytes(StandardCharsets.ISO_8859_1);
  private static final long FALLBACK_TAIL_SCAN_BYTES = 256L << 10;
  private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

  private PdfDocumentXmp() {}

  public static byte[] computeFallbackXmp(Path sourcePath, byte[] sourceBytes) {
    if (sourceBytes != null) {
      return extractXmpFromSegment(MemorySegment.ofArray(sourceBytes));
    }
    if (sourcePath != null) {
      try (FileChannel fc = FileChannel.open(sourcePath, StandardOpenOption.READ);
          Arena arena = Arena.ofConfined()) {
        long fileSize = fc.size();
        if (fileSize <= 0) return EMPTY_BYTE_ARRAY;
        long tailSize = Math.min(fileSize, FALLBACK_TAIL_SCAN_BYTES);
        long tailStart = fileSize - tailSize;
        MemorySegment tail = fc.map(FileChannel.MapMode.READ_ONLY, tailStart, tailSize, arena);
        byte[] xmp = extractXmpFromSegment(tail);
        if (xmp.length > 0 || tailStart == 0) return xmp;
        // Fallback for uncommon PDFs where the packet is not in the tail window.
        MemorySegment full = fc.map(FileChannel.MapMode.READ_ONLY, 0, fileSize, arena);
        return extractXmpFromSegment(full);
      } catch (IOException e) {
        PdfiumLibrary.ignore(e);
      }
    }
    return EMPTY_BYTE_ARRAY;
  }

  public static byte[] extractXmpFromSegment(MemorySegment pdf) {
    long start = lastIndexOf(pdf, XMP_START);
    if (start < 0) return EMPTY_BYTE_ARRAY;

    long end = indexOf(pdf, XMP_END, start);
    if (end < 0) return EMPTY_BYTE_ARRAY;

    long term = indexOf(pdf, XMP_TERM, end);
    if (term < 0) return EMPTY_BYTE_ARRAY;

    return pdf.asSlice(start, term + 2 - start).toArray(JAVA_BYTE);
  }

  private static long indexOf(MemorySegment segment, byte[] pattern, long start) {
    long limit = segment.byteSize() - pattern.length;
    for (long i = start; i <= limit; i++) {
      if (matches(segment, i, pattern)) return i;
    }
    return -1;
  }

  private static long lastIndexOf(MemorySegment segment, byte[] pattern) {
    long limit = segment.byteSize() - pattern.length;
    for (long i = limit; i >= 0; i--) {
      if (matches(segment, i, pattern)) return i;
    }
    return -1;
  }

  private static boolean matches(MemorySegment segment, long pos, byte[] pattern) {
    for (int i = 0; i < pattern.length; i++) {
      if (segment.get(JAVA_BYTE, pos + i) != pattern[i]) return false;
    }
    return true;
  }
}
