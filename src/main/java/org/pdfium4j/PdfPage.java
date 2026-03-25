package org.pdfium4j;

import org.pdfium4j.exception.PdfiumException;
import org.pdfium4j.internal.AnnotBindings;
import org.pdfium4j.internal.BitmapBindings;
import org.pdfium4j.internal.EditBindings;
import org.pdfium4j.internal.FfmHelper;
import org.pdfium4j.internal.TextBindings;
import org.pdfium4j.internal.ViewBindings;
import org.pdfium4j.model.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;

import java.util.List;
import java.util.Optional;

/**
 * Represents an open page within a {@link PdfDocument}.
 *
 * <p><strong>Thread safety:</strong> Confined to the thread that opened it.
 * Must be closed after use to release native resources.
 *
 * <pre>{@code
 * try (var page = doc.page(0)) {
 *     PageSize size = page.size();
 *     RenderResult result = page.render(300);
 *     BufferedImage image = result.toBufferedImage();
 *     String text = page.extractText();
 *     int rotation = page.rotation();
 * }
 * }</pre>
 */
public final class PdfPage implements AutoCloseable {

    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    private static final int BYTES_PER_PIXEL = 4; // RGBA
    private static final int UNICODE_BOM = 0xFFFE;
    private static final int UNICODE_INVALID = 0xFFFF;

    private final MemorySegment handle;
    private final Thread ownerThread;
    private final long maxRenderPixels;
    private final Runnable onClose;
    private volatile boolean closed = false;

    PdfPage(MemorySegment handle, Thread ownerThread, long maxRenderPixels, Runnable onClose) {
        this.handle = handle;
        this.ownerThread = ownerThread;
        this.maxRenderPixels = maxRenderPixels;
        this.onClose = onClose;
    }

    /**
     * Get the page dimensions in points (1 pt = 1/72 inch).
     */
    public PageSize size() {
        ensureOpen();
        try {
            float w = (float) ViewBindings.FPDF_GetPageWidthF.invokeExact(handle);
            float h = (float) ViewBindings.FPDF_GetPageHeightF.invokeExact(handle);
            return new PageSize(w, h);
        } catch (Throwable t) {
            throw new PdfiumException("Failed to get page size", t);
        }
    }

    /**
     * Render this page at the given DPI with default flags (annotations, anti-aliasing).
     *
     * @param dpi render resolution (e.g. 150 for thumbnails, 300 for high quality)
     * @return rendered pixel data
     */
    public RenderResult render(int dpi) {
        return render(dpi, RenderFlags.DEFAULT);
    }

    /**
     * Render this page at the given DPI with custom flags.
     *
     * @param dpi   render resolution
     * @param flags rendering flags controlling quality and features
     * @return rendered pixel data
     */
    public RenderResult render(int dpi, RenderFlags flags) {
        return render(dpi, flags, OPAQUE_WHITE);
    }

    /**
     * Render this page at the given DPI with custom flags and background color.
     *
     * @param dpi        render resolution
     * @param flags      rendering flags
     * @param background background color as 0xAARRGGBB
     * @return rendered pixel data
     */
    public RenderResult render(int dpi, RenderFlags flags, int background) {
        ensureOpen();

        PageSize size = size();
        int w = size.widthPixels(dpi);
        int h = size.heightPixels(dpi);

        if (w <= 0 || h <= 0) {
            return new RenderResult(1, 1, new byte[4]);
        }

        return renderAtSize(w, h, flags, background);
    }

    /**
     * Extract all text content from this page.
     *
     * @return the page text, or empty string if no text content
     */
    public String extractText() {
        return withTextPage("", "Failed to extract text", textPage -> {
            int charCount = (int) TextBindings.FPDFText_CountChars.invokeExact(textPage);
            if (charCount <= 0) {
                return "";
            }

            try (Arena arena = Arena.ofConfined()) {
                long bufSize = ((long) charCount + 1) * 2;
                MemorySegment buf = arena.allocate(bufSize);

                int written = (int) TextBindings.FPDFText_GetText.invokeExact(
                        textPage, 0, charCount, buf);
                if (written <= 0) {
                    return "";
                }

                return FfmHelper.fromWideString(buf, (long) written * 2);
            }
        });
    }

    /**
     * Get the number of characters on this page.
     *
     * @return character count, or 0 if no text
     */
    public int charCount() {
        return withTextPage(0, "Failed to count characters", textPage -> {
            int count = (int) TextBindings.FPDFText_CountChars.invokeExact(textPage);
            return Math.max(0, count);
        });
    }

    /**
     * Get the page rotation in degrees.
     *
     * @return rotation in degrees: 0, 90, 180, or 270
     */
    public int rotation() {
        ensureOpen();
        try {
            int rot = (int) EditBindings.FPDFPage_GetRotation.invokeExact(handle);
            return switch (rot) {
                case 1 -> 90;
                case 2 -> 180;
                case 3 -> 270;
                default -> 0;
            };
        } catch (Throwable t) {
            throw new PdfiumException("Failed to get page rotation", t);
        }
    }

    /**
     * Set the page rotation. The document must be saved for the change to persist.
     *
     * @param degrees rotation in degrees: 0, 90, 180, or 270
     * @throws IllegalArgumentException if degrees is not 0, 90, 180, or 270
     */
    public void setRotation(int degrees) {
        ensureOpen();
        int rot = switch (degrees) {
            case 0 -> 0;
            case 90 -> 1;
            case 180 -> 2;
            case 270 -> 3;
            default -> throw new IllegalArgumentException(
                    "Rotation must be 0, 90, 180, or 270 degrees, got: " + degrees);
        };
        try {
            EditBindings.FPDFPage_SetRotation.invokeExact(handle, rot);
        } catch (Throwable t) {
            throw new PdfiumException("Failed to set page rotation", t);
        }
    }

    /**
     * Render this page at the given DPI, but constrained so the output fits within
     * the specified pixel bounds. Useful for thumbnails and previews where you want
     * to limit memory usage regardless of the page's physical dimensions.
     *
     * <p>The page aspect ratio is always preserved. The actual output dimensions
     * will be at most {@code maxWidth × maxHeight}, but may be smaller.
     *
     * @param dpi       base render resolution
     * @param maxWidth  maximum output width in pixels
     * @param maxHeight maximum output height in pixels
     * @return rendered pixel data fitting within the bounds
     */
    public RenderResult renderBounded(int dpi, int maxWidth, int maxHeight) {
        return renderBounded(dpi, maxWidth, maxHeight, RenderFlags.DEFAULT);
    }

    /**
     * Render this page at the given DPI, constrained to fit within the specified
     * pixel bounds, with custom render flags.
     *
     * @param dpi       base render resolution
     * @param maxWidth  maximum output width in pixels
     * @param maxHeight maximum output height in pixels
     * @param flags     rendering flags
     * @return rendered pixel data fitting within the bounds
     */
    public RenderResult renderBounded(int dpi, int maxWidth, int maxHeight, RenderFlags flags) {
        ensureOpen();
        if (maxWidth <= 0 || maxHeight <= 0) {
            throw new IllegalArgumentException("maxWidth and maxHeight must be positive");
        }

        PageSize size = size();
        int naturalW = size.widthPixels(dpi);
        int naturalH = size.heightPixels(dpi);

        if (naturalW <= 0 || naturalH <= 0) {
            return new RenderResult(1, 1, new byte[4]);
        }

        int w = naturalW;
        int h = naturalH;
        if (w > maxWidth || h > maxHeight) {
            double scaleW = (double) maxWidth / w;
            double scaleH = (double) maxHeight / h;
            double scale = Math.min(scaleW, scaleH);
            w = Math.max(1, (int) Math.round(w * scale));
            h = Math.max(1, (int) Math.round(h * scale));
        }

        return renderAtSize(w, h, flags, OPAQUE_WHITE);
    }

    /**
     * Render a thumbnail of this page, fitting within a square of the given dimension.
     * Uses fast rendering settings optimized for small output.
     *
     * @param maxDimension maximum width or height in pixels
     * @return rendered thumbnail
     */
    public RenderResult renderThumbnail(int maxDimension) {
        if (maxDimension <= 0) {
            throw new IllegalArgumentException("maxDimension must be positive");
        }

        // Use 150 DPI as base for thumbnails - enough quality for small sizes
        RenderFlags thumbnailFlags = RenderFlags.builder()
                .annotations(false)
                .antiAlias(true)
                .build();

        return renderBounded(150, maxDimension, maxDimension, thumbnailFlags);
    }

    /**
     * Render this page at an exact pixel size (bypassing DPI calculation).
     */
    private RenderResult renderAtSize(int w, int h, RenderFlags flags, int background) {
        ensureOpen();
        ensureRenderBudget(w, h);

        MemorySegment bitmap = MemorySegment.NULL;
        try {
            bitmap = (MemorySegment) BitmapBindings.FPDFBitmap_Create.invokeExact(w, h, 1);
            if (FfmHelper.isNull(bitmap)) {
                throw new PdfiumException("FPDFBitmap_Create failed for " + w + "x" + h);
            }

            BitmapBindings.FPDFBitmap_FillRect.invokeExact(
                    bitmap, 0, 0, w, h, (long) (background & 0xFFFFFFFFL));

            ViewBindings.FPDF_RenderPageBitmap.invokeExact(
                    bitmap, handle, 0, 0, w, h, 0, flags.value());

            MemorySegment buffer = (MemorySegment) BitmapBindings.FPDFBitmap_GetBuffer.invokeExact(bitmap);
            int stride = (int) BitmapBindings.FPDFBitmap_GetStride.invokeExact(bitmap);

            byte[] rgba = buffer.reinterpret((long) stride * h).toArray(ValueLayout.JAVA_BYTE);

            if (stride != w * BYTES_PER_PIXEL) {
                byte[] packed = new byte[w * h * BYTES_PER_PIXEL];
                for (int row = 0; row < h; row++) {
                    System.arraycopy(rgba, row * stride, packed, row * w * BYTES_PER_PIXEL, w * BYTES_PER_PIXEL);
                }
                rgba = packed;
            }

            return new RenderResult(w, h, rgba);
        } catch (PdfiumException e) {
            throw e;
        } catch (Throwable t) {
            throw new PdfiumException("Failed to render page at " + w + "x" + h, t);
        } finally {
            if (!FfmHelper.isNull(bitmap)) {
                try {
                    BitmapBindings.FPDFBitmap_Destroy.invokeExact(bitmap);
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Extract text content with character-level bounding boxes and font sizes.
     * Each character is returned as a {@link TextCharInfo} record containing its
     * Unicode code point, bounding box in page coordinates, and font size.
     *
     * <p>This is useful for search hit highlighting, text reflow, and
     * determining whether a page contains extractable text.
     *
     * @return list of character info records, or empty list if no text
     */
    public List<TextCharInfo> extractTextWithBounds() {
        return withTextPage(List.of(), "Failed to extract text with bounds", textPage -> {
            int charCount = (int) TextBindings.FPDFText_CountChars.invokeExact(textPage);
            if (charCount <= 0) {
                return List.of();
            }

            List<TextCharInfo> result = new ArrayList<>(charCount);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment leftSeg = arena.allocate(ValueLayout.JAVA_DOUBLE);
                MemorySegment rightSeg = arena.allocate(ValueLayout.JAVA_DOUBLE);
                MemorySegment bottomSeg = arena.allocate(ValueLayout.JAVA_DOUBLE);
                MemorySegment topSeg = arena.allocate(ValueLayout.JAVA_DOUBLE);

                for (int i = 0; i < charCount; i++) {
                    int charCode = (int) TextBindings.FPDFText_GetUnicode.invokeExact(textPage, i);
                    if (charCode <= 0 || charCode == UNICODE_BOM || charCode == UNICODE_INVALID) {
                        continue;
                    }

                    int ok = (int) TextBindings.FPDFText_GetCharBox.invokeExact(
                            textPage, i, leftSeg, rightSeg, bottomSeg, topSeg);

                    double left = 0, right = 0, bottom = 0, top = 0;
                    if (ok != 0) {
                        left = leftSeg.get(ValueLayout.JAVA_DOUBLE, 0);
                        right = rightSeg.get(ValueLayout.JAVA_DOUBLE, 0);
                        bottom = bottomSeg.get(ValueLayout.JAVA_DOUBLE, 0);
                        top = topSeg.get(ValueLayout.JAVA_DOUBLE, 0);
                    }

                    double fontSize = (double) TextBindings.FPDFText_GetFontSize.invokeExact(textPage, i);
                    result.add(new TextCharInfo(charCode, left, bottom, right, top, fontSize));
                }
            }
            return List.copyOf(result);
        });
    }

    /**
     * Check if this page has any extractable text content.
     * This is a lightweight check that avoids loading all character data.
     *
     * @return true if the page has at least one extractable character
     */
    public boolean hasText() {
        return charCount() > 0;
    }

    /**
     * Get all annotations on this page.
     *
     * @return list of annotations, or empty list if none
     */
    public List<PdfAnnotation> annotations() {
        ensureOpen();
        try {
            int count = (int) AnnotBindings.FPDFPage_GetAnnotCount.invokeExact(handle);
            if (count <= 0) {
                return List.of();
            }

            List<PdfAnnotation> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                MemorySegment annot = MemorySegment.NULL;
                try {
                    annot = (MemorySegment) AnnotBindings.FPDFPage_GetAnnot.invokeExact(handle, i);
                    if (FfmHelper.isNull(annot)) continue;

                    int subtypeCode = (int) AnnotBindings.FPDFAnnot_GetSubtype.invokeExact(annot);
                    AnnotationType type = AnnotationType.fromCode(subtypeCode);

                    PdfAnnotation.Rect rect = getAnnotRect(annot);
                    Optional<String> contents = getAnnotStringValue(annot, "Contents");
                    Optional<String> author = getAnnotStringValue(annot, "T");
                    Optional<String> subject = getAnnotStringValue(annot, "Subj");

                    result.add(new PdfAnnotation(type, rect, contents, author, subject));
                } finally {
                    if (!FfmHelper.isNull(annot)) {
                        try {
                            AnnotBindings.FPDFPage_CloseAnnot.invokeExact(annot);
                        } catch (Throwable ignored) {}
                    }
                }
            }
            return List.copyOf(result);
        } catch (PdfiumException e) {
            throw e;
        } catch (Throwable t) {
            throw new PdfiumException("Failed to read annotations", t);
        }
    }

    private static PdfAnnotation.Rect getAnnotRect(MemorySegment annot) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rectSeg = arena.allocate(AnnotBindings.FS_RECTF_LAYOUT);
            int ok = (int) AnnotBindings.FPDFAnnot_GetRect.invokeExact(annot, rectSeg);
            if (ok != 0) {
                float left = rectSeg.get(ValueLayout.JAVA_FLOAT, 0);
                float bottom = rectSeg.get(ValueLayout.JAVA_FLOAT, 4);
                float right = rectSeg.get(ValueLayout.JAVA_FLOAT, 8);
                float top = rectSeg.get(ValueLayout.JAVA_FLOAT, 12);
                return new PdfAnnotation.Rect(left, bottom, right, top);
            }
        } catch (Throwable ignored) {}
        return new PdfAnnotation.Rect(0, 0, 0, 0);
    }

    private static Optional<String> getAnnotStringValue(MemorySegment annot, String key) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment keySeg = arena.allocateFrom(key);

            long needed = (long) AnnotBindings.FPDFAnnot_GetStringValue.invokeExact(
                    annot, keySeg, MemorySegment.NULL, 0L);
            if (needed <= 2) return Optional.empty();

            MemorySegment buf = arena.allocate(needed);
            AnnotBindings.FPDFAnnot_GetStringValue.invokeExact(annot, keySeg, buf, needed);
            String value = FfmHelper.fromWideString(buf, needed);
            return value.isEmpty() ? Optional.empty() : Optional.of(value);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /**
     * Detect and extract web links (URLs) found in the page text.
     * This uses PDFium's text analysis to find URL patterns in the text content.
     *
     * @return list of detected web links, or empty list if none
     */
    public List<PdfLink> webLinks() {
        return withTextPage(List.of(), "Failed to extract web links", textPage -> {
            MemorySegment pageLink = MemorySegment.NULL;
            try {
                pageLink = (MemorySegment) TextBindings.FPDFLink_LoadWebLinks.invokeExact(textPage);
                if (FfmHelper.isNull(pageLink)) return List.of();

                int count = (int) TextBindings.FPDFLink_CountWebLinks.invokeExact(pageLink);
                if (count <= 0) return List.of();

                List<PdfLink> result = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    String url = getWebLinkUrl(pageLink, i);
                    PdfAnnotation.Rect rect = getWebLinkRect(pageLink, i);
                    result.add(new PdfLink(
                            url.isEmpty() ? Optional.empty() : Optional.of(url),
                            rect, -1));
                }
                return List.copyOf(result);
            } finally {
                if (!FfmHelper.isNull(pageLink)) {
                    try { TextBindings.FPDFLink_CloseWebLinks.invokeExact(pageLink); } catch (Throwable ignored) {}
                }
            }
        });
    }

    private static String getWebLinkUrl(MemorySegment pageLink, int linkIndex) {
        try (Arena arena = Arena.ofConfined()) {
            int charCount = (int) TextBindings.FPDFLink_GetURL.invokeExact(
                    pageLink, linkIndex, MemorySegment.NULL, 0);
            if (charCount <= 1) return "";

            MemorySegment buf = arena.allocate((long) charCount * 2);
            TextBindings.FPDFLink_GetURL.invokeExact(pageLink, linkIndex, buf, charCount);
            return FfmHelper.fromWideString(buf, (long) charCount * 2);
        } catch (Throwable t) {
            return "";
        }
    }

    private static PdfAnnotation.Rect getWebLinkRect(MemorySegment pageLink, int linkIndex) {
        try (Arena arena = Arena.ofConfined()) {
            int rectCount = (int) TextBindings.FPDFLink_CountRects.invokeExact(pageLink, linkIndex);
            if (rectCount <= 0) return new PdfAnnotation.Rect(0, 0, 0, 0);

            MemorySegment left = arena.allocate(ValueLayout.JAVA_DOUBLE);
            MemorySegment top = arena.allocate(ValueLayout.JAVA_DOUBLE);
            MemorySegment right = arena.allocate(ValueLayout.JAVA_DOUBLE);
            MemorySegment bottom = arena.allocate(ValueLayout.JAVA_DOUBLE);

            int ok = (int) TextBindings.FPDFLink_GetRect.invokeExact(
                    pageLink, linkIndex, 0, left, top, right, bottom);
            if (ok != 0) {
                return new PdfAnnotation.Rect(
                        (float) left.get(ValueLayout.JAVA_DOUBLE, 0),
                        (float) bottom.get(ValueLayout.JAVA_DOUBLE, 0),
                        (float) right.get(ValueLayout.JAVA_DOUBLE, 0),
                        (float) top.get(ValueLayout.JAVA_DOUBLE, 0));
            }
        } catch (Throwable ignored) {}
        return new PdfAnnotation.Rect(0, 0, 0, 0);
    }

    /**
     * Returns the raw FPDF_PAGE MemorySegment for direct PDFium FFM calls.
     */
    public MemorySegment rawHandle() {
        ensureOpen();
        return handle;
    }

    @FunctionalInterface
    private interface TextPageFunction<T> {
        T apply(MemorySegment textPage) throws Throwable;
    }

    private <T> T withTextPage(T emptyValue, String errorContext, TextPageFunction<T> action) {
        ensureOpen();
        MemorySegment textPage = MemorySegment.NULL;
        try {
            textPage = (MemorySegment) TextBindings.FPDFText_LoadPage.invokeExact(handle);
            if (FfmHelper.isNull(textPage)) {
                throw new PdfiumException(errorContext + ": FPDFText_LoadPage returned NULL");
            }
            return action.apply(textPage);
        } catch (PdfiumException e) {
            throw e;
        } catch (Throwable t) {
            throw new PdfiumException(errorContext, t);
        } finally {
            if (!FfmHelper.isNull(textPage)) {
                try {
                    TextBindings.FPDFText_ClosePage.invokeExact(textPage);
                } catch (Throwable ignored) {}
            }
        }
    }

    private void ensureOpen() {
        ensureThreadConfinement();
        if (closed) throw new IllegalStateException("PdfPage is already closed");
    }

    private void ensureThreadConfinement() {
        Thread current = Thread.currentThread();
        if (current != ownerThread) {
            throw new IllegalStateException(
                    "PdfPage must be accessed from its owner thread. owner="
                            + ownerThread.getName() + ", current=" + current.getName());
        }
    }

    private void ensureRenderBudget(int width, int height) {
        long pixels = (long) width * height;
        if (pixels > maxRenderPixels) {
            throw new PdfiumException("Render exceeds policy pixel budget: " + pixels
                    + " > " + maxRenderPixels + ". Use renderBounded() or lower DPI.");
        }
    }

    @Override
    public void close() {
        ensureThreadConfinement();
        doClose();
    }

    void closeFromDocument() {
        doClose();
    }

    private void doClose() {
        if (closed) return;
        closed = true;
        if (onClose != null) {
            onClose.run();
        }
        try {
            ViewBindings.FPDF_ClosePage.invokeExact(handle);
        } catch (Throwable ignored) {}
    }
}
