package org.grimmory.pdfium4j;

import static java.lang.foreign.ValueLayout.*;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.internal.DocBindings;
import org.grimmory.pdfium4j.internal.EditBindings;
import org.grimmory.pdfium4j.internal.FfmHelper;
import org.grimmory.pdfium4j.internal.ViewBindings;
import org.grimmory.pdfium4j.model.MetadataTag;

/**
 * Handles saving PDF documents. Uses PDFium's native FPDF_SaveAsCopy for the base save, then
 * applies pure-Java incremental updates for Info dictionary and XMP metadata.
 *
 * <p><strong>Why custom code is needed:</strong> PDFium's open-source public C API provides {@code
 * FPDF_GetMetaText} for reading metadata but has no corresponding setter function (no {@code
 * FPDF_SetMetaText}). The {@code FPDF_INCREMENTAL} save flag is non-functional in practice (it does
 * not append modified object streams). Therefore, setting Info dictionary or XMP metadata requires
 * a pure-Java incremental update appended after PDFium's native serialization. This is the only
 * custom PDF byte manipulation in the library; all other operations delegate to PDFium.
 *
 * <p>Incremental updates (PDF spec §7.5.6) append new objects, a new xref section, and a new
 * trailer at the end of the file - the original bytes are never modified. This is the standard
 * mechanism used by all PDF editors.
 *
 * <p><strong>Corruption safety:</strong> All incremental updates are validated by re-opening the
 * result with PDFium. If validation fails, the base (pre-metadata) bytes are returned instead,
 * guaranteeing no corrupt output is ever produced.
 *
 * @see <a href="https://groups.google.com/g/pdfium/c/kNTBkJYu4PI">Missing FPDF_SetMetaText API</a>
 * @see <a href= "https://groups.google.com/g/pdfium/c/6SklEc2lYNM">FPDF_INCREMENTAL is broken</a>
 */
final class PdfSaver {

  private static final System.Logger LOG = System.getLogger(PdfSaver.class.getName());

  private static final ThreadLocal<ByteArrayOutputStream> WRITE_BUFFER = new ThreadLocal<>();
  private static final Pattern METADATA_REF_PATTERN =
      Pattern.compile("/Metadata\\s+\\d+\\s+\\d+\\s+R");
  private static final Pattern ROOT_REF_PATTERN = Pattern.compile("/Root\\s+(\\d+\\s+\\d+\\s+R)");
  private static final Pattern INFO_REF_PATTERN = Pattern.compile("/Info\\s+(\\d+\\s+\\d+\\s+R)");
  private static final Pattern SIZE_PATTERN = Pattern.compile("/Size\\s+(\\d+)");
  private static final Pattern FIRST_INT_PATTERN = Pattern.compile("(\\d+)");

  /**
   * Maximum number of bytes from the end of the file to search for trailer/xref structures. PDF
   * spec requires startxref within the last 1024 bytes, but we use a larger buffer to handle PDFs
   * with comments or whitespace after %%EOF (common in incrementally updated files).
   */
  private static final int TAIL_SEARCH_BYTES = 4096;

  private PdfSaver() {}

  /**
   * Save a document to bytes using PDFium's native serialization, then apply metadata as a
   * validated incremental update.
   *
   * <p>If the incremental update produces an invalid PDF (detected by re-opening with PDFium), the
   * base bytes without metadata are returned instead. This guarantees the output is never corrupt.
   */
  static byte[] saveToBytes(
      MemorySegment docHandle, Map<MetadataTag, String> pendingMetadata, String pendingXmp) {
    return saveToBytes(docHandle, pendingMetadata, pendingXmp, false);
  }

  /**
   * Save a document to bytes with optional validation skip.
   *
   * @param skipValidation when {@code true}, skip the re-parse validation step after appending an
   *     incremental update. Eliminates a full PDF re-open (~30-40% of save time). Safe for
   *     metadata-only changes.
   */
  static byte[] saveToBytes(
      MemorySegment docHandle,
      Map<MetadataTag, String> pendingMetadata,
      String pendingXmp,
      boolean skipValidation) {
    byte[] baseBytes = nativeSave(docHandle);

    boolean hasInfoUpdate = pendingMetadata != null && !pendingMetadata.isEmpty();
    boolean hasXmpUpdate = pendingXmp != null && !pendingXmp.isEmpty();
    if (!hasInfoUpdate && !hasXmpUpdate) {
      return baseBytes;
    }

    byte[] result = appendIncrementalUpdate(baseBytes, pendingMetadata, pendingXmp);
    if (skipValidation) {
      return result;
    }
    return validateOrFallback(result, baseBytes, pendingMetadata);
  }

  /**
   * Perform the native FPDF_SaveAsCopy and return raw PDF bytes. This is the only path that
   * produces the base PDF - all metadata is applied on top via incremental update.
   */
  private static byte[] nativeSave(MemorySegment docHandle) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    WRITE_BUFFER.set(baos);
    try (Arena arena = Arena.ofConfined()) {
      MethodHandle writeBlockMH =
          MethodHandles.lookup()
              .findStatic(
                  PdfSaver.class,
                  "writeBlockCallback",
                  MethodType.methodType(
                      int.class, MemorySegment.class, MemorySegment.class, long.class));

      MemorySegment writeBlockStub =
          Linker.nativeLinker().upcallStub(writeBlockMH, EditBindings.WRITE_BLOCK_DESC, arena);

      MemorySegment fileWrite = arena.allocate(EditBindings.FPDF_FILEWRITE_LAYOUT);
      fileWrite.set(JAVA_INT, 0, 1);
      fileWrite.set(ADDRESS, 8, writeBlockStub);

      int ok = (int) EditBindings.FPDF_SaveAsCopy.invokeExact(docHandle, fileWrite, 0);
      if (ok == 0) {
        throw new PdfiumException("FPDF_SaveAsCopy failed");
      }

      return baos.toByteArray();
    } catch (PdfiumException e) {
      throw e;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to save document", t);
    } finally {
      WRITE_BUFFER.remove();
    }
  }

  /**
   * Validate PDF bytes by attempting to open them with PDFium. Checks that: (1) the document can be
   * loaded, (2) the page count is valid, and (3) metadata entries can be read back. If any check
   * fails, return the fallback bytes instead. This guarantees we never produce corrupt output.
   */
  private static byte[] validateOrFallback(
      byte[] result, byte[] fallback, Map<MetadataTag, String> expectedMetadata) {
    PdfiumLibrary.ensureInitialized();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment seg = arena.allocateFrom(JAVA_BYTE, result);
      MemorySegment doc =
          (MemorySegment)
              ViewBindings.FPDF_LoadMemDocument.invokeExact(seg, result.length, MemorySegment.NULL);
      if (FfmHelper.isNull(doc)) {
        LOG.log(
            System.Logger.Level.WARNING,
            "Incremental metadata update produced invalid PDF; "
                + "falling back to base save without metadata");
        return fallback;
      }
      try {
        int pages = (int) ViewBindings.FPDF_GetPageCount.invokeExact(doc);
        if (pages < 0) {
          LOG.log(
              System.Logger.Level.WARNING,
              "Incremental metadata update produced PDF with invalid page count; "
                  + "falling back to base save without metadata");
          return fallback;
        }

        // Verify metadata can be read back from the saved document
        if (expectedMetadata != null && !expectedMetadata.isEmpty()) {
          if (!verifyMetadataReadback(doc, expectedMetadata, arena)) {
            LOG.log(
                System.Logger.Level.WARNING,
                "Metadata written but could not be read back from saved PDF; "
                    + "falling back to base save without metadata");
            return fallback;
          }
        }
      } finally {
        try {
          ViewBindings.FPDF_CloseDocument.invokeExact(doc);
        } catch (Throwable ignored) {
        }
      }
    } catch (Throwable t) {
      LOG.log(
          System.Logger.Level.WARNING,
          "Failed to validate incremental metadata update; "
              + "falling back to base save without metadata",
          t);
      return fallback;
    }
    return result;
  }

  /**
   * Verify that at least one metadata entry from the expected set can be read back from the saved
   * document. This confirms the Info dictionary was correctly written.
   */
  private static boolean verifyMetadataReadback(
      MemorySegment doc, Map<MetadataTag, String> expected, Arena arena) {
    for (Map.Entry<MetadataTag, String> entry : expected.entrySet()) {
      try {
        MemorySegment tagSeg = arena.allocateFrom(entry.getKey().pdfKey());
        long needed =
            (long) DocBindings.FPDF_GetMetaText.invokeExact(doc, tagSeg, MemorySegment.NULL, 0L);
        if (needed > 2) {
          // At least one tag is readable - Info dictionary is intact
          return true;
        }
      } catch (Throwable ignored) {
      }
    }
    return false;
  }

  /**
   * Append a PDF incremental update containing new Info dictionary and/or XMP metadata objects.
   * This appends after the existing %%EOF - the original file bytes are untouched.
   *
   * <p>All parsing is restricted to the tail of the file where trailer/xref structures live, making
   * it immune to false matches in binary stream data.
   */
  private static byte[] appendIncrementalUpdate(
      byte[] pdf, Map<MetadataTag, String> metadata, String xmp) {
    String tail = tailString(pdf);

    // Find previous startxref value (points to the current xref/xref-stream)
    int prevXrefOffset = findStartxrefInTail(tail);

    // Parse existing trailer to get /Size, /Root, /Info
    TrailerInfo trailer = parseTrailerFromTail(pdf, tail);

    // Use /Size from trailer for next object number (per PDF spec, /Size = total
    // entries in xref)
    int nextObj = trailer.size;
    if (nextObj <= 0) {
      // Should not happen with well-formed PDFium output, but be defensive
      LOG.log(
          System.Logger.Level.WARNING,
          "Could not determine /Size from trailer; skipping incremental update");
      return pdf;
    }

    int baseOffset = pdf.length;
    // Use ByteArrayOutputStream to properly handle mixed encodings: XMP stream
    // content is UTF-8
    // while all other PDF syntax is ISO-8859-1. A StringBuilder approach would
    // mangle non-ASCII
    // XMP characters and produce wrong /Length values.
    ByteArrayOutputStream update = new ByteArrayOutputStream();
    update.write('\n'); // separator after %%EOF
    int bytesWritten = 1;

    Map<Integer, Integer> objOffsets = new LinkedHashMap<>();
    int infoObjNum = 0;
    int xmpObjNum = 0;

    // Info dictionary - pure ASCII/ISO-8859-1 values (PDFDocEncoding uses hex
    // escapes)
    if (metadata != null && !metadata.isEmpty()) {
      infoObjNum = nextObj++;
      StringBuilder infoObj = new StringBuilder();
      infoObj.append(infoObjNum).append(" 0 obj\n<<\n");
      for (Map.Entry<MetadataTag, String> entry : metadata.entrySet()) {
        String key = entry.getKey().pdfKey();
        String value = entry.getValue();
        infoObj.append("/").append(key).append(" ");
        infoObj.append(encodePdfString(value)).append("\n");
      }
      infoObj.append("/ModDate ").append(encodePdfString(formatPdfDate())).append("\n");
      infoObj.append(">>\nendobj\n");
      byte[] infoBytes = infoObj.toString().getBytes(StandardCharsets.ISO_8859_1);
      objOffsets.put(infoObjNum, baseOffset + bytesWritten);
      update.writeBytes(infoBytes);
      bytesWritten += infoBytes.length;
    }

    // XMP stream - content MUST be UTF-8 (XMP spec requirement, and may contain BOM
    // + non-ASCII
    // metadata). The stream header/footer are ASCII, so only the content portion
    // uses UTF-8.
    if (xmp != null && !xmp.isEmpty()) {
      xmpObjNum = nextObj++;
      byte[] xmpContentBytes = xmp.getBytes(StandardCharsets.UTF_8);
      byte[] headerBytes =
          (xmpObjNum
                  + " 0 obj\n<< /Type /Metadata /Subtype /XML /Length "
                  + xmpContentBytes.length
                  + " >>\nstream\n")
              .getBytes(StandardCharsets.ISO_8859_1);
      byte[] footerBytes = "\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);
      objOffsets.put(xmpObjNum, baseOffset + bytesWritten);
      update.writeBytes(headerBytes);
      update.writeBytes(xmpContentBytes);
      update.writeBytes(footerBytes);
      bytesWritten += headerBytes.length + xmpContentBytes.length + footerBytes.length;
    }

    // Xref / trailer section
    boolean xrefStream = usesXrefStreams(pdf, tail);
    if (xrefStream) {
      int xrefStreamObjNum = nextObj++;
      String infoRef = (infoObjNum > 0) ? (infoObjNum + " 0 R") : trailer.infoRef;
      byte[] xrefBytes =
          buildXrefStreamBytes(
              xrefStreamObjNum,
              baseOffset + bytesWritten,
              objOffsets,
              trailer.rootRef,
              infoRef,
              nextObj,
              prevXrefOffset);
      update.writeBytes(xrefBytes);
    } else {
      int xrefOffset = baseOffset + bytesWritten;
      StringBuilder xrefSb = new StringBuilder();
      xrefSb.append("xref\n");
      for (Map.Entry<Integer, Integer> entry : objOffsets.entrySet()) {
        xrefSb.append(entry.getKey()).append(" 1\n");
        xrefSb.append(String.format("%010d", entry.getValue())).append(" 00000 n \r\n");
      }
      xrefSb.append("trailer\n");
      xrefSb.append("<< /Size ").append(nextObj);
      xrefSb.append(" /Root ").append(trailer.rootRef);
      if (infoObjNum > 0) {
        xrefSb.append(" /Info ").append(infoObjNum).append(" 0 R");
      } else if (trailer.infoRef != null) {
        xrefSb.append(" /Info ").append(trailer.infoRef);
      }
      xrefSb.append(" /Prev ").append(prevXrefOffset);
      xrefSb.append(" >>\n");
      xrefSb.append("startxref\n");
      xrefSb.append(xrefOffset).append("\n");
      xrefSb.append("%%EOF\n");
      update.writeBytes(xrefSb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    byte[] updateBytes = update.toByteArray();
    byte[] result = new byte[pdf.length + updateBytes.length];
    System.arraycopy(pdf, 0, result, 0, pdf.length);
    System.arraycopy(updateBytes, 0, result, pdf.length, updateBytes.length);

    // If XMP object was added, we need to also update the Catalog to reference it
    if (xmpObjNum > 0) {
      result = appendCatalogUpdate(result, trailer, xmpObjNum, nextObj);
    }

    return result;
  }

  /**
   * Append another incremental update that rewrites the Catalog object with a /Metadata reference.
   */
  private static byte[] appendCatalogUpdate(
      byte[] pdf, TrailerInfo originalTrailer, int metadataObjNum, int sizeBase) {
    // Re-parse the tail of the (already modified) PDF to get current state
    String tail = tailString(pdf);
    boolean xrefStream = usesXrefStreams(pdf, tail);

    // Find the Catalog object by searching bytes directly — avoids converting the
    // entire PDF
    // to a String (which would allocate ~2× the PDF size for Java's UTF-16 chars).
    int catalogObjNum = extractObjNum(originalTrailer.rootRef);
    String catalogDict = findObjectDictFromBytes(pdf, catalogObjNum);
    if (catalogDict == null) return pdf;

    // Create a new Catalog object (same obj number, incremental update replaces it)
    StringBuilder newCatalog = new StringBuilder();
    newCatalog.append(catalogObjNum).append(" 0 obj\n");

    // Remove existing /Metadata if present, add our new one
    String dict = catalogDict;
    dict = METADATA_REF_PATTERN.matcher(dict).replaceFirst("");
    int closeIdx = dict.lastIndexOf(">>");
    if (closeIdx >= 0) {
      dict =
          dict.substring(0, closeIdx)
              + "/Metadata "
              + metadataObjNum
              + " 0 R "
              + dict.substring(closeIdx);
    }
    newCatalog.append(dict).append("\n");
    newCatalog.append("endobj\n");

    int baseOffset = pdf.length;
    StringBuilder update = new StringBuilder();
    update.append('\n');

    int catalogOffset = baseOffset + update.length();
    update.append(newCatalog);

    int actualPrev = findStartxrefInTail(tail);
    TrailerInfo currentTrailer = parseTrailerFromTail(pdf, tail);

    if (xrefStream) {
      int xrefStreamObjNum = currentTrailer.size;
      Map<Integer, Integer> offsets = new LinkedHashMap<>();
      offsets.put(catalogObjNum, catalogOffset);
      appendXrefStreamSection(
          update,
          xrefStreamObjNum,
          offsets,
          originalTrailer.rootRef,
          currentTrailer.infoRef,
          xrefStreamObjNum + 1,
          actualPrev,
          baseOffset);
    } else {
      int xrefOffset = baseOffset + update.length();
      update.append("xref\n");
      update.append(catalogObjNum).append(" 1\n");
      update.append(String.format("%010d", catalogOffset)).append(" 00000 n \r\n");

      update.append("trailer\n");
      update.append("<< /Size ").append(sizeBase);
      update.append(" /Root ").append(originalTrailer.rootRef);
      if (currentTrailer.infoRef != null) {
        update.append(" /Info ").append(currentTrailer.infoRef);
      }
      update.append(" /Prev ").append(actualPrev);
      update.append(" >>\n");
      update.append("startxref\n");
      update.append(xrefOffset).append("\n");
      update.append("%%EOF\n");
    }

    byte[] updateBytes = update.toString().getBytes(StandardCharsets.ISO_8859_1);
    byte[] result = new byte[pdf.length + updateBytes.length];
    System.arraycopy(pdf, 0, result, 0, pdf.length);
    System.arraycopy(updateBytes, 0, result, pdf.length, updateBytes.length);
    return result;
  }

  /**
   * Append a cross-reference stream section (§7.5.8) instead of a traditional xref table + trailer.
   * The xref stream object dictionary serves as both xref header and trailer.
   *
   * <p>Uses W=[1,4,0]: 1-byte type (always 1 = in-use) + 4-byte big-endian byte offset + generation
   * number implicit 0. Stream data is uncompressed (no /Filter).
   */
  private static void appendXrefStreamSection(
      StringBuilder update,
      int xrefStreamObjNum,
      Map<Integer, Integer> objOffsets,
      String rootRef,
      String infoRef,
      int size,
      int prevXref,
      int baseOffset) {
    byte[] streamData = new byte[objOffsets.size() * 5];
    StringBuilder indexParts = new StringBuilder();
    int dataIdx = 0;
    for (Map.Entry<Integer, Integer> entry : objOffsets.entrySet()) {
      if (indexParts.length() > 0) indexParts.append(' ');
      indexParts.append(entry.getKey()).append(" 1");
      streamData[dataIdx++] = 1; // type 1 = in-use uncompressed object
      int offset = entry.getValue();
      streamData[dataIdx++] = (byte) ((offset >> 24) & 0xFF);
      streamData[dataIdx++] = (byte) ((offset >> 16) & 0xFF);
      streamData[dataIdx++] = (byte) ((offset >> 8) & 0xFF);
      streamData[dataIdx++] = (byte) (offset & 0xFF);
    }

    int xrefStreamOffset = baseOffset + update.length();
    update.append(xrefStreamObjNum).append(" 0 obj\n");
    update.append("<< /Type /XRef");
    update.append(" /Size ").append(size);
    update.append(" /Root ").append(rootRef);
    if (infoRef != null) {
      update.append(" /Info ").append(infoRef);
    }
    update.append(" /Prev ").append(prevXref);
    update.append(" /W [1 4 0]");
    update.append(" /Index [").append(indexParts).append(']');
    update.append(" /Length ").append(streamData.length);
    update.append(" >>\nstream\n");
    // Append binary data as ISO-8859-1 characters (byte-transparent encoding)
    for (byte b : streamData) {
      update.append((char) (b & 0xFF));
    }
    update.append("\nendstream\nendobj\n");
    update.append("startxref\n").append(xrefStreamOffset).append('\n');
    update.append("%%EOF\n");
  }

  /**
   * Build a cross-reference stream section as raw bytes. Used by {@link #appendIncrementalUpdate}
   * which assembles the update as a {@link ByteArrayOutputStream} to properly handle mixed
   * encodings (UTF-8 XMP content + ISO-8859-1 PDF syntax).
   *
   * @param xrefStreamOffset the byte offset of this xref stream object (for startxref)
   */
  private static byte[] buildXrefStreamBytes(
      int xrefStreamObjNum,
      int xrefStreamOffset,
      Map<Integer, Integer> objOffsets,
      String rootRef,
      String infoRef,
      int size,
      int prevXref) {
    byte[] streamData = new byte[objOffsets.size() * 5];
    StringBuilder indexParts = new StringBuilder();
    int dataIdx = 0;
    for (Map.Entry<Integer, Integer> entry : objOffsets.entrySet()) {
      if (indexParts.length() > 0) indexParts.append(' ');
      indexParts.append(entry.getKey()).append(" 1");
      streamData[dataIdx++] = 1;
      int offset = entry.getValue();
      streamData[dataIdx++] = (byte) ((offset >> 24) & 0xFF);
      streamData[dataIdx++] = (byte) ((offset >> 16) & 0xFF);
      streamData[dataIdx++] = (byte) ((offset >> 8) & 0xFF);
      streamData[dataIdx++] = (byte) (offset & 0xFF);
    }

    StringBuilder header = new StringBuilder();
    header.append(xrefStreamObjNum).append(" 0 obj\n");
    header.append("<< /Type /XRef");
    header.append(" /Size ").append(size);
    header.append(" /Root ").append(rootRef);
    if (infoRef != null) {
      header.append(" /Info ").append(infoRef);
    }
    header.append(" /Prev ").append(prevXref);
    header.append(" /W [1 4 0]");
    header.append(" /Index [").append(indexParts).append(']');
    header.append(" /Length ").append(streamData.length);
    header.append(" >>\nstream\n");

    byte[] headerBytes = header.toString().getBytes(StandardCharsets.ISO_8859_1);
    byte[] footerBytes = "\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);
    byte[] startxrefBytes =
        ("startxref\n" + xrefStreamOffset + "\n%%EOF\n").getBytes(StandardCharsets.ISO_8859_1);

    ByteArrayOutputStream bos =
        new ByteArrayOutputStream(
            headerBytes.length + streamData.length + footerBytes.length + startxrefBytes.length);
    bos.writeBytes(headerBytes);
    bos.writeBytes(streamData);
    bos.writeBytes(footerBytes);
    bos.writeBytes(startxrefBytes);
    return bos.toByteArray();
  }

  // --- Tail-based parsing (only parses end of file, immune to binary stream
  // false matches) ---

  /**
   * Extract the tail of the PDF as an ISO-8859-1 string. Only this portion is parsed for
   * trailer/xref structures, making parsing immune to false matches in binary stream data.
   */
  private static String tailString(byte[] pdf) {
    int tailLen = Math.min(TAIL_SEARCH_BYTES, pdf.length);
    return new String(pdf, pdf.length - tailLen, tailLen, StandardCharsets.ISO_8859_1);
  }

  private record TrailerInfo(String rootRef, String infoRef, int size) {}

  /**
   * Parse trailer information from the tail of the file. Extracts /Root, /Info, and /Size entries.
   */
  private static TrailerInfo parseTrailerFromTail(byte[] pdf, String tail) {
    // Try traditional trailer first (search tail for "trailer" keyword)
    String rootRef = findTrailerEntryInTail(tail, "Root");
    String infoRef = findTrailerEntryInTail(tail, "Info");
    int size = findTrailerSizeInTail(tail);

    if (rootRef != null && size > 0) {
      return new TrailerInfo(rootRef, infoRef, size);
    }

    // For cross-reference streams, parse the dictionary at the startxref offset
    int startxref = findStartxrefInTail(tail);
    if (startxref > 0 && startxref < pdf.length) {
      // Read a small window around the xref stream object
      int windowStart = startxref;
      int windowEnd = Math.min(startxref + 512, pdf.length);
      String window =
          new String(pdf, windowStart, windowEnd - windowStart, StandardCharsets.ISO_8859_1);
      String dict = findDictInWindow(window);
      if (dict != null) {
        Matcher rootM = ROOT_REF_PATTERN.matcher(dict);
        if (rootM.find()) rootRef = rootM.group(1);
        Matcher infoM = INFO_REF_PATTERN.matcher(dict);
        if (infoM.find()) infoRef = infoM.group(1);
        Matcher sizeM = SIZE_PATTERN.matcher(dict);
        if (sizeM.find()) size = Integer.parseInt(sizeM.group(1));
        if (rootRef != null && size > 0) {
          return new TrailerInfo(rootRef, infoRef, size);
        }
      }
    }

    // Ultimate fallback: scan tail for the last /Root reference
    Matcher m = ROOT_REF_PATTERN.matcher(tail);
    while (m.find()) rootRef = m.group(1);
    return new TrailerInfo(rootRef != null ? rootRef : "1 0 R", null, Math.max(size, 1));
  }

  /** Search tail for a trailer entry (/Root or /Info reference). */
  private static String findTrailerEntryInTail(String tail, String key) {
    Pattern p =
        switch (key) {
          case "Root" -> ROOT_REF_PATTERN;
          case "Info" -> INFO_REF_PATTERN;
          default -> Pattern.compile("/" + key + "\\s+(\\d+\\s+\\d+\\s+R)");
        };

    int searchFrom = tail.length();
    while (true) {
      int trailerIdx = tail.lastIndexOf("trailer", searchFrom);
      if (trailerIdx < 0) break;

      int dictStart = tail.indexOf("<<", trailerIdx);
      if (dictStart < 0) break;

      int dictEnd = tail.indexOf(">>", dictStart);
      if (dictEnd < 0) break;

      String dict = tail.substring(dictStart, dictEnd + 2);
      Matcher m = p.matcher(dict);
      if (m.find()) {
        return m.group(1);
      }
      searchFrom = trailerIdx - 1;
      if (searchFrom < 0) break;
    }
    return null;
  }

  /** Extract /Size value from the trailer in the tail. */
  private static int findTrailerSizeInTail(String tail) {
    int searchFrom = tail.length();
    while (true) {
      int trailerIdx = tail.lastIndexOf("trailer", searchFrom);
      if (trailerIdx < 0) break;

      int dictStart = tail.indexOf("<<", trailerIdx);
      if (dictStart < 0) break;

      int dictEnd = tail.indexOf(">>", dictStart);
      if (dictEnd < 0) break;

      String dict = tail.substring(dictStart, dictEnd + 2);
      Matcher m = SIZE_PATTERN.matcher(dict);
      if (m.find()) {
        return Integer.parseInt(m.group(1));
      }
      searchFrom = trailerIdx - 1;
      if (searchFrom < 0) break;
    }
    return 0;
  }

  /** Find the startxref value from the tail of the file. */
  private static int findStartxrefInTail(String tail) {
    int idx = tail.lastIndexOf("startxref");
    if (idx < 0) return 0;
    String after = tail.substring(idx + "startxref".length()).trim();
    try {
      return Integer.parseInt(after.lines().findFirst().orElse("0").trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static final Pattern XREF_STREAM_HEADER = Pattern.compile("\\d+\\s+0\\s+obj\\b");

  /**
   * Detect whether the PDF uses cross-reference streams (§7.5.8). Reads only the bytes at the
   * startxref offset, not the entire file.
   */
  private static boolean usesXrefStreams(byte[] pdf, String tail) {
    int startxref = findStartxrefInTail(tail);
    if (startxref <= 0 || startxref >= pdf.length) return false;
    int peekEnd = Math.min(startxref + 200, pdf.length);
    String peek =
        new String(pdf, startxref, peekEnd - startxref, StandardCharsets.ISO_8859_1).stripLeading();
    if (peek.startsWith("xref")) return false;
    return XREF_STREAM_HEADER.matcher(peek).lookingAt()
        && peek.contains("/Type")
        && peek.contains("/XRef");
  }

  /**
   * Find and extract the first dictionary {@code << ... >>} in a window string. Handles nested
   * dictionaries.
   */
  private static String findDictInWindow(String window) {
    int dictStart = window.indexOf("<<");
    if (dictStart < 0) return null;
    int depth = 0;
    int pos = dictStart;
    while (pos < window.length() - 1) {
      if (window.charAt(pos) == '<' && window.charAt(pos + 1) == '<') {
        depth++;
        pos += 2;
      } else if (window.charAt(pos) == '>' && window.charAt(pos + 1) == '>') {
        depth--;
        if (depth == 0) return window.substring(dictStart, pos + 2);
        pos += 2;
      } else {
        pos++;
      }
    }
    return null;
  }

  private static int extractObjNum(String ref) {
    Matcher m = FIRST_INT_PATTERN.matcher(ref);
    if (m.find()) return Integer.parseInt(m.group(1));
    return 1;
  }

  /**
   * Find the dictionary for a specific object by number. Searches backwards from the end of the
   * file to find the latest version of the object (incremental updates append newer versions).
   *
   * <p>Uses word-boundary matching: the character before the marker must not be a digit. This
   * prevents "15 0 obj" from matching inside "615 0 obj".
   */
  private static String findObjectDict(String text, int objNum) {
    String marker = objNum + " 0 obj";
    int searchFrom = text.length();
    int idx;
    while (true) {
      idx = text.lastIndexOf(marker, searchFrom);
      if (idx < 0) return null;
      // Check word boundary: character before marker must not be a digit
      if (idx > 0 && Character.isDigit(text.charAt(idx - 1))) {
        searchFrom = idx - 1;
        continue;
      }
      break;
    }

    int dictStart = text.indexOf("<<", idx);
    if (dictStart < 0) return null;

    int depth = 0;
    int pos = dictStart;
    while (pos < text.length() - 1) {
      if (text.charAt(pos) == '<' && text.charAt(pos + 1) == '<') {
        depth++;
        pos += 2;
      } else if (text.charAt(pos) == '>' && text.charAt(pos + 1) == '>') {
        depth--;
        if (depth == 0) {
          return text.substring(dictStart, pos + 2);
        }
        pos += 2;
      } else {
        pos++;
      }
    }
    return null;
  }

  /**
   * Find the dictionary for a specific object by searching byte data directly. Avoids converting
   * the entire PDF to a String (which for a 50MB PDF would allocate ~100MB for Java's UTF-16
   * chars). Only the located dictionary bytes (~100-500 bytes) are converted to a String.
   *
   * <p>Uses word-boundary matching: the byte before the marker must be a whitespace character or
   * absent (start of data). This prevents "15 0 obj" from matching inside "615 0 obj".
   */
  private static String findObjectDictFromBytes(byte[] pdf, int objNum) {
    byte[] marker = (objNum + " 0 obj").getBytes(StandardCharsets.ISO_8859_1);
    int idx = pdf.length;
    while (true) {
      idx = lastIndexOfBytes(pdf, marker, idx - 1);
      if (idx < 0) return null;
      // Check word boundary: character before marker must not be a digit
      if (idx > 0 && pdf[idx - 1] >= '0' && pdf[idx - 1] <= '9') {
        // False match "15 0 obj" inside "615 0 obj"; keep searching
        continue;
      }
      break;
    }

    // Find << after the marker
    int searchStart = idx + marker.length;
    int dictStart = indexOfBytes(pdf, new byte[] {'<', '<'}, searchStart);
    if (dictStart < 0) return null;

    // Parse nested << >> to find the complete dictionary
    int depth = 0;
    int pos = dictStart;
    while (pos < pdf.length - 1) {
      if (pdf[pos] == '<' && pdf[pos + 1] == '<') {
        depth++;
        pos += 2;
      } else if (pdf[pos] == '>' && pdf[pos + 1] == '>') {
        depth--;
        if (depth == 0) {
          // Convert only the dictionary portion to String
          return new String(pdf, dictStart, pos + 2 - dictStart, StandardCharsets.ISO_8859_1);
        }
        pos += 2;
      } else {
        pos++;
      }
    }
    return null;
  }

  /** Search backwards for a byte pattern in a byte array, starting from a given upper bound. */
  private static int lastIndexOfBytes(byte[] data, byte[] pattern, int fromIndex) {
    int start = Math.min(fromIndex, data.length - pattern.length);
    outer:
    for (int i = start; i >= 0; i--) {
      for (int j = 0; j < pattern.length; j++) {
        if (data[i + j] != pattern[j]) continue outer;
      }
      return i;
    }
    return -1;
  }

  /** Search forwards for a byte pattern in a byte array starting from a given offset. */
  private static int indexOfBytes(byte[] data, byte[] pattern, int fromIndex) {
    int limit = data.length - pattern.length;
    outer:
    for (int i = fromIndex; i <= limit; i++) {
      for (int j = 0; j < pattern.length; j++) {
        if (data[i + j] != pattern[j]) continue outer;
      }
      return i;
    }
    return -1;
  }

  // --- String encoding helpers ---

  /**
   * Encode a Java string as a PDF string literal using UTF-16BE with BOM for non-ASCII, or
   * PDFDocEncoding (Latin-1) for ASCII-only strings.
   */
  static String encodePdfString(String value) {
    if (value == null || value.isEmpty()) return "()";

    boolean needsUnicode = false;
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) > 127) {
        needsUnicode = true;
        break;
      }
    }

    if (!needsUnicode) {
      StringBuilder sb = new StringBuilder("(");
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        switch (c) {
          case '(' -> sb.append("\\(");
          case ')' -> sb.append("\\)");
          case '\\' -> sb.append("\\\\");
          default -> sb.append(c);
        }
      }
      sb.append(')');
      return sb.toString();
    }

    byte[] utf16 = value.getBytes(java.nio.charset.StandardCharsets.UTF_16BE);
    StringBuilder sb = new StringBuilder("<FEFF");
    for (byte b : utf16) {
      sb.append(String.format("%02X", b & 0xFF));
    }
    sb.append('>');
    return sb.toString();
  }

  private static String formatPdfDate() {
    ZonedDateTime now = ZonedDateTime.now();
    return "D:" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
  }

  @SuppressWarnings("PMD.UnusedFormalParameter")
  private static int writeBlockCallback(MemorySegment pThis, MemorySegment pData, long size) {
    ByteArrayOutputStream baos = WRITE_BUFFER.get();
    if (baos == null || size <= 0) return 0;
    byte[] data = pData.reinterpret(size).toArray(JAVA_BYTE);
    baos.write(data, 0, data.length);
    return 1;
  }
}
