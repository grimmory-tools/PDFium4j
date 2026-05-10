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
  private static final byte[] XMPMETA_START = "<x:xmpmeta".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] XMPMETA_END = "</x:xmpmeta>".getBytes(StandardCharsets.ISO_8859_1);

  private static final byte[] RDF_START = "<rdf:RDF".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] RDF_END = "</rdf:RDF>".getBytes(StandardCharsets.ISO_8859_1);
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
        byte[] tailXmp = extractXmpFromSegment(tail);
        if (tailStart == 0) return tailXmp;

        if (tailXmp.length == 0) {
          // Common case when XMP sits before the tail scan window.
          MemorySegment full = fc.map(FileChannel.MapMode.READ_ONLY, 0, fileSize, arena);
          return extractXmpFromSegment(full);
        }

        // If tail extraction found a canonical xpacket, accept it. Otherwise, run a full-file
        // scan and prefer that result to avoid accidental matches in trailing update sections.
        if (startsWith(tailXmp, XMP_START)) {
          return tailXmp;
        }

        // Fallback for uncommon PDFs where the packet is not in the tail window.
        MemorySegment full = fc.map(FileChannel.MapMode.READ_ONLY, 0, fileSize, arena);
        byte[] fullXmp = extractXmpFromSegment(full);
        return fullXmp.length > 0 ? fullXmp : tailXmp;
      } catch (IOException e) {
        PdfiumLibrary.ignore(e);
      }
    }
    return EMPTY_BYTE_ARRAY;
  }

  public static byte[] extractXmpFromSegment(MemorySegment pdf) {
    long searchFrom = 0;
    byte[] bestFound = EMPTY_BYTE_ARRAY;

    try {
      while (searchFrom < pdf.byteSize()) {
        long next = findEarliestBlockStart(pdf, searchFrom);
        if (next < 0) break;

        byte[] currentBlock;
        long jump;

        if (matches(pdf, next, XMP_START)) {
          currentBlock = extractXpacketAt(pdf, next);
          jump = XMP_START.length;
        } else if (matches(pdf, next, XMPMETA_START)) {
          currentBlock = extractBlock(pdf, XMPMETA_START, XMPMETA_END, next);
          jump = XMPMETA_START.length;
        } else {
          currentBlock = extractBlock(pdf, RDF_START, RDF_END, next);
          jump = RDF_START.length;
        }

        if (currentBlock.length > 0 && isMeaningfulXmp(currentBlock)) {
          bestFound = currentBlock;
        }
        searchFrom = next + Math.max(1, jump);
      }
    } catch (Exception e) {
      PdfiumLibrary.ignore(e);
    }
    return bestFound;
  }

  private static long findEarliestBlockStart(MemorySegment pdf, long searchFrom) {
    long nextXpacket = indexOf(pdf, XMP_START, searchFrom);
    long nextXmpmeta = indexOf(pdf, XMPMETA_START, searchFrom);
    long nextRdf = indexOf(pdf, RDF_START, searchFrom);

    long next = -1;
    if (nextXpacket >= 0) next = nextXpacket;
    if (nextXmpmeta >= 0 && (next < 0 || nextXmpmeta < next)) next = nextXmpmeta;
    if (nextRdf >= 0 && (next < 0 || nextRdf < next)) next = nextRdf;
    return next;
  }

  private static boolean isMeaningfulXmp(byte[] data) {
    if (data == null || data.length < 50) return false;
    String s = new String(data, StandardCharsets.UTF_8);
    // Must contain actual metadata content, not just an empty RDF wrapper.
    // We look for rdf:Description which is the container for actual properties.
    return s.contains("<rdf:Description") || s.contains(":Description");
  }

  private static byte[] extractXpacketAt(MemorySegment pdf, long start) {
    long afterStart = start + XMP_START.length;
    long end = indexOf(pdf, XMP_END, afterStart);

    if (end >= 0) {
      long term = indexOf(pdf, XMP_TERM, end + XMP_END.length);
      if (term >= 0) {
        return pdf.asSlice(start, term + XMP_TERM.length - start).toArray(JAVA_BYTE);
      }
    }
    return EMPTY_BYTE_ARRAY;
  }

  private static byte[] extractBlock(
      MemorySegment segment, byte[] startPattern, byte[] endPattern, long start) {
    long afterStart = start + startPattern.length;
    long end = indexOf(segment, endPattern, afterStart);

    // Skip unclosed blocks: if another start appears before an end, it's likely
    // a corrupted or partially rewritten stream.
    // RELAXED: only skip if we can't find an end at all.
    if (end < 0) {
      return EMPTY_BYTE_ARRAY;
    }

    long sliceLength = end + endPattern.length - start;
    return segment.asSlice(start, sliceLength).toArray(JAVA_BYTE);
  }

  private static long indexOf(MemorySegment segment, byte[] pattern, long start) {
    if (start < 0) return -1;
    long limit = segment.byteSize() - pattern.length;
    for (long i = start; i <= limit; i++) {
      if (matches(segment, i, pattern)) return i;
    }
    return -1;
  }

  private static boolean startsWith(byte[] data, byte[] prefix) {
    if (data.length < prefix.length) return false;
    for (int i = 0; i < prefix.length; i++) {
      if (data[i] != prefix[i]) return false;
    }
    return true;
  }

  private static boolean matches(MemorySegment segment, long pos, byte[] pattern) {
    for (int i = 0; i < pattern.length; i++) {
      if (segment.get(JAVA_BYTE, pos + i) != pattern[i]) return false;
    }
    return true;
  }
}
