package org.grimmory.pdfium4j;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
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
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.internal.EditBindings;
import org.grimmory.pdfium4j.internal.FfmHelper;
import org.grimmory.pdfium4j.internal.Generators;
import org.grimmory.pdfium4j.internal.ScratchBuffer;
import org.grimmory.pdfium4j.internal.ShimBindings;
import org.grimmory.pdfium4j.internal.XmpUpdate;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.grimmory.pdfium4j.model.PdfProcessingPolicy;
import org.grimmory.pdfium4j.model.XmpMetadata;

/**
 * Handles saving PDF documents. Uses PDFium's native fpdfSaveAsCopy for the base save, then applies
 * pure-Java incremental updates for Info dictionary and XMP metadata.
 *
 * <p>Uses byte-level scanning to avoid OOM issues with large files.
 */
final class PdfSaver {
  private static final Logger LOGGER = Logger.getLogger(PdfSaver.class.getName());
  private static final Arena NATIVE_SAVE_ARENA = Arena.ofShared();
  private static final MethodHandle WRITE_BLOCK_CALLBACK = lookupWriteBlockCallback();
  private static final MethodHandle MEMCPY = lookupMemcpy();
  private static final MemorySegment WRITE_BLOCK_STUB =
      Linker.nativeLinker()
          .upcallStub(WRITE_BLOCK_CALLBACK, EditBindings.WRITE_BLOCK_DESC, NATIVE_SAVE_ARENA);

  /**
   * Per-thread callback sink for native fpdfSaveAsCopy bytes. Set for one save call and removed in
   * finally so large backing arrays are not retained by pooled threads.
   */
  private static final int STREAM_DECODE_INITIAL_CAPACITY = 8192;

  private static final int SAVE_CALLBACK_MAX_RETAINED_CAPACITY = 65536;

  private static final ThreadLocal<ReusableByteArrayOutputStream> SAVE_CALLBACK_TARGET =
      ThreadLocal.withInitial(() -> new ReusableByteArrayOutputStream(1024 * 1024));
  private static final ThreadLocal<ReusableByteArrayOutputStream> XMP_CONTENT_BUFFER =
      ThreadLocal.withInitial(() -> new ReusableByteArrayOutputStream(8192));

  /** Reused per-thread staging buffer for native save callbacks; bounded to 64 KiB. */
  private static final ThreadLocal<byte[]> SAVE_CALLBACK_BUF =
      ThreadLocal.withInitial(() -> new byte[8192]);

  private static final ThreadLocal<MemorySegment> SAVE_CALLBACK_BUF_SEG =
      ThreadLocal.withInitial(() -> MemorySegment.ofArray(SAVE_CALLBACK_BUF.get()));

  private static final ThreadLocal<ReusableByteArrayOutputStream> STREAM_DECODE_TARGET =
      ThreadLocal.withInitial(
          () -> new ReusableByteArrayOutputStream(STREAM_DECODE_INITIAL_CAPACITY));

  private static final ThreadLocal<byte[]> STREAM_DECODE_BUF =
      ThreadLocal.withInitial(() -> new byte[8192]);
  private static final ThreadLocal<byte[]> PREDICTOR_BUF =
      ThreadLocal.withInitial(() -> new byte[8192]);
  private static final ThreadLocal<Inflater> REUSABLE_INFLATER =
      ThreadLocal.withInitial(Inflater::new);

  private static final ThreadLocal<NativeSaveContext> NATIVE_SAVE_CONTEXT =
      ThreadLocal.withInitial(NativeSaveContext::new);

  private static final ThreadLocal<long[]> REPAIR_OFFSETS =
      ThreadLocal.withInitial(() -> new long[1024]);
  private static final ThreadLocal<byte[]> REPAIR_INT_BUF =
      ThreadLocal.withInitial(() -> new byte[11]);
  private static final ThreadLocal<byte[]> REPAIR_ENTRY_BUF =
      ThreadLocal.withInitial(() -> new byte[20]);
  private static final ThreadLocal<byte[]> IO_BUFFER =
      ThreadLocal.withInitial(() -> new byte[65536]);
  private static final ThreadLocal<MemorySegment> IO_BUFFER_SEGMENT =
      ThreadLocal.withInitial(() -> MemorySegment.ofArray(IO_BUFFER.get()));
  private static final ThreadLocal<RepairResult> REPAIR_RESULT =
      ThreadLocal.withInitial(RepairResult::new);

  private static final class RepairResult {
    int rootNum = -1;
    int infoNum = -1;
    int encryptNum = -1;
    int pagesNum = -1;
    long idOffset = -1;
    long idLen = -1;
  }

  private static final XmpMetadataWriter XMP_WRITER = new XmpMetadataWriter();
  private static final long INITIAL_TAIL_SCAN_BYTES = 64L << 10;
  private static final long SECONDARY_TAIL_SCAN_BYTES = 256L << 10;
  private static final long TAIL_SCAN_BYTES = 1024L << 10;
  private static final long[] TAIL_SCAN_STEPS = {
    INITIAL_TAIL_SCAN_BYTES, SECONDARY_TAIL_SCAN_BYTES, TAIL_SCAN_BYTES, Long.MAX_VALUE
  };
  private static final long XREF_OFFSET_FUZZ_BYTES = 1024L;
  private static final long MAX_XREF_OFFSET = 9_999_999_999L;

  /** Parameters for saving a PDF document. */
  record SaveParams(
      MemorySegment docHandle,
      Map<MetadataTag, String> pendingMetadata,
      MetadataProvider nativeMetadata,
      boolean hasInfoUpdate,
      XmpUpdate pendingXmp,
      SeekableByteChannel originalSource,
      Path sourcePath,
      byte[] originalBytes,
      MemorySegment sourceSegment,
      boolean structurallyModified,
      OutputStream out,
      boolean allowIncrementalOutput,
      boolean forceNativeRewrite,
      int nativeSaveFlags,
      int sourceFileVersion) {}

  private static final MetadataTag[] METADATA_TAG_VALUES = MetadataTag.values();

  @FunctionalInterface
  interface MetadataProvider {
    String get(MetadataTag tag);
  }

  private static long packRef(int num, int gen) {
    return ((long) num << 32) | (gen & 0xFFFFFFFFL);
  }

  private static int unpackNum(long ref) {
    return (int) (ref >> 32);
  }

  private static int unpackGen(long ref) {
    return (int) ref;
  }

  private static final long NULL_REF = packRef(-1, -1);

  // Byte constants for zero-allocation dictionary key scanning (replacing regex patterns).
  private static final byte[] FILTER_KEY = "/Filter".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] LENGTH_KEY = "/Length".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] PREDICTOR_KEY = "/Predictor".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] COLUMNS_KEY = "/Columns".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] W_KEY = "/W".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] INDEX_KEY = "/Index".getBytes(StandardCharsets.ISO_8859_1);

  private static final byte[] DICT_START = "<<".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] DICT_END = ">>".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] TRAILER_KEYWORD = "trailer".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] STARTXREF_KEYWORD = "startxref".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] XREF_KEYWORD = "xref".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] STREAM_KEYWORD = "stream".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] ENDSTREAM_KEYWORD = "endstream".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] TYPE_KEY = "/Type".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] XREF_TYPE_NAME = "/XRef".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] CATALOG_TYPE_NAME = "/Catalog".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] PAGES_TYPE_NAME = "/Pages".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] ROOT_KEY = "/Root".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] INFO_KEY = "/Info".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] PREV_KEY = "/Prev".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] PAGES_KEY = "/Pages".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] SIZE_KEY = "/Size".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] OBJ_KEYWORD = "obj".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] ENCRYPT_KEY = "/Encrypt".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] ID_KEY = "/ID".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] AUTHOR_KEY = "/Author".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] TITLE_KEY = "/Title".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] CREATION_DATE_KEY =
      "/CreationDate".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] MOD_DATE_KEY = "/ModDate".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] PRODUCER_KEY = "/Producer".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] CREATOR_KEY = "/Creator".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] XREF_HEADER = "xref\n".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] XREF_ENTRY_TEMPLATE =
      "0000000000 00000 n \n".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] XREF_FREE_ENTRY_0 =
      "0000000000 65535 f \r\n".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] OBJ_MARKER =
      " 0 obj\n<< /Type /Catalog /Pages ".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] R_MARKER = " R >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] TRAILER_START =
      "trailer\n<< /Size ".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] ROOT_KEY_BYTES = " /Root ".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] INFO_KEY_BYTES = " /Info ".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] ID_KEY_BYTES = " /ID ".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] ENCRYPT_KEY_BYTES =
      " /Encrypt ".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] PREV_KEY_BYTES = " /Prev ".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] STARTXREF_MARKER =
      " >>\nstartxref\n".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] EOF_MARKER = "\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] R_REF_SUFFIX = " R".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] CLOSE_BRACKET = "]".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] ZERO_R = " 0 R".getBytes(StandardCharsets.ISO_8859_1);

  // Indices for trailer data in primitive long[] buffer
  private static final int TRAILER_IDX_ROOT = 0;
  private static final int TRAILER_IDX_INFO = 1;
  private static final int TRAILER_IDX_SIZE = 2;
  private static final int TRAILER_IDX_PREV = 3;
  private static final int TRAILER_IDX_ID_OFF = 4;
  private static final int TRAILER_IDX_ID_LEN = 5;
  private static final int TRAILER_IDX_ENCRYPT = 6;
  private static final int TRAILER_IDX_HAS_SIZE = 7;
  private static final int TRAILER_IDX_HAS_ENCRYPT = 8;
  private static final int TRAILER_IDX_TEMP_LEN = 9;
  private static final int TRAILER_IDX_COUNT = 10;

  private static final ThreadLocal<int[]> XREF_W_BUF = ThreadLocal.withInitial(() -> new int[3]);
  private static final ThreadLocal<int[]> XREF_INDEX_BUF =
      ThreadLocal.withInitial(
          () -> {
            int[] arr = new int[256];
            Arrays.fill(arr, -1);
            return arr;
          });
  private static final ThreadLocal<long[]> XREF_ENTRY_BUF_TL =
      ThreadLocal.withInitial(() -> new long[3]);
  private static final ThreadLocal<long[]> RANGE_BUF = ThreadLocal.withInitial(() -> new long[2]);

  private static final ThreadLocal<long[][]> TRAILER_STACK =
      ThreadLocal.withInitial(
          () -> {
            long[][] stack = new long[8][];
            for (int i = 0; i < 8; i++) stack[i] = new long[TRAILER_IDX_COUNT];
            return stack;
          });
  private static final ThreadLocal<int[]> TRAILER_STACK_PTR =
      ThreadLocal.withInitial(() -> new int[1]);

  private static long[] acquireTrailerData() {
    int ptr = TRAILER_STACK_PTR.get()[0];
    if (ptr >= 8) return new long[TRAILER_IDX_COUNT]; // Fallback to allocation if too deep
    long[] arr = TRAILER_STACK.get()[ptr];
    TRAILER_STACK_PTR.get()[0] = ptr + 1;
    Arrays.fill(arr, -1L);
    return arr;
  }

  private static void releaseTrailerData() {
    TRAILER_STACK_PTR.get()[0]--;
  }

  private static final ThreadLocal<BasePdf> REUSABLE_BASE_PDF =
      ThreadLocal.withInitial(() -> new BasePdf(null, null));

  // getTrailerData removed as unused

  private static final class BasePdf {
    private MemorySegment segment;
    private Path tempPath;

    BasePdf(MemorySegment segment, Path tempPath) {
      this.segment = segment;
      this.tempPath = tempPath;
    }

    void update(MemorySegment segment, Path tempPath) {
      this.segment = segment;
      this.tempPath = tempPath;
    }

    MemorySegment segment() {
      return segment;
    }

    Path tempPath() {
      return tempPath;
    }
  }

  private PdfSaver() {
    super();
  }

  static void saveNativeFullRewrite(
      MemorySegment docHandle, OutputStream out, int sourceFileVersion) throws IOException {
    NativeSaveContext context = NATIVE_SAVE_CONTEXT.get();
    context.prepare(out);
    try {
      int ok;
      if (sourceFileVersion > 0 && EditBindings.fpdfSaveWithVersion() != null) {
        ok =
            (int)
                EditBindings.fpdfSaveWithVersion()
                    .invokeExact(
                        docHandle,
                        context.fileWrite(),
                        EditBindings.FPDF_NO_INCREMENTAL,
                        sourceFileVersion);
      } else {
        ok =
            (int)
                EditBindings.fpdfSaveAsCopy()
                    .invokeExact(docHandle, context.fileWrite(), EditBindings.FPDF_NO_INCREMENTAL);
      }
      if (ok == 0) {
        if (context.failure() != null) {
          throw context.failure();
        }
        throw new IOException("Native PDFium full rewrite failed");
      }
      if (context.failure() != null) {
        throw context.failure();
      }
    } catch (IOException e) {
      throw e;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to save document", t);
    } finally {
      context.finish();
    }
  }

  static void save(SaveParams params) throws IOException {
    boolean hasXmpUpdate = hasXmpUpdate(params.pendingXmp());
    boolean hasUpdate = params.hasInfoUpdate() || hasXmpUpdate;

    if (hasUpdate && !params.allowIncrementalOutput() && !params.forceNativeRewrite()) {
      throw new IOException(
          "Incremental save to a generic OutputStream is disabled; use save(Path) or saveToBytes() instead");
    }

    try (var _ = ScratchBuffer.acquireScope()) {
      if (hasUpdate && !params.forceNativeRewrite()) {
        try {
          writeIncrementalUpdate(params);
        } catch (PdfiumException e) {
          if (!hasXmpUpdate && isRecoverable(e)) {
            applyMetadataToNative(params);
            saveNativeFullRewrite(params.docHandle(), params.out(), params.sourceFileVersion());
          } else {
            throw e;
          }
        }
      } else if (hasUpdate) {
        applyMetadataToNative(params);
        saveNativeFullRewrite(params.docHandle(), params.out(), params.sourceFileVersion());
      } else {
        BasePdf base = getBaseSegment(params);
        try {
          writeSource(params, base.segment(), params.out());
        } finally {
          deleteIfExists(base.tempPath());
        }
      }
    }
  }

  static void repair(Path source, OutputStream out) throws IOException {
    // Phase 1: Try native repair (PDFium's internal recovery)
    if (nativeRepair(source, out)) {
      return;
    }

    // Phase 2: Fallback to brute-force scanner for severely corrupted files
    try (var _ = ScratchBuffer.acquireScope();
        FileChannel fc = FileChannel.open(source, StandardOpenOption.READ)) {
      MemorySegment pdf = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size(), Arena.ofShared());
      repair(pdf, out);
    }
  }

  static byte[] repair(byte[] data) throws IOException {
    // Phase 1: Try native repair
    byte[] nativeRepaired = nativeRepair(data);
    if (nativeRepaired != null && nativeRepaired.length > 0) {
      return nativeRepaired;
    }

    // Phase 2: Fallback to brute-force
    ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 256);
    repair(MemorySegment.ofArray(data), out);
    return out.toByteArray();
  }

  private static void applyMetadataToNative(SaveParams params) {
    if (params.pendingMetadata().isEmpty()) return;
    for (var entry : params.pendingMetadata().entrySet()) {
      try {
        MemorySegment keySeg = ScratchBuffer.getUtf8(entry.getKey().pdfKey());
        MemorySegment valSeg = ScratchBuffer.getUtf8(entry.getValue());
        int rc =
            (int)
                ShimBindings.pdfium4jSetMetaUtf8().invokeExact(params.docHandle(), keySeg, valSeg);
        if (rc == 0 && LOGGER.isLoggable(Level.FINE)) {
          LOGGER.log(
              Level.FINE, "pdfium4j_set_meta_utf8 returned 0 for {0}", entry.getKey().pdfKey());
        }
      } catch (Throwable t) {
        PdfiumLibrary.ignore(t);
      }
    }
  }

  private static boolean isRecoverable(PdfiumException e) {
    String msg = e.getMessage();
    if (msg == null) return false;
    return msg.contains("encrypted")
        || msg.contains("incremental metadata save")
        || msg.contains("IOException")
        || e.getCause() instanceof IOException;
  }

  private static boolean nativeRepair(Path source, OutputStream out) {
    try {
      // We open without repair policy to avoid recursion, but in STRICT mode to let PDFium handle
      // recovery
      // We use a low pixel budget since we don't plan to render
      PdfProcessingPolicy policy =
          PdfProcessingPolicy.defaultPolicy().withMode(PdfProcessingPolicy.Mode.STRICT);
      try (PdfDocument doc = PdfDocument.open(source, null, policy)) {
        // If it opened, PDFium's recovery worked. Save it clean.
        saveNativeFullRewrite(doc.handle(), out, doc.fileVersion());
        return true;
      }
    } catch (Exception t) {
      PdfiumLibrary.ignore(t);
      return false;
    }
  }

  private static byte[] nativeRepair(byte[] data) {
    try {
      PdfProcessingPolicy policy =
          PdfProcessingPolicy.defaultPolicy().withMode(PdfProcessingPolicy.Mode.STRICT);
      try (PdfDocument doc = PdfDocument.open(data, null, policy)) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        saveNativeFullRewrite(doc.handle(), out, doc.fileVersion());
        return out.toByteArray();
      }
    } catch (Exception e) {
      PdfiumLibrary.ignore(e);
      return null;
    }
  }

  private static boolean nativeRepair(MemorySegment pdf, OutputStream out) {
    try {
      PdfProcessingPolicy policy =
          PdfProcessingPolicy.defaultPolicy().withMode(PdfProcessingPolicy.Mode.STRICT);
      try (PdfDocument doc = PdfDocument.open(pdf, policy)) {
        saveNativeFullRewrite(doc.handle(), out, doc.fileVersion());
        return true;
      }
    } catch (Exception t) {
      PdfiumLibrary.ignore(t);
      return false;
    }
  }

  static void repair(MemorySegment pdf, OutputStream out) throws IOException {
    // Phase 1: Try native repair
    if (nativeRepair(pdf, out)) {
      return;
    }

    // Phase 2: Brute-force fallback
    long currentXrefOffset = findLastStartxrefValue(pdf);
    if (LOGGER.isLoggable(Level.INFO)) {
      LOGGER.log(
          Level.INFO,
          "Starting zero-allocation PDF repair. Original xref offset: {0}",
          currentXrefOffset);
    }

    // Single Pass: Reconstruct offsets and find roots - zero allocation
    RepairResult result = REPAIR_RESULT.get();
    resetRepairResult(result);

    // We don't know maxObj yet, so we use a large enough initial array or grow it.
    // For most PDFs, obj count is < 1M.
    long[] offsets = getRepairOffsets(1024);
    Arrays.fill(offsets, -1L);

    int finalMaxObj = scanAndReconstruct(pdf, offsets, result);

    // Synthesis and Output - zero allocation
    writeSegment(pdf, out);
    out.write('\n');
    long currentOffset = pdf.byteSize() + 1;

    if (result.rootNum > finalMaxObj) {
      // It's a synthesized catalog
      offsets = getRepairOffsets(result.rootNum + 1);
      offsets[result.rootNum] = currentOffset;
      currentOffset += writeSynthesizedCatalog(out, result.rootNum, result.pagesNum);
      finalMaxObj = result.rootNum;
    }

    long xrefOffset = currentOffset;
    writeFullReconstructedXrefTable(out, offsets, finalMaxObj);
    writeTrailerPrimitives(out, result, 0, finalMaxObj + 1, 0, xrefOffset, pdf);
  }

  private static int writeSynthesizedCatalog(OutputStream out, int num, int pagesNum)
      throws IOException {
    byte[] intBuf = REPAIR_INT_BUF.get();
    int written = 0;

    int len = formatInt(intBuf, num);
    out.write(intBuf, intBuf.length - len, len);
    out.write(OBJ_MARKER);
    written += len + OBJ_MARKER.length;

    len = formatInt(intBuf, pagesNum);
    out.write(intBuf, intBuf.length - len, len);
    out.write(' ');
    out.write('0');
    out.write(R_MARKER);
    written += len + 1 + 1 + R_MARKER.length;
    return written;
  }

  private static long[] getRepairOffsets(int needed) {
    long[] offsets = REPAIR_OFFSETS.get();
    if (offsets.length < needed) {
      offsets = new long[needed + 1024];
      REPAIR_OFFSETS.set(offsets);
    }
    return offsets;
  }

  private static void writeFullReconstructedXrefTable(
      OutputStream update, long[] objOffsets, int maxObj) throws IOException {
    update.write(XREF_HEADER);
    // Write a single subsection from 0 to maxObj
    byte[] intBuf = REPAIR_INT_BUF.get();
    int len = formatInt(intBuf, 0);
    update.write(intBuf, intBuf.length - len, len);
    update.write(' ');
    len = formatInt(intBuf, maxObj + 1);
    update.write(intBuf, intBuf.length - len, len);
    update.write('\n');

    byte[] entryBuf = REPAIR_ENTRY_BUF.get();
    for (int i = 0; i <= maxObj; i++) {
      if (i == 0) {
        update.write(XREF_FREE_ENTRY_0);
        continue;
      }
      long offset = objOffsets[i];
      if (offset != -1) {
        formatXrefEntry(entryBuf, offset, true);
      } else {
        formatXrefEntry(entryBuf, 0, false);
      }
      update.write(entryBuf);
    }
  }

  private static void formatXrefEntry(byte[] buf, long offset, boolean inUse) {
    // Standard entry: "nnnnnnnnnn ggggg n \n" or "nnnnnnnnnn ggggg f \n"
    System.arraycopy(XREF_ENTRY_TEMPLATE, 0, buf, 0, 20);
    long tempOffset = offset;
    for (int i = 9; i >= 0; i--) {
      buf[i] = (byte) ('0' + (tempOffset % 10));
      tempOffset /= 10;
    }
    for (int i = 15; i >= 11; i--) {
      buf[i] = (byte) ('0' + (0));
    }
    buf[17] = (byte) (inUse ? 'n' : 'f');
  }

  private static void resetRepairResult(RepairResult res) {
    res.rootNum = -1;
    res.infoNum = -1;
    res.encryptNum = -1;
    res.pagesNum = -1;
    res.idOffset = -1;
    res.idLen = -1;
  }

  private static int scanAndReconstruct(MemorySegment pdf, long[] objOffsets, RepairResult result)
      throws IOException {
    int maxObj = 0;
    long searchPos = 0;
    long size = pdf.byteSize();

    while (searchPos < size) {
      long nextPos = processNextToken(pdf, searchPos, size, objOffsets, result);
      if (nextPos > searchPos) {
        searchPos = nextPos;
      } else {
        searchPos++;
      }
    }

    // Re-scan to find maxObj accurately if needed, or just track it in processNextToken
    for (int i = objOffsets.length - 1; i >= 0; i--) {
      if (objOffsets[i] != -1) {
        maxObj = i;
        break;
      }
    }

    validateRepairResult(result, maxObj);
    return maxObj;
  }

  private static long processNextToken(
      MemorySegment pdf, long pos, long size, long[] objOffsets, RepairResult result) {
    byte b = pdf.get(JAVA_BYTE, pos);
    if (b == '(') return findStringEnd(pdf, pos, size);
    if (b == '%') return skipComment(pdf, pos, size);
    if (b == 'o' && matchesBytesAt(pdf, pos, OBJ_KEYWORD)) {
      processObjKeyword(pdf, pos, objOffsets, result);
      return pos + OBJ_KEYWORD.length;
    }
    if (b == 't' && matchesBytesAt(pdf, pos, TRAILER_KEYWORD)) {
      processTrailerKeyword(pdf, pos, result);
      return pos + TRAILER_KEYWORD.length;
    }
    if (b == '/' && matchesBytesAt(pdf, pos, ID_KEY)) {
      processIdKey(pdf, pos, size, result);
      return pos + ID_KEY.length;
    }
    return -1;
  }

  private static void validateRepairResult(RepairResult result, int maxObj) throws IOException {
    if (result.rootNum == -1) {
      if (result.pagesNum != -1) {
        result.rootNum = maxObj + 1;
      } else {
        throw new IOException(
            "Failed to locate Catalog root or Page tree via exhaustive single-pass scan");
      }
    }
  }

  private static int processObjKeyword(
      MemorySegment pdf, long pos, long[] objOffsets, RepairResult result) {
    long ref = parseObjectHeaderRef(pdf, pos);
    if (ref == NULL_REF) return -1;
    int num = unpackNum(ref);
    if (num <= 0 || num > 10_000_000) return -1;

    objOffsets[num] = pos;
    if (isCatalogAt(pdf, pos)) result.rootNum = num;
    else if (isInfoAt(pdf, pos)) result.infoNum = num;
    else if (isPagesAt(pdf, pos)) result.pagesNum = num;
    return num;
  }

  private static void processTrailerKeyword(MemorySegment pdf, long pos, RepairResult result) {
    long dictStart = indexOf(pdf, DICT_START, pos);
    if (dictStart < 0) return;
    long dictEnd = findDictionaryEnd(pdf, dictStart);
    if (dictEnd <= dictStart) return;

    int rootNum = findTopLevelObjectRefNumForKey(pdf, dictStart, dictEnd, ROOT_KEY);
    if (rootNum > 0) result.rootNum = rootNum;

    int infoNum = findTopLevelObjectRefNumForKey(pdf, dictStart, dictEnd, INFO_KEY);
    if (infoNum > 0) result.infoNum = infoNum;

    int encryptNum = findTopLevelObjectRefNumForKey(pdf, dictStart, dictEnd, ENCRYPT_KEY);
    if (encryptNum > 0) result.encryptNum = encryptNum;

    long idKeyPos = findTopLevelKey(pdf, dictStart, dictEnd, ID_KEY);
    if (idKeyPos >= 0) {
      long valPos = skipAsciiWhitespace(pdf, idKeyPos + ID_KEY.length, dictEnd);
      if (valPos < dictEnd && pdf.get(JAVA_BYTE, valPos) == '[') {
        long endPos = indexOf(pdf, new byte[] {']'}, valPos);
        if (endPos >= 0 && endPos < dictEnd) {
          result.idOffset = valPos;
          result.idLen = endPos + 1 - valPos;
        }
      }
    }
  }

  private static void processIdKey(MemorySegment pdf, long pos, long size, RepairResult result) {
    long valPos = skipAsciiWhitespace(pdf, pos + ID_KEY.length, size);
    if (valPos < size && pdf.get(JAVA_BYTE, valPos) == '[') {
      long endPos = indexOf(pdf, CLOSE_BRACKET, valPos);
      if (endPos >= 0) {
        result.idOffset = valPos;
        result.idLen = endPos + 1 - valPos;
      }
    }
  }

  private static boolean isInfoDictionary(MemorySegment pdf, long ds, long de) {
    return findTopLevelKey(pdf, ds, de, TITLE_KEY) >= 0
        || findTopLevelKey(pdf, ds, de, AUTHOR_KEY) >= 0
        || findTopLevelKey(pdf, ds, de, PRODUCER_KEY) >= 0
        || findTopLevelKey(pdf, ds, de, CREATOR_KEY) >= 0
        || findTopLevelKey(pdf, ds, de, CREATION_DATE_KEY) >= 0
        || findTopLevelKey(pdf, ds, de, MOD_DATE_KEY) >= 0;
  }

  private static int findTopLevelObjectRefNumForKey(
      MemorySegment pdf, long ds, long de, byte[] key) {
    long keyPos = findTopLevelKey(pdf, ds, de, key);
    if (keyPos < 0) return -1;
    long valPos = skipAsciiWhitespace(pdf, keyPos + key.length, de);
    long ref = parseObjectRef(pdf, valPos, de);
    return ref != NULL_REF ? unpackNum(ref) : -1;
  }

  private static boolean isCatalogAt(MemorySegment pdf, long headerPos) {
    try {
      long dictStart = indexOf(pdf, DICT_START, headerPos);
      if (dictStart < 0) return false;
      long dictEnd = findDictionaryEnd(pdf, dictStart);
      if (dictEnd <= dictStart) return false;
      return isCatalogDictionary(pdf, dictStart, dictEnd);
    } catch (Exception _) {
      return false;
    }
  }

  private static boolean isInfoAt(MemorySegment pdf, long headerPos) {
    try {
      long dictStart = indexOf(pdf, DICT_START, headerPos);
      if (dictStart < 0) return false;
      long dictEnd = findDictionaryEnd(pdf, dictStart);
      if (dictEnd <= dictStart) return false;
      return isInfoDictionary(pdf, dictStart, dictEnd);
    } catch (Exception _) {
      return false;
    }
  }

  private static boolean isPagesAt(MemorySegment pdf, long headerPos) {
    try {
      long dictStart = indexOf(pdf, DICT_START, headerPos);
      if (dictStart < 0) return false;
      long dictEnd = findDictionaryEnd(pdf, dictStart);
      if (dictEnd <= dictStart) return false;
      return isPagesDictionary(pdf, dictStart, dictEnd);
    } catch (Exception _) {
      return false;
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

  private static BasePdf getBaseSegment(SaveParams params) throws IOException {
    BasePdf res = REUSABLE_BASE_PDF.get();
    if (params.forceNativeRewrite() || params.structurallyModified()) {
      res.update(
          nativeSaveToSegment(
              params.docHandle(), params.nativeSaveFlags(), params.sourceFileVersion()),
          null);
    } else if (params.sourceSegment() != null) {
      res.update(params.sourceSegment(), null);
    } else if (params.originalBytes() != null) {
      res.update(MemorySegment.ofArray(params.originalBytes()), null);
    } else if (params.sourcePath() != null) {
      try (FileChannel fc = FileChannel.open(params.sourcePath(), StandardOpenOption.READ)) {
        res.update(fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size(), Arena.ofShared()), null);
      }
    }
    return res;
  }

  private static MemorySegment nativeSaveToSegment(
      MemorySegment docHandle, int saveFlags, int sourceFileVersion) {
    ReusableByteArrayOutputStream baos = SAVE_CALLBACK_TARGET.get();
    baos.resetForReuse(SAVE_CALLBACK_MAX_RETAINED_CAPACITY);
    try (var _ = ScratchBuffer.acquireScope()) {
      if (EditBindings.fpdfSaveAsCopy() == null) {
        throw new PdfiumException("fpdfSaveAsCopy not available in this PDFium build");
      }
      MemorySegment fileWrite = ScratchBuffer.get(EditBindings.FPDF_FILEWRITE_LAYOUT.byteSize());
      fileWrite.set(JAVA_INT, 0, 1);
      fileWrite.set(ADDRESS, 8, WRITE_BLOCK_STUB);

      int ok;
      if (sourceFileVersion > 0 && EditBindings.fpdfSaveWithVersion() != null) {
        ok =
            (int)
                EditBindings.fpdfSaveWithVersion()
                    .invokeExact(docHandle, fileWrite, saveFlags, sourceFileVersion);
      } else {
        ok = (int) EditBindings.fpdfSaveAsCopy().invokeExact(docHandle, fileWrite, saveFlags);
      }
      if (ok == 0) throw new PdfiumException("Native PDFium save failed");

      // Zero-allocation: use a slice of the pooled buffer
      return MemorySegment.ofArray(baos.internalBuffer()).asSlice(0, baos.size());
    } catch (PdfiumException e) {
      throw e;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to save document", t);
    }
  }

  @SuppressWarnings({"PMD.UnusedFormalParameter", "unused"})
  private static int writeBlockCallback(MemorySegment pThis, MemorySegment pData, long size) {
    NativeSaveContext streamContext = NATIVE_SAVE_CONTEXT.get();
    if (streamContext.isActive()) {
      return streamContext.write(pData, size);
    }
    if (FfmHelper.isNull(pThis) || FfmHelper.isNull(pData)) return 0;
    MemorySegment pDataSized = pData.reinterpret(size);
    ReusableByteArrayOutputStream baos = SAVE_CALLBACK_TARGET.get();
    if (baos == null) return 0;
    if (size <= 0) return 0;
    byte[] buf = SAVE_CALLBACK_BUF.get();
    long offset = 0;
    long remaining = size;
    while (remaining > 0) {
      int chunk = (int) Math.min(buf.length, remaining);
      MemorySegment.copy(
          pDataSized, JAVA_BYTE, offset, SAVE_CALLBACK_BUF_SEG.get(), JAVA_BYTE, 0, chunk);
      baos.write(buf, 0, chunk);
      offset += chunk;
      remaining -= chunk;
    }
    return 1;
  }

  private static MethodHandle lookupMemcpy() {
    Linker linker = Linker.nativeLinker();
    return linker
        .defaultLookup()
        .find("memcpy")
        .map(
            addr ->
                linker.downcallHandle(
                    addr, FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_LONG)))
        .orElse(null);
  }

  private static MethodHandle lookupWriteBlockCallback() {
    try {
      MethodHandle mh =
          MethodHandles.lookup()
              .findStatic(
                  PdfSaver.class,
                  "writeBlockCallback",
                  MethodType.methodType(
                      int.class, MemorySegment.class, MemorySegment.class, long.class));
      // Adapt carrier to match FFM's C_LONG (which is int on Windows)
      return mh.asType(EditBindings.WRITE_BLOCK_DESC.toMethodType());
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static final class NativeSaveContext {
    private final MemorySegment fileWrite =
        NATIVE_SAVE_ARENA.allocate(EditBindings.FPDF_FILEWRITE_LAYOUT);
    private final ByteBuffer transferBuffer = ByteBuffer.allocateDirect(8192);
    private final MemorySegment transferSegment = MemorySegment.ofBuffer(transferBuffer);
    private final byte[] fallbackBuffer = new byte[8192];
    private final MemorySegment fallbackSegment = MemorySegment.ofArray(fallbackBuffer);
    private OutputStream out;
    private FileChannel channel;
    private IOException failure;

    private NativeSaveContext() {
      fileWrite.set(JAVA_INT, 0, 1);
      fileWrite.set(ADDRESS, 8, WRITE_BLOCK_STUB);
    }

    private MemorySegment fileWrite() {
      return fileWrite;
    }

    private void prepare(OutputStream out) {
      this.out = out;
      if (out instanceof FileOutputStream fos) {
        this.channel = fos.getChannel();
      } else {
        this.channel = null;
      }
      this.failure = null;
    }

    private void finish() {
      this.out = null;
      this.channel = null;
      this.failure = null;
    }

    private boolean isActive() {
      return out != null;
    }

    private IOException failure() {
      return failure;
    }

    private void copyData(long transferAddr, long pDataAddr, long offset, int chunk)
        throws IOException {
      try {
        MEMCPY.invokeExact(transferAddr, pDataAddr + offset, (long) chunk);
      } catch (Throwable t) {
        throw new IOException("Failed to copy data during save", t);
      }
    }

    private int write(MemorySegment pData, long size) {
      if (failure != null || FfmHelper.isNull(pData) || size <= 0) {
        return failure == null ? 1 : 0;
      }

      try {
        long pDataAddr = pData.address();
        long offset = 0;
        long remaining = size;

        if (channel != null) {
          long transferAddr = transferSegment.address();
          while (remaining > 0) {
            int chunk = (int) Math.min(transferBuffer.capacity(), remaining);
            copyData(transferAddr, pDataAddr, offset, chunk);
            transferBuffer.position(0);
            transferBuffer.limit(chunk);
            while (transferBuffer.hasRemaining()) {
              channel.write(transferBuffer);
            }
            offset += chunk;
            remaining -= chunk;
          }
          return 1;
        }

        while (remaining > 0) {
          int chunk = (int) Math.min(fallbackBuffer.length, remaining);
          // We can't use memcpy for array-backed segments as their address is not stable
          // and not a raw pointer. Use MemorySegment.copy instead.
          // This will allocate a small wrapper, but this is the fallback path.
          MemorySegment.copy(
              pData.reinterpret(size), JAVA_BYTE, offset, fallbackSegment, JAVA_BYTE, 0, chunk);
          out.write(fallbackBuffer, 0, chunk);
          offset += chunk;
          remaining -= chunk;
        }
        return 1;
      } catch (IOException e) {
        failure = e;
        return 0;
      }
    }
  }

  private static final class ReusableByteArrayOutputStream extends ByteArrayOutputStream {
    private final int initialCapacity;

    ReusableByteArrayOutputStream(int initialCapacity) {
      super(initialCapacity);
      this.initialCapacity = initialCapacity;
    }

    void resetForReuse(int maxRetainedCapacity) {
      if (buf.length > maxRetainedCapacity) {
        buf = new byte[initialCapacity];
      }
      reset();
    }

    byte[] internalBuffer() {
      return buf;
    }
  }

  private static void writeIncrementalUpdate(SaveParams params) throws IOException {
    BasePdf base = getBaseSegment(params);
    MemorySegment pdf = base.segment();
    try {
      writeIncrementalUpdateBody(pdf, params);
    } catch (IOException e) {
      throw new PdfiumException("Incremental update failed", e);
    } finally {
      deleteIfExists(base.tempPath());
    }
  }

  private static void writeIncrementalUpdateBody(MemorySegment pdf, SaveParams params)
      throws IOException {
    long[] tail = parseTail(pdf);
    try {
      writeUpdate(pdf, tail, params);
    } finally {
      releaseTrailerData();
    }
  }

  private static void writeUpdate(MemorySegment pdf, long[] tail, SaveParams params)
      throws IOException {
    OutputStream out = params.out();
    writeSource(params, pdf, out);
    out.write('\n');

    ReusableByteArrayOutputStream updateBuf = SAVE_CALLBACK_TARGET.get();
    updateBuf.reset();

    int maxNeeded = (int) tail[TRAILER_IDX_SIZE] + 10;
    long[] objOffsets = getRepairOffsets(maxNeeded);
    Arrays.fill(objOffsets, -1L);

    int nextObj = (int) tail[TRAILER_IDX_SIZE];
    long currentOffset = pdf.byteSize() + 1;

    int infoObjNum = -1;
    if (params.hasInfoUpdate()) {
      infoObjNum = nextObj++;
      objOffsets[infoObjNum] = currentOffset;
      long before = updateBuf.size();
      writeInfoObject(updateBuf, infoObjNum, params.pendingMetadata(), params.nativeMetadata());
      currentOffset += (updateBuf.size() - before);
    }

    if (params.pendingXmp() != null) {
      int xmpObjNum = nextObj++;
      long beforeXmp = updateBuf.size();
      writeXmpObject(updateBuf, xmpObjNum, params.pendingXmp());
      long xmpSize = updateBuf.size() - beforeXmp;
      objOffsets[xmpObjNum] = currentOffset;
      currentOffset += xmpSize;

      long catalogRef = tail[TRAILER_IDX_ROOT];
      long[] catalogRange = RANGE_BUF.get();
      if (resolveObjectDictionaryRange(pdf, catalogRef, tail[TRAILER_IDX_PREV], catalogRange)) {
        long beforeCatalog = updateBuf.size();
        writeModifiedCatalog(
            updateBuf, catalogRef, pdf, catalogRange[0], catalogRange[1], xmpObjNum);
        objOffsets[unpackNum(catalogRef)] = currentOffset;
        currentOffset += (updateBuf.size() - beforeCatalog);
      }
    }

    long prevXrefOffset = tail[TRAILER_IDX_PREV];
    long xrefOffset = currentOffset;

    writeXrefTable(updateBuf, objOffsets);
    writeTrailer(updateBuf, tail, infoObjNum, nextObj, prevXrefOffset, xrefOffset, pdf);

    updateBuf.writeTo(out);
  }

  private static void writeXrefTable(OutputStream update, long[] objOffsets) throws IOException {
    update.write(XREF_HEADER);
    byte[] intBuf = REPAIR_INT_BUF.get();
    byte[] entryBuf = REPAIR_ENTRY_BUF.get();

    int maxObj = objOffsets.length - 1;
    int i = 0;
    while (i <= maxObj) {
      if (objOffsets[i] == -1) {
        i++;
        continue;
      }
      int start = i;
      int count = 0;
      while (i <= maxObj && objOffsets[i] != -1) {
        count++;
        i++;
      }

      int len = formatInt(intBuf, start);
      update.write(intBuf, intBuf.length - len, len);
      update.write(' ');
      len = formatInt(intBuf, count);
      update.write(intBuf, intBuf.length - len, len);
      update.write('\n');

      for (int j = 0; j < count; j++) {
        long offset = objOffsets[start + j];
        formatXrefEntry(entryBuf, offset, true);
        update.write(entryBuf);
      }
    }
  }

  private static int formatInt(byte[] buf, int value) {
    int pos = buf.length;
    if (value == 0) {
      --pos;
      buf[pos] = '0';
      return 1;
    }
    int temp = value;
    while (temp > 0) {
      --pos;
      buf[pos] = (byte) ('0' + (temp % 10));
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
    try {
      while (src.read(buffer) != -1) {
        buffer.flip();
        while (buffer.hasRemaining()) {
          dst.write(buffer);
        }
        buffer.clear();
      }
    } finally {
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
      OutputStream update,
      long[] tail,
      int infoObjNum,
      int nextObj,
      long prevXrefOffset,
      long xrefOffset,
      MemorySegment pdf)
      throws IOException {
    RepairResult result = REPAIR_RESULT.get();
    resetRepairResult(result);
    result.rootNum = unpackNum(tail[TRAILER_IDX_ROOT]);
    result.infoNum = (tail[TRAILER_IDX_INFO] != NULL_REF) ? unpackNum(tail[TRAILER_IDX_INFO]) : -1;
    // We don't currently support modifying Encrypt or ID in this path easily,
    // but we can preserve them if they were in the original trailer.
    writeTrailerPrimitives(update, result, infoObjNum, nextObj, prevXrefOffset, xrefOffset, pdf);
  }

  private static void writeTrailerPrimitives(
      OutputStream update,
      RepairResult result,
      int infoObjNum,
      int nextObj,
      long prevXrefOffset,
      long xrefOffset,
      MemorySegment pdf)
      throws IOException {
    update.write(TRAILER_START);

    byte[] intBuf = REPAIR_INT_BUF.get();
    int len = formatInt(intBuf, nextObj);
    update.write(intBuf, intBuf.length - len, len);

    update.write(ROOT_KEY_BYTES);
    writeRefNum(update, result.rootNum);

    if (infoObjNum > 0) {
      update.write(INFO_KEY_BYTES);
      len = formatInt(intBuf, infoObjNum);
      update.write(intBuf, intBuf.length - len, len);
      update.write(ZERO_R);
    } else if (result.infoNum > 0) {
      update.write(INFO_KEY_BYTES);
      writeRefNum(update, result.infoNum);
    }

    if (result.idOffset >= 0 && pdf != null) {
      update.write(ID_KEY_BYTES);
      long idLen = result.idLen;
      byte[] buffer = IO_BUFFER.get();
      long pos = result.idOffset;
      while (idLen > 0) {
        int toCopy = (int) Math.min(idLen, buffer.length);
        MemorySegment.copy(pdf, JAVA_BYTE, pos, buffer, 0, toCopy);
        update.write(buffer, 0, toCopy);
        pos += toCopy;
        idLen -= toCopy;
      }
    }

    if (result.encryptNum > 0) {
      update.write(ENCRYPT_KEY_BYTES);
      writeRefNum(update, result.encryptNum);
    }

    if (prevXrefOffset > 0) {
      update.write(PREV_KEY_BYTES);
      writeLong(update, prevXrefOffset);
    }

    update.write(STARTXREF_MARKER);
    writeLong(update, xrefOffset);
    update.write(EOF_MARKER);
  }

  private static void writeRefNum(OutputStream out, int num) throws IOException {
    byte[] intBuf = REPAIR_INT_BUF.get();
    int len = formatInt(intBuf, num);
    out.write(intBuf, intBuf.length - len, len);
    out.write(' ');
    len = formatInt(intBuf, 0);
    out.write(intBuf, intBuf.length - len, len);
    out.write(R_REF_SUFFIX);
  }

  private static void writeLong(OutputStream out, long value) throws IOException {
    byte[] longBuf = REPAIR_ENTRY_BUF.get(); // 20 bytes
    int pos = longBuf.length;
    if (value == 0) {
      out.write('0');
      return;
    }
    long temp = value;
    while (temp > 0) {
      --pos;
      longBuf[pos] = (byte) ('0' + (temp % 10));
      temp /= 10;
    }
    out.write(longBuf, pos, longBuf.length - pos);
  }

  private static void writeInfoObject(
      OutputStream out, int num, Map<MetadataTag, String> pending, MetadataProvider nativeMeta)
      throws IOException {
    writeLong(out, num);
    out.write(OBJ_START_BYTES);
    out.write(DICT_START);

    writeMetadataTags(out, pending, nativeMeta);
    writeModificationDate(out, pending);

    out.write(DICT_END);
    out.write(OBJ_END_BYTES);
  }

  private static void writeMetadataTags(
      OutputStream out, Map<MetadataTag, String> pending, MetadataProvider nativeMeta)
      throws IOException {
    for (MetadataTag tag : METADATA_TAG_VALUES) {
      String value = pending.getOrDefault(tag, nativeMeta.get(tag));
      if (value != null && !value.isEmpty()) {
        out.write('/');
        out.write(tag.pdfKeyBytes());
        out.write(' ');
        writePdfString(out, value);
        out.write('\n');
      }
    }
  }

  private static void writeModificationDate(OutputStream out, Map<MetadataTag, String> pending)
      throws IOException {
    String explicitModDate = pending.get(MetadataTag.MOD_DATE);
    boolean hasExplicitModDate =
        explicitModDate != null
            && !explicitModDate.isBlank()
            && isLikelyPdfDate(explicitModDate.trim());
    if (!hasExplicitModDate) {
      out.write(MOD_DATE_KEY);
      writeCurrentPdfDate(out);
      out.write('\n');
    }
  }

  private static void writeUtf8(OutputStream out, String s) throws IOException {
    if (s == null || s.isEmpty()) return;
    out.write(s.getBytes(StandardCharsets.UTF_8));
  }

  private static void writePdfString(OutputStream out, String s) throws IOException {
    if (s == null || s.isEmpty()) {
      out.write('(');
      out.write(')');
      return;
    }

    boolean isAscii = true;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) > 127) {
        isAscii = false;
        break;
      }
    }

    out.write('(');
    if (isAscii) {
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c == '(' || c == ')' || c == '\\') {
          out.write('\\');
        }
        out.write(c);
      }
    } else {
      // Write UTF-16BE with BOM
      writeEscapedByte(out, (byte) 0xFE);
      writeEscapedByte(out, (byte) 0xFF);
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        writeEscapedByte(out, (byte) (c >> 8));
        writeEscapedByte(out, (byte) (c & 0xFF));
      }
    }
    out.write(')');
  }

  private static void writeEscapedByte(OutputStream out, byte b) throws IOException {
    if (b == '(' || b == ')' || b == '\\') {
      out.write('\\');
    }
    out.write(b);
  }

  private static void writeXmpObject(OutputStream out, int num, XmpUpdate xmp) throws IOException {
    ReusableByteArrayOutputStream contentBuf = XMP_CONTENT_BUFFER.get();
    contentBuf.reset();
    switch (xmp) {
      case XmpUpdate.Raw(String xmpStr) -> writeUtf8(contentBuf, xmpStr);
      case XmpUpdate.Structured(XmpMetadata metadata) -> XMP_WRITER.write(metadata, contentBuf);
    }

    writeLong(out, num);
    out.write(XMP_HEADER_START);
    writeLong(out, contentBuf.size());
    out.write(XMP_HEADER_END);
    contentBuf.writeTo(out);
    out.write(XMP_FOOTER);
  }

  private static final byte[] XMP_HEADER_START =
      " 0 obj\n<< /Type /Metadata /Subtype /XML /Length ".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] XMP_HEADER_END =
      " >>\nstream\n".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] XMP_FOOTER =
      "\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);

  private static void writeModifiedCatalog(
      OutputStream out,
      long catalogRef,
      MemorySegment pdf,
      long dictStart,
      long dictEnd,
      int xmpObjNum)
      throws IOException {
    writeLong(out, unpackNum(catalogRef));
    out.write(' ');
    writeLong(out, unpackGen(catalogRef));
    out.write(OBJ_START_BYTES);

    // Remove existing /Metadata reference and write updated catalog dict
    long metadataPos = findTopLevelKey(pdf, dictStart, dictEnd, METADATA_KEY);
    if (metadataPos >= 0) {
      // Write before /Metadata
      writeSegmentPart(pdf, dictStart, metadataPos, out);
      // Inject new /Metadata reference
      out.write(METADATA_KEY);
      out.write(' ');
      writeLong(out, xmpObjNum);
      out.write(ZERO_R);
      // Skip old /Metadata N N R
      long end = skipMetadataRef(pdf, metadataPos, dictEnd);
      // Write after /Metadata
      writeSegmentPart(pdf, end, dictEnd, out);
    } else {
      // Inject before closing >>
      long closeIdx = lastIndexOf(pdf, DICT_END, dictEnd);
      if (closeIdx >= dictStart) {
        writeSegmentPart(pdf, dictStart, closeIdx, out);
        out.write(METADATA_KEY);
        out.write(' ');
        writeLong(out, xmpObjNum);
        out.write(ZERO_R);
        writeSegmentPart(pdf, closeIdx, dictEnd, out);
      } else {
        writeSegmentPart(pdf, dictStart, dictEnd, out);
      }
    }
    out.write(OBJ_END_BYTES);
  }

  private static long skipMetadataRef(MemorySegment pdf, long start, long end) {
    long pos = start + METADATA_KEY.length;
    pos = skipAsciiWhitespace(pdf, pos, end);
    pos = scanDigits(pdf, pos, end); // obj num
    pos = skipAsciiWhitespace(pdf, pos, end);
    pos = scanDigits(pdf, pos, end); // gen num
    pos = skipAsciiWhitespace(pdf, pos, end);
    if (pos < end && pdf.get(JAVA_BYTE, pos) == 'R') {
      return pos + 1;
    }
    return pos;
  }

  private static void writeSegmentPart(MemorySegment seg, long start, long end, OutputStream out)
      throws IOException {
    if (start >= end) return;
    byte[] buffer = IO_BUFFER.get();
    long pos = start;
    long len = end - start;
    while (len > 0) {
      int toCopy = (int) Math.min(len, buffer.length);
      MemorySegment.copy(seg, JAVA_BYTE, pos, buffer, 0, toCopy);
      out.write(buffer, 0, toCopy);
      pos += toCopy;
      len -= toCopy;
    }
  }

  private static final byte[] OBJ_START_BYTES = " 0 obj\n".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] OBJ_END_BYTES = "\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] METADATA_KEY = "/Metadata".getBytes(StandardCharsets.ISO_8859_1);

  // isWs removed as unused

  private static long[] parseTail(MemorySegment pdf) throws IOException {
    for (long step : TAIL_SCAN_STEPS) {
      long[] parsed = tryParseTail(pdf, Math.min(pdf.byteSize(), step));
      if (parsed != null) {
        return parsed;
      }
    }
    throw new IOException("Failed to parse PDF trailer from tail window");
  }

  @CheckForNull
  private static long[] tryParseTail(MemorySegment pdf, long requestedTailBytes) {
    long tailLen = Math.min(pdf.byteSize(), requestedTailBytes);
    if (tailLen <= 0) {
      return null;
    }
    MemorySegment tail = pdf.asSlice(pdf.byteSize() - tailLen, tailLen);
    try {
      long prevXrefOffset = findLastStartxrefValue(tail);
      long[] trailer = acquireTrailerData();
      parseTrailer(tail, pdf, prevXrefOffset, trailer);
      trailer[TRAILER_IDX_PREV] = prevXrefOffset;
      return trailer;
    } catch (IOException _) {
      return null;
    }
  }

  private static void parseTrailer(
      MemorySegment tail, MemorySegment pdf, long prevXrefOffset, long[] out) throws IOException {
    TrailerState state = new TrailerState();

    scanTailForTrailers(tail, pdf, out, state);

    if (shouldScanXrefStream(state, prevXrefOffset)) {
      parseXrefStreamFields(pdf, prevXrefOffset, out);
      updateTrailerState(pdf, out, state, false);
    }

    validateAndFinalizeTrailer(pdf, out, state, prevXrefOffset);
  }

  private static class TrailerState {
    long rootRef = NULL_REF;
    long infoRef = NULL_REF;
    int size = 0;
    long idOffset = -1;
    long idLen = -1;
    boolean hasSizeEntry = false;
    boolean hasEncrypt = false;
  }

  private static void scanTailForTrailers(
      MemorySegment tail, MemorySegment pdf, long[] out, TrailerState state) {
    long searchFrom = tail.byteSize();
    while (shouldContinueTailScan(state, searchFrom)) {
      long trailerIdx = lastIndexOf(tail, TRAILER_KEYWORD, searchFrom);
      if (trailerIdx < 0) break;

      long dictStart = indexOf(tail, DICT_START, trailerIdx + TRAILER_KEYWORD.length);
      if (dictStart >= 0) {
        long dictEnd = findDictionaryEnd(tail, dictStart);
        if (dictEnd > dictStart) {
          parseTrailerDictionary(tail, dictStart, dictEnd, out);
          updateTrailerState(pdf, out, state, true);
        }
      }
      searchFrom = trailerIdx - 1;
    }
  }

  private static boolean shouldContinueTailScan(TrailerState state, long searchFrom) {
    return (state.rootRef == NULL_REF || state.infoRef == NULL_REF || state.size == 0)
        && searchFrom > 0;
  }

  private static boolean shouldScanXrefStream(TrailerState state, long prevXrefOffset) {
    return (state.rootRef == NULL_REF || state.size == 0) && prevXrefOffset > 0;
  }

  private static void updateTrailerState(
      MemorySegment pdf, long[] out, TrailerState state, boolean fromTail) {
    if (state.rootRef == NULL_REF && out[TRAILER_IDX_ROOT] != NULL_REF) {
      state.rootRef = out[TRAILER_IDX_ROOT];
    }
    if (state.infoRef == NULL_REF && out[TRAILER_IDX_INFO] != NULL_REF) {
      state.infoRef = out[TRAILER_IDX_INFO];
    }
    if (state.size == 0 && out[TRAILER_IDX_SIZE] > 0) {
      state.size = (int) out[TRAILER_IDX_SIZE];
    }
    if (!state.hasSizeEntry && out[TRAILER_IDX_HAS_SIZE] != 0) {
      state.hasSizeEntry = true;
    }
    if (state.idOffset < 0 && out[TRAILER_IDX_ID_OFF] >= 0) {
      state.idOffset =
          fromTail
              ? (pdf.byteSize() - out[TRAILER_IDX_TEMP_LEN] + out[TRAILER_IDX_ID_OFF])
              : out[TRAILER_IDX_ID_OFF];
      state.idLen = out[TRAILER_IDX_ID_LEN];
    }
    if (!state.hasEncrypt && out[TRAILER_IDX_HAS_ENCRYPT] != 0) {
      state.hasEncrypt = true;
    }
  }

  private static void validateAndFinalizeTrailer(
      MemorySegment pdf, long[] out, TrailerState state, long prevXrefOffset) throws IOException {
    if (state.hasEncrypt) {
      throw new PdfiumException(
          "Incremental metadata save is not supported on encrypted documents;");
    }
    if (state.size == 0 && state.hasSizeEntry) {
      throw new IOException("Trailer /Size is zero or invalid");
    }
    if (state.size == 0) {
      state.size = findMaxObjectNumber(pdf) + 1;
    }
    if (state.size <= 0) {
      throw new IOException("Failed to determine next PDF object number");
    }
    if (state.rootRef == NULL_REF) {
      throw new IOException("Failed to find PDF Root (Catalog) reference");
    }

    out[TRAILER_IDX_ROOT] = state.rootRef;
    out[TRAILER_IDX_INFO] = state.infoRef;
    out[TRAILER_IDX_SIZE] = state.size;
    out[TRAILER_IDX_ID_OFF] = state.idOffset;
    out[TRAILER_IDX_ID_LEN] = state.idLen;
    out[TRAILER_IDX_ENCRYPT] = NULL_REF;

    long[] rootRange = RANGE_BUF.get();
    boolean foundRoot = false;
    try {
      foundRoot = resolveObjectDictionaryRange(pdf, state.rootRef, prevXrefOffset, rootRange);
    } catch (IOException e) {
      PdfiumLibrary.ignore(e);
    }
    if (foundRoot && !isCatalogDictionary(pdf, rootRange[0], rootRange[1])) {
      throw new IOException("Trailer Root does not reference a Catalog object");
    }
  }

  private static void parseTrailerDictionary(
      MemorySegment tail, long dictStart, long dictEndExclusive, long[] out) {
    long rootPos = findTopLevelKey(tail, dictStart, dictEndExclusive, ROOT_KEY);
    out[TRAILER_IDX_ROOT] =
        rootPos >= 0 ? parseObjectRef(tail, rootPos + ROOT_KEY.length, dictEndExclusive) : NULL_REF;

    long infoPos = findTopLevelKey(tail, dictStart, dictEndExclusive, INFO_KEY);
    out[TRAILER_IDX_INFO] =
        infoPos >= 0 ? parseObjectRef(tail, infoPos + INFO_KEY.length, dictEndExclusive) : NULL_REF;

    long sizePos = findTopLevelKey(tail, dictStart, dictEndExclusive, SIZE_KEY);
    out[TRAILER_IDX_SIZE] =
        sizePos >= 0 ? parseTrailerSize(tail, sizePos + SIZE_KEY.length, dictEndExclusive) : 0;
    out[TRAILER_IDX_HAS_SIZE] = sizePos >= 0 ? 1 : 0;

    long idOffset = -1;
    long idLen = -1;
    long idPos = findTopLevelKey(tail, dictStart, dictEndExclusive, ID_KEY);
    if (idPos >= 0) {
      long valPos = skipAsciiWhitespace(tail, idPos + ID_KEY.length, dictEndExclusive);
      if (valPos < dictEndExclusive && tail.get(JAVA_BYTE, valPos) == '[') {
        long endPos = indexOf(tail, new byte[] {']'}, valPos);
        if (endPos >= 0 && endPos < dictEndExclusive) {
          idOffset = valPos;
          idLen = endPos + 1 - valPos;
        }
      }
    }
    out[TRAILER_IDX_ID_OFF] = idOffset;
    out[TRAILER_IDX_ID_LEN] = idLen;

    out[TRAILER_IDX_HAS_ENCRYPT] =
        findTopLevelKey(tail, dictStart, dictEndExclusive, ENCRYPT_KEY) >= 0 ? 1 : 0;
  }

  private static void parseXrefStreamFields(MemorySegment pdf, long xrefOffset, long[] out)
      throws IOException {
    parseXrefStreamSection(pdf, xrefOffset, out);
  }

  private static boolean isXrefStreamDictionary(
      MemorySegment seg, long dictStart, long dictEndExclusive) {
    long typePos = findTopLevelKey(seg, dictStart, dictEndExclusive, TYPE_KEY);
    if (typePos < 0) return false;
    long typeValPos = skipAsciiWhitespace(seg, typePos + TYPE_KEY.length, dictEndExclusive);
    return matchesNameTokenAt(seg, typeValPos, XREF_TYPE_NAME, dictEndExclusive);
  }

  private static boolean isCatalogDictionary(MemorySegment seg, long start, long end) {
    if (dictionaryHasTopLevelNameValue(seg, start, end, CATALOG_TYPE_NAME)) return true;
    // Lenient: Has /Pages but NOT /Type /Pages
    return findTopLevelKey(seg, start, end, PAGES_KEY) >= 0
        && !dictionaryHasTopLevelNameValue(seg, start, end, PAGES_TYPE_NAME);
  }

  private static boolean isPagesDictionary(MemorySegment seg, long start, long end) {
    if (dictionaryHasTopLevelNameValue(seg, start, end, PAGES_TYPE_NAME)) return true;
    // Lenient: Has /Kids and /Count
    return findTopLevelKey(seg, start, end, new byte[] {'/', 'K', 'i', 'd', 's'}) >= 0
        && findTopLevelKey(seg, start, end, new byte[] {'/', 'C', 'o', 'u', 'n', 't'}) >= 0;
  }

  @CheckForNull
  private static byte[] resolveObjectDictionaryBytes(MemorySegment pdf, long ref, long xrefOffset)
      throws IOException {
    long[] range = RANGE_BUF.get();
    if (!resolveObjectDictionaryRange(pdf, ref, xrefOffset, range)) {
      return null;
    }
    // If it's a range on the original PDF, we still have to allocate a byte[] for legacy
    // modification code.
    // However, this is only used in non-repair save paths.
    return pdf.asSlice(range[0], range[1] - range[0]).toArray(JAVA_BYTE);
  }

  private static boolean resolveObjectDictionaryRange(
      MemorySegment pdf, long ref, long xrefOffset, long[] rangeOut) throws IOException {
    int targetNum = unpackNum(ref);
    int targetGen = unpackGen(ref);
    if (findObjectDictionaryRange(pdf, targetNum, targetGen, rangeOut)) {
      return true;
    }
    if (xrefOffset <= 0) {
      return false;
    }
    return resolveObjectDictionaryRangeFromXref(pdf, ref, xrefOffset, rangeOut, 0);
  }

  private static boolean resolveObjectDictionaryRangeFromXref(
      MemorySegment pdf, long ref, long xrefOffset, long[] rangeOut, int depth) throws IOException {
    if (xrefOffset <= 0 || depth > 10) { // Safety limit instead of Set
      return false;
    }

    long[] trailer = acquireTrailerData();
    try {
      byte[] data;
      try {
        data = parseXrefStreamSection(pdf, xrefOffset, trailer);
      } catch (IOException _) {
        long prevClassicOffset = parseClassicXrefPrevOffset(pdf, xrefOffset);
        return prevClassicOffset > 0
            && resolveObjectDictionaryRangeFromXref(
                pdf, ref, prevClassicOffset, rangeOut, depth + 1);
      }

      int dataLen = (int) trailer[TRAILER_IDX_TEMP_LEN];
      long[] entry = XREF_ENTRY_BUF_TL.get();
      if (findXrefEntry(data, dataLen, unpackNum(ref), entry)) {
        int type = (int) entry[0];
        if (type == 1) {
          return findObjectDictionaryRange(pdf, unpackNum(ref), (int) entry[2], rangeOut);
        } else if (type == 2) {
          return false; // Object streams not handled here
        }
      }

      long prevOffset = trailer[TRAILER_IDX_PREV];
      if (prevOffset > 0) {
        return resolveObjectDictionaryRangeFromXref(pdf, ref, prevOffset, rangeOut, depth + 1);
      }
    } finally {
      releaseTrailerData();
    }
    return false;
  }

  private static long parseClassicXrefPrevOffset(MemorySegment pdf, long xrefOffset) {
    long resolvedOffset = locateClassicXrefOffset(pdf, xrefOffset);
    if (resolvedOffset < 0) {
      return 0;
    }
    long limit = pdf.byteSize();
    long start = skipAsciiWhitespace(pdf, resolvedOffset, limit);
    if (!matchesBytesAt(pdf, start, XREF_KEYWORD)) {
      return 0;
    }
    long trailerIdx = indexOf(pdf, TRAILER_KEYWORD, start + XREF_KEYWORD.length);
    if (trailerIdx < 0) {
      return 0;
    }
    long dictStart = indexOf(pdf, DICT_START, trailerIdx + TRAILER_KEYWORD.length);
    if (dictStart < 0) {
      return 0;
    }
    long dictEnd = findDictionaryEnd(pdf, dictStart);
    if (dictEnd <= dictStart) {
      return 0;
    }
    long prevPos = findTopLevelKey(pdf, dictStart, dictEnd, PREV_KEY);
    if (prevPos >= 0) {
      long numStart = skipAsciiWhitespace(pdf, prevPos + PREV_KEY.length, dictEnd);
      long numEnd = scanDigits(pdf, numStart, dictEnd);
      return numEnd > numStart ? parsePositiveLong(pdf, numStart, numEnd) : 0;
    }
    return 0;
  }

  private static long locateClassicXrefOffset(MemorySegment pdf, long hintedOffset) {
    long limit = pdf.byteSize();
    long exact = skipAsciiWhitespace(pdf, hintedOffset, limit);
    if (matchesBytesAt(pdf, exact, XREF_KEYWORD) && isAtLineBoundary(pdf, exact)) {
      return exact;
    }

    long windowStart = Math.max(0, hintedOffset - XREF_OFFSET_FUZZ_BYTES);
    long windowEnd = Math.min(limit, hintedOffset + XREF_OFFSET_FUZZ_BYTES);
    long best = -1;
    long bestDistance = Long.MAX_VALUE;
    long pos = indexOf(pdf, XREF_KEYWORD, windowStart);
    while (pos >= 0 && pos < windowEnd) {
      if (isAtLineBoundary(pdf, pos)) {
        long distance = Math.abs(pos - hintedOffset);
        if (distance < bestDistance) {
          best = pos;
          bestDistance = distance;
        }
      }
      pos = indexOf(pdf, XREF_KEYWORD, pos + 1);
    }
    return best;
  }

  private static byte[] parseXrefStreamSection(MemorySegment pdf, long xrefOffset, long[] out)
      throws IOException {
    long limit = pdf.byteSize();
    long numEnd = scanDigits(pdf, skipAsciiWhitespace(pdf, xrefOffset, limit), limit);
    long genStart = skipAsciiWhitespace(pdf, numEnd, limit);
    long genEnd = scanDigits(pdf, genStart, limit);
    long objKeywordPos = skipAsciiWhitespace(pdf, genEnd, limit);
    if (numEnd <= skipAsciiWhitespace(pdf, xrefOffset, limit) || genEnd <= genStart) {
      throw new IOException("startxref does not point to an indirect object");
    }
    if (!matchesNameTokenAt(pdf, objKeywordPos, OBJ_KEYWORD, limit)) {
      throw new IOException("startxref indirect object is missing obj keyword");
    }

    long dictStart = indexOf(pdf, DICT_START, objKeywordPos + OBJ_KEYWORD.length);
    if (dictStart < 0) {
      throw new IOException("Failed to locate xref stream dictionary");
    }
    long dictEnd = findDictionaryEnd(pdf, dictStart);
    if (dictEnd <= dictStart || !isXrefStreamDictionary(pdf, dictStart, dictEnd)) {
      throw new IOException("startxref does not reference an XRef stream dictionary");
    }

    parseTrailerDictionary(pdf, dictStart, dictEnd, out);
    if (out[TRAILER_IDX_SIZE] <= 0) {
      throw new IOException("XRef stream dictionary is missing a valid /Size");
    }

    int[] widths = XREF_W_BUF.get();
    scanWArray(pdf, dictStart, dictEnd, widths);

    int[] indexPairs = XREF_INDEX_BUF.get();
    Arrays.fill(indexPairs, -1);
    if (!scanIndexArray(pdf, dictStart, dictEnd, indexPairs)) {
      // Default index [0 Size]
      indexPairs[0] = 0;
      indexPairs[1] = (int) out[TRAILER_IDX_SIZE];
      indexPairs[2] = -1;
    }

    long prevOffset = scanIntAfterKey(pdf, dictStart, dictEnd, PREV_KEY);
    out[TRAILER_IDX_PREV] = prevOffset;

    ReusableByteArrayOutputStream decodeTarget = STREAM_DECODE_TARGET.get();
    decodeTarget.reset();
    decodeDirectStreamObject(pdf, dictStart, dictEnd, decodeTarget);
    out[TRAILER_IDX_TEMP_LEN] = decodeTarget.size();
    return decodeTarget.internalBuffer();
  }

  private static boolean findXrefEntry(byte[] data, int dataLen, int objNum, long[] out)
      throws IOException {
    int[] widths = XREF_W_BUF.get();
    int entryWidth = widths[0] + widths[1] + widths[2];
    if (entryWidth <= 0) {
      throw new IOException("XRef stream has invalid /W entry widths");
    }

    int pos = 0;
    int[] indexPairs = XREF_INDEX_BUF.get();
    for (int i = 0; i < indexPairs.length && indexPairs[i] != -1; i += 2) {
      int firstObj = indexPairs[i];
      int count = indexPairs[i + 1];
      for (int delta = 0; delta < count; delta++) {
        if (pos + entryWidth > dataLen) {
          throw new IOException("XRef stream data is shorter than declared /Index coverage");
        }
        int currentObj = firstObj + delta;
        if (currentObj == objNum) {
          out[0] = widths[0] == 0 ? 1 : readUnsigned(data, pos, widths[0]);
          out[1] = readUnsigned(data, pos + widths[0], widths[1]);
          out[2] = readUnsigned(data, pos + widths[0] + widths[1], widths[2]);
          return true;
        }
        pos += entryWidth;
      }
    }
    return false;
  }

  private static void decodeDirectStreamObject(
      MemorySegment pdf, long dictStart, long dictEnd, ReusableByteArrayOutputStream out)
      throws IOException {
    long streamStart = findStreamDataStart(pdf, dictEnd);
    if (streamStart < 0) {
      throw new IOException("Failed to locate stream payload after dictionary");
    }

    long rawLength = resolveIndirectLength(pdf, dictStart, dictEnd);
    if (rawLength <= 0) {
      rawLength = scanIntAfterKey(pdf, dictStart, dictEnd, LENGTH_KEY);
    }
    if (rawLength <= 0 || streamStart + rawLength > pdf.byteSize()) {
      long endstream = indexOf(pdf, ENDSTREAM_KEYWORD, streamStart);
      if (endstream < 0) {
        throw new IOException("Failed to determine stream length");
      }
      rawLength = endstream - streamStart;
    }

    byte[] chunk = STREAM_DECODE_BUF.get();
    long pos = streamStart;
    long remaining = rawLength;

    // We need a temporary buffer for the raw data to pass to Inflater if we don't refactor Inflater
    // But Inflater can take a byte[] slice.
    // For now, we'll copy to the ReusableByteArrayOutputStream and then use its buffer.
    out.reset();
    while (remaining > 0) {
      int toCopy = (int) Math.min(chunk.length, remaining);
      MemorySegment.copy(pdf, JAVA_BYTE, pos, chunk, 0, toCopy);
      out.write(chunk, 0, toCopy);
      pos += toCopy;
      remaining -= toCopy;
    }

    byte[] raw = out.internalBuffer();
    int rawLen = out.size();

    applyFilters(pdf, dictStart, dictEnd, raw, rawLen, out);
    applyPredictor(pdf, dictStart, dictEnd, out);
  }

  /**
   * Applies stream filters by scanning /Filter from the dictionary MemorySegment directly.
   * Zero-allocation: no String/Matcher created.
   */
  private static void applyFilters(
      MemorySegment seg,
      long dictStart,
      long dictEnd,
      byte[] data,
      int dataLen,
      ReusableByteArrayOutputStream out)
      throws IOException {
    long filterPos = findTopLevelKey(seg, dictStart, dictEnd, FILTER_KEY);
    if (filterPos < 0) return;

    long valPos = skipAsciiWhitespace(seg, filterPos + FILTER_KEY.length, dictEnd);
    if (valPos >= dictEnd) return;

    byte b = seg.get(JAVA_BYTE, valPos);
    if (b == '/') {
      // Single filter: /Filter /FlateDecode
      inflateSingleFilter(seg, valPos, dictEnd, data, dataLen, out);
    } else if (b == '[') {
      // Array: /Filter [/FlateDecode]
      applyFilterArray(seg, valPos, dictEnd, out);
    }
  }

  private static void applyFilterArray(
      MemorySegment seg, long valPos, long dictEnd, ReusableByteArrayOutputStream out)
      throws IOException {
    long arrayEnd = indexOf(seg, new byte[] {']'}, valPos);
    if (arrayEnd < 0) arrayEnd = dictEnd;
    long scanPos = valPos + 1;
    while (scanPos < arrayEnd) {
      scanPos = skipAsciiWhitespace(seg, scanPos, arrayEnd);
      if (scanPos >= arrayEnd) break;
      if (seg.get(JAVA_BYTE, scanPos) == '/') {
        inflateSingleFilter(seg, scanPos, arrayEnd, out.internalBuffer(), out.size(), out);
        // advance past the name token
        do scanPos++;
        while (scanPos < arrayEnd && !isPdfNameDelimiter(seg, scanPos));
      } else {
        scanPos++;
      }
    }
  }

  private static void inflateSingleFilter(
      MemorySegment seg,
      long namePos,
      long limit,
      byte[] data,
      int dataLen,
      ReusableByteArrayOutputStream out)
      throws IOException {
    // namePos points to '/'; check if the name is FlateDecode or Fl
    if (matchesNameTokenAt(
            seg,
            namePos,
            new byte[] {'/', 'F', 'l', 'a', 't', 'e', 'D', 'e', 'c', 'o', 'd', 'e'},
            limit)
        || matchesNameTokenAt(seg, namePos, new byte[] {'/', 'F', 'l'}, limit)) {
      inflate(data, dataLen, out);
      return;
    }
    // Extract filter name for error message
    long end = namePos + 1;
    while (end < limit && !isPdfNameDelimiter(seg, end)) end++;
    byte[] nameBytes = seg.asSlice(namePos, end - namePos).toArray(JAVA_BYTE);
    throw new IOException(
        "Unsupported stream filter: " + new String(nameBytes, StandardCharsets.ISO_8859_1));
  }

  private static void applyPredictor(
      MemorySegment seg, long dictStart, long dictEnd, ReusableByteArrayOutputStream out)
      throws IOException {
    long predictor = scanIntAfterKey(seg, dictStart, dictEnd, PREDICTOR_KEY);
    if (predictor <= 1) {
      return;
    }
    if (predictor == 2) {
      throw new IOException("TIFF predictor is not supported for PDF stream decoding");
    }
    if (predictor < 10 || predictor > 15) {
      throw new IOException("Unsupported predictor value: " + predictor);
    }
    long columns = scanIntAfterKey(seg, dictStart, dictEnd, COLUMNS_KEY);
    if (columns <= 0) {
      throw new IOException("PNG predictor requires a valid /Columns entry");
    }
    undoPngPredictor(out, (int) columns);
  }

  private static long findStreamDataStart(MemorySegment pdf, long dictEndExclusive) {
    long pos = skipAsciiWhitespace(pdf, dictEndExclusive, pdf.byteSize());
    if (!matchesBytesAt(pdf, pos, STREAM_KEYWORD)) {
      return -1;
    }
    long dataStart = pos + STREAM_KEYWORD.length;
    if (dataStart < pdf.byteSize() && pdf.get(JAVA_BYTE, dataStart) == '\r') {
      dataStart++;
      if (dataStart < pdf.byteSize() && pdf.get(JAVA_BYTE, dataStart) == '\n') {
        dataStart++;
      }
    } else if (dataStart < pdf.byteSize() && pdf.get(JAVA_BYTE, dataStart) == '\n') {
      dataStart++;
    }
    return dataStart;
  }

  private static boolean matchesBytesAt(MemorySegment seg, long offset, byte[] token) {
    if (offset < 0 || offset + token.length > seg.byteSize()) {
      return false;
    }
    for (int i = 0; i < token.length; i++) {
      if (seg.get(JAVA_BYTE, offset + i) != token[i]) {
        return false;
      }
    }
    return true;
  }

  private static void undoPngPredictor(ReusableByteArrayOutputStream out, int columns)
      throws IOException {
    byte[] data = out.internalBuffer();
    int dataLen = out.size();
    int rowSpan = columns + 1;
    if (rowSpan <= 1 || (dataLen % rowSpan) != 0) {
      throw new IOException("PNG predictor stream length does not align to row size");
    }

    byte[] result = PREDICTOR_BUF.get();
    int expectedLen = (dataLen / rowSpan) * columns;
    if (result.length < expectedLen) {
      result = new byte[expectedLen];
      PREDICTOR_BUF.set(result);
    }

    for (int row = 0; row < dataLen / rowSpan; row++) {
      int src = row * rowSpan;
      int dst = row * columns;
      int filter = data[src] & 0xFF;
      src++;
      for (int i = 0; i < columns; i++) {
        int left = getRowByte(result, dst + i - 1, i > 0);
        int up = getRowByte(result, dst - columns + i, row > 0);
        int upLeft = getRowByte(result, dst - columns + i - 1, row > 0 && i > 0);

        int predicted = calculatePrediction(filter, left, up, upLeft);
        result[dst + i] = (byte) ((data[src + i] + predicted) & 0xFF);
      }
    }
    out.reset();
    out.write(result, 0, expectedLen);
  }

  private static int calculatePrediction(int filter, int left, int up, int upLeft)
      throws IOException {
    return switch (filter) {
      case 0 -> 0;
      case 1 -> left;
      case 2 -> up;
      case 3 -> (left + up) >>> 1;
      case 4 -> paeth(left, up, upLeft);
      default -> throw new IOException("Unsupported PNG predictor filter: " + filter);
    };
  }

  private static int paeth(int left, int up, int upLeft) {
    int p = left + up - upLeft;
    int leftDist = Math.abs(p - left);
    int upDist = Math.abs(p - up);
    int upLeftDist = Math.abs(p - upLeft);
    if (leftDist <= upDist && leftDist <= upLeftDist) {
      return left;
    }
    if (upDist <= upLeftDist) {
      return up;
    }
    return upLeft;
  }

  private static int getRowByte(byte[] data, int index, boolean condition) {
    return condition ? data[index] & 0xFF : 0;
  }

  /**
   * Scans a dictionary MemorySegment for a key and returns the integer value after it.
   * Zero-allocation replacement for parseOptionalLong(String, Pattern). Returns -1 if the key is
   * not found or the value is not a valid integer.
   */
  private static long scanIntAfterKey(MemorySegment seg, long dictStart, long dictEnd, byte[] key) {
    long keyPos = findTopLevelKey(seg, dictStart, dictEnd, key);
    if (keyPos < 0) return -1;
    long valStart = skipAsciiWhitespace(seg, keyPos + key.length, dictEnd);
    long valEnd = scanDigits(seg, valStart, dictEnd);
    if (valEnd <= valStart) return -1;
    return parsePositiveLong(seg, valStart, valEnd);
  }

  /**
   * Scans /W array from a dictionary MemorySegment. Zero-allocation replacement for
   * parseRequiredTriple(String, W_ARRAY_PATTERN, "/W").
   */
  private static void scanWArray(MemorySegment seg, long dictStart, long dictEnd, int[] out)
      throws IOException {
    long wPos = findTopLevelKey(seg, dictStart, dictEnd, W_KEY);
    if (wPos < 0) throw new IOException("Missing required /W entry");
    long pos = skipAsciiWhitespace(seg, wPos + W_KEY.length, dictEnd);
    if (pos >= dictEnd || seg.get(JAVA_BYTE, pos) != '[') {
      throw new IOException("Missing required /W entry");
    }
    pos++; // skip '['
    for (int i = 0; i < 3; i++) {
      pos = skipAsciiWhitespace(seg, pos, dictEnd);
      long numEnd = scanDigits(seg, pos, dictEnd);
      if (numEnd <= pos) throw new IOException("Invalid /W array");
      out[i] = parsePositiveInt(seg, pos, numEnd);
      pos = numEnd;
    }
  }

  /**
   * Scans /Index array from a dictionary MemorySegment. Zero-allocation replacement for
   * parseRequiredTriple(String, INDEX_ARRAY_PATTERN, "/Index").
   */
  private static boolean scanIndexArray(
      MemorySegment seg, long dictStart, long dictEnd, int[] out) {
    long indexPos = findTopLevelKey(seg, dictStart, dictEnd, INDEX_KEY);
    if (indexPos < 0) return false;
    long pos = skipAsciiWhitespace(seg, indexPos + INDEX_KEY.length, dictEnd);
    if (pos >= dictEnd || seg.get(JAVA_BYTE, pos) != '[') return false;
    pos++; // skip '['
    int count = 0;
    while (count < out.length - 1) {
      pos = skipAsciiWhitespace(seg, pos, dictEnd);
      if (pos >= dictEnd || seg.get(JAVA_BYTE, pos) == ']') {
        break;
      }
      long numEnd = scanDigits(seg, pos, dictEnd);
      if (numEnd > pos) {
        out[count++] = parsePositiveInt(seg, pos, numEnd);
        pos = numEnd;
      } else {
        pos = dictEnd; // Force next iteration to break
      }
    }
    if (count < out.length) out[count] = -1;
    return count > 0;
  }

  /**
   * Resolves an indirect /Length reference from a dictionary MemorySegment. Zero-allocation
   * replacement for resolveIndirectLength(MemorySegment, String).
   */
  private static long resolveIndirectLength(MemorySegment pdf, long dictStart, long dictEnd) {
    long lengthPos = findTopLevelKey(pdf, dictStart, dictEnd, LENGTH_KEY);
    if (lengthPos < 0) return -1;
    long valStart = skipAsciiWhitespace(pdf, lengthPos + LENGTH_KEY.length, dictEnd);
    long n1End = scanDigits(pdf, valStart, dictEnd);
    if (n1End <= valStart) return -1;

    long n2Start = skipAsciiWhitespace(pdf, n1End, dictEnd);
    long n2End = scanDigits(pdf, n2Start, dictEnd);
    if (n2End <= n2Start) {
      return parsePositiveLong(pdf, valStart, n1End);
    }

    return tryParseIndirectLength(pdf, valStart, n1End, n2Start, n2End, dictEnd);
  }

  private static long tryParseIndirectLength(
      MemorySegment pdf, long n1Start, long n1End, long n2Start, long n2End, long dictEnd) {
    long rPos = skipAsciiWhitespace(pdf, n2End, dictEnd);
    if (rPos < dictEnd && pdf.get(JAVA_BYTE, rPos) == 'R') {
      int objNum = parsePositiveInt(pdf, n1Start, n1End);
      int genNum = parsePositiveInt(pdf, n2Start, n2End);
      if (objNum > 0 && genNum >= 0) {
        return readDirectIntObject(pdf, objNum, genNum);
      }
    }
    // Fall back to direct integer
    return parsePositiveLong(pdf, n1Start, n1End);
  }

  private static long readDirectIntObject(MemorySegment pdf, int objNum, int genNum) {
    long idx = lastIndexOf(pdf, OBJ_KEYWORD, pdf.byteSize());
    while (idx >= 0) {
      long value = tryReadIntObjectAt(pdf, idx, objNum, genNum);
      if (value != -2) return value;
      idx = lastIndexOf(pdf, OBJ_KEYWORD, idx - 1);
    }
    return -1;
  }

  private static long tryReadIntObjectAt(MemorySegment pdf, long idx, int objNum, int genNum) {
    long headerRef = parseObjectHeaderRef(pdf, idx);
    if (headerRef != NULL_REF && unpackNum(headerRef) == objNum && unpackGen(headerRef) == genNum) {
      long start = skipAsciiWhitespace(pdf, idx + OBJ_KEYWORD.length, pdf.byteSize());
      long end = scanDigits(pdf, start, pdf.byteSize());
      if (end > start) {
        return parsePositiveInt(pdf, start, end);
      }
      return -1;
    }
    return -2; // Not found
  }

  private static long parseObjectHeaderRef(MemorySegment seg, long objKeywordPos) {
    long keywordEnd = objKeywordPos + OBJ_KEYWORD.length;
    if (!matchesBytesAt(seg, objKeywordPos, OBJ_KEYWORD)
        || !isObjectHeaderTerminator(seg, keywordEnd)) {
      return NULL_REF;
    }

    long genEnd = skipWhitespaceBackwards(seg, objKeywordPos);
    long genStart = scanDigitsBackwards(seg, genEnd);
    if (genStart == genEnd) {
      return NULL_REF;
    }

    long separatorEnd = skipWhitespaceBackwards(seg, genStart);
    if (separatorEnd == genStart) {
      return NULL_REF;
    }

    long objEnd = separatorEnd;
    long objStart = scanDigitsBackwards(seg, objEnd);
    if (objStart == objEnd || !isAtLineBoundary(seg, objStart)) {
      return NULL_REF;
    }

    int num = parsePositiveInt(seg, objStart, objEnd);
    int gen = parsePositiveInt(seg, genStart, genEnd);
    if (num <= 0 || gen < 0) {
      return NULL_REF;
    }
    return packRef(num, gen);
  }

  private static long skipWhitespaceBackwards(MemorySegment seg, long pos) {
    long p = pos;
    while (p > 0 && isAsciiWhitespace(seg, p - 1)) {
      p--;
    }
    return p;
  }

  private static long scanDigitsBackwards(MemorySegment seg, long pos) {
    long p = pos;
    while (p > 0 && isAsciiDigit(seg, p - 1)) {
      p--;
    }
    return p;
  }

  private static long readUnsigned(byte[] data, int offset, int length) {
    long value = 0;
    for (int i = 0; i < length; i++) {
      value = (value << 8) | (data[offset + i] & 0xFFL);
    }
    return value;
  }

  private static void inflate(byte[] raw, int len, ReusableByteArrayOutputStream out)
      throws IOException {
    byte[] chunk = STREAM_DECODE_BUF.get();
    out.reset();
    Inflater inflater = REUSABLE_INFLATER.get();
    inflater.reset();
    inflater.setInput(raw, 0, len);
    try {
      while (!inflater.finished()) {
        int read = inflater.inflate(chunk);
        if (read > 0) {
          out.write(chunk, 0, read);
        } else if (inflater.needsDictionary()) {
          throw new IOException("Flate stream requires an unsupported preset dictionary");
        } else if (inflater.needsInput()) {
          break;
        } else {
          throw new IOException("Failed to inflate Flate stream");
        }
      }
      if (!inflater.finished()) {
        throw new IOException("Flate stream ended before inflater reached stream end");
      }
    } catch (DataFormatException e) {
      throw new IOException("Failed to inflate Flate stream", e);
    }
  }

  private static boolean dictionaryHasTopLevelNameValue(
      MemorySegment seg, long dictStart, long dictEndExclusive, byte[] value) {
    long keyPos = findTopLevelKey(seg, dictStart, dictEndExclusive, PdfSaver.TYPE_KEY);
    if (keyPos < 0) return false;
    long valPos = skipAsciiWhitespace(seg, keyPos + PdfSaver.TYPE_KEY.length, dictEndExclusive);
    return matchesNameTokenAt(seg, valPos, value, dictEndExclusive);
  }

  private static long findTopLevelKey(
      MemorySegment seg, long dictStart, long dictEndExclusive, byte[] key) {
    long pos = dictStart + DICT_START.length;
    int depth = 1;
    while (pos <= dictEndExclusive - key.length) {
      byte b = seg.get(JAVA_BYTE, pos);
      if (b == '(') {
        pos = findStringEnd(seg, pos, dictEndExclusive);
      } else if (b == '%') {
        pos = skipComment(seg, pos, dictEndExclusive);
      } else if (isDictStart(seg, pos, dictEndExclusive)) {
        depth++;
        pos += 2;
      } else if (isDictEnd(seg, pos, dictEndExclusive)) {
        depth--;
        if (depth == 0) {
          break;
        }
        pos += 2;
      } else if (depth == 1 && b == '/' && matchesNameTokenAt(seg, pos, key, dictEndExclusive)) {
        return pos;
      } else {
        pos++;
      }
    }
    return -1;
  }

  private static boolean isDictStart(MemorySegment seg, long pos, long limit) {
    return pos < limit - 1 && seg.get(JAVA_BYTE, pos) == '<' && seg.get(JAVA_BYTE, pos + 1) == '<';
  }

  private static boolean isDictEnd(MemorySegment seg, long pos, long limit) {
    return pos < limit - 1 && seg.get(JAVA_BYTE, pos) == '>' && seg.get(JAVA_BYTE, pos + 1) == '>';
  }

  private static long findStringEnd(MemorySegment seg, long start, long limit) {
    int parenDepth = 1;
    long pos = start + 1;
    while (pos < limit && parenDepth > 0) {
      byte b = seg.get(JAVA_BYTE, pos);
      if (b == '\\') {
        pos += 2;
        continue;
      }
      if (b == '(') parenDepth++;
      else if (b == ')') parenDepth--;
      pos++;
    }
    return pos;
  }

  private static long skipComment(MemorySegment seg, long start, long limit) {
    long pos = start + 1;
    while (pos < limit) {
      byte b = seg.get(JAVA_BYTE, pos);
      if (b == '\r' || b == '\n') break;
      pos++;
    }
    return pos;
  }

  private static long parseObjectRef(MemorySegment seg, long from, long endExclusive) {
    long pos = skipAsciiWhitespace(seg, from, endExclusive);
    long n1End = scanDigits(seg, pos, endExclusive);
    if (n1End <= pos) return NULL_REF;
    long n2Start = skipAsciiWhitespace(seg, n1End, endExclusive);
    long n2End = scanDigits(seg, n2Start, endExclusive);
    if (n2End <= n2Start) return NULL_REF;
    long rStart = skipAsciiWhitespace(seg, n2End, endExclusive);
    if (rStart >= endExclusive || seg.get(JAVA_BYTE, rStart) != 'R') return NULL_REF;

    int num = parsePositiveInt(seg, pos, n1End);
    int gen = parsePositiveInt(seg, n2Start, n2End);
    return packRef(num, gen);
  }

  private static int parseTrailerSize(MemorySegment seg, long from, long endExclusive) {
    long start = skipAsciiWhitespace(seg, from, endExclusive);
    long end = scanDigits(seg, start, endExclusive);
    if (end <= start) {
      return 0;
    }
    return parsePositiveInt(seg, start, end);
  }

  private static boolean isLikelyPdfDate(String value) {
    if (value == null || !value.startsWith("D:")) return false;

    int pos = 2;
    int digits = 0;
    while (pos < value.length() && digits < 14) {
      char c = value.charAt(pos);
      if (c < '0' || c > '9') break;
      digits++;
      pos++;
    }

    if (!isValidDateDigitCount(digits)) return false;
    if (pos == value.length()) return true;

    return isValidPdfTimezone(value, pos);
  }

  private static boolean isValidDateDigitCount(int digits) {
    return digits >= 4 && (digits & 1) == 0 && digits <= 14;
  }

  private static boolean isValidPdfTimezone(String value, int pos) {
    char tz = value.charAt(pos);
    if (tz == 'Z') return pos + 1 == value.length();
    if (tz != '+' && tz != '-') return false;

    pos++;
    int tzHourStart = pos;
    while (pos < value.length() && pos - tzHourStart < 2) {
      char c = value.charAt(pos);
      if (c < '0' || c > '9') break;
      pos++;
    }
    if (pos - tzHourStart != 2) return false;

    if (pos == value.length()) return true;
    if (value.charAt(pos) != '\'') return false;

    return isValidPdfTimezoneMinutes(value, pos + 1);
  }

  private static boolean isValidPdfTimezoneMinutes(String value, int pos) {
    int tzMinStart = pos;
    while (pos < value.length() && pos - tzMinStart < 2) {
      char c = value.charAt(pos);
      if (c < '0' || c > '9') break;
      pos++;
    }
    return (pos - tzMinStart == 2);
  }

  private static long findLastStartxrefValue(MemorySegment tail) {
    long idx = lastIndexOf(tail, STARTXREF_KEYWORD, tail.byteSize());
    if (idx < 0) {
      return 0;
    }
    long pos = skipAsciiWhitespace(tail, idx + STARTXREF_KEYWORD.length, tail.byteSize());
    long end = scanDigits(tail, pos, tail.byteSize());
    if (end <= pos) {
      return 0;
    }

    long value = 0;
    for (long i = pos; i < end; i++) {
      byte b = tail.get(JAVA_BYTE, i);
      value = (value * 10) + (b - '0');
      if (value < 0) {
        return 0;
      }
    }
    return value;
  }

  private static long findDictionaryEnd(MemorySegment seg, long dictStart) {
    int depth = 0;
    int parenDepth = 0;
    long pos = dictStart;
    long limit = seg.byteSize() - 1;
    while (pos < limit) {
      byte b = seg.get(JAVA_BYTE, pos);
      if (parenDepth > 0) {
        pos = scanInsideLiteralString(seg, pos, limit, parenDepth);
        parenDepth = 0; // reset for the next loop iteration (the helper handles the full string)
        continue;
      }
      if (b == '(') {
        parenDepth++;
      } else if (b == '<' && seg.get(JAVA_BYTE, pos + 1) == '<') {
        depth++;
        pos++;
      } else if (b == '>' && seg.get(JAVA_BYTE, pos + 1) == '>') {
        depth--;
        pos++;
        if (depth == 0) return pos + 1;
      }
      pos++;
    }
    return -1;
  }

  private static long scanInsideLiteralString(
      MemorySegment seg, long pos, long limit, int initialParenDepth) {
    int parenDepth = initialParenDepth;
    while (pos < limit && parenDepth > 0) {
      byte b = seg.get(JAVA_BYTE, pos);
      if (b == '\\') {
        pos += 2;
        continue;
      }
      if (b == '(') parenDepth++;
      else if (b == ')') parenDepth--;
      pos++;
    }
    return pos;
  }

  private static boolean matchesNameTokenAt(
      MemorySegment seg, long offset, byte[] token, long endExclusive) {
    long tokenEnd = offset + token.length;
    if (tokenEnd > endExclusive) {
      return false;
    }
    for (int i = 0; i < token.length; i++) {
      if (seg.get(JAVA_BYTE, offset + i) != token[i]) {
        return false;
      }
    }
    // The token must end at the boundary OR be followed by a PDF name-delimiter character.
    // PDF names end at whitespace OR any of: ( ) < > [ ] { } / %
    return tokenEnd >= endExclusive || isPdfNameDelimiter(seg, tokenEnd);
  }

  /** Returns true if the byte at {@code idx} cannot appear inside an unescaped PDF name token. */
  private static boolean isPdfNameDelimiter(MemorySegment seg, long idx) {
    byte b = seg.get(JAVA_BYTE, idx);
    return b == 0x00 || b == '\t' || b == '\n' || b == 0x0C || b == '\r' || b == ' ' || b == '('
        || b == ')' || b == '<' || b == '>' || b == '[' || b == ']' || b == '{' || b == '}'
        || b == '/' || b == '%';
  }

  private static long skipAsciiWhitespace(MemorySegment seg, long from, long endExclusive) {
    long i = Math.max(0, from);
    while (i < endExclusive && isAsciiWhitespace(seg, i)) {
      i++;
    }
    return i;
  }

  private static long scanDigits(MemorySegment seg, long from, long endExclusive) {
    long i = Math.max(0, from);
    while (i < endExclusive && isAsciiDigit(seg, i)) {
      i++;
    }
    return i;
  }

  private static boolean isAtLineBoundary(MemorySegment seg, long start) {
    long pos = start;
    while (pos > 0) {
      byte b = seg.get(JAVA_BYTE, pos - 1);
      if (b == ' ' || b == '\t') {
        pos--;
        continue;
      }
      return b == '\n' || b == '\r';
    }
    return true;
  }

  private static boolean isObjectHeaderTerminator(MemorySegment seg, long pos) {
    return pos >= seg.byteSize() || isPdfNameDelimiter(seg, pos);
  }

  private static int findMaxObjectNumber(MemorySegment pdf) {
    byte[] marker = OBJ_KEYWORD;
    int max = 0;
    long searchPos = 0;
    while (true) {
      long pos = indexOf(pdf, marker, searchPos);
      if (pos < 0) {
        break;
      }
      searchPos = pos + marker.length;
      long ref = parseObjectHeaderRef(pdf, pos);
      if (ref == NULL_REF) {
        continue;
      }
      int num = unpackNum(ref);
      if (num > max) {
        max = num;
      }
    }
    return max;
  }

  private static boolean hasXmpUpdate(@CheckForNull XmpUpdate xmp) {
    if (xmp == null) return false;
    return switch (xmp) {
      case XmpUpdate.Raw(String xmpStr) -> xmpStr != null && !xmpStr.isBlank();
      case XmpUpdate.Structured _ -> true;
    };
  }

  private static boolean findObjectDictionaryRange(
      MemorySegment pdf, int objNum, int genNum, long[] rangeOut) {
    byte[] marker = OBJ_KEYWORD;
    long searchFrom = pdf.byteSize();
    long idx;
    while (true) {
      idx = lastIndexOf(pdf, marker, searchFrom);
      if (idx < 0) return false;
      long headerRef = parseObjectHeaderRef(pdf, idx);
      if (headerRef == NULL_REF
          || unpackNum(headerRef) != objNum
          || unpackGen(headerRef) != genNum) {
        searchFrom = idx - 1;
        continue;
      }
      break;
    }

    long dictStart = indexOf(pdf, DICT_START, idx + marker.length);
    if (dictStart < 0) return false;
    long dictEnd = findDictionaryEnd(pdf, dictStart);
    if (dictEnd <= dictStart) {
      return false;
    }
    rangeOut[0] = dictStart;
    rangeOut[1] = dictEnd;
    return true;
  }

  private static int parsePositiveInt(MemorySegment seg, long start, long endExclusive) {
    if (start < 0 || endExclusive <= start || endExclusive > seg.byteSize()) {
      return -1;
    }
    long value = 0;
    for (long i = start; i < endExclusive; i++) {
      byte b = seg.get(JAVA_BYTE, i);
      if (b < '0' || b > '9') {
        return -1;
      }
      value = (value * 10) + (b - '0');
      if (value > Integer.MAX_VALUE) {
        return -1;
      }
    }
    return (int) value;
  }

  private static long parsePositiveLong(MemorySegment seg, long start, long endExclusive) {
    if (start < 0 || endExclusive <= start || endExclusive > seg.byteSize()) {
      return -1;
    }
    long value = 0;
    for (long i = start; i < endExclusive; i++) {
      byte b = seg.get(JAVA_BYTE, i);
      if (b < '0' || b > '9') {
        return -1;
      }
      value = (value * 10) + (b - '0');
      if (value > MAX_XREF_OFFSET) {
        return -1;
      }
    }
    return value;
  }

  private static boolean isAsciiDigit(MemorySegment seg, long idx) {
    byte b = seg.get(JAVA_BYTE, idx);
    return b >= '0' && b <= '9';
  }

  private static boolean isAsciiWhitespace(MemorySegment seg, long idx) {
    byte b = seg.get(JAVA_BYTE, idx);
    // PDF spec Table 1: NUL, HT, LF, FF, CR, SP are whitespace.
    return b == 0x00 || b == '\t' || b == '\n' || b == 0x0C || b == '\r' || b == ' ';
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

  private static void writeCurrentPdfDate(OutputStream out) throws IOException {
    out.write('(');
    org.grimmory.pdfium4j.util.PdfDateUtils.writeCurrentPdfDate(out);
    out.write(')');
  }

  private static void writeSegment(MemorySegment seg, OutputStream out) throws IOException {
    long size = seg.byteSize();
    long pos = 0;
    byte[] buf = IO_BUFFER.get();
    MemorySegment bufSeg = IO_BUFFER_SEGMENT.get();
    while (pos < size) {
      int len = (int) Math.min(buf.length, size - pos);
      MemorySegment.copy(seg, JAVA_BYTE, pos, bufSeg, JAVA_BYTE, 0, len);
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
