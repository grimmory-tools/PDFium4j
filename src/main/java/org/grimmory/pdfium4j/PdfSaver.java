package org.grimmory.pdfium4j;

import static java.lang.foreign.ValueLayout.*;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.internal.EditBindings;
import org.grimmory.pdfium4j.internal.FfmHelper;
import org.grimmory.pdfium4j.model.MetadataTag;

/**
 * Handles saving PDF documents. Uses PDFium's native FPDF_SaveAsCopy for the base save, then
 * applies pure-Java incremental updates for Info dictionary and XMP metadata.
 *
 * <p>Uses byte-level scanning to avoid OOM issues with large files.
 */
final class PdfSaver {

  private static final Map<Long, ByteArrayOutputStream> BUFFERS = new ConcurrentHashMap<>();
  private static final AtomicLong BUFFER_ID_SEQ = new AtomicLong();
  private static final Pattern XREF = Pattern.compile("\\s+");

  private record ObjectRef(int num, int gen) {
    @CheckForNull
    static ObjectRef parse(String ref) {
      if (ref == null) return null;
      Pattern p = Pattern.compile("(\\d+)\\s+(\\d+)\\s+R");
      Matcher m = p.matcher(ref);
      return m.find()
          ? new ObjectRef(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)))
          : null;
    }

    @Override
    public String toString() {
      return num + " " + gen + " R";
    }
  }

  private static final Pattern METADATA_REF_PATTERN =
      Pattern.compile("/Metadata\\s+\\d+\\s+\\d+\\s+R\\b");
  private static final Pattern ROOT_REF_PATTERN = Pattern.compile("/Root\\s+(\\d+)\\s+(\\d+)\\s+R");
  private static final Pattern INFO_REF_PATTERN = Pattern.compile("/Info\\s+(\\d+)\\s+(\\d+)\\s+R");
  private static final Pattern SIZE_PATTERN = Pattern.compile("/Size\\s+(\\d+)");

  private static final byte[] DICT_START = "<<".getBytes(StandardCharsets.ISO_8859_1);

  private PdfSaver() {}

  static void save(
      MemorySegment docHandle,
      Map<MetadataTag, String> allMetadata,
      boolean hasInfoUpdate,
      String pendingXmp,
      boolean skipValidation,
      SeekableByteChannel originalSource,
      Path sourcePath,
      byte[] originalBytes,
      boolean structurallyModified,
      OutputStream out)
      throws IOException {

    boolean hasXmpUpdate = pendingXmp != null && !pendingXmp.isEmpty();

    byte[] baseBytes;
    if (structurallyModified) {
      baseBytes = nativeSaveBytes(docHandle);
    } else if (originalBytes != null) {
      baseBytes = originalBytes;
    } else if (originalSource != null) {
      originalSource.position(0);
      baseBytes = new byte[(int) originalSource.size()];
      java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(baseBytes);
      while (bb.hasRemaining()) {
        if (originalSource.read(bb) == -1) break;
      }
    } else if (sourcePath != null) {
      baseBytes = Files.readAllBytes(sourcePath);
    } else {
      baseBytes = nativeSaveBytes(docHandle);
    }

    byte[] result = baseBytes;
    if (hasInfoUpdate || hasXmpUpdate) {
      result = appendIncrementalUpdate(baseBytes, hasInfoUpdate ? allMetadata : null, pendingXmp);
    }
    out.write(result);
  }

  private static byte[] nativeSaveBytes(MemorySegment docHandle) {
    long bufferId = BUFFER_ID_SEQ.incrementAndGet();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    BUFFERS.put(bufferId, baos);
    try (Arena arena = Arena.ofShared()) {
      if (EditBindings.FPDF_SaveAsCopy == null) {
        throw new PdfiumException("FPDF_SaveAsCopy not available in this PDFium build");
      }
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
      fileWrite.set(JAVA_LONG, 16, bufferId);

      int ok = (int) EditBindings.FPDF_SaveAsCopy.invokeExact(docHandle, fileWrite, 0);
      if (ok == 0) throw new PdfiumException("FPDF_SaveAsCopy failed");
      return baos.toByteArray();
    } catch (PdfiumException e) {
      throw e;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to save document", t);
    } finally {
      BUFFERS.remove(bufferId);
    }
  }

  @SuppressWarnings("PMD.UnusedFormalParameter")
  private static int writeBlockCallback(MemorySegment pThis, MemorySegment pData, long size) {
    if (FfmHelper.isNull(pThis) || FfmHelper.isNull(pData)) return 0;
    // Reinterpret pThis to allow reading our custom bufferId field
    long bufferId =
        pThis.reinterpret(EditBindings.FPDF_FILEWRITE_LAYOUT.byteSize()).get(JAVA_LONG, 16);
    ByteArrayOutputStream baos = BUFFERS.get(bufferId);
    if (baos == null || size <= 0) return 0;
    byte[] data = pData.reinterpret(size).toArray(JAVA_BYTE);
    baos.write(data, 0, data.length);
    return 1;
  }

  private record TrailerInfo(ObjectRef rootRef, ObjectRef infoRef, int size) {}

  @SuppressFBWarnings(
      value = "VA_FORMAT_STRING_USES_NEWLINE",
      justification = "PDF xref entries must be exactly 20 bytes; %n is platform-dependent")
  private static byte[] appendIncrementalUpdate(
      byte[] pdf, Map<MetadataTag, String> metadata, String xmp) {
    try {
      int tailLen = Math.min(pdf.length, 4096);
      String tail = new String(pdf, pdf.length - tailLen, tailLen, StandardCharsets.ISO_8859_1);

      int prevXrefOffset = findLastStartxrefValue(tail);
      TrailerInfo trailer = parseTrailer(tail);

      int nextObj = trailer.size();
      if (nextObj <= 0) {
        nextObj = findMaxObjectNumber(pdf) + 1;
      }

      int infoObjNum = 0;
      int xmpObjNum;
      ObjectRef catalogRef;

      ByteArrayOutputStream update = new ByteArrayOutputStream();
      update.write('\n');

      int baseOffset = pdf.length;
      Map<Integer, Integer> objOffsets = new LinkedHashMap<>();

      if (metadata != null && !metadata.isEmpty()) {
        infoObjNum = nextObj++;
        objOffsets.put(infoObjNum, baseOffset + update.size());
        update.write(buildInfoObject(infoObjNum, metadata));
      }

      if (xmp != null && !xmp.isEmpty()) {
        xmpObjNum = nextObj++;
        objOffsets.put(xmpObjNum, baseOffset + update.size());
        update.write(buildXmpObject(xmpObjNum, xmp));

        catalogRef = trailer.rootRef();
        String catalogDict = findObjectDictFromBytes(pdf, catalogRef.num, catalogRef.gen);
        if (catalogDict == null) {
          throw new IOException(
              "Failed to find Catalog object (%s) for XMP update".formatted(catalogRef));
        }
        objOffsets.put(catalogRef.num, baseOffset + update.size());
        update.write(buildModifiedCatalog(catalogRef.num, catalogRef.gen, catalogDict, xmpObjNum));
      }

      int xrefOffset = baseOffset + update.size();
      update.write("xref\n".getBytes(StandardCharsets.ISO_8859_1));
      List<Integer> sortedNums = new ArrayList<>(objOffsets.keySet());
      Collections.sort(sortedNums);

      for (int i = 0; i < sortedNums.size(); ) {
        int start = sortedNums.get(i);
        int count = 1;
        while (i + count < sortedNums.size() && sortedNums.get(i + count) == start + count) {
          count++;
        }
        update.write((start + " " + count + "\n").getBytes(StandardCharsets.ISO_8859_1));
        for (int j = 0; j < count; j++) {
          int offset = objOffsets.get(start + j);
          // 20-byte record using space and \n
          String record = String.format("%010d 00000 n \n", offset);
          update.write(record.getBytes(StandardCharsets.ISO_8859_1));
        }
        i += count;
      }

      StringBuilder trailerSb = new StringBuilder(256);
      trailerSb.append("trailer\n<< /Size ").append(nextObj);
      trailerSb.append(" /Root ").append(trailer.rootRef());
      if (infoObjNum > 0) {
        trailerSb.append(" /Info ").append(infoObjNum).append(" 0 R");
      } else if (trailer.infoRef() != null) {
        trailerSb.append(" /Info ").append(trailer.infoRef());
      }
      if (prevXrefOffset > 0) {
        trailerSb.append(" /Prev ").append(prevXrefOffset);
      }
      trailerSb.append(" >>\nstartxref\n").append(xrefOffset).append("\n%%EOF\n");
      update.write(trailerSb.toString().getBytes(StandardCharsets.ISO_8859_1));

      byte[] updateBytes = update.toByteArray();
      byte[] result = new byte[pdf.length + updateBytes.length];
      System.arraycopy(pdf, 0, result, 0, pdf.length);
      System.arraycopy(updateBytes, 0, result, pdf.length, updateBytes.length);
      return result;
    } catch (IOException e) {
      throw new PdfiumException("Incremental update failed", e);
    }
  }

  private static byte[] buildInfoObject(int num, Map<MetadataTag, String> metadata) {
    StringBuilder sb = new StringBuilder(metadata.size() * 64 + 64);
    sb.append(num).append(" 0 obj\n<<\n");
    for (Map.Entry<MetadataTag, String> entry : metadata.entrySet()) {
      if (entry.getValue() != null && !entry.getValue().isEmpty()) {
        sb.append("/").append(entry.getKey().pdfKey()).append(" ");
        sb.append(encodePdfString(entry.getValue())).append("\n");
      }
    }
    sb.append("/ModDate ").append(encodePdfString(formatPdfDate())).append("\n");
    sb.append(">>\nendobj\n");
    return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
  }

  private static byte[] buildXmpObject(int num, String xmp) {
    byte[] content = xmp.getBytes(StandardCharsets.UTF_8);
    String header =
        num
            + " 0 obj\n<< /Type /Metadata /Subtype /XML /Length "
            + content.length
            + " >>\nstream\n";
    String footer = "\nendstream\nendobj\n";
    byte[] hb = header.getBytes(StandardCharsets.ISO_8859_1);
    byte[] fb = footer.getBytes(StandardCharsets.ISO_8859_1);
    byte[] res = new byte[hb.length + content.length + fb.length];
    System.arraycopy(hb, 0, res, 0, hb.length);
    System.arraycopy(content, 0, res, hb.length, content.length);
    System.arraycopy(fb, 0, res, hb.length + content.length, fb.length);
    return res;
  }

  private static byte[] buildModifiedCatalog(
      int objNum, int genNum, String oldDict, int xmpObjNum) {
    StringBuilder sb = new StringBuilder(oldDict.length() + 128);
    sb.append(objNum).append(" ").append(genNum).append(" obj\n");
    String dict = METADATA_REF_PATTERN.matcher(oldDict).replaceFirst("");
    int closeIdx = dict.lastIndexOf(">>");
    if (closeIdx >= 0) {
      dict =
          dict.substring(0, closeIdx)
              + "/Metadata "
              + xmpObjNum
              + " 0 R "
              + dict.substring(closeIdx);
    }
    sb.append(dict).append("\nendobj\n");
    return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
  }

  private static TrailerInfo parseTrailer(String tail) throws IOException {
    String rootStr = findTrailerEntryFromTail(tail, "Root");
    String infoStr = findTrailerEntryFromTail(tail, "Info");
    int size = findTrailerSizeFromTail(tail);

    ObjectRef rootRef = ObjectRef.parse(rootStr);
    ObjectRef infoRef = ObjectRef.parse(infoStr);

    if (rootRef == null) {
      Matcher m = ROOT_REF_PATTERN.matcher(tail);
      while (m.find()) {
        rootRef = new ObjectRef(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
      }
    }
    if (infoRef == null) {
      Matcher m = INFO_REF_PATTERN.matcher(tail);
      while (m.find()) {
        infoRef = new ObjectRef(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
      }
    }
    if (size == 0) {
      Matcher m = SIZE_PATTERN.matcher(tail);
      while (m.find()) {
        size = Integer.parseInt(m.group(1));
      }
    }

    if (rootRef == null) {
      throw new IOException("Failed to find PDF Root (Catalog) reference");
    }

    return new TrailerInfo(rootRef, infoRef, size);
  }

  @CheckForNull
  private static String findTrailerEntryFromTail(String tail, String key) {
    int searchFrom = tail.length();
    while (true) {
      int trailerIdx = tail.lastIndexOf("trailer", searchFrom);
      if (trailerIdx < 0) break;
      int dictStart = tail.indexOf("<<", trailerIdx);
      if (dictStart < 0) break;
      int dictEnd = tail.indexOf(">>", dictStart);
      if (dictEnd < 0) break;
      String dict = tail.substring(dictStart, dictEnd + 2);
      Pattern p = Pattern.compile("/" + key + "\\s+(\\d+\\s+(\\d+)\\s+R)");
      Matcher m = p.matcher(dict);
      if (m.find()) return m.group(1);
      searchFrom = trailerIdx - 1;
      if (searchFrom < 0) break;
    }
    return null;
  }

  private static int findTrailerSizeFromTail(String tail) {
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
      if (m.find()) return Integer.parseInt(m.group(1));
      searchFrom = trailerIdx - 1;
      if (searchFrom < 0) break;
    }
    return 0;
  }

  private static int findLastStartxrefValue(String tail) {
    int idx = tail.lastIndexOf("startxref");
    if (idx < 0) return 0;
    String after = tail.substring(idx + 9).trim();
    try {
      return Integer.parseInt(XREF.split(after)[0]);
    } catch (Exception e) {
      return 0;
    }
  }

  @CheckForNull
  private static String findObjectDictFromBytes(byte[] pdf, int objNum, int genNum) {
    byte[] marker = (objNum + " " + genNum + " obj").getBytes(StandardCharsets.ISO_8859_1);
    int searchFrom = pdf.length;
    int idx;
    while (true) {
      idx = lastIndexOf(pdf, marker, searchFrom);
      if (idx < 0) return null;
      if (idx > 0 && Character.isDigit((char) pdf[idx - 1])) {
        searchFrom = idx - 1;
        continue;
      }
      break;
    }

    int dictStart = indexOf(pdf, DICT_START, idx);
    if (dictStart < 0) return null;

    int depth = 0;
    int pos = dictStart;
    while (pos < pdf.length - 1) {
      if (pdf[pos] == '<' && pdf[pos + 1] == '<') {
        depth++;
        pos += 2;
      } else if (pdf[pos] == '>' && pdf[pos + 1] == '>') {
        depth--;
        if (depth == 0) {
          return new String(pdf, dictStart, pos + 2 - dictStart, StandardCharsets.ISO_8859_1);
        }
        pos += 2;
      } else {
        pos++;
      }
    }
    return null;
  }

  private static int findMaxObjectNumber(byte[] pdf) {
    String text = new String(pdf, StandardCharsets.ISO_8859_1);
    Pattern p = Pattern.compile("(\\d+)\\s+0\\s+obj\\b");
    Matcher matcher = p.matcher(text);
    int max = 0;
    while (matcher.find()) {
      int num = Integer.parseInt(matcher.group(1));
      if (num > max) {
        max = num;
      }
    }
    return max;
  }

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
      sb.append(")");
      return sb.toString();
    }
    byte[] utf16 = value.getBytes(StandardCharsets.UTF_16BE);
    StringBuilder sb = new StringBuilder("<FEFF");
    for (byte b : utf16) {
      sb.append(String.format("%02X", b & 0xFF));
    }
    sb.append(">");
    return sb.toString();
  }

  private static String formatPdfDate() {
    return "D:" + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
  }

  private static int indexOf(byte[] haystack, byte[] needle, int fromIndex) {
    outer:
    for (int i = fromIndex; i <= haystack.length - needle.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) continue outer;
      }
      return i;
    }
    return -1;
  }

  private static int lastIndexOf(byte[] haystack, byte[] needle, int fromIndex) {
    int start = Math.min(fromIndex, haystack.length - needle.length);
    outer:
    for (int i = start; i >= 0; i--) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) continue outer;
      }
      return i;
    }
    return -1;
  }
}
