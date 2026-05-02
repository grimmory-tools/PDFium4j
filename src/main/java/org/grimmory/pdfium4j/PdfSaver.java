package org.grimmory.pdfium4j;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  private static final Map<Long, ByteArrayOutputStream> BUFFERS = new ConcurrentHashMap<>(16);
  private static final AtomicLong BUFFER_ID_SEQ = new AtomicLong();

  /** Thread-local staging buffer for writeBlockCallback – avoids per-callback byte[] allocation. */
  private static final ThreadLocal<byte[]> WRITE_BUF =
      ThreadLocal.withInitial(() -> new byte[65536]);

  /** Parameters for saving a PDF document. */
  record SaveParams(
      MemorySegment docHandle,
      Map<MetadataTag, String> allMetadata,
      boolean hasInfoUpdate,
      String pendingXmp,
      SeekableByteChannel originalSource,
      Path sourcePath,
      byte[] originalBytes,
      boolean structurallyModified,
      OutputStream out) {}

  private record ObjectRef(int num, int gen) {
    @CheckForNull
    static ObjectRef parse(String ref) {
      if (ref == null) return null;
      Matcher m = OBJECT_REF_PATTERN.matcher(ref);
      return m.find()
          ? new ObjectRef(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)))
          : null;
    }

    @Override
    public String toString() {
      return num + " " + gen + " R";
    }
  }

  private static final Pattern OBJECT_REF_PATTERN = Pattern.compile("(\\d+)\\s+(\\d+)\\s+R");
  private static final Pattern METADATA_REF_PATTERN =
      Pattern.compile("/Metadata\\s+\\d+\\s+\\d+\\s+R\\b");
  private static final Pattern ROOT_REF_PATTERN = Pattern.compile("/Root\\s+(\\d+)\\s+(\\d+)\\s+R");
  private static final Pattern INFO_REF_PATTERN = Pattern.compile("/Info\\s+(\\d+)\\s+(\\d+)\\s+R");
  private static final Pattern SIZE_PATTERN = Pattern.compile("/Size\\s+(\\d+)");

  private static final byte[] DICT_START = "<<".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] XREF_HEADER = "xref\n".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] XREF_ENTRY_TEMPLATE =
      "0000000000 00000 n \n".getBytes(StandardCharsets.ISO_8859_1);

  private record BasePdf(MemorySegment segment, @CheckForNull Path tempPath) {}

  private PdfSaver() {
    super();
  }

  static void save(SaveParams params) throws IOException {
    boolean hasXmpUpdate = params.pendingXmp() != null && !params.pendingXmp().isEmpty();
    boolean hasUpdate = params.hasInfoUpdate() || hasXmpUpdate;

    try (Arena arena = Arena.ofConfined()) {
      if (hasUpdate) {
        writeIncrementalUpdate(params, arena);
      } else {
        BasePdf base = getBaseSegment(params, arena);
        try {
          writeSource(params, base.segment(), params.out());
        } finally {
          deleteIfExists(base.tempPath());
        }
      }
    }
  }

  private static void writeSource(SaveParams params, MemorySegment baseSegment, OutputStream out)
      throws IOException {
    WritableByteChannel target = Channels.newChannel(out);
    if (!params.structurallyModified() && params.originalSource() instanceof FileChannel fc) {
      transferAll(fc, target);
    } else if (!params.structurallyModified() && params.sourcePath() != null) {
      try (FileChannel fc = FileChannel.open(params.sourcePath(), StandardOpenOption.READ)) {
        transferAll(fc, target);
      }
    } else {
      writeSegment(baseSegment, out);
    }
  }

  private static BasePdf getBaseSegment(SaveParams params, Arena arena) throws IOException {
    if (params.structurallyModified()) {
      return new BasePdf(MemorySegment.ofArray(nativeSaveBytes(params.docHandle())), null);
    } else if (params.originalBytes() != null) {
      return new BasePdf(MemorySegment.ofArray(params.originalBytes()), null);
    } else if (params.sourcePath() != null) {
      try (FileChannel fc = FileChannel.open(params.sourcePath(), StandardOpenOption.READ)) {
        return new BasePdf(fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size(), arena), null);
      }
    } else if (params.originalSource() instanceof FileChannel fc) {
      return new BasePdf(fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size(), arena), null);
    } else if (params.originalSource() != null) {
      Path temp = Files.createTempFile("pdfium4j-base-", ".pdf");
      try {
        params.originalSource().position(0);
        try (FileChannel fc =
            FileChannel.open(
                temp,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.READ)) {
          copyAll(params.originalSource(), fc);
          return new BasePdf(fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size(), arena), temp);
        }
      } catch (IOException e) {
        deleteIfExists(temp);
        throw e;
      }
    } else {
      return new BasePdf(MemorySegment.ofArray(nativeSaveBytes(params.docHandle())), null);
    }
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
    // Reinterpret pData to expose its full size before copying.
    MemorySegment data = pData.reinterpret(size);
    // Reuse a thread-local staging buffer to avoid per-callback heap allocation.
    byte[] buf = WRITE_BUF.get();
    long offset = 0;
    long remaining = size;
    while (remaining > 0) {
      int chunk = (int) Math.min(buf.length, remaining);
      MemorySegment.copy(data, JAVA_BYTE, offset, buf, 0, chunk);
      baos.write(buf, 0, chunk);
      offset += chunk;
      remaining -= chunk;
    }
    return 1;
  }

  private record TrailerInfo(ObjectRef rootRef, ObjectRef infoRef, int size) {}

  private static void writeIncrementalUpdate(SaveParams params, Arena arena) throws IOException {
    BasePdf base = getBaseSegment(params, arena);
    MemorySegment pdf = base.segment();
    try {
      long tailLen = Math.min(pdf.byteSize(), 65536); // 64KB tail for robust parsing
      byte[] tailBytes = pdf.asSlice(pdf.byteSize() - tailLen, tailLen).toArray(JAVA_BYTE);
      String tail = new String(tailBytes, StandardCharsets.ISO_8859_1);

      long prevXrefOffset = findLastStartxrefValue(tail);
      TrailerInfo trailer = parseTrailer(tail);

      performIncrementalUpdate(pdf, params, trailer, prevXrefOffset);
    } catch (IOException e) {
      throw new PdfiumException("Incremental update failed", e);
    } finally {
      deleteIfExists(base.tempPath());
    }
  }

  private static void performIncrementalUpdate(
      MemorySegment pdf, SaveParams params, TrailerInfo trailer, long prevXrefOffset)
      throws IOException {
    int nextObj = trailer.size() > 0 ? trailer.size() : findMaxObjectNumber(pdf) + 1;
    ByteArrayOutputStream update = new ByteArrayOutputStream();
    update.write('\n');

    long baseOffset = pdf.byteSize();
    Map<Integer, Long> objOffsets = LinkedHashMap.newLinkedHashMap(16);

    int infoObjNum = 0;
    Map<MetadataTag, String> metadata = params.hasInfoUpdate() ? params.allMetadata() : null;
    if (metadata != null && !metadata.isEmpty()) {
      infoObjNum = nextObj;
      nextObj++;
      objOffsets.put(infoObjNum, baseOffset + update.size());
      update.write(buildInfoObject(infoObjNum, metadata));
    }

    String xmp = params.pendingXmp();
    if (xmp != null && !xmp.isEmpty()) {
      int xmpObjNum = nextObj;
      nextObj++;
      objOffsets.put(xmpObjNum, baseOffset + update.size());
      update.write(buildXmpObject(xmpObjNum, xmp));

      ObjectRef catalogRef = trailer.rootRef();
      String catalogDict = findObjectDictFromBytes(pdf, catalogRef.num, catalogRef.gen);
      if (catalogDict == null) {
        throw new IOException("Failed to find Catalog object for XMP update");
      }
      objOffsets.put(catalogRef.num, baseOffset + update.size());
      update.write(buildModifiedCatalog(catalogRef.num, catalogRef.gen, catalogDict, xmpObjNum));
    }

    long xrefOffset = baseOffset + update.size();
    writeXrefTable(update, objOffsets);
    writeTrailer(update, trailer, infoObjNum, nextObj, prevXrefOffset, xrefOffset);

    // Stream original source then update
    writeSource(params, pdf, params.out());
    update.writeTo(params.out());
  }

  private static void writeXrefTable(OutputStream update, Map<Integer, Long> objOffsets)
      throws IOException {
    update.write(XREF_HEADER);
    List<Integer> sortedNums = new ArrayList<>(objOffsets.keySet());
    Collections.sort(sortedNums);

    byte[] intBuf = new byte[11];
    byte[] entryBuf = new byte[20];
    int totalObjs = sortedNums.size();
    for (int i = 0; i < totalObjs; ) {
      int start = sortedNums.get(i);
      int count = 1;
      while (i + count < totalObjs && sortedNums.get(i + count) == start + count) {
        count++;
      }
      // Section header: "start count\n"
      int len = formatInt(intBuf, start);
      update.write(intBuf, intBuf.length - len, len);
      update.write(' ');
      len = formatInt(intBuf, count);
      update.write(intBuf, intBuf.length - len, len);
      update.write('\n');

      for (int j = 0; j < count; j++) {
        long offset = objOffsets.get(start + j);
        // Manual fixed-width formatting: 10 digits zero-padded "0000000000 00000 n \n"
        // Total 20 bytes.
        System.arraycopy(XREF_ENTRY_TEMPLATE, 0, entryBuf, 0, 20);
        long tempOffset = offset;
        for (int k = 9; k >= 0; k--) {
          entryBuf[k] = (byte) ('0' + (tempOffset % 10));
          tempOffset /= 10;
        }
        update.write(entryBuf);
      }
      i += count;
    }
  }

  private static int formatInt(byte[] buf, int value) {
    int pos = buf.length;
    if (value == 0) {
      buf[--pos] = '0';
      return 1;
    }
    int temp = value;
    while (temp > 0) {
      buf[--pos] = (byte) ('0' + (temp % 10));
      temp /= 10;
    }
    return buf.length - pos;
  }

  private static void transferAll(FileChannel src, WritableByteChannel target) throws IOException {
    long size = src.size();
    long pos = 0;
    while (pos < size) {
      long moved = src.transferTo(pos, size - pos, target);
      if (moved <= 0) {
        break;
      }
      pos += moved;
    }
    if (pos < size) {
      src.position(pos);
      copyAll(src, target);
    }
  }

  private static void copyAll(SeekableByteChannel src, WritableByteChannel dst) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocateDirect(65536);
    while (src.read(buffer) != -1) {
      buffer.flip();
      while (buffer.hasRemaining()) {
        dst.write(buffer);
      }
      buffer.clear();
    }
  }

  private static void deleteIfExists(@CheckForNull Path path) {
    if (path == null) return;
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      PdfiumLibrary.ignore(e);
    }
  }

  private static void writeTrailer(
      ByteArrayOutputStream update,
      TrailerInfo trailer,
      int infoObjNum,
      int nextObj,
      long prevXrefOffset,
      long xrefOffset)
      throws IOException {
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
  }

  private static byte[] buildInfoObject(int num, Map<MetadataTag, String> metadata) {
    StringBuilder sb = new StringBuilder((metadata.size() * 64) + 64);
    sb.append(num).append(" 0 obj\n<<\n");
    for (Map.Entry<MetadataTag, String> entry : metadata.entrySet()) {
      if (entry.getValue() != null && !entry.getValue().isEmpty()) {
        sb.append('/').append(entry.getKey().pdfKey()).append(' ');
        sb.append(encodePdfString(entry.getValue())).append('\n');
      }
    }
    sb.append("/ModDate ").append(encodePdfString(formatPdfDate())).append('\n');
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
    String keyPrefix = "/" + key;
    int searchFrom = tail.length();
    while (true) {
      int trailerIdx = tail.lastIndexOf("trailer", searchFrom);
      if (trailerIdx < 0) break;
      int dictStart = tail.indexOf("<<", trailerIdx);
      if (dictStart < 0) break;
      int dictEnd = tail.indexOf(">>", dictStart);
      if (dictEnd < 0) break;
      String dict = tail.substring(dictStart, dictEnd + 2);
      int keyIdx = dict.indexOf(keyPrefix);
      if (keyIdx >= 0) {
        int pos = keyIdx + keyPrefix.length();
        int n1Start = skipAsciiWhitespace(dict, pos);
        int n1End = scanDigits(dict, n1Start);
        int n2Start = skipAsciiWhitespace(dict, n1End);
        int n2End = scanDigits(dict, n2Start);
        int rPos = skipAsciiWhitespace(dict, n2End);
        if (n1End > n1Start
            && n2End > n2Start
            && rPos < dict.length()
            && dict.charAt(rPos) == 'R') {
          return dict.substring(n1Start, n1End) + " " + dict.substring(n2Start, n2End) + " R";
        }
      }
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

  private static long findLastStartxrefValue(String tail) {
    int idx = tail.lastIndexOf("startxref");
    if (idx < 0) return 0;
    int pos = skipAsciiWhitespace(tail, idx + 9);
    int end = scanDigits(tail, pos);
    if (end <= pos) return 0;
    try {
      return Long.parseLong(tail.substring(pos, end));
    } catch (Exception e) {
      return 0;
    }
  }

  private static int skipAsciiWhitespace(String s, int from) {
    int i = Math.max(0, from);
    while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
      i++;
    }
    return i;
  }

  private static int scanDigits(String s, int from) {
    int i = Math.max(0, from);
    while (i < s.length() && Character.isDigit(s.charAt(i))) {
      i++;
    }
    return i;
  }

  @CheckForNull
  private static String findObjectDictFromBytes(MemorySegment pdf, int objNum, int genNum) {
    byte[] marker = (objNum + " " + genNum + " obj").getBytes(StandardCharsets.ISO_8859_1);
    long searchFrom = pdf.byteSize();
    long idx;
    while (true) {
      idx = lastIndexOf(pdf, marker, searchFrom);
      if (idx < 0) return null;
      if (idx > 0 && Character.isDigit((char) pdf.get(JAVA_BYTE, idx - 1))) {
        searchFrom = idx - 1;
        continue;
      }
      break;
    }

    long dictStart = indexOf(pdf, DICT_START, idx);
    if (dictStart < 0) return null;

    int depth = 0;
    long pos = dictStart;
    while (pos < pdf.byteSize() - 1) {
      byte b1 = pdf.get(JAVA_BYTE, pos);
      byte b2 = pdf.get(JAVA_BYTE, pos + 1);
      if (b1 == '<' && b2 == '<') {
        depth++;
        pos += 2;
      } else if (b1 == '>' && b2 == '>') {
        depth--;
        if (depth == 0) {
          byte[] dictBytes = pdf.asSlice(dictStart, pos + 2 - dictStart).toArray(JAVA_BYTE);
          return new String(dictBytes, StandardCharsets.ISO_8859_1);
        }
        pos += 2;
      } else {
        pos++;
      }
    }
    return null;
  }

  private static int findMaxObjectNumber(MemorySegment pdf) {
    // Scan for " obj" and walk back through "genNum objNum" to extract the object number.
    // Handles any generation number, not just 0.
    byte[] marker = " obj".getBytes(StandardCharsets.ISO_8859_1);
    int max = 0;
    long searchPos = 0;
    while (true) {
      long pos = indexOf(pdf, marker, searchPos);
      if (pos < 0) break;
      searchPos = pos + marker.length;
      // pos points to the space in " obj"
      // Walk back: skip gen digits, skip whitespace, read obj number digits
      long p = pos - 1;
      if (p < 0 || !isAsciiDigit(pdf, p)) continue;
      // skip gen digits
      while (p > 0 && isAsciiDigit(pdf, p - 1)) p--;
      // p = first digit of genNum; expect whitespace before it
      if (p <= 0 || !isAsciiWhitespace(pdf, p - 1)) continue;
      p--;
      while (p > 0 && isAsciiWhitespace(pdf, p - 1)) p--;
      // p = last digit of objNum
      if (!isAsciiDigit(pdf, p)) continue;
      long numEnd = p;
      long numStart = numEnd;
      while (numStart > 0 && isAsciiDigit(pdf, numStart - 1)) numStart--;
      try {
        byte[] numBytes = pdf.asSlice(numStart, numEnd - numStart + 1).toArray(JAVA_BYTE);
        int num = Integer.parseInt(new String(numBytes, StandardCharsets.ISO_8859_1));
        if (num > max) max = num;
      } catch (Exception ignored) {
        // ignore malformed
      }
    }
    return max;
  }

  private static boolean isAsciiDigit(MemorySegment seg, long idx) {
    byte b = seg.get(JAVA_BYTE, idx);
    return b >= '0' && b <= '9';
  }

  private static boolean isAsciiWhitespace(MemorySegment seg, long idx) {
    byte b = seg.get(JAVA_BYTE, idx);
    return b == ' ' || b == '\t' || b == '\r' || b == '\n';
  }

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

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
      StringBuilder sb = new StringBuilder(value.length() + 2);
      sb.append('(');
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
    StringBuilder sb = new StringBuilder(value.length() * 4 + 6);
    sb.append("<FEFF");
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      sb.append(HEX[(c >> 12) & 0x0F]);
      sb.append(HEX[(c >> 8) & 0x0F]);
      sb.append(HEX[(c >> 4) & 0x0F]);
      sb.append(HEX[c & 0x0F]);
    }
    sb.append('>');
    return sb.toString();
  }

  private static String formatPdfDate() {
    return "D:" + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
  }

  private static void writeSegment(MemorySegment seg, OutputStream out) throws IOException {
    long size = seg.byteSize();
    long pos = 0;
    byte[] buf = new byte[8192];
    while (pos < size) {
      int len = (int) Math.min(buf.length, size - pos);
      MemorySegment.copy(seg, JAVA_BYTE, pos, buf, 0, len);
      out.write(buf, 0, len);
      pos += len;
    }
  }

  private static long indexOf(MemorySegment haystack, byte[] needle, long fromIndex) {
    long limit = haystack.byteSize() - needle.length;
    for (long i = fromIndex; i <= limit; i++) {
      boolean match = true;
      for (int j = 0; j < needle.length; j++) {
        if (haystack.get(JAVA_BYTE, i + j) != needle[j]) {
          match = false;
          break;
        }
      }
      if (match) return i;
    }
    return -1;
  }

  private static long lastIndexOf(MemorySegment haystack, byte[] needle, long fromIndex) {
    long start = Math.min(fromIndex, haystack.byteSize() - needle.length);
    for (long i = start; i >= 0; i--) {
      boolean match = true;
      for (int j = 0; j < needle.length; j++) {
        if (haystack.get(JAVA_BYTE, i + j) != needle[j]) {
          match = false;
          break;
        }
      }
      if (match) return i;
    }
    return -1;
  }
}
