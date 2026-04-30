package org.grimmory.pdfium4j;

import static java.lang.foreign.ValueLayout.*;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.pdfium4j.exception.PdfCorruptException;
import org.grimmory.pdfium4j.exception.PdfPasswordException;
import org.grimmory.pdfium4j.exception.PdfUnsupportedSecurityException;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.internal.DocBindings;
import org.grimmory.pdfium4j.internal.EditBindings;
import org.grimmory.pdfium4j.internal.FfmHelper;
import org.grimmory.pdfium4j.internal.ViewBindings;
import org.grimmory.pdfium4j.model.*;

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

  private static final Map<Long, SeekableByteChannel> CHANNELS = new ConcurrentHashMap<>();
  private static final AtomicLong CHANNEL_ID_SEQ = new AtomicLong();
  private static final Cleaner CLEANER = Cleaner.create();

  private final MemorySegment handle;
  private final Arena docArena;
  private SeekableByteChannel sourceChannel;
  private final Path sourcePath;
  private final byte[] sourceBytes;
  private final long channelId;
  private final PdfProcessingPolicy policy;
  private final Thread ownerThread;
  private final List<PdfPage> openPages = new ArrayList<>();
  private volatile boolean closed = false;
  private volatile boolean structurallyModified = false;
  private final Map<MetadataTag, String> pendingMetadata = new LinkedHashMap<>();
  private String pendingXmpMetadata = null;
  private final State state;
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
    this.sourceChannel = sourceChannel;
    this.channelId = channelId;
    this.sourcePath = sourcePath;
    this.sourceBytes = sourceBytes;
    this.policy = policy;
    this.ownerThread = ownerThread;
    this.state = new State(channelId, sourceChannel, tempFile, docArena);
    this.cleanable = CLEANER.register(this, state);
    PdfiumLibrary.incrementDocumentCount();
  }

  /** Represents the cleanup state to be executed by the {@link Cleaner}. */
  private static final class State implements Runnable {
    private final long channelId;
    private SeekableByteChannel sourceChannel;
    private final Path tempFile;
    private final Arena docArena;

    private State(
        long channelId, SeekableByteChannel sourceChannel, Path tempFile, Arena docArena) {
      this.channelId = channelId;
      this.sourceChannel = sourceChannel;
      this.tempFile = tempFile;
      this.docArena = docArena;
    }

    synchronized void updateSourceChannel(SeekableByteChannel newChannel) {
      this.sourceChannel = newChannel;
    }

    @Override
    public void run() {
      try {
        if (channelId > 0) {
          CHANNELS.remove(channelId);
        }
        synchronized (this) {
          if (sourceChannel != null) {
            try {
              sourceChannel.close();
            } catch (IOException ignored) {
            }
          }
        }
        if (tempFile != null) {
          try {
            Files.deleteIfExists(tempFile);
          } catch (IOException ignored) {
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

  // --- Static Factories (Opening) ---

  public static PdfDocument open(Path path) {
    return open(path, null, resolvePolicy(null));
  }

  public static PdfDocument open(Path path, String password) {
    return open(path, password, resolvePolicy(null));
  }

  @SuppressWarnings("resource")
  public static PdfDocument open(Path path, String password, PdfProcessingPolicy policy) {
    PdfProcessingPolicy resolvedPolicy = resolvePolicy(policy);
    PdfiumLibrary.ensureInitialized();
    try {
      SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ);
      return openFromChannel(channel, path, null, password, path.toString(), resolvedPolicy);
    } catch (IOException e) {
      throw new PdfiumException("Failed to open file: " + path, e);
    }
  }

  public static PdfDocument open(byte[] data) {
    return open(data, null, resolvePolicy(null));
  }

  public static PdfDocument open(byte[] data, String password) {
    return open(data, password, resolvePolicy(null));
  }

  public static PdfDocument open(byte[] data, String password, PdfProcessingPolicy policy) {
    PdfProcessingPolicy resolvedPolicy = resolvePolicy(policy);
    if (data == null || data.length == 0)
      throw new IllegalArgumentException("data is null or empty");
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

  @SuppressWarnings("resource")
  public static PdfDocument open(InputStream in) throws IOException {
    Path temp = Files.createTempFile("pdfium4j-stream-", ".pdf");
    try (OutputStream out = Files.newOutputStream(temp)) {
      in.transferTo(out);
    }
    PdfiumLibrary.ensureInitialized();
    SeekableByteChannel channel = Files.newByteChannel(temp, StandardOpenOption.READ);
    return openFromChannel(channel, temp, temp, null, "InputStream", resolvePolicy(null));
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
        int err = (int) (long) ViewBindings.FPDF_GetLastError.invokeExact();
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
      } catch (IOException ignored) {
      }
      if (tempFile != null) {
        try {
          Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
        }
      }
      docArena.close();
      throw e;
    } catch (Throwable t) {
      if (channelId > 0) CHANNELS.remove(channelId);
      try {
        channel.close();
      } catch (IOException ignored) {
      }
      if (tempFile != null) {
        try {
          Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
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
      synchronized (channel) {
        channel.position(pos);
        ByteBuffer bb = buf.reinterpret(size).asByteBuffer();
        while (bb.hasRemaining()) {
          if (channel.read(bb) == -1) break;
        }
      }
      return 1;
    } catch (IOException e) {
      return 0;
    }
  }

  // --- API ---

  public int pageCount() {
    ensureOpen();
    try {
      return (int) ViewBindings.FPDF_GetPageCount.invokeExact(handle);
    } catch (Throwable t) {
      throw new PdfiumException("Failed to get page count", t);
    }
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
              handle,
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
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment w = arena.allocate(JAVA_DOUBLE);
      MemorySegment h = arena.allocate(JAVA_DOUBLE);
      int ok = (int) ViewBindings.FPDF_GetPageSizeByIndex.invokeExact(handle, index, w, h);
      if (ok == 0) throwLastError("Failed to get page size " + index);
      return new PageSize((float) w.get(JAVA_DOUBLE, 0), (float) h.get(JAVA_DOUBLE, 0));
    } catch (Throwable t) {
      throw new PdfiumException("Failed to get page size " + index, t);
    }
  }

  public List<Bookmark> bookmarks() {
    ensureOpen();
    return BookmarkReader.readBookmarks(handle);
  }

  public Optional<String> pageLabel(int index) {
    ensureOpen();
    try (Arena arena = Arena.ofConfined()) {
      long needed =
          (long) DocBindings.FPDF_GetPageLabel.invokeExact(handle, index, MemorySegment.NULL, 0L);
      if (needed <= 2) return Optional.empty();
      MemorySegment buf = arena.allocate(needed);
      DocBindings.FPDF_GetPageLabel.invokeExact(handle, index, buf, needed);
      return Optional.of(FfmHelper.fromWideString(buf, needed));
    } catch (Throwable t) {
      return Optional.empty();
    }
  }

  public List<PageSize> allPageSizes() {
    int count = pageCount();
    List<PageSize> sizes = new ArrayList<>(count);
    for (int i = 0; i < count; i++) sizes.add(pageSize(i));
    return sizes;
  }

  public int fileVersion() {
    ensureOpen();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment v = arena.allocate(JAVA_INT);
      int ok = (int) DocBindings.FPDF_GetFileVersion.invokeExact(handle, v);
      return ok != 0 ? v.get(JAVA_INT, 0) : 0;
    } catch (Throwable t) {
      return 0;
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
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment keySeg = arena.allocateFrom(tag.pdfKey());
      long needed =
          (long) DocBindings.FPDF_GetMetaText.invokeExact(handle, keySeg, MemorySegment.NULL, 0L);
      if (needed <= 2) return metadataFallback(tag);
      MemorySegment buf = arena.allocate(needed);
      DocBindings.FPDF_GetMetaText.invokeExact(handle, keySeg, buf, needed);
      String val = FfmHelper.fromWideString(buf, needed);
      return (val == null || val.isEmpty()) ? Optional.empty() : Optional.of(val);
    } catch (Throwable t) {
      return metadataFallback(tag);
    }
  }

  private Optional<String> metadataFallback(MetadataTag tag) {
    if (sourceBytes != null) return extractMetadataFromBytes(sourceBytes, tag);
    if (sourcePath != null) {
      try {
        byte[] bytes = Files.readAllBytes(sourcePath);
        return extractMetadataFromBytes(bytes, tag);
      } catch (IOException ignored) {
      }
    }
    return Optional.empty();
  }

  private static Optional<String> extractMetadataFromBytes(byte[] pdf, MetadataTag tag) {
    String tail =
        new String(
            pdf,
            Math.max(0, pdf.length - 4096),
            Math.min(pdf.length, 4096),
            java.nio.charset.StandardCharsets.ISO_8859_1);
    // Find latest /Info reference in tail
    Pattern infoP = Pattern.compile("/Info\\s+(\\d+)\\s+(\\d+)\\s+R");
    Matcher m = infoP.matcher(tail);
    int objNum = -1;
    int genNum = 0;
    while (m.find()) {
      objNum = Integer.parseInt(m.group(1));
      genNum = Integer.parseInt(m.group(2));
    }
    if (objNum < 0) return Optional.empty();

    // Find the object
    String dict = extractDict(pdf, objNum, genNum);
    if (dict == null) return Optional.empty();

    // Find the tag in dict
    Pattern tagP = Pattern.compile("/" + tag.pdfKey() + "\\s+\\((.*?)\\)");
    Matcher tagM = tagP.matcher(dict);
    if (tagM.find()) {
      String val = tagM.group(1);
      return (val == null || val.isEmpty()) ? Optional.empty() : Optional.of(val);
    }

    // Hex string fallback
    tagP = Pattern.compile("/" + tag.pdfKey() + "\\s+<(.*?)>");
    tagM = tagP.matcher(dict);
    if (tagM.find()) {
      String val = decodeHexPdfString(tagM.group(1));
      return (val == null || val.isEmpty()) ? Optional.empty() : Optional.of(val);
    }

    return Optional.empty();
  }

  @CheckForNull
  private static String extractDict(byte[] pdf, int objNum, int genNum) {
    String s = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
    // Use regex to find latest object definition
    Pattern p = Pattern.compile("\\b" + objNum + "\\s+" + genNum + "\\s+obj\\b");
    Matcher m = p.matcher(s);
    int startIdx = -1;
    while (m.find()) {
      startIdx = m.start();
    }
    if (startIdx < 0) return null;

    int dictStart = s.indexOf("<<", startIdx);
    if (dictStart < 0) return null;

    int depth = 0;
    int pos = dictStart;
    while (pos < s.length() - 1) {
      if (s.charAt(pos) == '<' && s.charAt(pos + 1) == '<') {
        depth++;
        pos += 2;
      } else if (s.charAt(pos) == '>' && s.charAt(pos + 1) == '>') {
        depth--;
        if (depth == 0) return s.substring(dictStart, pos + 2);
        pos += 2;
      } else {
        pos++;
      }
    }
    return null;
  }

  private static String decodeHexPdfString(String hex) {
    if (hex.startsWith("FEFF")) {
      // UTF-16BE
      try {
        byte[] bytes = new byte[(hex.length() - 4) / 2];
        for (int i = 0; i < bytes.length; i++) {
          bytes[i] = (byte) Integer.parseInt(hex.substring(4 + i * 2, 6 + i * 2), 16);
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_16BE);
      } catch (Exception e) {
        return hex;
      }
    }
    return hex; // Raw hex fallback
  }

  public Optional<String> metadata(String customKey) {
    for (MetadataTag tag : MetadataTag.values())
      if (tag.pdfKey().equalsIgnoreCase(customKey)) return metadata(tag);
    return Optional.empty();
  }

  public Map<String, String> metadata() {
    Map<String, String> meta = new LinkedHashMap<>();
    for (MetadataTag tag : MetadataTag.values())
      metadata(tag).ifPresent(v -> meta.put(tag.name(), v));
    return meta;
  }

  public byte[] xmpMetadata() {
    ensureOpen();
    if (pendingXmpMetadata != null) {
      return pendingXmpMetadata.getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
    } catch (Throwable ignored) {
    }
    if (sourceBytes != null) return extractXmpFromBytes(sourceBytes);
    if (sourcePath != null) {
      try {
        byte[] bytes = Files.readAllBytes(sourcePath);
        return extractXmpFromBytes(bytes);
      } catch (IOException ignored) {
      }
    }
    return new byte[0];
  }

  private static byte[] extractXmpFromBytes(byte[] pdf) {
    String s = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
    int start = s.lastIndexOf("<?xpacket begin");
    if (start < 0) return new byte[0];
    int end = s.indexOf("<?xpacket end", start);
    if (end < 0) return new byte[0];
    int term = s.indexOf("?>", end);
    if (term < 0) return new byte[0];
    return Arrays.copyOfRange(pdf, start, term + 2);
  }

  public String xmpMetadataString() {
    return new String(xmpMetadata(), java.nio.charset.StandardCharsets.UTF_8);
  }

  public void setMetadata(MetadataTag tag, String value) {
    ensureOpen();
    pendingMetadata.put(tag, value);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment tagSeg = arena.allocateFrom(tag.pdfKey());
      MemorySegment valSeg = FfmHelper.toWideString(arena, value);
      if (EditBindings.FPDF_SetMetaText != null) {
        int ok = (int) EditBindings.FPDF_SetMetaText.invokeExact(handle, tagSeg, valSeg);
        if (ok != 0) {
          structurallyModified = true;
        }
      }
    } catch (Throwable ignored) {
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
      structurallyModified = true;
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
      structurallyModified = true;
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
      structurallyModified = true;
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

  public void save(Path path) {
    save(path, SaveOptions.DEFAULT);
  }

  @SuppressWarnings("resource")
  public void save(Path path, SaveOptions options) {
    if (path.equals(sourcePath)) {
      try {
        Path temp = Files.createTempFile("pdfium4j-save-", ".pdf");
        try (OutputStream out = Files.newOutputStream(temp)) {
          save(out, options);
        }
        if (sourceChannel != null) {
          sourceChannel.close();
          sourceChannel = null;
          CHANNELS.remove(channelId);
          state.updateSourceChannel(null);
        }
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        if (channelId > 0) {
          sourceChannel = Files.newByteChannel(path, StandardOpenOption.READ);
          CHANNELS.put(channelId, sourceChannel);
          state.updateSourceChannel(sourceChannel);
        }
      } catch (IOException e) {
        throw new PdfiumException("Failed to save to source path: " + path, e);
      }
    } else {
      try (OutputStream out = Files.newOutputStream(path)) {
        save(out, options);
      } catch (IOException e) {
        throw new PdfiumException("Failed to save to " + path, e);
      }
    }
  }

  public void save(OutputStream out) {
    save(out, SaveOptions.DEFAULT);
  }

  public void save(OutputStream out, SaveOptions options) {
    ensureOpen();
    try {
      PdfSaver.save(
          handle,
          buildMergedMetadata(),
          !pendingMetadata.isEmpty(),
          pendingXmpMetadata,
          options.skipValidation(),
          sourceChannel,
          sourcePath,
          sourceBytes,
          structurallyModified,
          out);
    } catch (IOException e) {
      throw new PdfiumException("Failed to save document", e);
    }
  }

  public byte[] saveToBytes() {
    return saveToBytes(SaveOptions.DEFAULT);
  }

  public byte[] saveToBytes(SaveOptions options) {
    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
    save(bos, options);
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
      for (PdfPage page : snapshot) {
        page.closeFromDocument();
      }
      ViewBindings.FPDF_CloseDocument.invokeExact(handle);
    } catch (Throwable ignored) {
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
      err = (int) (long) ViewBindings.FPDF_GetLastError.invokeExact();
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
    Map<MetadataTag, String> merged = new LinkedHashMap<>();
    for (MetadataTag tag : MetadataTag.values())
      if (!pendingMetadata.containsKey(tag)) metadata(tag).ifPresent(v -> merged.put(tag, v));
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
        int err = (int) (long) ViewBindings.FPDF_GetLastError.invokeExact();
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
        int err = (int) (long) ViewBindings.FPDF_GetLastError.invokeExact();
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
