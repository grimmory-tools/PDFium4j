package org.grimmory.pdfium4j;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Cleaner;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.grimmory.pdfium4j.exception.PdfCorruptException;
import org.grimmory.pdfium4j.exception.PdfPasswordException;
import org.grimmory.pdfium4j.exception.PdfUnsupportedSecurityException;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.internal.DocBindings;
import org.grimmory.pdfium4j.internal.EditBindings;
import org.grimmory.pdfium4j.internal.FfmHelper;
import org.grimmory.pdfium4j.internal.Generators;
import org.grimmory.pdfium4j.internal.IntObjectCache;
import org.grimmory.pdfium4j.internal.IoUtils;
import org.grimmory.pdfium4j.internal.NoAllocationPathProbe;
import org.grimmory.pdfium4j.internal.PdfDocumentAttachments;
import org.grimmory.pdfium4j.internal.PdfDocumentIndexer;
import org.grimmory.pdfium4j.internal.PdfDocumentMetadata;
import org.grimmory.pdfium4j.internal.PdfDocumentNativeSaver;
import org.grimmory.pdfium4j.internal.PdfDocumentSignatures;
import org.grimmory.pdfium4j.internal.PdfDocumentXmp;
import org.grimmory.pdfium4j.internal.ScratchBuffer;
import org.grimmory.pdfium4j.internal.SegmentOutputStream;
import org.grimmory.pdfium4j.internal.ShimBindings;
import org.grimmory.pdfium4j.internal.TrigramTokenizer;
import org.grimmory.pdfium4j.internal.ViewBindings;
import org.grimmory.pdfium4j.internal.XmpUpdate;
import org.grimmory.pdfium4j.model.BookMetadata;
import org.grimmory.pdfium4j.model.Bookmark;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.grimmory.pdfium4j.model.PageSize;
import org.grimmory.pdfium4j.model.PdfAttachment;
import org.grimmory.pdfium4j.model.PdfBookMetadata;
import org.grimmory.pdfium4j.model.PdfDiagnostic;
import org.grimmory.pdfium4j.model.PdfErrorCode;
import org.grimmory.pdfium4j.model.PdfProbeResult;
import org.grimmory.pdfium4j.model.PdfProcessingPolicy;
import org.grimmory.pdfium4j.model.PdfSignature;
import org.grimmory.pdfium4j.model.RenderResult;
import org.grimmory.pdfium4j.model.XmpMetadata;

/** Represents an open PDF document backed by native PDFium. */
public final class PdfDocument implements AutoCloseable {
  private static final Logger LOGGER = Logger.getLogger(PdfDocument.class.getName());

  private static final class RegistryHolder {
    private static final Map<Long, SeekableByteChannel> CHANNELS = new ConcurrentHashMap<>(16);
  }

  private static final Cleaner CLEANER = Cleaner.create();
  private static final int[] EMPTY_INT_ARRAY = Generators.emptyIntArray();

  private static final MetadataTag[] METADATA_TAGS = MetadataTag.values();

  // MetadataCache and indexText logic moved to specialized internal classes

  private static final XmpMetadataWriter XMP_WRITER = new XmpMetadataWriter();

  private final MemorySegment handle;
  private final Arena docArena;
  private SeekableByteChannel docSourceChannel;
  private final Path sourcePath;
  private final byte[] sourceBytes;
  private final long channelId;
  private final PdfProcessingPolicy policy;
  private final int sourceFileVersion;
  private final Thread ownerThread;
  private final List<PdfPage> openPages = new ArrayList<>(8);
  private volatile boolean closed = false;

  @SuppressWarnings("PMD.UnusedPrivateField")
  MemorySegment sourceSegment;

  @SuppressWarnings("PMD.UnusedPrivateField")
  Arena sourceSegmentArena;

  MemorySegment handle() {
    return handle;
  }

  private volatile int cachedPageCount = -1;

  /** Lazy-parsed fallback Info dict key→value map (populated at most once per document). */
  private Map<String, String> cachedFallbackMeta;

  /** Memoized XMP bytes from file-system fallback path. */
  private byte[] cachedFallbackXmp;

  private final Map<MetadataTag, String> pendingMetadata = new EnumMap<>(MetadataTag.class);
  private final Map<String, String> pendingCustomMetadata =
      new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private XmpUpdate pendingXmp = null;
  private final PdfSaver.MetadataProvider nativeMetadataProvider = this::metadataString;
  final CleanupState state;
  private final Cleaner.Cleanable cleanable;

  private final IntObjectCache<PdfPage> pageCache;
  private final IntObjectCache<PageSize> pageSizeCache;
  private final IntObjectCache<Integer> rotationCache;
  private Map<Long, int[]> textIndex = null;
  private volatile boolean structurallyModified = false;

  /** Internal metadata about the document source. */
  record SourceInfo(Path path, Path tempFile, byte[] sourceBytes, int version, Thread ownerThread) {
    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SourceInfo other)) {
        return false;
      }
      return Objects.equals(path, other.path)
          && Objects.equals(tempFile, other.tempFile)
          && Arrays.equals(sourceBytes, other.sourceBytes)
          && version == other.version
          && Objects.equals(ownerThread, other.ownerThread);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(path, tempFile, version, ownerThread);
      result = 31 * result + Arrays.hashCode(sourceBytes);
      return result;
    }

    @Override
    public String toString() {
      return "SourceInfo[path="
          + path
          + ", tempFile="
          + tempFile
          + ", sourceBytes="
          + Arrays.toString(sourceBytes)
          + ", version="
          + version
          + ", ownerThread="
          + ownerThread
          + "]";
    }
  }

  PdfDocument(MemorySegment handle, Arena docArena, PdfProcessingPolicy policy, SourceInfo source) {
    this.handle = handle.reinterpret(ValueLayout.ADDRESS.byteSize());
    this.docArena = docArena;
    this.docSourceChannel = null;
    this.channelId = 0L;
    this.sourcePath = source.path();
    this.sourceBytes = source.sourceBytes();
    this.sourceSegment =
        source.sourceBytes() != null ? MemorySegment.ofArray(source.sourceBytes()) : null;
    this.sourceSegmentArena = null;
    this.policy = policy;
    this.sourceFileVersion = source.version();
    this.ownerThread = source.ownerThread();
    this.state = new CleanupState(handle, 0L, null, source.tempFile(), docArena, null);
    this.cleanable = CLEANER.register(this, state);
    this.pageCache = new IntObjectCache<>(policy.maxPageCacheBytes(), PdfPage::release);
    // PageSize and rotation caches are small, so we use a 1MB budget which is plenty
    this.pageSizeCache = new IntObjectCache<>(1024L * 1024L);
    this.rotationCache = new IntObjectCache<>(1024L * 1024L);
    PdfiumLibrary.incrementDocumentCount();
  }

  static final class CleanupState implements Runnable {
    private final MemorySegment handle;
    private final long channelId;
    private final AtomicReference<SeekableByteChannel> sourceChannelRef = new AtomicReference<>();
    private final Path tempFile;
    private final Arena docArena;
    private final AtomicReference<Arena> sourceSegmentArenaRef = new AtomicReference<>();

    private CleanupState(
        MemorySegment handle,
        long channelId,
        SeekableByteChannel chan,
        Path tempFile,
        Arena docArena,
        Arena sourceSegmentArena) {
      this.handle = handle;
      this.channelId = channelId;
      this.sourceChannelRef.set(chan);
      this.tempFile = tempFile;
      this.docArena = docArena;
      this.sourceSegmentArenaRef.set(sourceSegmentArena);
    }

    void updateSourceChannel(SeekableByteChannel newChannel) {
      this.sourceChannelRef.set(newChannel);
    }

    void updateSourceSegmentArena(Arena newArena) {
      this.sourceSegmentArenaRef.set(newArena);
    }

    @Override
    public void run() {
      try {
        cleanupChannels();
        cleanupTempFile();
        cleanupSourceSegmentArena();
        cleanupNativeHandle();
        cleanupArena();
      } finally {
        PdfiumLibrary.decrementDocumentCount();
      }
    }

    private void cleanupChannels() {
      if (channelId > 0) {
        SeekableByteChannel removed = RegistryHolder.CHANNELS.remove(channelId);
        SeekableByteChannel current = sourceChannelRef.get();
        if (removed != null && removed != current) {
          closeQuietly(removed);
        }
      }
      closeQuietly(sourceChannelRef.getAndSet(null));
    }

    private void cleanupSourceSegmentArena() {
      Arena arena = sourceSegmentArenaRef.getAndSet(null);
      if (arena != null) {
        try {
          arena.close();
        } catch (Exception e) {
          PdfiumLibrary.ignore(e);
        }
      }
    }

    private static void closeQuietly(SeekableByteChannel chan) {
      if (chan == null) return;
      try {
        chan.close();
      } catch (IOException e) {
        PdfiumLibrary.ignore(e);
      }
    }

    private void cleanupTempFile() {
      if (tempFile == null) return;
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException e) {
        PdfiumLibrary.ignore(e);
      }
    }

    private void cleanupNativeHandle() {
      if (FfmHelper.isNull(handle)) return;
      try {
        ViewBindings.fpdfCloseDocument().invokeExact(handle);
      } catch (Throwable t) {
        PdfiumLibrary.ignore(t);
      }
    }

    private void cleanupArena() {
      if (docArena != null) {
        docArena.close();
      }
    }
  }

  /**
   * Create a new, empty PDF document.
   *
   * @return a new PdfDocument instance
   * @throws PdfiumException if creation fails
   */
  public static PdfDocument create() {
    return PdfDocumentOpener.create(resolvePolicy(null));
  }

  public static PdfDocument open(Path path) {
    return PdfDocumentOpener.open(path, null, resolvePolicy(null));
  }

  @SuppressWarnings("resource")
  public static PdfDocument open(Path path, String password, PdfProcessingPolicy policy) {
    return PdfDocumentOpener.open(path, password, resolvePolicy(policy));
  }

  /**
   * Opens a PDF document from an {@link InputStream}.
   *
   * <p>Since PDFium requires seekable access to the document, the entire stream is buffered to a
   * temporary file. The temporary file is automatically deleted when the document is closed.
   *
   * @param in the input stream containing the PDF data
   * @return a new PdfDocument instance
   * @throws PdfiumException if the document is corrupt, password protected, or if buffering fails
   */
  public static PdfDocument open(InputStream in) {
    return PdfDocumentOpener.open(in, null, resolvePolicy(null));
  }

  /**
   * Opens a PDF document from an {@link InputStream} with the given password and policy.
   *
   * @param in the input stream containing the PDF data
   * @param password optional document password
   * @param policy processing policy
   * @return a new PdfDocument instance
   */
  public static PdfDocument open(InputStream in, String password, PdfProcessingPolicy policy) {
    return PdfDocumentOpener.open(in, password, resolvePolicy(policy));
  }

  public static PdfDocument open(byte[] data) {
    return PdfDocumentOpener.open(data, null, resolvePolicy(null));
  }

  public static PdfDocument open(byte[] data, String password, PdfProcessingPolicy policy) {
    return PdfDocumentOpener.open(data, password, resolvePolicy(policy));
  }

  static PdfDocument open(MemorySegment segment, PdfProcessingPolicy policy) {
    Arena arena = Arena.ofShared();
    boolean success = false;
    try {
      PdfDocument document =
          PdfDocumentOpener.open(segment, null, resolvePolicy(policy), arena, null);
      success = true;
      return document;
    } finally {
      if (!success) {
        arena.close();
      }
    }
  }

  public synchronized int pageCount() {
    ensureOpen();
    if (cachedPageCount >= 0) return cachedPageCount;
    try {
      cachedPageCount = (int) ShimBindings.pdfium4jPageCount().invokeExact(handle);
      return cachedPageCount;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to get page count", t);
    }
  }

  /** Marks the document as structurally modified and invalidates the cached page count. */
  private synchronized void markStructurallyModified() {
    cachedPageCount = -1;
    pageCache.clear();
    pageSizeCache.clear();
    rotationCache.clear();
    textIndex = null;
    structurallyModified = true;
  }

  public synchronized PdfPage page(int index) {
    ensureOpen();
    if (index < 0 || index >= pageCount())
      throw new IllegalArgumentException("Index out of bounds: " + index);

    PdfPage cached = pageCache.get(index);
    if (cached != null && !cached.isClosed()) {
      cached.acquire();
      return cached;
    }

    try {
      MemorySegment pageHandle =
          (MemorySegment) ViewBindings.fpdfLoadPage().invokeExact(handle, index);
      if (FfmHelper.isNull(pageHandle)) throwLastError("Failed to load page " + index);
      PdfPage page =
          new PdfPage(
              pageHandle,
              ownerThread,
              policy.maxRenderPixels(),
              p -> {
                unregisterPage(p);
                pageCache.removeIf(index, p);
              },
              this::markStructurallyModified);

      registerPage(page);
      page.acquire(); // Cache takes a reference
      pageCache.put(index, page, page.estimatedSizeBytes());

      return page;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to load page " + index, t);
    }
  }

  public synchronized PageSize pageSize(int index) {
    ensureOpen();
    if (index < 0 || index >= pageCount()) {
      throw new IllegalArgumentException("Index out of bounds: " + index);
    }
    PageSize cached = pageSizeCache.get(index);
    if (cached != null) return cached;

    try (var _ = ScratchBuffer.acquireScope()) {
      float width = (float) ShimBindings.pdfium4jPageWidth().invokeExact(handle, index);
      float height = (float) ShimBindings.pdfium4jPageHeight().invokeExact(handle, index);
      PageSize size = new PageSize(width, height);
      pageSizeCache.put(index, size, 32);
      return size;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to get page size " + index, t);
    }
  }

  public synchronized List<Bookmark> bookmarks() {
    ensureOpen();
    try (var _ = ScratchBuffer.acquireScope()) {
      return BookmarkReader.readBookmarks(handle);
    }
  }

  public synchronized Optional<String> pageLabel(int index) {
    ensureOpen();
    try (var _ = ScratchBuffer.acquireScope()) {
      int needed =
          (int) ShimBindings.pdfium4jPageLabel().invokeExact(handle, index, MemorySegment.NULL, 0);
      if (needed <= 1) return Optional.empty();
      MemorySegment buf = ScratchBuffer.get(needed);
      int copied = (int) ShimBindings.pdfium4jPageLabel().invokeExact(handle, index, buf, needed);
      if (copied <= 1) return Optional.empty();
      return Optional.of(FfmHelper.fromWideString(buf, copied));
    } catch (Throwable _) {
      return Optional.empty();
    }
  }

  public synchronized List<PageSize> allPageSizes() {
    ensureOpen();
    int count = pageCount();
    if (count <= 0) return List.of();
    List<PageSize> sizes = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      sizes.add(pageSize(i));
    }
    return sizes;
  }

  public synchronized int fileVersion() {
    ensureOpen();
    return sourceFileVersion;
  }

  public static NoAllocationPathProbe noAllocationPathProbe(Path path) {
    if (path == null) {
      throw new IllegalArgumentException("path must not be null");
    }
    return new NoAllocationPathProbe(path.toAbsolutePath().normalize(), null);
  }

  public synchronized boolean hasValidCrossReferenceTable() {
    ensureOpen();
    if (ViewBindings.fpdfDocumentHasValidCrossReferenceTable() == null) {
      return true;
    }
    try {
      return (int) ViewBindings.fpdfDocumentHasValidCrossReferenceTable().invokeExact(handle) != 0;
    } catch (PdfiumException e) {
      throw e;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to inspect cross reference table", t);
    }
  }

  public synchronized int[] trailerEnds() {
    ensureOpen();
    if (ViewBindings.fpdfGetTrailerEnds() == null) {
      return EMPTY_INT_ARRAY;
    }
    try (var _ = ScratchBuffer.acquireScope()) {
      int capacity = 8;
      while (true) {
        long byteSize = Math.multiplyExact(capacity, JAVA_INT.byteSize());
        MemorySegment buffer = ScratchBuffer.get(byteSize);
        long written =
            (long) ViewBindings.fpdfGetTrailerEnds().invoke(handle, buffer, (long) capacity);
        if (written <= 0) {
          return EMPTY_INT_ARRAY;
        }
        if (written <= capacity) {
          long usedBytes = Math.multiplyExact(written, JAVA_INT.byteSize());
          return buffer.asSlice(0, usedBytes).toArray(JAVA_INT);
        }
        capacity = Math.toIntExact(written);
      }
    } catch (PdfiumException e) {
      throw e;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to inspect trailer ends", t);
    }
  }

  public synchronized boolean isImageOnly() {
    int count = pageCount();
    int sampleCount = Math.min(count, 10);
    for (int i = 0; i < sampleCount; i++) {
      try (PdfPage p = page(i)) {
        if (p.hasText()) return false;
      }
    }
    return true;
  }

  public synchronized Optional<String> metadata(MetadataTag tag) {
    PdfDocumentMetadata.ensureInitialized();
    String val = metadataString(tag);
    return (val == null || val.isEmpty()) ? Optional.empty() : Optional.of(val);
  }

  public synchronized String metadataString(MetadataTag tag) {
    ensureOpen();
    PdfDocumentMetadata.ensureInitialized();
    return PdfDocumentMetadata.readMetadataString(
        handle, tag, PdfDocumentMetadata.getHandle(tag), pendingMetadata, this::metadataFallback);
  }

  /**
   * Builds a trigram-based full-text index of the entire document. This enables fast search()
   * operations but may take time for large documents.
   */
  public synchronized void indexText() {
    ensureOpen();
    this.textIndex = PdfDocumentIndexer.buildIndex(this);
  }

  /**
   * Searches the document for the given query using the trigram index if available, otherwise
   * performs a linear scan.
   *
   * @param query the text to search for
   * @return a list of page indices containing the query
   */
  public synchronized List<Integer> search(String query) {
    ensureOpen();
    if (query == null || query.isEmpty()) return List.of();

    String normalized = query.toLowerCase(Locale.ROOT);
    if (textIndex == null) {
      // Fallback to linear scan if not indexed
      List<Integer> results = new ArrayList<>(pageCount());
      int count = pageCount();
      for (int i = 0; i < count; i++) {
        try (PdfPage p = page(i)) {
          if (p.extractText().toLowerCase(Locale.ROOT).contains(normalized)) {
            results.add(i);
          }
        }
      }
      return results;
    }

    // Use trigram index to filter candidates
    long[] queryHashes = TrigramTokenizer.generateTrigramHashes(normalized);
    if (queryHashes.length == 0) {
      // Query too short for trigrams, fallback to linear scan
      return searchFallback(normalized);
    }

    List<int[]> candidates = new ArrayList<>(queryHashes.length);
    for (long hash : queryHashes) {
      int[] pages = textIndex.get(hash);
      if (pages == null) return List.of(); // No page has this trigram
      candidates.add(pages);
    }

    // Intersection of candidate page lists
    List<Integer> intersected = intersect(candidates);

    // Final verification (exact match)
    List<Integer> results = new ArrayList<>(intersected.size());
    for (int pageIdx : intersected) {
      try (PdfPage p = page(pageIdx)) {
        if (p.extractText().toLowerCase(Locale.ROOT).contains(normalized)) {
          results.add(pageIdx);
        }
      }
    }
    return results;
  }

  private List<Integer> searchFallback(String normalized) {
    List<Integer> results = new ArrayList<>(pageCount());
    int count = pageCount();
    for (int i = 0; i < count; i++) {
      try (PdfPage p = page(i)) {
        if (p.extractText().toLowerCase(Locale.ROOT).contains(normalized)) {
          results.add(i);
        }
      }
    }
    return results;
  }

  private static List<Integer> intersect(List<int[]> lists) {
    if (lists.isEmpty()) return List.of();

    // Sort lists by size to optimize intersection
    lists.sort(Comparator.comparingInt(a -> a.length));

    int[] first = lists.getFirst();
    List<Integer> result = new ArrayList<>(first.length);
    for (int val : first) {
      boolean presentInAll = true;
      for (int i = 1; i < lists.size(); i++) {
        if (Arrays.binarySearch(lists.get(i), val) < 0) {
          presentInAll = false;
          break;
        }
      }
      if (presentInAll) result.add(val);
    }
    return result;
  }

  int probeMetadataUtf16ByteLength() {
    ensureOpen();
    if (pendingMetadata.containsKey(MetadataTag.TITLE)) {
      return wideStringByteLength(pendingMetadata.get(MetadataTag.TITLE));
    }
    try {
      long needed =
          (long)
              DocBindings.fpdfGetMetaText()
                  .invokeExact(
                      handle, metadataKeySegment(MetadataTag.TITLE), MemorySegment.NULL, 0L);
      return needed <= 0 ? 0 : Math.toIntExact(needed);
    } catch (Throwable t) {
      throw new PdfiumException("Failed to inspect metadata length for " + MetadataTag.TITLE, t);
    }
  }

  int readMetadataUtf16(MemorySegment buffer) {
    ensureOpen();
    if (buffer == null || FfmHelper.isNull(buffer)) {
      throw new IllegalArgumentException("buffer must not be null");
    }
    if (pendingMetadata.containsKey(MetadataTag.TITLE)) {
      return writeWideString(buffer, pendingMetadata.get(MetadataTag.TITLE));
    }
    long capacity = buffer.byteSize();
    if (capacity <= 0) {
      return 0;
    }
    try {
      long copied =
          (long)
              DocBindings.fpdfGetMetaText()
                  .invokeExact(handle, metadataKeySegment(MetadataTag.TITLE), buffer, capacity);
      if (copied <= 0) {
        return 0;
      }
      if (copied > capacity) {
        return Math.toIntExact(copied);
      }
      long byteLen = FfmHelper.normalizeWideByteLength(buffer, copied, capacity);
      return Math.toIntExact(byteLen);
    } catch (Throwable t) {
      throw new PdfiumException("Failed to read metadata for " + MetadataTag.TITLE, t);
    }
  }

  /**
   * Functional interface for consuming a memory segment with a specific length without allocating a
   * slice object.
   */
  @FunctionalInterface
  public interface MemorySegmentConsumer {
    /**
     * Consumes the given segment.
     *
     * @param segment the memory segment (may be larger than the actual data)
     * @param length the actual length of the valid data in the segment
     */
    void accept(MemorySegment segment, long length);
  }

  /**
   * Accesses metadata in a zero-allocation manner by yielding a memory segment and its actual
   * length to the consumer. The segment is valid only during the callback execution.
   *
   * @param tag the metadata tag to read
   * @param consumer a consumer that will receive the memory segment and the length of the UTF-16LE
   *     data
   */
  public void withMetadataUtf16(MetadataTag tag, MemorySegmentConsumer consumer) {
    ensureOpen();
    if (consumer == null) {
      throw new IllegalArgumentException("consumer must not be null");
    }

    // Check pending metadata first
    if (pendingMetadata.containsKey(tag)) {
      String pending = pendingMetadata.get(tag);
      if (pending == null || pending.isEmpty()) return;
      try (var _ = ScratchBuffer.acquireScope()) {
        int needed = wideStringByteLength(pending);
        MemorySegment buf = ScratchBuffer.get(needed);
        writeWideString(buf, pending);
        consumer.accept(buf, needed);
      }
      return;
    }

    try (var _ = ScratchBuffer.acquireScope()) {
      long needed =
          (long)
              DocBindings.fpdfGetMetaText()
                  .invokeExact(handle, metadataKeySegment(tag), MemorySegment.NULL, 0L);
      if (needed <= 2) return;

      MemorySegment buf = ScratchBuffer.get(needed);
      long copied =
          (long)
              DocBindings.fpdfGetMetaText()
                  .invokeExact(handle, metadataKeySegment(tag), buf, needed);
      long byteLen = FfmHelper.normalizeWideByteLength(buf, copied, needed);
      if (byteLen > 0) {
        consumer.accept(buf, byteLen);
      }
    } catch (Throwable t) {
      throw new PdfiumException("Failed to read metadata for " + tag, t);
    }
  }

  /**
   * Returns a zero-allocation InputStream for the given metadata tag (UTF-16LE). The stream MUST be
   * closed to release resources.
   */
  public InputStream metadataStream(MetadataTag tag) {
    ensureOpen();
    if (pendingMetadata.containsKey(tag)) {
      String pending = pendingMetadata.get(tag);
      if (pending == null || pending.isEmpty()) {
        return InputStream.nullInputStream();
      }
      byte[] data = new byte[wideStringByteLength(pending)];
      writeWideString(MemorySegment.ofArray(data), pending);
      return new ByteArrayInputStream(data);
    }
    try (var _ = ScratchBuffer.acquireScope()) {
      long needed =
          (long)
              DocBindings.fpdfGetMetaText()
                  .invokeExact(handle, metadataKeySegment(tag), MemorySegment.NULL, 0L);
      if (needed <= 2) return InputStream.nullInputStream();

      MemorySegment buf = ScratchBuffer.get(needed);
      long copied =
          (long)
              DocBindings.fpdfGetMetaText()
                  .invokeExact(handle, metadataKeySegment(tag), buf, needed);
      long byteLen = FfmHelper.normalizeWideByteLength(buf, copied, needed);
      if (byteLen <= 0) return InputStream.nullInputStream();

      byte[] data = buf.asSlice(0, byteLen).toArray(ValueLayout.JAVA_BYTE);
      return new ByteArrayInputStream(data);
    } catch (Throwable t) {
      PdfiumLibrary.ignore(t);
      return InputStream.nullInputStream();
    }
  }

  private Optional<String> metadataFallback(MetadataTag tag) {
    PdfDocumentMetadata.ensureInitialized();
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
    Map<String, String> result =
        PdfDocumentMetadata.readAllInfoDict(sourcePath, sourceBytes, sourceSegment);
    cachedFallbackMeta = result;
    return result;
  }

  /**
   * Parses ALL key-value entries from the PDF Info dictionary in one pass. Avoids per-tag
   * re-mapping and repeated Pattern.compile() calls.
   */
  public Optional<String> metadata(String customKey) {
    // Check known enum-backed tags first
    for (MetadataTag tag : METADATA_TAGS) {
      if (tag.pdfKey().equalsIgnoreCase(customKey)) return metadata(tag);
    }
    // Check pending custom metadata first
    String pending = pendingCustomMetadata.get(customKey);
    if (pending != null) {
      return pending.isEmpty() ? Optional.empty() : Optional.of(pending);
    }
    // Support arbitrary /Info keys (e.g. /Language) via fpdfGetMetaText
    ensureOpen();
    Optional<String> val = tryGetMetaText(customKey);
    if (val.isPresent()) {
      return val;
    }
    PdfDocumentMetadata.ensureInitialized();
    return findMetadataInFallback(customKey);
  }

  private Optional<String> tryGetMetaText(String customKey) {
    try (var _ = ScratchBuffer.acquireScope()) {
      try {
        MemorySegment keyProbe = ScratchBuffer.getUtf8(customKey);
        int needed =
            (int)
                ShimBindings.pdfium4jGetMetaUtf8()
                    .invokeExact(handle, keyProbe, MemorySegment.NULL, 0);
        if (needed <= 1) return Optional.empty();

        MemorySegment buf = ScratchBuffer.get(needed);
        int copied =
            (int) ShimBindings.pdfium4jGetMetaUtf8().invokeExact(handle, keyProbe, buf, needed);
        if (copied <= 1) return Optional.empty();

        String val = buf.reinterpret(copied).getString(0, StandardCharsets.UTF_8);
        return (val == null || val.isEmpty()) ? Optional.empty() : Optional.of(val);
      } catch (Throwable _) {
        return Optional.empty();
      }
    }
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
    PdfDocumentMetadata.ensureInitialized();
    Map<String, String> meta = LinkedHashMap.newLinkedHashMap(METADATA_TAGS.length);
    Map<String, String> all = getOrBuildFallbackMeta();
    for (Map.Entry<String, String> entry : all.entrySet()) {
      String key = entry.getKey();
      String val = entry.getValue();
      // Use uppercase for standard tags if they match
      MetadataTag tag = MetadataTag.fromPdfKey(key);
      if (tag != null) {
        meta.put(tag.name(), val);
      } else {
        meta.put(key, val);
      }
    }
    // Overlay pending changes
    for (Map.Entry<MetadataTag, String> entry : pendingMetadata.entrySet()) {
      meta.put(entry.getKey().name(), entry.getValue());
    }
    for (Map.Entry<String, String> entry : pendingCustomMetadata.entrySet()) {
      meta.put(entry.getKey(), entry.getValue());
    }
    return meta;
  }

  /**
   * Returns high-level structured metadata for the document, combining Info dictionary and XMP
   * data.
   */
  public BookMetadata bookMetadata() {
    ensureOpen();
    XmpMetadata xmp = XmpMetadataParser.parseFrom(this);
    Map<String, String> info = metadata();

    return new PdfBookMetadata(
        metadata(MetadataTag.TITLE).or(xmp::title),
        authors(info, xmp),
        xmp.calibreSeries().or(() -> metadata("Series")),
        xmp.calibreSeriesIndex().stream()
            .mapToObj(d -> (float) d)
            .findFirst()
            .or(
                () ->
                    metadata("SeriesNumber")
                        .flatMap(
                            s -> {
                              try {
                                return Optional.of(Float.parseFloat(s));
                              } catch (Exception _) {
                                return Optional.empty();
                              }
                            })),
        xmp.isbns().stream().findFirst().or(() -> metadata("ISBN")),
        xmp.language().or(() -> metadata(MetadataTag.LANGUAGE)),
        xmp.date()
            .flatMap(
                d -> {
                  try {
                    return Optional.of(LocalDate.parse(d.substring(0, 10)));
                  } catch (Exception _) {
                    return Optional.empty();
                  }
                }),
        xmp.subjects(),
        xmp.description().or(() -> metadata(MetadataTag.SUBJECT)),
        metadata(MetadataTag.PRODUCER).or(xmp::publisher),
        xmp,
        info);
  }

  private List<String> authors(Map<String, String> info, XmpMetadata xmp) {
    if (!xmp.creators().isEmpty()) return xmp.creators();
    String author = info.get(MetadataTag.AUTHOR.name());
    if (author == null || author.isBlank()) return List.of();
    return Arrays.stream(author.split("[,;]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  public String xmpMetadataString() {
    byte[] xmp = xmpMetadata();
    return xmp == null ? "" : new String(xmp, StandardCharsets.UTF_8);
  }

  /**
   * Returns a zero-allocation InputStream for the document's XMP metadata (UTF-8). The stream MUST
   * be closed to release resources.
   */
  public InputStream xmpMetadataStream() {
    ensureOpen();
    Arena arena = Arena.ofShared();
    try {
      MemorySegment data;
      long size;

      if (pendingXmp != null) {
        try (var _ = ScratchBuffer.acquireScope()) {
          switch (pendingXmp) {
            case XmpUpdate.Raw(String xmp) -> {
              MemorySegment temp = ScratchBuffer.getUtf8(xmp);
              size = temp.byteSize() - 1;
              data = arena.allocate(size);
              data.copyFrom(temp.asSlice(0, size));
            }
            case XmpUpdate.Structured(XmpMetadata metadata) -> {
              MemorySegment temp = ScratchBuffer.get(1024);
              SegmentOutputStream sos = new SegmentOutputStream(temp);
              XMP_WRITER.write(metadata, sos);
              size = sos.size();
              data = arena.allocate(size);
              data.copyFrom(sos.segment().asSlice(0, size));
            }
            default -> {
              arena.close();
              return InputStream.nullInputStream();
            }
          }
        }
      } else {
        try (var _ = ScratchBuffer.acquireScope()) {
          int needed =
              (int)
                  ShimBindings.pdfium4jGetXmpMetadata().invokeExact(handle, MemorySegment.NULL, 0);
          if (needed <= 0) {
            arena.close();
            return InputStream.nullInputStream();
          }
          data = arena.allocate(needed);
          int copied =
              (int) ShimBindings.pdfium4jGetXmpMetadata().invokeExact(handle, data, needed);
          size = Math.min(copied, needed);
          if (size <= 0) {
            arena.close();
            return InputStream.nullInputStream();
          }
        }
      }
      return new SegmentInputStream(arena, data.asSlice(0, size));
    } catch (Error e) {
      arena.close();
      throw e;
    } catch (Throwable t) {
      arena.close();
      if (t instanceof PdfiumException pe) throw pe;
      PdfiumLibrary.ignore(t);
      return InputStream.nullInputStream();
    }
  }

  private static final class SegmentInputStream extends InputStream {
    private final Arena arena;
    private final MemorySegment segment;
    private long pos = 0;

    SegmentInputStream(Arena arena, MemorySegment segment) {
      this.arena = arena;
      this.segment = segment;
    }

    @Override
    public int read() throws IOException {
      if (pos >= segment.byteSize()) return -1;
      return segment.get(JAVA_BYTE, pos++) & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      Objects.checkFromIndexSize(off, len, b.length);
      if (len == 0) return 0;
      if (pos >= segment.byteSize()) return -1;
      int toRead = (int) Math.min(len, segment.byteSize() - pos);
      MemorySegment.copy(segment, pos, MemorySegment.ofArray(b), off, toRead);
      pos += toRead;
      return toRead;
    }

    @Override
    public int available() {
      return (int) (segment.byteSize() - pos);
    }

    @Override
    public void close() {
      arena.close();
    }
  }

  public byte[] xmpMetadata() {
    ensureOpen();
    if (pendingXmp != null) {
      if (pendingXmp instanceof XmpUpdate.Raw(String xmp)) {
        return xmp.getBytes(StandardCharsets.UTF_8);
      }
      if (pendingXmp instanceof XmpUpdate.Structured(XmpMetadata metadata)) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        XMP_WRITER.write(metadata, baos);
        return baos.toByteArray();
      }
    }
    try (var _ = ScratchBuffer.acquireScope()) {
      int needed =
          (int) ShimBindings.pdfium4jGetXmpMetadata().invokeExact(handle, MemorySegment.NULL, 0);
      if (needed > 0) {
        MemorySegment buf = ScratchBuffer.get(needed);
        int copied = (int) ShimBindings.pdfium4jGetXmpMetadata().invokeExact(handle, buf, needed);
        if (copied > 0) return buf.asSlice(0, Math.min(copied, needed)).toArray(JAVA_BYTE);
      }
    } catch (Throwable e) {
      PdfiumLibrary.ignore(e);
    }
    // Fallback path: memoize so repeated calls never re-map the file.
    if (cachedFallbackXmp != null) return cachedFallbackXmp.clone();
    byte[] result = PdfDocumentXmp.computeFallbackXmp(sourcePath, sourceBytes);
    cachedFallbackXmp = result;
    return result == null ? null : result.clone();
  }

  public void setMetadata(MetadataTag tag, String value) {
    ensureOpen();
    pendingMetadata.put(tag, value);
    // Keep the in-memory PDFium handle in sync so reads-before-save observe the change.
    writeNativeInfo(tag.pdfKey(), value);
  }

  public void setMetadata(String key, String value) {
    ensureOpen();
    MetadataTag known = MetadataTag.fromPdfKey(key);
    if (known != null) {
      pendingMetadata.put(known, value);
    } else {
      pendingCustomMetadata.put(key, value);
    }
    writeNativeInfo(key, value);
  }

  private void writeNativeInfo(String key, String value) {
    try (var _ = ScratchBuffer.acquireScope()) {
      MemorySegment keySeg = ScratchBuffer.getUtf8(key);
      MemorySegment valSeg = ScratchBuffer.getUtf8(value);
      int rc = (int) ShimBindings.pdfium4jSetMetaUtf8().invokeExact(handle, keySeg, valSeg);
      if (rc == 0 && LOGGER.isLoggable(Level.FINE)) {
        LOGGER.log(Level.FINE, "pdfium4j_set_meta_utf8 returned 0 for {0}", key);
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
    pendingXmp = (xmp == null || xmp.isBlank()) ? null : new XmpUpdate.Raw(xmp);
  }

  public void setXmpMetadata(XmpMetadata metadata) {
    ensureOpen();
    pendingXmp = new XmpUpdate.Structured(metadata);
  }

  public PdfPage insertBlankPage(int index, PageSize size) {
    ensureOpen();
    try {
      MemorySegment p =
          (MemorySegment)
              EditBindings.fpdfPageNew()
                  .invokeExact(handle, index, (double) size.width(), (double) size.height());
      if (FfmHelper.isNull(p)) throwLastError("Failed to insert page");
      markStructurallyModified();

      PdfPage page =
          new PdfPage(
              p.reinterpret(ValueLayout.ADDRESS.byteSize()),
              ownerThread,
              policy.maxRenderPixels(),
              pg -> {
                unregisterPage(pg);
              },
              this::markStructurallyModified);

      pageCache.clear();
      registerPage(page);
      return page;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to insert page", t);
    }
  }

  public void deletePage(int index) {
    ensureOpen();
    if (index < 0 || index >= pageCount())
      throw new IllegalArgumentException("Index out of bounds");
    try {
      DocBindings.fpdfPageDelete().invokeExact(handle, index);
      markStructurallyModified();
    } catch (Throwable t) {
      throw new PdfiumException("Failed to delete page", t);
    }
  }

  public void importPages(PdfDocument src, String range, int index) {
    ensureOpen();
    try {
      MemorySegment r = (range != null) ? docArena.allocateFrom(range) : MemorySegment.NULL;
      int ok = (int) EditBindings.fpdfImportPages().invokeExact(handle, src.handle, r, index);
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

  public void save(Path path) {
    ensureOpen();
    if (path.equals(sourcePath)) {
      saveToSourcePath(path);
    } else {
      saveToNewPath(path);
    }
  }

  private void saveToSourcePath(Path path) {
    Path temp = null;
    boolean detachedSource = false;
    try {
      temp = IoUtils.createTempFile("pdfium4j-save-", ".pdf");
      saveToNewPath(temp);

      if (docSourceChannel != null) {
        docSourceChannel.close();
        docSourceChannel = null;
        RegistryHolder.CHANNELS.remove(channelId);
        state.updateSourceChannel(null);
        detachedSource = true;
      }

      if (sourceSegmentArena != null) {
        sourceSegmentArena.close();
        sourceSegmentArena = null;
        sourceSegment = null;
        state.updateSourceSegmentArena(null);
        detachedSource = true;
      }
      Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
      temp = null;
      if (channelId > 0) {
        docSourceChannel = Files.newByteChannel(path, StandardOpenOption.READ);
        RegistryHolder.CHANNELS.put(channelId, docSourceChannel);
        state.updateSourceChannel(docSourceChannel);
      }
      if (sourcePath != null && sourceSegment == null) {
        sourceSegmentArena = Arena.ofShared();
        sourceSegment = mapSourceSegment(path, sourceSegmentArena);
        state.updateSourceSegmentArena(sourceSegmentArena);
      }
    } catch (IOException e) {
      handleSaveError(path, e, detachedSource);
    } finally {
      cleanupTempFile(temp);
    }
  }

  private void handleSaveError(Path path, IOException e, boolean detachedSource) {
    if (detachedSource && channelId > 0 && docSourceChannel == null) {
      try {
        docSourceChannel = Files.newByteChannel(path, StandardOpenOption.READ);
        RegistryHolder.CHANNELS.put(channelId, docSourceChannel);
        state.updateSourceChannel(docSourceChannel);
      } catch (IOException restoreEx) {
        PdfiumLibrary.ignore(restoreEx);
      }
    }
    throw new PdfiumException("Failed to save to source path: " + path, e);
  }

  private void saveToNewPath(Path path) {
    ensureOpen();
    try {
      byte[] modifiedBytes = null;
      if (structurallyModified) {
        modifiedBytes = saveNativeToBytes();
      }

      PdfSaver.SaveParams params =
          new PdfSaver.SaveParams(
              handle,
              pendingMetadata,
              pendingCustomMetadata,
              nativeMetadataProvider,
              !pendingMetadata.isEmpty() || !pendingCustomMetadata.isEmpty(),
              pendingXmp,
              modifiedBytes != null ? null : sourcePath,
              path,
              modifiedBytes != null ? modifiedBytes : sourceBytes,
              modifiedBytes != null ? MemorySegment.NULL : sourceSegment,
              null);
      PdfSaver.save(params);
      structurallyModified = false;
    } catch (IOException e) {
      throw new PdfiumException("Failed to save to " + path, e);
    }
  }

  public void save(OutputStream out) {
    save(out, null);
  }

  public void save(OutputStream out, Path targetPath) {
    ensureOpen();
    try {
      byte[] modifiedBytes = null;
      if (structurallyModified) {
        modifiedBytes = saveNativeToBytes();
      }

      PdfSaver.SaveParams params =
          new PdfSaver.SaveParams(
              handle,
              pendingMetadata,
              pendingCustomMetadata,
              nativeMetadataProvider,
              !pendingMetadata.isEmpty() || !pendingCustomMetadata.isEmpty(),
              pendingXmp,
              modifiedBytes != null ? null : sourcePath,
              targetPath,
              modifiedBytes != null ? modifiedBytes : sourceBytes,
              modifiedBytes != null ? MemorySegment.NULL : sourceSegment,
              out);
      PdfSaver.save(params);
      structurallyModified = false;
    } catch (IOException e) {
      throw new PdfiumException("Failed to save document", e);
    }
  }

  public byte[] saveToBytes() {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    save(bos);
    return bos.toByteArray();
  }

  private byte[] saveNativeToBytes() throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    PdfDocumentNativeSaver.save(handle, bos);
    return bos.toByteArray();
  }

  public List<PdfAttachment> attachments() {
    ensureOpen();
    return PdfDocumentAttachments.readAttachments(handle);
  }

  public List<PdfSignature> signatures() {
    ensureOpen();
    return PdfDocumentSignatures.readSignatures(handle);
  }

  private static MemorySegment mapSourceSegment(Path path, Arena arena) {
    try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
      return fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size(), arena);
    } catch (IOException e) {
      PdfiumLibrary.ignore(e);
      return null;
    }
  }

  @Override
  public synchronized void close() {
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
    } catch (Exception e) {
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
      err = (int) ViewBindings.fpdfGetLastError().invokeExact();
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

  public static void repair(Path path) {
    if (path == null) {
      throw new IllegalArgumentException("path must not be null");
    }
    repairInPlace(path);
  }

  private static void repairInPlace(Path path) {
    Path temp = null;
    try {
      temp = IoUtils.createTempFile("pdfium4j-repair-", ".pdf");
      try (OutputStream out = Files.newOutputStream(temp)) {
        PdfSaver.SaveParams params =
            new PdfSaver.SaveParams(
                MemorySegment.NULL,
                Map.of(),
                Map.of(),
                _ -> null,
                false,
                null,
                path,
                temp,
                null,
                MemorySegment.NULL,
                out);
        PdfSaver.save(params);
      }
      Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
      temp = null;
    } catch (IOException e) {
      throw new PdfiumException("Failed to repair document: " + path, e);
    } finally {
      cleanupTempFile(temp);
    }
  }

  private static void cleanupTempFile(Path temp) {
    if (temp != null) {
      try {
        Files.deleteIfExists(temp);
      } catch (IOException e) {
        PdfiumLibrary.ignore(e);
      }
    }
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
          (MemorySegment) ViewBindings.fpdfLoadDocument().invokeExact(pathSeg, MemorySegment.NULL);
      if (FfmHelper.isNull(doc)) {
        int err = (int) ViewBindings.fpdfGetLastError().invokeExact();
        if (err == ViewBindings.FPDF_ERR_PASSWORD) return PdfProbeResult.ok(-1, true);
        return PdfProbeResult.error(
            PdfProbeResult.Status.CORRUPT, PdfErrorCode.fromCode(err), "Failed to probe document");
      }
      int count = (int) ViewBindings.fpdfGetPageCount().invokeExact(doc);
      ViewBindings.fpdfCloseDocument().invokeExact(doc);
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
              ViewBindings.fpdfLoadMemDocument().invokeExact(seg, data.length, MemorySegment.NULL);
      if (FfmHelper.isNull(doc)) {
        int err = (int) ViewBindings.fpdfGetLastError().invokeExact();
        if (err == ViewBindings.FPDF_ERR_PASSWORD) return PdfProbeResult.ok(-1, true);
        return PdfProbeResult.error(
            PdfProbeResult.Status.CORRUPT, PdfErrorCode.fromCode(err), "Failed to probe document");
      }
      int count = (int) ViewBindings.fpdfGetPageCount().invokeExact(doc);
      ViewBindings.fpdfCloseDocument().invokeExact(doc);
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

  private static MemorySegment metadataKeySegment(MetadataTag tag) {
    MemorySegment pre = PdfDocumentMetadata.tagSegment(tag);
    if (pre != null) return pre;
    return ScratchBuffer.getUtf8(tag.pdfKey());
  }

  private static int wideStringByteLength(String value) {
    if (value == null || value.isEmpty()) {
      return 0;
    }
    return Math.toIntExact(Math.addExact(Math.multiplyExact(value.length(), 2L), 2L));
  }

  private static int writeWideString(MemorySegment buffer, String value) {
    int needed = wideStringByteLength(value);
    if (needed == 0) {
      return 0;
    }
    if (buffer.byteSize() < needed) {
      return needed;
    }
    long offset = 0;
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      buffer.set(JAVA_BYTE, offset, (byte) (ch & 0xff));
      buffer.set(JAVA_BYTE, offset + 1, (byte) ((ch >>> 8) & 0xff));
      offset += 2;
    }
    buffer.set(JAVA_BYTE, offset, (byte) 0);
    buffer.set(JAVA_BYTE, offset + 1, (byte) 0);
    return needed;
  }
}
