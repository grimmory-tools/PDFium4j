package org.pdfium4j;

import org.pdfium4j.exception.PdfCorruptException;
import org.pdfium4j.exception.PdfPasswordException;
import org.pdfium4j.exception.PdfiumException;
import org.pdfium4j.internal.DocBindings;
import org.pdfium4j.internal.EditBindings;
import org.pdfium4j.internal.FfmHelper;
import org.pdfium4j.internal.ViewBindings;
import org.pdfium4j.model.*;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;

import static java.lang.foreign.ValueLayout.*;

/**
 * Represents an open PDF document backed by native PDFium.
 *
 * <p><strong>Thread safety:</strong> A single {@code PdfDocument} instance (and any
 * {@link PdfPage} handles obtained from it) must be confined to one thread at a time.
 * Multiple independent {@code PdfDocument} instances on separate threads are safe.
 *
 * <pre>{@code
 * try (PdfDocument doc = PdfDocument.open(path)) {
 *     int pages = doc.pageCount();
 *     try (PdfPage page = doc.page(0)) {
 *         RenderResult result = page.render(300);
 *         BufferedImage image = result.toBufferedImage();
 *         String text = page.extractText();
 *     }
 *     List<Bookmark> toc = doc.bookmarks();
 *     Optional<String> title = doc.metadata(MetadataTag.TITLE);
 *     byte[] xmp = doc.xmpMetadata();
 *     doc.save(outputPath);
 * }
 * }</pre>
 */
public final class PdfDocument implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(PdfDocument.class.getName());
    private final MemorySegment handle;
    private final Arena docArena;
    private final byte[] rawBytes;
    private final PdfProcessingPolicy policy;
    private final Thread ownerThread;
    private final Set<PdfPage> openPages;
    private volatile boolean closed = false;
    private boolean metadataDirty = false;
    private byte[] pendingXmpPacket;

    private PdfDocument(MemorySegment handle, Arena docArena, byte[] rawBytes, PdfProcessingPolicy policy, Thread ownerThread) {
        this.handle = handle;
        this.docArena = docArena;
        this.rawBytes = rawBytes;
        this.policy = policy;
        this.ownerThread = ownerThread;
        this.openPages = Collections.newSetFromMap(new IdentityHashMap<>());
    }


    /**
     * Probe a PDF file to determine its validity and basic characteristics
     * without fully opening the document. This is a lightweight check suitable
     * for scanning large numbers of files.
     *
     * <p>The returned {@link PdfProbeResult} indicates whether the file is valid,
     * needs a password, is corrupt, or has other issues, without throwing exceptions.
     *
     * @param path path to the PDF file
     * @return probe result with status, page count, and encryption info
     */
    public static PdfProbeResult probe(Path path) {
        return probe(path, PdfProcessingPolicy.defaultPolicy());
    }

    /**
     * Probe a PDF file with an explicit processing policy.
     */
    public static PdfProbeResult probe(Path path, PdfProcessingPolicy policy) {
        PdfProcessingPolicy resolvedPolicy = resolvePolicy(policy);
        if (path == null) {
            return PdfProbeResult.error(PdfProbeResult.Status.UNREADABLE,
                    PdfErrorCode.FILE, "Path is null");
        }

        long fileSize;
        try {
            fileSize = Files.size(path);
        } catch (IOException e) {
            return PdfProbeResult.error(PdfProbeResult.Status.UNREADABLE,
                    PdfErrorCode.FILE, "Cannot read file: " + e.getMessage());
        }
        if (fileSize > resolvedPolicy.maxDocumentBytes()) {
            return PdfProbeResult.error(PdfProbeResult.Status.UNSUPPORTED,
                    PdfErrorCode.FILE,
                    "Document exceeds policy limit: " + fileSize + " > " + resolvedPolicy.maxDocumentBytes() + " bytes");
        }

        byte[] data;
        try {
            data = Files.readAllBytes(path);
        } catch (IOException e) {
            return PdfProbeResult.error(PdfProbeResult.Status.UNREADABLE,
                    PdfErrorCode.FILE, "Cannot read file: " + e.getMessage());
        }

        return probe(data, resolvedPolicy);
    }

    /**
     * Probe in-memory PDF data for validity and basic characteristics.
     *
     * @param data the PDF file content
     * @return probe result
     */
    public static PdfProbeResult probe(byte[] data) {
        return probe(data, PdfProcessingPolicy.defaultPolicy());
    }

    /**
     * Probe in-memory PDF data for validity and basic characteristics using an explicit policy.
     */
    public static PdfProbeResult probe(byte[] data, PdfProcessingPolicy policy) {
        PdfProcessingPolicy resolvedPolicy = resolvePolicy(policy);
        if (data == null || data.length == 0) {
            return PdfProbeResult.error(PdfProbeResult.Status.UNREADABLE,
                    PdfErrorCode.FILE, "Data is null or empty");
        }
        if ((long) data.length > resolvedPolicy.maxDocumentBytes()) {
            return PdfProbeResult.error(PdfProbeResult.Status.UNSUPPORTED,
                    PdfErrorCode.FILE,
                    "Document exceeds policy limit: " + data.length + " > " + resolvedPolicy.maxDocumentBytes() + " bytes");
        }

        if (data.length < 5 || data[0] != '%' || data[1] != 'P' || data[2] != 'D' || data[3] != 'F') {
            return PdfProbeResult.error(PdfProbeResult.Status.CORRUPT,
                    PdfErrorCode.FORMAT, "Not a PDF file (missing %PDF- header)");
        }

        PdfiumLibrary.ensureInitialized();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dataSeg = arena.allocateFrom(JAVA_BYTE, data);
            MemorySegment doc = (MemorySegment) ViewBindings.FPDF_LoadMemDocument.invokeExact(
                    dataSeg, data.length, MemorySegment.NULL);

            if (FfmHelper.isNull(doc)) {
                int err;
                try {
                    err = (int) (long) ViewBindings.FPDF_GetLastError.invokeExact();
                } catch (Throwable t) {
                    err = 1;
                }

                PdfErrorCode errorCode = PdfErrorCode.fromCode(err);
                return switch (errorCode) {
                    case PASSWORD -> PdfProbeResult.error(PdfProbeResult.Status.PASSWORD_REQUIRED,
                            errorCode, "Password required");
                    case FORMAT -> PdfProbeResult.error(PdfProbeResult.Status.CORRUPT,
                            errorCode, "Invalid or corrupt PDF");
                    case SECURITY -> PdfProbeResult.error(PdfProbeResult.Status.UNSUPPORTED,
                            errorCode, "Unsupported security handler");
                    case FILE -> PdfProbeResult.error(PdfProbeResult.Status.UNREADABLE,
                            errorCode, "File error");
                    default -> PdfProbeResult.error(PdfProbeResult.Status.ERROR,
                            errorCode, errorCode.description());
                };
            }

            try {
                int pageCount = (int) ViewBindings.FPDF_GetPageCount.invokeExact(doc);
                boolean encrypted;
                try {
                    encrypted = (int) DocBindings.FPDF_GetSecurityHandlerRevision.invokeExact(doc) > 0;
                } catch (Throwable t) {
                    encrypted = false;
                }
                return PdfProbeResult.ok(pageCount, encrypted);
            } finally {
                try {
                    ViewBindings.FPDF_CloseDocument.invokeExact(doc);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            return PdfProbeResult.error(PdfProbeResult.Status.ERROR,
                    PdfErrorCode.UNKNOWN, "Probe failed: " + t.getMessage());
        }
    }

    /**
     * Calculate a KOReader-compatible partial MD5 checksum for a PDF file path.
     *
     * <p>This is useful for lightweight file identity in dedupe/progress sync workflows.
     * The checksum is based on sampled windows rather than full-file hashing.</p>
     *
     * @param path path to the PDF file
     * @return partial MD5 checksum, or empty if unreadable
     */
    public static Optional<String> koReaderPartialMd5(Path path) {
        return KoReaderChecksum.calculate(path);
    }

    /**
     * Calculate a KOReader-compatible partial MD5 checksum for in-memory PDF bytes.
     *
     * @param data PDF bytes
     * @return partial MD5 checksum, or empty if input is null
     */
    public static Optional<String> koReaderPartialMd5(byte[] data) {
        return KoReaderChecksum.calculate(data);
    }


    /**
     * Open a PDF from a file path.
     *
     * @throws PdfPasswordException if the PDF is encrypted
     * @throws PdfCorruptException  if the PDF is malformed
     * @throws PdfiumException      on other errors
     */
    public static PdfDocument open(Path path) {
        return open(path, null, PdfProcessingPolicy.defaultPolicy());
    }

    /**
     * Open a password-protected PDF from a file path.
     *
     * @param path     path to the PDF file
     * @param password the password (null for unprotected PDFs)
     */
    public static PdfDocument open(Path path, String password) {
        return open(path, password, PdfProcessingPolicy.defaultPolicy());
    }

    /**
     * Open a password-protected PDF from a file path with an explicit processing policy.
     */
    public static PdfDocument open(Path path, String password, PdfProcessingPolicy policy) {
        PdfProcessingPolicy resolvedPolicy = resolvePolicy(policy);
        if (path == null) throw new IllegalArgumentException("path must not be null");
        PdfiumLibrary.ensureInitialized();

        long fileSize;
        try {
            fileSize = Files.size(path);
        } catch (IOException e) {
            throw new PdfiumException("Failed to stat file: " + path, e);
        }
        if (fileSize > Integer.MAX_VALUE) {
            throw new PdfiumException("Document is too large for in-process loading: " + fileSize + " bytes");
        }
        validateDocumentSize((int) fileSize, resolvedPolicy);
        checkMemoryPressure(fileSize, path);

        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment pathSeg = tempArena.allocateFrom(path.toAbsolutePath().toString());
            MemorySegment pwdSeg = (password != null) ? tempArena.allocateFrom(password) : MemorySegment.NULL;

            MemorySegment doc = (MemorySegment) ViewBindings.FPDF_LoadDocument.invokeExact(pathSeg, pwdSeg);
            if (FfmHelper.isNull(doc)) {
                throwLastError("Failed to open: " + path);
            }
            return new PdfDocument(doc, null, null, resolvedPolicy, Thread.currentThread());
        } catch (PdfiumException e) {
            throw e;
        } catch (Throwable t) {
            throw new PdfiumException("Failed to open: " + path, t);
        }
    }

    /**
     * Open a PDF from an in-memory byte buffer.
     *
     * @param data the complete PDF file content
     */
    public static PdfDocument open(byte[] data) {
        return open(data, null, PdfProcessingPolicy.defaultPolicy());
    }

    /**
     * Open a password-protected PDF from an in-memory byte buffer.
     *
     * @param data     the complete PDF file content
     * @param password the password (null for unprotected PDFs)
     */
    public static PdfDocument open(byte[] data, String password) {
        return open(data, password, PdfProcessingPolicy.defaultPolicy());
    }

    /**
     * Open a password-protected PDF from an in-memory byte buffer with an explicit processing policy.
     */
    public static PdfDocument open(byte[] data, String password, PdfProcessingPolicy policy) {
        PdfProcessingPolicy resolvedPolicy = resolvePolicy(policy);
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("data must not be null or empty");
        }
        return openFromBytes(data, password, "Failed to open PDF from memory", resolvedPolicy);
    }

    private static PdfDocument openFromBytes(byte[] data, String password, String errorContext, PdfProcessingPolicy policy) {
        PdfiumLibrary.ensureInitialized();
        validateDocumentSize(data.length, policy);

        // Arena must outlive the document; PDFium reads from this buffer
        // for the lifetime of the FPDF_DOCUMENT handle.
        Arena docArena = Arena.ofShared();
        try {
            MemorySegment dataSeg = docArena.allocateFrom(JAVA_BYTE, data);

            MemorySegment pwdSeg;
            if (password != null) {
                pwdSeg = docArena.allocateFrom(password);
            } else {
                pwdSeg = MemorySegment.NULL;
            }

            MemorySegment doc = (MemorySegment) ViewBindings.FPDF_LoadMemDocument.invokeExact(
                    dataSeg, data.length, pwdSeg);
            if (FfmHelper.isNull(doc)) {
                docArena.close();
                throwLastError(errorContext);
            }
            return new PdfDocument(doc, docArena, data, policy, Thread.currentThread());
        } catch (PdfiumException e) {
            throw e;
        } catch (Throwable t) {
            docArena.close();
            throw new PdfiumException(errorContext, t);
        }
    }


    /**
     * Get the number of pages in the document.
     */
    public int pageCount() {
        ensureOpen();
        try {
            return (int) ViewBindings.FPDF_GetPageCount.invokeExact(handle);
        } catch (Throwable t) {
            throw new PdfiumException("Failed to get page count", t);
        }
    }

    /**
     * Get page dimensions without fully loading the page.
     *
     * @param pageIndex 0-based page index
     */
    public PageSize pageSize(int pageIndex) {
        ensureOpen();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment wSeg = arena.allocate(JAVA_DOUBLE);
            MemorySegment hSeg = arena.allocate(JAVA_DOUBLE);
            int ok = (int) ViewBindings.FPDF_GetPageSizeByIndex.invokeExact(
                    handle, pageIndex, wSeg, hSeg);
            if (ok == 0) {
                throw new PdfiumException("Failed to get page size for index " + pageIndex);
            }
            return new PageSize(
                    (float) wSeg.get(JAVA_DOUBLE, 0),
                    (float) hSeg.get(JAVA_DOUBLE, 0));
        } catch (PdfiumException e) {
            throw e;
        } catch (Throwable t) {
            throw new PdfiumException("Failed to get page size", t);
        }
    }

    /**
     * Open a page for rendering or inspection.
     *
     * @param index 0-based page index
     * @return the page handle (must be closed after use)
     */
    public PdfPage page(int index) {
        ensureOpen();
        try {
            MemorySegment pageSeg = (MemorySegment) ViewBindings.FPDF_LoadPage.invokeExact(handle, index);
            if (FfmHelper.isNull(pageSeg)) {
                throw new PdfiumException("Failed to load page " + index);
            }
            final PdfPage[] holder = new PdfPage[1];
            PdfPage page = new PdfPage(pageSeg, ownerThread, policy.maxRenderPixels(), () -> unregisterPage(holder[0]));
            holder[0] = page;
            registerPage(page);
            return page;
        } catch (PdfiumException e) {
            throw e;
        } catch (Throwable t) {
            throw new PdfiumException("Failed to load page " + index, t);
        }
    }


    /**
     * Get the dimensions of all pages in a single pass, without loading page handles.
     * This is significantly faster than calling {@link #pageSize(int)} in a loop
     * when you need dimensions for all pages (e.g., for layout calculations).
     *
     * @return list of page sizes, indexed by page number (0-based)
     */
    public List<PageSize> allPageSizes() {
        ensureOpen();
        int count = pageCount();
        List<PageSize> sizes = new ArrayList<>(count);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment wSeg = arena.allocate(JAVA_DOUBLE);
            MemorySegment hSeg = arena.allocate(JAVA_DOUBLE);
            for (int i = 0; i < count; i++) {
                int ok = (int) ViewBindings.FPDF_GetPageSizeByIndex.invokeExact(
                        handle, i, wSeg, hSeg);
                if (ok == 0) {
                    sizes.add(new PageSize(0, 0));
                } else {
                    sizes.add(new PageSize(
                            (float) wSeg.get(JAVA_DOUBLE, 0),
                            (float) hSeg.get(JAVA_DOUBLE, 0)));
                }
            }
        } catch (PdfiumException e) {
            throw e;
        } catch (Throwable t) {
            throw new PdfiumException("Failed to get all page sizes", t);
        }
        return List.copyOf(sizes);
    }

    /**
     * Check if this document appears to be image-only (scanned document) with
     * no extractable text on any page. This is a heuristic check that samples
     * pages for text content.
     *
     * <p>For large documents, only a sample of pages is checked to keep the
     * check fast. The sampling strategy checks the first page, last page,
     * and evenly-spaced pages in between.
     *
     * @return true if no page has extractable text
     */
    public boolean isImageOnly() {
        ensureOpen();
        int count = pageCount();
        if (count == 0) return false;

        List<Integer> pagesToCheck = new ArrayList<>();
        if (count <= 10) {
            for (int i = 0; i < count; i++) pagesToCheck.add(i);
        } else {
            pagesToCheck.add(0);
            for (int i = 1; i <= 8; i++) {
                pagesToCheck.add(i * count / 9);
            }
            pagesToCheck.add(count - 1);
        }

        for (int pageIndex : pagesToCheck) {
            try (PdfPage page = page(pageIndex)) {
                if (page.charCount() > 0) {
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * Render multiple pages as a batch. Pages are rendered sequentially because
     * PDFium page handles are thread-confined.
     *
     * @param pageIndices list of 0-based page indices to render
     * @param dpi render resolution
     * @return map of page index to render result, in iteration order
     */
    public Map<Integer, RenderResult> renderPages(List<Integer> pageIndices, int dpi) {
        ensureOpen();
        if (pageIndices == null || pageIndices.isEmpty()) {
            return Map.of();
        }

        Map<Integer, RenderResult> results = new LinkedHashMap<>(pageIndices.size());
        for (int pageIndex : pageIndices) {
            try (PdfPage page = page(pageIndex)) {
                results.put(pageIndex, page.render(dpi));
            }
        }
        return Map.copyOf(results);
    }

    /**
     * Returns the active processing policy for this document.
     */
    public PdfProcessingPolicy policy() {
        return policy;
    }

    private static PdfProcessingPolicy resolvePolicy(PdfProcessingPolicy policy) {
        return policy != null ? policy : PdfProcessingPolicy.defaultPolicy();
    }

    private static void validateDocumentSize(int documentBytes, PdfProcessingPolicy policy) {
        if ((long) documentBytes > policy.maxDocumentBytes()) {
            throw new PdfiumException(
                    "Document exceeds policy limit: " + (long) documentBytes + " > " + policy.maxDocumentBytes() + " bytes");
        }
    }

    /**
     * Log a warning if loading a PDF that is large relative to available heap.
     * This helps callers detect potential OOM situations before they occur.
     */
    private static void checkMemoryPressure(long fileSize, Path path) {
        Runtime rt = Runtime.getRuntime();
        long freeMemory = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
        if (fileSize > freeMemory / 2) {
            LOG.log(System.Logger.Level.WARNING,
                    "Loading large PDF ({0} MB) with limited free heap ({1} MB available): {2}",
                    fileSize / (1024 * 1024),
                    freeMemory / (1024 * 1024),
                    path);
        }
    }

    /**
     * Render a contiguous range of pages as a batch.
     *
     * @param startIndex inclusive start index (0-based)
     * @param endIndex inclusive end index (0-based)
     * @param dpi render resolution
     * @return map of page index to render result, in iteration order
     */
    public Map<Integer, RenderResult> renderPages(int startIndex, int endIndex, int dpi) {
        if (startIndex < 0 || endIndex >= pageCount() || startIndex > endIndex) {
            throw new IllegalArgumentException(
                    "Invalid range: [" + startIndex + ", " + endIndex + "] for document with " + pageCount() + " pages");
        }

        List<Integer> indices = IntStream.rangeClosed(startIndex, endIndex)
                .boxed()
                .toList();
        return renderPages(indices, dpi);
    }

    /**
     * Render all pages as a batch.
     * Warning: this can consume significant memory for large documents.
     *
     * @param dpi render resolution
     * @return map of page index to render result, in iteration order
     */
    public Map<Integer, RenderResult> renderAllPages(int dpi) {
        List<Integer> indices = IntStream.range(0, pageCount())
                .boxed()
                .toList();
        return renderPages(indices, dpi);
    }

    /**
     * Get the PDF file version number.
     *
     * @return version number (e.g., 14 for PDF 1.4, 17 for PDF 1.7, 20 for PDF 2.0),
     *         or 0 if the version cannot be determined
     */
    public int fileVersion() {
        ensureOpen();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment versionSeg = arena.allocate(JAVA_INT);
            int ok = (int) DocBindings.FPDF_GetFileVersion.invokeExact(handle, versionSeg);
            if (ok != 0) {
                return versionSeg.get(JAVA_INT, 0);
            }
            return 0;
        } catch (Throwable t) {
            return 0;
        }
    }


    /**
     * Delete a page from the document. The page is removed immediately;
     * save the document to persist the change.
     *
     * <p>After deletion, page indices shift down: if you delete page 2 from
     * a 5-page document, the former page 3 becomes page 2.
     *
     * @param pageIndex 0-based index of the page to delete
     * @throws IllegalArgumentException if pageIndex is out of range
     */
    public void deletePage(int pageIndex) {
        ensureOpen();
        int count = pageCount();
        if (pageIndex < 0 || pageIndex >= count) {
            throw new IllegalArgumentException(
                    "Page index " + pageIndex + " out of range [0, " + (count - 1) + "]");
        }
        try {
            EditBindings.FPDFPage_Delete.invokeExact(handle, pageIndex);
        } catch (Throwable t) {
            throw new PdfiumException("Failed to delete page " + pageIndex, t);
        }
    }

    /**
     * Insert a new blank page at the given index with the specified dimensions.
     *
     * @param pageIndex 0-based index where the new page will be inserted
     * @param size      the page dimensions in points
     * @throws PdfiumException if page creation fails
     */
    public void insertBlankPage(int pageIndex, PageSize size) {
        ensureOpen();
        MemorySegment pageSeg = MemorySegment.NULL;
        try {
            pageSeg = (MemorySegment) EditBindings.FPDFPage_New.invokeExact(
                    handle, pageIndex, (double) size.width(), (double) size.height());
            if (FfmHelper.isNull(pageSeg)) {
                throw new PdfiumException("FPDFPage_New failed for index " + pageIndex);
            }
            int ok = (int) EditBindings.FPDFPage_GenerateContent.invokeExact(pageSeg);
            if (ok == 0) {
                throw new PdfiumException("FPDFPage_GenerateContent failed for index " + pageIndex);
            }
        } catch (PdfiumException e) {
            throw e;
        } catch (Throwable t) {
            throw new PdfiumException("Failed to insert blank page at " + pageIndex, t);
        } finally {
            if (!FfmHelper.isNull(pageSeg)) {
                try {
                    ViewBindings.FPDF_ClosePage.invokeExact(pageSeg);
                } catch (Throwable ignored) {}
            }
        }
    }


    /**
     * Import pages from another open document into this document.
     * This enables PDF merging: open multiple documents, import pages from each
     * into a target document, then save.
     *
     * <p>The source document must remain open during this operation.
     * After importing, save this document to persist the changes.
     *
     * @param source      the source document to import pages from
     * @param pageRange   comma-separated page ranges (1-based), e.g., "1,3,5-7",
     *                    or null to import all pages
     * @param insertIndex 0-based position in this document where pages will be inserted
     * @throws PdfiumException if the import fails
     */
    public void importPages(PdfDocument source, String pageRange, int insertIndex) {
        ensureOpen();
        if (source == null) throw new IllegalArgumentException("source must not be null");
        source.ensureOpen();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rangeSeg = (pageRange != null)
                    ? arena.allocateFrom(pageRange)
                    : MemorySegment.NULL;

            int ok = (int) EditBindings.FPDF_ImportPages.invokeExact(
                    handle, source.handle, rangeSeg, insertIndex);
            if (ok == 0) {
                throw new PdfiumException("FPDF_ImportPages failed for range: " + pageRange);
            }
        } catch (PdfiumException e) {
            throw e;
        } catch (Throwable t) {
            throw new PdfiumException("Failed to import pages", t);
        }
    }

    /**
     * Import all pages from another document, appending them at the end.
     *
     * @param source the source document
     */
    public void importAllPages(PdfDocument source) {
        importPages(source, null, pageCount());
    }


    /**
     * Perform a health check on a PDF file, returning a diagnostic report
     * with information about the file's validity, features, and any issues.
     *
     * <p>This is more thorough than {@link #probe(Path)} as it opens the document
     * and inspects its content. It never throws exceptions for content issues.
     *
     * @param path path to the PDF file
     * @return diagnostic report
     */
    public static PdfDiagnostic diagnose(Path path) {
        String filePath = path != null ? path.toString() : null;
        List<String> warnings = new ArrayList<>();

        PdfProbeResult probeResult = probe(path);
        if (!probeResult.isValid()) {
            return new PdfDiagnostic(filePath, false, probeResult.pageCount(),
                    probeResult.encrypted(), false, 0,
                    List.of(probeResult.errorMessage() != null ? probeResult.errorMessage() : "Unknown error"));
        }

        try (PdfDocument doc = PdfDocument.open(path)) {
            int pageCount = doc.pageCount();
            boolean encrypted = doc.isEncrypted();
            int fileVersion = doc.fileVersion();

            boolean hasText = !doc.isImageOnly();

            try {
                List<PageSize> sizes = doc.allPageSizes();
                for (int i = 0; i < sizes.size(); i++) {
                    PageSize s = sizes.get(i);
                    if (s.width() <= 0 || s.height() <= 0) {
                        warnings.add("Page " + i + " has zero or negative dimensions");
                    } else if (s.width() > 14400 || s.height() > 14400) {
                        // >200 inches - likely corrupted or engineering drawing
                        warnings.add("Page " + i + " has unusually large dimensions: "
                                + s.width() + "x" + s.height() + " points");
                    }
                }
            } catch (Exception e) {
                warnings.add("Failed to read page dimensions: " + e.getMessage());
            }

            return new PdfDiagnostic(filePath, true, pageCount, encrypted,
                    hasText, fileVersion, List.copyOf(warnings));
        } catch (Exception e) {
            warnings.add("Failed to fully diagnose: " + e.getMessage());
            return new PdfDiagnostic(filePath, false, -1, false, false, 0,
                    List.copyOf(warnings));
        }
    }


    /**
     * Get a metadata value by tag.
     *
     * @param tag the metadata tag (e.g. {@link MetadataTag#TITLE})
     * @return the value, or empty if not present
     */
    public Optional<String> metadata(MetadataTag tag) {
        ensureOpen();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment tagSeg = arena.allocateFrom(tag.pdfKey());

            long needed = (long) DocBindings.FPDF_GetMetaText.invokeExact(
                    handle, tagSeg, MemorySegment.NULL, 0L);
            if (needed <= 2) return Optional.empty();

            MemorySegment buf = arena.allocate(needed);
            long _ = (long) DocBindings.FPDF_GetMetaText.invokeExact(handle, tagSeg, buf, needed);

            String value = FfmHelper.fromWideString(buf, needed);
            return value.isEmpty() ? Optional.empty() : Optional.of(value);
        } catch (Throwable t) {
            throw new PdfiumException("Failed to read metadata: " + tag.pdfKey(), t);
        }
    }

    /**
     * Get all standard metadata as a map. Only non-empty values are included.
     */
    public Map<String, String> metadata() {
        Map<String, String> map = new LinkedHashMap<>();
        for (MetadataTag tag : MetadataTag.values()) {
            metadata(tag).ifPresent(v -> map.put(tag.pdfKey(), v));
        }
        return map;
    }

    /**
     * Set a single metadata tag value (e.g. Title, Author).
     * The change is applied to the in-memory document; call {@link #save} to persist.
     *
     * @param tag   the metadata tag to set
     * @param value the value to set (empty string clears the tag)
     * @throws PdfiumException if the native call fails
     */
    public void setMetadata(MetadataTag tag, String value) {
        ensureOpen();
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(value, "value");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment tagSeg = arena.allocateFrom(tag.pdfKey());
            MemorySegment valueSeg = FfmHelper.toWideString(arena, value);
            int result = (int) EditBindings.EPDF_SetMetaText.invokeExact(handle, tagSeg, valueSeg);
            if (result == 0) {
                throw new PdfiumException("Failed to set metadata: " + tag.pdfKey());
            }
            metadataDirty = true;
        } catch (PdfiumException e) {
            throw e;
        } catch (Throwable t) {
            throw new PdfiumException("Failed to set metadata: " + tag.pdfKey(), t);
        }
    }

    /**
     * Set multiple metadata tags at once.
     * The changes are applied to the in-memory document; call {@link #save} to persist.
     *
     * @param metadata map of tags to values
     * @throws PdfiumException if any native call fails
     */
    public void setMetadata(Map<MetadataTag, String> metadata) {
        Objects.requireNonNull(metadata, "metadata");
        for (Map.Entry<MetadataTag, String> entry : metadata.entrySet()) {
            setMetadata(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Set XMP metadata that will be embedded in the PDF on the next save.
     * Uses the provided {@link XmpMetadataWriter} to serialize the metadata.
     *
     * @param metadata the metadata to embed
     * @param writer   the writer (may have custom namespaces registered)
     */
    public void setXmpMetadata(XmpMetadata metadata, XmpMetadataWriter writer) {
        ensureOpen();
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(writer, "writer");
        this.pendingXmpPacket = writer.writeBytes(metadata);
    }

    /**
     * Set XMP metadata from a pre-built XMP packet string.
     * The string should be a complete XMP packet including xpacket processing instructions.
     *
     * @param xmpPacket the raw XMP XML packet
     */
    public void setXmpMetadata(String xmpPacket) {
        ensureOpen();
        Objects.requireNonNull(xmpPacket, "xmpPacket");
        this.pendingXmpPacket = xmpPacket.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Get document permissions bitmask.
     * See PDF Reference Table 3.20 for bit definitions.
     */
    public int permissions() {
        ensureOpen();
        try {
            return (int) DocBindings.FPDF_GetDocPermissions.invokeExact(handle);
        } catch (Throwable t) {
            throw new PdfiumException("Failed to get permissions", t);
        }
    }

    /**
     * Check if the document is encrypted.
     */
    public boolean isEncrypted() {
        ensureOpen();
        try {
            return (int) DocBindings.FPDF_GetSecurityHandlerRevision.invokeExact(handle) > 0;
        } catch (Throwable t) {
            return false;
        }
    }


    /**
     * Get the logical page label for a given page index.
     * Page labels are defined in the page label dictionary and may be
     * roman numerals (i, ii, iii), letters (A, B), or custom strings.
     *
     * @param pageIndex 0-based page index
     * @return the page label, or empty if no label is defined
     */
    public Optional<String> pageLabel(int pageIndex) {
        ensureOpen();
        try (Arena arena = Arena.ofConfined()) {
            long needed = (long) DocBindings.FPDF_GetPageLabel.invokeExact(
                    handle, pageIndex, MemorySegment.NULL, 0L);
            if (needed <= 2) return Optional.empty();

            MemorySegment buf = arena.allocate(needed);
            long _ = (long) DocBindings.FPDF_GetPageLabel.invokeExact(handle, pageIndex, buf, needed);

            String label = FfmHelper.fromWideString(buf, needed);
            return label.isEmpty() ? Optional.empty() : Optional.of(label);
        } catch (Throwable t) {
            throw new PdfiumException("Failed to get page label for index " + pageIndex, t);
        }
    }


    /**
     * Extract the raw XMP metadata packet from the PDF as bytes.
     *
     * <p>XMP metadata is embedded as an XML packet in the PDF, typically
     * delimited by {@code <?xpacket begin=...?>} and {@code <?xpacket end=...?>}.
     * This method scans the raw PDF bytes to find and extract this packet.
     *
     * <p>The returned bytes can be parsed as XML using standard Java XML APIs
     * (e.g., XPath with namespace-aware DocumentBuilder) to extract Dublin Core,
     * Calibre, Booklore, or other custom namespace metadata.
     *
     * @return the raw XMP XML bytes, or empty array if no XMP packet found
     */
    public byte[] xmpMetadata() {
        ensureOpen();
        if (rawBytes != null) {
            return extractXmpPacket(rawBytes);
        }
        // Fallback for path-based documents where we avoid retaining a second raw copy.
        return extractXmpPacket(saveToBytes());
    }

    /**
     * Extract the raw XMP metadata packet as a String.
     *
     * @return the XMP XML string, or empty if no XMP packet found
     */
    public String xmpMetadataString() {
        byte[] xmp = xmpMetadata();
        if (xmp.length == 0) return "";
        return new String(xmp, StandardCharsets.UTF_8);
    }

    private static final byte[] XMP_BEGIN_MARKER = "<?xpacket begin=".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] XMP_END_MARKER = "<?xpacket end=".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] XMP_PI_CLOSE = "?>".getBytes(StandardCharsets.US_ASCII);

    private static byte[] extractXmpPacket(byte[] pdf) {
        int beginPos = indexOf(pdf, XMP_BEGIN_MARKER, 0);
        if (beginPos < 0) return new byte[0];

        int endPos = indexOf(pdf, XMP_END_MARKER, beginPos);
        if (endPos < 0) return new byte[0];

        int endTagClose = indexOf(pdf, XMP_PI_CLOSE, endPos);
        if (endTagClose < 0) return new byte[0];
        int packetEnd = endTagClose + 2;

        byte[] xmp = new byte[packetEnd - beginPos];
        System.arraycopy(pdf, beginPos, xmp, 0, xmp.length);
        return xmp;
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

    private static byte[] replaceXmpPacket(byte[] pdf, byte[] newPacket) {
        int beginPos = indexOf(pdf, XMP_BEGIN_MARKER, 0);
        if (beginPos < 0) {
            // No existing XMP packet - cannot inject without rewriting cross-reference table
            return pdf;
        }

        int endPos = indexOf(pdf, XMP_END_MARKER, beginPos);
        if (endPos < 0) return pdf;

        int endTagClose = indexOf(pdf, XMP_PI_CLOSE, endPos);
        if (endTagClose < 0) return pdf;
        int packetEnd = endTagClose + 2;

        int oldLen = packetEnd - beginPos;
        if (newPacket.length <= oldLen) {
            // Pad the new packet to exactly match old length (in-place replacement preserves xref offsets)
            byte[] padded = new byte[oldLen];
            System.arraycopy(newPacket, 0, padded, 0, newPacket.length);
            // Fill remainder with spaces (safe XMP padding)
            Arrays.fill(padded, newPacket.length, oldLen, (byte) ' ');
            System.arraycopy(padded, 0, pdf, beginPos, oldLen);
            return pdf;
        }

        // New packet is larger - splice into the byte array
        byte[] result = new byte[pdf.length - oldLen + newPacket.length];
        System.arraycopy(pdf, 0, result, 0, beginPos);
        System.arraycopy(newPacket, 0, result, beginPos, newPacket.length);
        System.arraycopy(pdf, packetEnd, result, beginPos + newPacket.length, pdf.length - packetEnd);
        return result;
    }


    /**
     * Get the full bookmark (outline) tree for the document.
     *
     * @return root-level bookmarks (each may have children), or empty list if none
     */
    public List<Bookmark> bookmarks() {
        ensureOpen();
        return BookmarkReader.readBookmarks(handle);
    }


    /**
     * Save the document to a file.
     *
     * <p>This saves the current state of the document, including any
     * modifications made via the API (e.g., page rotation changes).
     *
     * @param path output file path
     */
    public void save(Path path) {
        byte[] bytes = saveToBytes();
        try {
            Files.write(path, bytes);
        } catch (IOException e) {
            throw new PdfiumException("Failed to write PDF to " + path, e);
        }
    }

    /**
     * Save the document to a byte array.
     *
     * @return the complete PDF file content
     */
    public byte[] saveToBytes() {
        ensureOpen();
        byte[] result = PdfSaver.saveToBytes(handle, metadataDirty);
        if (pendingXmpPacket != null) {
            result = replaceXmpPacket(result, pendingXmpPacket);
        }
        return result;
    }

    /**
     * Save the document directly to an OutputStream, suitable for streaming
     * responses (e.g., HTTP responses) without intermediate byte arrays.
     *
     * @param out the output stream to write to
     * @throws PdfiumException if saving fails
     */
    public void save(OutputStream out) {
        byte[] bytes = saveToBytes();
        try {
            out.write(bytes);
        } catch (IOException e) {
            throw new PdfiumException("Failed to write PDF to output stream", e);
        }
    }


    /**
     * Returns the raw FPDF_DOCUMENT MemorySegment for direct PDFium FFM calls.
     */
    public MemorySegment rawHandle() {
        ensureOpen();
        return handle;
    }


    private void ensureOpen() {
        ensureThreadConfinement();
        if (closed) throw new IllegalStateException("PdfDocument is already closed");
    }

    private void ensureThreadConfinement() {
        Thread current = Thread.currentThread();
        if (current != ownerThread) {
            throw new IllegalStateException(
                    "PdfDocument must be accessed from its owner thread. owner="
                            + ownerThread.getName() + ", current=" + current.getName());
        }
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

    @Override
    public void close() {
        ensureThreadConfinement();
        if (closed) return;
        closed = true;

        List<PdfPage> pagesToClose;
        synchronized (openPages) {
            pagesToClose = new ArrayList<>(openPages);
        }
        for (PdfPage page : pagesToClose) {
            try {
                page.closeFromDocument();
            } catch (Throwable ignored) {
                // Continue closing other pages and document resources.
            }
        }

        try {
            ViewBindings.FPDF_CloseDocument.invokeExact(handle);
        } catch (Throwable ignored) {}
        if (docArena != null) {
            try {
                docArena.close();
            } catch (IllegalStateException ignored) {
                // Arena.ofAuto() doesn't support explicit close, which is fine
            }
        }
    }


    private static void throwLastError(String context) {
        int err;
        try {
            err = (int) (long) ViewBindings.FPDF_GetLastError.invokeExact();
        } catch (Throwable t) {
            throw new PdfiumException(context);
        }

        throw switch (err) {
            case ViewBindings.FPDF_ERR_PASSWORD ->
                    new PdfPasswordException(context + " - password required or incorrect");
            case ViewBindings.FPDF_ERR_FORMAT ->
                    new PdfCorruptException(context + " - invalid or corrupt PDF");
            case ViewBindings.FPDF_ERR_FILE ->
                    new PdfiumException(context + " - file not found or cannot be opened");
            case ViewBindings.FPDF_ERR_SECURITY ->
                    new PdfiumException(context + " - unsupported security handler");
            default -> new PdfiumException(context + " - error code " + err);
        };
    }
}
