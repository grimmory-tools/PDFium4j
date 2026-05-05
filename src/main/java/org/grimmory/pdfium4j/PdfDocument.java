package org.grimmory.pdfium4j;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.pdfium4j.exception.PdfCorruptException;
import org.grimmory.pdfium4j.exception.PdfPasswordException;
import org.grimmory.pdfium4j.exception.PdfUnsupportedSecurityException;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.internal.DocBindings;
import org.grimmory.pdfium4j.internal.EditBindings;
import org.grimmory.pdfium4j.internal.FfmHelper;
import org.grimmory.pdfium4j.internal.ScratchBuffer;
import org.grimmory.pdfium4j.internal.ViewBindings;
import org.grimmory.pdfium4j.model.Bookmark;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.grimmory.pdfium4j.model.PageSize;
import org.grimmory.pdfium4j.model.PdfDiagnostic;
import org.grimmory.pdfium4j.model.PdfErrorCode;
import org.grimmory.pdfium4j.model.PdfProbeResult;
import org.grimmory.pdfium4j.model.PdfProcessingPolicy;
import org.grimmory.pdfium4j.model.RenderResult;

/**
 * Represents an open PDF document backed by native PDFium.
 *
 * <p><strong>Streaming:</strong> This implementation uses a true streaming approach. Documents can
 * be opened from {@link Path}, {@link SeekableByteChannel}, or {@link InputStream}. Memory usage is
 * minimized by using native callbacks ({@code FPDF_FILEACCESS}) for data retrieval.
 *
 * <p><strong>Thread Safety:</strong> Instances must be accessed only from the thread that opened
 * them.
 */
public final class PdfDocument implements AutoCloseable {

  private static final Map<Long, SeekableByteChannel> CHANNELS = new ConcurrentHashMap<>(16);
  private static final AtomicLong CHANNEL_ID_SEQ = new AtomicLong();
  private static final Cleaner CLEANER = Cleaner.create();
  private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

  private static final MetadataTag[] METADATA_TAGS = MetadataTag.values();

  private static final Pattern STATIC_INFO_PATTERN =
      Pattern.compile("/Info\\s+(\\d+)\\s+(\\d+)\\s+R");

  /**
   * Matches /Key (literal value) – does not handle escaped parens in value, adequate for standard
   * Info dict.
   */
  private static final Pattern INFO_DICT_LITERAL_PATTERN =
      Pattern.compile("/(\\w+)\\s+\\(([^)\\\\]*)\\)");

  /** Matches /Key <hexvalue>. */
  private static final Pattern INFO_DICT_HEX_PATTERN =
      Pattern.compile("/(\\w+)\\s+<([A-Fa-f0-9]*)>");

  // Tail window for fallback file scanning (Info/XMP are typically near trailer/xref).
  private static final long FALLBACK_TAIL_SCAN_BYTES = 256L * 1024L;

  private final MemorySegment handle;
  private final Arena docArena;
  private SeekableByteChannel docSourceChannel;
  private final Path sourcePath;
  private final byte[] sourceBytes;
  private final long channelId;
  private final PdfProcessingPolicy policy;
  private final Thread ownerThread;
  private final List<PdfPage> openPages = new ArrayList<>(8);
  private volatile boolean closed = false;
  private volatile boolean structurallyModified = false;

  /** Cached page count; -1 means not yet fetched or invalidated. */
  private volatile int cachedPageCount = -1;

  /** Lazy-parsed fallback Info dict key→value map (populated at most once per document). */
  private Map<String, String> cachedFallbackMeta;

  /** Memoized XMP bytes from file-system fallback path. */
  private byte[] cachedFallbackXmp;

  private final Map<MetadataTag, String> pendingMetadata = LinkedHashMap.newLinkedHashMap(8);
  private String pendingXmpMetadata = null;
  private final CleanupState state;
  private final Cleaner.Cleanable cleanable;

  private PdfDocument(
      MemorySegment handle,
      Arena docArena,
      SeekableByteChannel sourceChannel,
      long channelId,
      Path sourcePath,
      Path tempFile,
      byte[] sourceBytes,
      PdfProcessingPolicy policy,
      Thread ownerThread) {
    this.handle = handle;
    this.docArena = docArena;
    this.docSourceChannel = sourceChannel;
    this.channelId = channelId;
    this.sourcePath = sourcePath;
    this.sourceBytes = sourceBytes;
    this.policy = policy;
    this.ownerThread = ownerThread;
    this.state = new CleanupState(channelId, sourceChannel, tempFile, docArena);
    this.cleanable = CLEANER.register(this, state);
    PdfiumLibrary.incrementDocumentCount();
  }

  private static final class CleanupState implements Runnable {
    private final long channelId;
    private final AtomicReference<SeekableByteChannel> sourceChannelRef = new AtomicReference<>();
    private final Path tempFile;
    private final Arena docArena;

    private CleanupState(long channelId, SeekableByteChannel chan, Path tempFile, Arena docArena) {
      this.channelId = channelId;
      this.sourceChannelRef.set(chan);
      this.tempFile = tempFile;
      this.docArena = docArena;
    }

    void updateSourceChannel(SeekableByteChannel newChannel) {
      this.sourceChannelRef.set(newChannel);
    }

    @Override
    public void run() {
      try {
        if (channelId > 0) {
          SeekableByteChannel removed = CHANNELS.remove(Long.valueOf(channelId));
          SeekableByteChannel current = sourceChannelRef.get();
          if (removed != null && removed != current) {
            try {
              removed.close();
            } catch (IOException e) {
              PdfiumLibrary.ignore(e);
            }
          }
        }
        SeekableByteChannel chan = sourceChannelRef.getAndSet(null);
        if (chan != null) {
          try {
            chan.close();
          } catch (IOException e) {
            PdfiumLibrary.ignore(e);
          }
        }
        if (tempFile != null) {
          try {
            Files.deleteIfExists(tempFile);
          } catch (IOException e) {
            PdfiumLibrary.ignore(e);
          }
        }
        if (docArena != null) {
          docArena.close();
        }
      } finally {
        PdfiumLibrary.decrementDocumentCount();
      }
    }
  }

  public static PdfDocument open(Path path) {
    return open(path, null, resolvePolicy(null));
  }

  @SuppressWarnings("resource")
  public static PdfDocument open(Path path, String password, PdfProcessingPolicy policy) {
    PdfProcessingPolicy resolvedPolicy = resolvePolicy(policy);
    PdfiumLibrary.ensureInitialized();
    try {
      long size = Files.size(path);
      if (size > resolvedPolicy.maxDocumentBytes()) {
        throw new PdfiumException(
            "Document size ("
                + size
                + " bytes) exceeds policy limit ("
                + resolvedPolicy.maxDocumentBytes()
                + ")",
            null);
      }
      SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ);
      return openFromChannel(channel, path, null, password, path.toString(), resolvedPolicy);
    } catch (IOException e) {
      throw new PdfiumException("Failed to open file: " + path, e);
    }
  }

  public static PdfDocument open(byte[] data) {
    return open(data, null, resolvePolicy(null));
  }

  public static PdfDocument open(byte[] data, String password, PdfProcessingPolicy policy) {
    PdfProcessingPolicy resolvedPolicy = resolvePolicy(policy);
    if (data == null || data.length == 0)
      throw new IllegalArgumentException("data is null or empty");
    if (data.length > resolvedPolicy.maxDocumentBytes()) {
      throw new PdfiumException(
          "Document size ("
              + data.length
              + " bytes) exceeds policy limit ("
              + resolvedPolicy.maxDocumentBytes()
              + ")",
          null);
    }
    PdfiumLibrary.ensureInitialized();
    Arena arena = Arena.ofShared();
    try {
      MemorySegment seg = arena.allocateFrom(JAVA_BYTE, data);
      MemorySegment pwdSeg = (password != null) ? arena.allocateFrom(password) : MemorySegment.NULL;
      MemorySegment doc =
          (MemorySegment) ViewBindings.FPDF_LoadMemDocument.invokeExact(seg, data.length, pwdSeg);
      if (FfmHelper.isNull(doc)) {
        int err = (int) (long) ViewBindings.FPDF_GetLastError.invokeExact();
        arena.close();
        throw mapOpenError("Failed to open document from bytes", err);
      }
      return new PdfDocument(
          doc, arena, null, 0L, null, null, data, resolvedPolicy, Thread.currentThread());
    } catch (PdfiumException e) {
      throw e;
    } catch (Throwable t) {
      arena.close();
      throw new PdfiumException("Failed to open document from bytes", t);
    }
  }

  private static PdfDocument openFromChannel(
      SeekableByteChannel channel,
      Path sourcePath,
      Path tempFile,
      String password,
      String label,
      PdfProcessingPolicy policy) {
    Arena docArena = Arena.ofShared();
    long channelId = 0;
    try {
      channelId = CHANNEL_ID_SEQ.incrementAndGet();
      CHANNELS.put(channelId, channel);

      if (ViewBindings.FPDF_LoadCustomDocument == null) {
        throw new PdfiumException("FPDF_LoadCustomDocument not available");
      }

      MemorySegment fileAccess = docArena.allocate(ViewBindings.FPDF_FILEACCESS_LAYOUT);
      fileAccess.set(JAVA_LONG, 0, channel.size());
      MethodHandle getBlockMH =
          MethodHandles.lookup()
              .findStatic(
                  PdfDocument.class,
                  "getFileBlock",
                  MethodType.methodType(
                      int.class, MemorySegment.class, long.class, MemorySegment.class, long.class));
      MemorySegment getBlockStub =
          Linker.nativeLinker().upcallStub(getBlockMH, ViewBindings.GET_BLOCK_DESC, docArena);
      fileAccess.set(ADDRESS, 8, getBlockStub);
      fileAccess.set(ADDRESS, 16, MemorySegment.ofAddress(channelId));

      MemorySegment pwdSeg =
          (password != null) ? docArena.allocateFrom(password) : MemorySegment.NULL;
      MemorySegment doc =
          (MemorySegment) ViewBindings.FPDF_LoadCustomDocument.invokeExact(fileAccess, pwdSeg);
      if (FfmHelper.isNull(doc)) {
        int err = (int) ViewBindings.FPDF_GetLastError.invokeExact();
        throw mapOpenError("Failed to open document: " + label, err);
      }
      return new PdfDocument(
          doc,
          docArena,
          channel,
          channelId,
          sourcePath,
          tempFile,
          null,
          policy,
          Thread.currentThread());
    } catch (PdfiumException e) {
      if (channelId > 0) CHANNELS.remove(channelId);
      try {
        channel.close();
      } catch (IOException ex) {
        PdfiumLibrary.ignore(ex);
      }
      if (tempFile != null) {
        try {
          Files.deleteIfExists(tempFile);
        } catch (IOException ex) {
          PdfiumLibrary.ignore(ex);
        }
      }
      docArena.close();
      throw e;
    } catch (Throwable t) {
      if (channelId > 0) CHANNELS.remove(channelId);
      try {
        channel.close();
      } catch (IOException ex) {
        PdfiumLibrary.ignore(ex);
      }
      if (tempFile != null) {
        try {
          Files.deleteIfExists(tempFile);
        } catch (IOException ex) {
          PdfiumLibrary.ignore(ex);
        }
      }
      docArena.close();
      throw new PdfiumException("Failed to open channel: " + label, t);
    }
  }

  @SuppressWarnings("PMD.UnusedFormalParameter")
  private static int getFileBlock(MemorySegment param, long pos, MemorySegment buf, long size) {
    if (FfmHelper.isNull(param) || FfmHelper.isNull(buf)) return 0;
    long channelId = param.address();
    SeekableByteChannel channel = CHANNELS.get(channelId);
    if (channel == null || size <= 0 || size > Integer.MAX_VALUE) return 0;
    try {
      ByteBuffer bb = buf.reinterpret(size).asByteBuffer();
      if (channel instanceof FileChannel fc) {
        // Positional read: no position mutation, no synchronization needed.
        long readPos = pos;
        while (bb.hasRemaining()) {
          int read = fc.read(bb, readPos);
          if (read <= 0) return 0; // EOF before filling buffer – signal failure to PDFium
          readPos += read;
        }
      } else {
        synchronized (channel) {
          channel.position(pos);
          while (bb.hasRemaining()) {
            int read = channel.read(bb);
            if (read <= 0) return 0; // EOF before filling buffer
          }
        }
      }
      return 1;
    } catch (IOException _) {
      return 0;
    }
  }

  public int pageCount() {
    ensureOpen();
    if (cachedPageCount >= 0) return cachedPageCount;
    try {
      cachedPageCount = (int) ViewBindings.FPDF_GetPageCount.invokeExact(handle);
      return cachedPageCount;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to get page count", t);
    }
  }

  /** Marks the document as structurally modified and invalidates the cached page count. */
  private void markStructurallyModified() {
    cachedPageCount = -1;
    structurallyModified = true;
  }

  public PdfPage page(int index) {
    ensureOpen();
    if (index < 0 || index >= pageCount())
      throw new IllegalArgumentException("Index out of bounds: " + index);
    try {
      MemorySegment pageHandle =
          (MemorySegment) ViewBindings.FPDF_LoadPage.invokeExact(handle, index);
      if (FfmHelper.isNull(pageHandle)) throwLastError("Failed to load page " + index);
      PdfPage page =
          new PdfPage(
              pageHandle,
              ownerThread,
              policy.maxRenderPixels(),
              this::unregisterPage,
              () -> structurallyModified = true);

      registerPage(page);
      return page;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to load page " + index, t);
    }
  }

  public PageSize pageSize(int index) {
    ensureOpen();
    ScratchBuffer.acquire();
    try {
      MemorySegment scratch = ScratchBuffer.get(16);
      MemorySegment w = scratch.asSlice(0, JAVA_DOUBLE.byteSize());
      MemorySegment h = scratch.asSlice(JAVA_DOUBLE.byteSize(), JAVA_DOUBLE.byteSize());
      int ok = (int) ViewBindings.FPDF_GetPageSizeByIndex.invokeExact(handle, index, w, h);
      if (ok == 0) throwLastError("Failed to get page size " + index);
      return new PageSize((float) w.get(JAVA_DOUBLE, 0), (float) h.get(JAVA_DOUBLE, 0));
    } catch (Throwable t) {
      throw new PdfiumException("Failed to get page size " + index, t);
    } finally {
      ScratchBuffer.release();
    }
  }

  public List<Bookmark> bookmarks() {
    ensureOpen();
    ScratchBuffer.acquire();
    try {
      return BookmarkReader.readBookmarks(handle);
    } finally {
      ScratchBuffer.release();
    }
  }

  public Optional<String> pageLabel(int index) {
    ensureOpen();
    ScratchBuffer.acquire();
    try {
      long needed =
          (long) DocBindings.FPDF_GetPageLabel.invokeExact(handle, index, MemorySegment.NULL, 0L);
      if (needed <= 2) return Optional.empty();
      MemorySegment buf = ScratchBuffer.get(needed);
      long copied = (long) DocBindings.FPDF_GetPageLabel.invokeExact(handle, index, buf, needed);
      long byteLen = FfmHelper.normalizeWideByteLength(buf, copied, needed);
      return byteLen == 0 ? Optional.empty() : Optional.of(FfmHelper.fromWideString(buf, byteLen));
    } catch (Throwable _) {
      return Optional.empty();
    } finally {
      ScratchBuffer.release();
    }
  }

  public List<PageSize> allPageSizes() {
    ensureOpen();
    int count = pageCount();
    if (count <= 0) return List.of();
    List<PageSize> sizes = new ArrayList<>(count);
    ScratchBuffer.acquire();
    try {
      MemorySegment loopScratch = ScratchBuffer.getLoopScratch(2 * JAVA_DOUBLE.byteSize());
      MemorySegment w = loopScratch.asSlice(0, JAVA_DOUBLE.byteSize());
      MemorySegment h = loopScratch.asSlice(JAVA_DOUBLE.byteSize(), JAVA_DOUBLE.byteSize());
      for (int i = 0; i < count; i++) {
        int ok = (int) ViewBindings.FPDF_GetPageSizeByIndex.invokeExact(handle, i, w, h);
        if (ok == 0) throwLastError("Failed to get page size " + i);
        sizes.add(new PageSize((float) w.get(JAVA_DOUBLE, 0), (float) h.get(JAVA_DOUBLE, 0)));
      }
    } catch (PdfiumException e) {
      throw e;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to get page sizes", t);
    } finally {
      ScratchBuffer.release();
    }
    return sizes;
  }

  public int fileVersion() {
    ensureOpen();
    ScratchBuffer.acquire();
    try {
      MemorySegment v = ScratchBuffer.get(JAVA_INT.byteSize());
      int ok = (int) DocBindings.FPDF_GetFileVersion.invokeExact(handle, v);
      return ok != 0 ? v.get(JAVA_INT, 0) : 0;
    } catch (Throwable _) {
      return 0;
    } finally {
      ScratchBuffer.release();
    }
  }

  public boolean isImageOnly() {
    int count = pageCount();
    int sampleCount = Math.min(count, 10);
    for (int i = 0; i < sampleCount; i++) {
      try (PdfPage p = page(i)) {
        if (p.hasText()) return false;
      }
    }
    return true;
  }

  public Optional<String> metadata(MetadataTag tag) {
    ensureOpen();
    if (pendingMetadata.containsKey(tag)) {
      String pending = pendingMetadata.get(tag);
      return (pending == null || pending.isEmpty()) ? Optional.empty() : Optional.of(pending);
    }
    ScratchBuffer.acquire();
    try {
      String key = tag.pdfKey();
      MemorySegment initialScratch = ScratchBuffer.utf8ProbeBuffer(key);
      MemorySegment keySeg = FfmHelper.writeUtf8String(initialScratch, key);

      long needed =
          (long) DocBindings.FPDF_GetMetaText.invokeExact(handle, keySeg, MemorySegment.NULL, 0L);
      if (needed <= 2) return metadataFallback(tag);

      ScratchBuffer.KeyValueSlots keyAndValue =
          resolveMetaBuffer(initialScratch, key, keySeg, needed);
      long copied =
          (long)
              DocBindings.FPDF_GetMetaText.invokeExact(
                  handle, keyAndValue.keySeg(), keyAndValue.valueSeg(), needed);
      long byteLen = FfmHelper.normalizeWideByteLength(keyAndValue.valueSeg(), copied, needed);
      if (byteLen == 0) return metadataFallback(tag);
      String val = FfmHelper.fromWideString(keyAndValue.valueSeg(), byteLen);
      return val.isEmpty() ? Optional.empty() : Optional.of(val);
    } catch (Throwable _) {
      return metadataFallback(tag);
    } finally {
      ScratchBuffer.release();
    }
  }

  private Optional<String> metadataFallback(MetadataTag tag) {
    Map<String, String> cache = getOrBuildFallbackMeta();
    String val = cache.get(tag.pdfKey());
    return (val == null || val.isEmpty()) ? Optional.empty() : Optional.of(val);
  }

  /**
   * Returns (and lazily builds) the per-document Info-dict cache. The file is mapped at most once
   * regardless of how many metadata tags are requested.
   */
  private Map<String, String> getOrBuildFallbackMeta() {
    if (cachedFallbackMeta != null) return cachedFallbackMeta;
    Map<String, String> result;
    if (sourceBytes != null) {
      result = parseAllInfoMetadata(MemorySegment.ofArray(sourceBytes));
    } else if (sourcePath != null) {
      try (FileChannel fc = FileChannel.open(sourcePath, StandardOpenOption.READ);
          Arena arena = Arena.ofConfined()) {
        long fileSize = fc.size();
        if (fileSize <= 0) {
          result = Map.of();
        } else {
          long tailSize = Math.min(fileSize, FALLBACK_TAIL_SCAN_BYTES);
          long tailStart = fileSize - tailSize;
          MemorySegment tail = fc.map(FileChannel.MapMode.READ_ONLY, tailStart, tailSize, arena);
          result = parseAllInfoMetadata(tail);
          // Keep behavior robust: if tail miss occurs, retry with full-file map once.
          if (result.isEmpty() && tailStart > 0) {
            MemorySegment full = fc.map(FileChannel.MapMode.READ_ONLY, 0, fileSize, arena);
            result = parseAllInfoMetadata(full);
          }
        }
      } catch (IOException e) {
        PdfiumLibrary.ignore(e);
        result = Map.of();
      }
    } else {
      result = Map.of();
    }
    cachedFallbackMeta = result;
    return result;
  }

  /**
   * Parses ALL key-value entries from the PDF Info dictionary in one pass. Avoids per-tag
   * re-mapping and repeated Pattern.compile() calls.
   */
  private static Map<String, String> parseAllInfoMetadata(MemorySegment pdf) {
    long size = pdf.byteSize();
    long tailLen = Math.min(size, 4096);
    if (tailLen == 0) return Map.of();
    byte[] tailBytes = pdf.asSlice(size - tailLen, tailLen).toArray(JAVA_BYTE);
    String tail = new String(tailBytes, StandardCharsets.ISO_8859_1);

    Matcher m = STATIC_INFO_PATTERN.matcher(tail);
    int objNum = -1;
    int genNum = 0;
    while (m.find()) {
      objNum = Integer.parseInt(m.group(1));
      genNum = Integer.parseInt(m.group(2));
    }
    if (objNum < 0) return Map.of();

    String dict = extractDictFromSegment(pdf, objNum, genNum);
    if (dict == null) return Map.of();
    return parseInfoDictAllFields(dict);
  }

  /** Parses all /Key (value) and /Key &lt;hex&gt; entries from an Info dictionary string. */
  private static Map<String, String> parseInfoDictAllFields(String dict) {
    Map<String, String> result = LinkedHashMap.newLinkedHashMap(16);
    Matcher m = INFO_DICT_LITERAL_PATTERN.matcher(dict);
    while (m.find()) {
      result.put(m.group(1), m.group(2));
    }
    Matcher hexM = INFO_DICT_HEX_PATTERN.matcher(dict);
    while (hexM.find()) {
      result.computeIfAbsent(hexM.group(1), _ -> decodeHexPdfString(hexM.group(2)));
    }
    return Collections.unmodifiableMap(result);
  }

  @CheckForNull
  private static String extractDictFromSegment(MemorySegment pdf, int objNum, int genNum) {
    byte[] marker = (objNum + " " + genNum + " obj").getBytes(StandardCharsets.ISO_8859_1);
    long pos = lastIndexOf(pdf, marker);
    if (pos < 0) return null;

    byte[] dictStartMarker = "<<".getBytes(StandardCharsets.ISO_8859_1);
    long dictStart = indexOf(pdf, dictStartMarker, pos);
    if (dictStart < 0) return null;

    int depth = 0;
    long curr = dictStart;
    long size = pdf.byteSize();
    while (curr < size - 1) {
      byte b1 = pdf.get(JAVA_BYTE, curr);
      byte b2 = pdf.get(JAVA_BYTE, curr + 1);
      if (b1 == '<' && b2 == '<') {
        depth++;
        curr += 2;
      } else if (b1 == '>' && b2 == '>') {
        depth--;
        if (depth == 0) {
          byte[] dictBytes = pdf.asSlice(dictStart, curr + 2 - dictStart).toArray(JAVA_BYTE);
          return new String(dictBytes, StandardCharsets.ISO_8859_1);
        }
        curr += 2;
      } else {
        curr++;
      }
    }
    return null;
  }

  private static long lastIndexOf(MemorySegment segment, byte[] needle) {
    long size = segment.byteSize();
    outer:
    for (long i = size - needle.length; i >= 0; i--) {
      for (int j = 0; j < needle.length; j++) {
        if (segment.get(JAVA_BYTE, i + j) != needle[j]) continue outer;
      }
      return i;
    }
    return -1;
  }

  private static long indexOf(MemorySegment segment, byte[] needle, long fromIndex) {
    long size = segment.byteSize();
    outer:
    for (long i = fromIndex; i <= size - needle.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (segment.get(JAVA_BYTE, i + j) != needle[j]) continue outer;
      }
      return i;
    }
    return -1;
  }

  private static String decodeHexPdfString(String hex) {
    if (hex.startsWith("FEFF")) {
      // UTF-16BE
      try {
        int len = (hex.length() - 4) / 2;
        if (len <= 0) return "";
        byte[] bytes = new byte[len];
        for (int i = 0; i < bytes.length; i++) {
          bytes[i] = (byte) Integer.parseInt(hex.substring(4 + i * 2, 6 + i * 2), 16);
        }
        return new String(bytes, StandardCharsets.UTF_16BE);
      } catch (Exception _) {
        return hex;
      }
    }
    return hex; // Raw hex fallback
  }

  public Optional<String> metadata(String customKey) {
    // Check known enum-backed tags first
    for (MetadataTag tag : METADATA_TAGS) {
      if (tag.pdfKey().equalsIgnoreCase(customKey)) return metadata(tag);
    }
    // Support arbitrary /Info keys (e.g. /Language) via FPDF_GetMetaText
    ensureOpen();
    Optional<String> val = tryGetMetaText(customKey);
    if (val.isPresent()) {
      return val;
    }
    return findMetadataInFallback(customKey);
  }

  private Optional<String> tryGetMetaText(String customKey) {
    ScratchBuffer.acquire();
    try {
      MemorySegment initialScratch = ScratchBuffer.utf8ProbeBuffer(customKey);
      MemorySegment keySeg = FfmHelper.writeUtf8String(initialScratch, customKey);
      long needed =
          (long) DocBindings.FPDF_GetMetaText.invokeExact(handle, keySeg, MemorySegment.NULL, 0L);
      if (needed <= 2) {
        return Optional.empty();
      }

      ScratchBuffer.KeyValueSlots keyAndValue =
          resolveMetaBuffer(initialScratch, customKey, keySeg, needed);
      long copied =
          (long)
              DocBindings.FPDF_GetMetaText.invokeExact(
                  handle, keyAndValue.keySeg(), keyAndValue.valueSeg(), needed);
      long byteLen = FfmHelper.normalizeWideByteLength(keyAndValue.valueSeg(), copied, needed);
      if (byteLen == 0) {
        return Optional.empty();
      }
      String val = FfmHelper.fromWideString(keyAndValue.valueSeg(), byteLen);
      return val.isEmpty() ? Optional.empty() : Optional.of(val);
    } catch (Throwable _) {
      return Optional.empty();
    } finally {
      ScratchBuffer.release();
    }
  }

  private static ScratchBuffer.KeyValueSlots resolveMetaBuffer(
      MemorySegment initialScratch, String key, MemorySegment keySeg, long needed) {
    if (keySeg.byteSize() + needed <= initialScratch.byteSize()) {
      return ScratchBuffer.keyAndWideValue(
          keySeg, initialScratch.asSlice(keySeg.byteSize(), needed));
    }
    return ScratchBuffer.utf8KeyAndWideValue(key, needed);
  }

  private Optional<String> findMetadataInFallback(String customKey) {
    Map<String, String> fallback = getOrBuildFallbackMeta();
    for (Map.Entry<String, String> entry : fallback.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(customKey)) {
        String v = entry.getValue();
        return (v == null || v.isEmpty()) ? Optional.empty() : Optional.of(v);
      }
    }
    return Optional.empty();
  }

  public Map<String, String> metadata() {
    Map<String, String> meta = LinkedHashMap.newLinkedHashMap(METADATA_TAGS.length);
    for (MetadataTag tag : METADATA_TAGS) metadata(tag).ifPresent(v -> meta.put(tag.name(), v));
    return meta;
  }

  public byte[] xmpMetadata() {
    ensureOpen();
    if (pendingXmpMetadata != null) {
      return pendingXmpMetadata.isEmpty()
          ? EMPTY_BYTE_ARRAY
          : pendingXmpMetadata.getBytes(StandardCharsets.UTF_8);
    }
    try (Arena arena = Arena.ofShared()) {
      if (DocBindings.FPDF_GetXMPMetadata != null) {
        long needed =
            (long) DocBindings.FPDF_GetXMPMetadata.invokeExact(handle, MemorySegment.NULL, 0L);
        if (needed > 0) {
          MemorySegment buf = arena.allocate(needed);
          DocBindings.FPDF_GetXMPMetadata.invokeExact(handle, buf, needed);
          return buf.toArray(JAVA_BYTE);
        }
      }
    } catch (Throwable e) {
      PdfiumLibrary.ignore(e);
    }
    // Fallback path: memoize so repeated calls never re-map the file.
    if (cachedFallbackXmp != null) return cachedFallbackXmp.clone();
    byte[] result = computeFallbackXmp();
    cachedFallbackXmp = result;
    return result == null ? null : result.clone();
  }

  private byte[] computeFallbackXmp() {
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

  private static byte[] extractXmpFromSegment(MemorySegment pdf) {
    byte[] startMarker = "<?xpacket begin".getBytes(StandardCharsets.ISO_8859_1);
    long start = lastIndexOf(pdf, startMarker);
    if (start < 0) return EMPTY_BYTE_ARRAY;

    byte[] endMarker = "<?xpacket end".getBytes(StandardCharsets.ISO_8859_1);
    long end = indexOf(pdf, endMarker, start);
    if (end < 0) return EMPTY_BYTE_ARRAY;

    byte[] termMarker = "?>".getBytes(StandardCharsets.ISO_8859_1);
    long term = indexOf(pdf, termMarker, end);
    if (term < 0) return EMPTY_BYTE_ARRAY;

    return pdf.asSlice(start, term + 2 - start).toArray(JAVA_BYTE);
  }

  public String xmpMetadataString() {
    return new String(xmpMetadata(), StandardCharsets.UTF_8);
  }

  public void setMetadata(MetadataTag tag, String value) {
    ensureOpen();
    pendingMetadata.put(tag, value);
    try (Arena arena = Arena.ofConfined()) {
      long keyUpperBound = Math.addExact(Math.multiplyExact((long) tag.pdfKey().length(), 4), 1);
      MemorySegment tagSeg =
          FfmHelper.writeUtf8String(arena.allocate(keyUpperBound, 1), tag.pdfKey());
      MemorySegment valSeg = FfmHelper.toWideString(arena, value);
      if (EditBindings.FPDF_SetMetaText != null) {
        int ok = (int) EditBindings.FPDF_SetMetaText.invokeExact(handle, tagSeg, valSeg);
        if (ok != 0) {
          structurallyModified = true;
        }
      }
    } catch (Throwable e) {
      PdfiumLibrary.ignore(e);
    }
  }

  public void setMetadata(Map<MetadataTag, String> metadata) {
    metadata.forEach(this::setMetadata);
  }

  public void setXmpMetadata(String xmp) {
    ensureOpen();
    pendingXmpMetadata = xmp;
  }

  public void insertBlankPage(int index, PageSize size) {
    ensureOpen();
    try {
      MemorySegment p =
          (MemorySegment)
              EditBindings.FPDFPage_New.invokeExact(
                  handle, index, (double) size.width(), (double) size.height());
      if (FfmHelper.isNull(p)) throwLastError("Failed to insert page");
      ViewBindings.FPDF_ClosePage.invokeExact(p);
      markStructurallyModified();
    } catch (Throwable t) {
      throw new PdfiumException("Failed to insert page", t);
    }
  }

  public void deletePage(int index) {
    ensureOpen();
    if (index < 0 || index >= pageCount())
      throw new IllegalArgumentException("Index out of bounds");
    try {
      DocBindings.FPDFPage_Delete.invokeExact(handle, index);
      markStructurallyModified();
    } catch (Throwable t) {
      throw new PdfiumException("Failed to delete page", t);
    }
  }

  public void importPages(PdfDocument src, String range, int index) {
    ensureOpen();
    try {
      MemorySegment r = (range != null) ? docArena.allocateFrom(range) : MemorySegment.NULL;
      int ok = (int) EditBindings.FPDF_ImportPages.invokeExact(handle, src.handle, r, index);
      if (ok == 0) throwLastError("Failed to import pages");
      markStructurallyModified();
    } catch (Throwable t) {
      throw new PdfiumException("Failed to import pages", t);
    }
  }

  public void importAllPages(PdfDocument src) {
    importPages(src, null, pageCount());
  }

  public byte[] renderPageToBytes(int index, int dpi, String format) {
    try (PdfPage p = page(index)) {
      RenderResult res = p.render(dpi);
      if ("png".equalsIgnoreCase(format)) return res.toPngBytes();
      if ("jpeg".equalsIgnoreCase(format) || "jpg".equalsIgnoreCase(format))
        return res.toJpegBytes();
      throw new IllegalArgumentException("Unsupported format: " + format);
    }
  }

  @SuppressWarnings("resource")
  public void save(Path path) {
    if (path.equals(sourcePath)) {
      Path temp = null;
      boolean detachedSource = false;
      try {
        temp = Files.createTempFile("pdfium4j-save-", ".pdf");
        try (OutputStream out = Files.newOutputStream(temp)) {
          save(out);
        }
        if (docSourceChannel != null) {
          docSourceChannel.close();
          docSourceChannel = null;
          CHANNELS.remove(channelId);
          state.updateSourceChannel(null);
          detachedSource = true;
        }
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        temp = null; // Prevent deletion in finally if move succeeded
        if (channelId > 0) {
          docSourceChannel = Files.newByteChannel(path, StandardOpenOption.READ);
          CHANNELS.put(channelId, docSourceChannel);
          state.updateSourceChannel(docSourceChannel);
          detachedSource = false;
        }
      } catch (IOException e) {
        if (detachedSource && channelId > 0 && docSourceChannel == null) {
          try {
            docSourceChannel = Files.newByteChannel(path, StandardOpenOption.READ);
            CHANNELS.put(channelId, docSourceChannel);
            state.updateSourceChannel(docSourceChannel);
          } catch (IOException restoreEx) {
            PdfiumLibrary.ignore(restoreEx);
          }
        }
        throw new PdfiumException("Failed to save to source path: " + path, e);
      } finally {
        if (temp != null) {
          try {
            Files.deleteIfExists(temp);
          } catch (IOException e) {
            PdfiumLibrary.ignore(e);
          }
        }
      }
    } else {
      try (OutputStream out = Files.newOutputStream(path)) {
        save(out);
      } catch (IOException e) {
        throw new PdfiumException("Failed to save to " + path, e);
      }
    }
  }

  public void save(OutputStream out) {
    ensureOpen();
    try {
      PdfSaver.SaveParams params =
          new PdfSaver.SaveParams(
              handle,
              buildMergedMetadata(),
              !pendingMetadata.isEmpty(),
              pendingXmpMetadata,
              docSourceChannel,
              sourcePath,
              sourceBytes,
              structurallyModified,
              out);
      PdfSaver.save(params);
    } catch (IOException e) {
      throw new PdfiumException("Failed to save document", e);
    }
  }

  public byte[] saveToBytes() {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    save(bos);
    return bos.toByteArray();
  }

  @Override
  public void close() {
    ensureThreadConfinement();
    if (closed) return;
    closed = true;
    try {
      List<PdfPage> snapshot;
      synchronized (openPages) {
        snapshot = new ArrayList<>(openPages);
        openPages.clear();
      }
      for (PdfPage pdfPage : snapshot) {
        pdfPage.closeFromDocument();
      }
      ViewBindings.FPDF_CloseDocument.invokeExact(handle);
    } catch (Throwable e) {
      PdfiumLibrary.ignore(e);
    } finally {
      cleanable.clean();
    }
  }

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("PdfDocument is already closed");
    ensureThreadConfinement();
  }

  private void ensureThreadConfinement() {
    if (Thread.currentThread() != ownerThread)
      throw new IllegalStateException("Thread confinement violation");
  }

  private void registerPage(PdfPage page) {
    synchronized (openPages) {
      openPages.add(page);
    }
  }

  private void unregisterPage(PdfPage page) {
    synchronized (openPages) {
      openPages.remove(page);
    }
  }

  private static PdfProcessingPolicy resolvePolicy(PdfProcessingPolicy policy) {
    return policy != null ? policy : PdfProcessingPolicy.defaultPolicy();
  }

  private static void throwLastError(String message) {
    int err;
    try {
      err = (int) ViewBindings.FPDF_GetLastError.invokeExact();
    } catch (Throwable t) {
      err = 0;
    }
    throw mapOpenError(message, err);
  }

  public static PdfDiagnostic diagnose(Path path) {
    if (path == null)
      return new PdfDiagnostic(null, false, -1, false, false, 0, List.of("Null path"));
    PdfProbeResult pr = probe(path);
    return new PdfDiagnostic(
        path.toString(), pr.isValid(), pr.pageCount(), pr.needsPassword(), false, 0, List.of());
  }

  private Map<MetadataTag, String> buildMergedMetadata() {
    Map<MetadataTag, String> merged = LinkedHashMap.newLinkedHashMap(METADATA_TAGS.length);
    for (MetadataTag tag : METADATA_TAGS) {
      if (!pendingMetadata.containsKey(tag)) metadata(tag).ifPresent(v -> merged.put(tag, v));
    }
    merged.putAll(pendingMetadata);
    return merged;
  }

  public static PdfProbeResult probe(Path path) {
    return probe(path, resolvePolicy(null));
  }

  public static PdfProbeResult probe(Path path, PdfProcessingPolicy policy) {
    if (path == null)
      return PdfProbeResult.error(PdfProbeResult.Status.UNREADABLE, PdfErrorCode.FILE, "Null path");
    PdfiumLibrary.ensureInitialized();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment pathSeg = arena.allocateFrom(path.toString());
      MemorySegment doc =
          (MemorySegment) ViewBindings.FPDF_LoadDocument.invokeExact(pathSeg, MemorySegment.NULL);
      if (FfmHelper.isNull(doc)) {
        int err = (int) ViewBindings.FPDF_GetLastError.invokeExact();
        if (err == ViewBindings.FPDF_ERR_PASSWORD) return PdfProbeResult.ok(-1, true);
        return PdfProbeResult.error(
            PdfProbeResult.Status.CORRUPT, PdfErrorCode.fromCode(err), "Failed to probe document");
      }
      int count = (int) ViewBindings.FPDF_GetPageCount.invokeExact(doc);
      ViewBindings.FPDF_CloseDocument.invokeExact(doc);
      return PdfProbeResult.ok(count, false);
    } catch (Throwable t) {
      return PdfProbeResult.error(
          PdfProbeResult.Status.CORRUPT, PdfErrorCode.FORMAT, t.getMessage());
    }
  }

  public static PdfProbeResult probe(byte[] data) {
    return probe(data, resolvePolicy(null));
  }

  public static PdfProbeResult probe(byte[] data, PdfProcessingPolicy policy) {
    if (data == null || data.length == 0)
      return PdfProbeResult.error(
          PdfProbeResult.Status.UNREADABLE, PdfErrorCode.FILE, "Empty data");
    PdfiumLibrary.ensureInitialized();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment seg = arena.allocateFrom(JAVA_BYTE, data);
      MemorySegment doc =
          (MemorySegment)
              ViewBindings.FPDF_LoadMemDocument.invokeExact(seg, data.length, MemorySegment.NULL);
      if (FfmHelper.isNull(doc)) {
        int err = (int) ViewBindings.FPDF_GetLastError.invokeExact();
        if (err == ViewBindings.FPDF_ERR_PASSWORD) return PdfProbeResult.ok(-1, true);
        return PdfProbeResult.error(
            PdfProbeResult.Status.CORRUPT, PdfErrorCode.fromCode(err), "Failed to probe document");
      }
      int count = (int) ViewBindings.FPDF_GetPageCount.invokeExact(doc);
      ViewBindings.FPDF_CloseDocument.invokeExact(doc);
      return PdfProbeResult.ok(count, false);
    } catch (Throwable t) {
      return PdfProbeResult.error(
          PdfProbeResult.Status.CORRUPT, PdfErrorCode.FORMAT, t.getMessage());
    }
  }

  public static Optional<String> koReaderPartialMd5(byte[] data) {
    return KoReaderChecksum.calculate(data);
  }

  public static Optional<String> koReaderPartialMd5(Path path) {
    return KoReaderChecksum.calculate(path);
  }

  public static PdfiumException mapOpenError(String ctx, int code) {
    PdfErrorCode ec = PdfErrorCode.fromCode(code);
    return switch (ec) {
      case PASSWORD -> new PdfPasswordException(ctx, ec, "open", null);
      case FORMAT -> new PdfCorruptException(ctx, ec, "open", null);
      case SECURITY -> new PdfUnsupportedSecurityException(ctx, ec, "open", null);
      default -> new PdfiumException(ctx, ec, "open", null);
    };
  }
}
