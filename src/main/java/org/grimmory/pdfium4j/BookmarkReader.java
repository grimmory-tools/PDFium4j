package org.grimmory.pdfium4j;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.internal.FfmHelper;
import org.grimmory.pdfium4j.internal.ScratchBuffer;
import org.grimmory.pdfium4j.internal.ShimBindings;
import org.grimmory.pdfium4j.model.Bookmark;

/** Internal helper to read the document outline (bookmarks) using optimized shim bindings. */
final class BookmarkReader {

  private BookmarkReader() {}

  static List<Bookmark> readBookmarks(MemorySegment docHandle) {
    try {
      MemorySegment first =
          (MemorySegment) ShimBindings.pdfium4jBookmarkFirst().invokeExact(docHandle);
      if (FfmHelper.isNull(first)) {
        return List.of();
      }
      return collectBookmarks(docHandle, first);
    } catch (Error e) {
      throw e;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to read bookmarks", t);
    }
  }

  private static List<Bookmark> collectBookmarks(MemorySegment docHandle, MemorySegment current)
      throws Throwable {
    int count = 0;
    MemorySegment it = current;
    while (!FfmHelper.isNull(it)) {
      count++;
      it = (MemorySegment) ShimBindings.pdfium4jBookmarkNext().invokeExact(docHandle, it);
    }

    List<Bookmark> result = new ArrayList<>(count);
    it = current;
    while (!FfmHelper.isNull(it)) {
      result.add(toBookmark(docHandle, it));
      it = (MemorySegment) ShimBindings.pdfium4jBookmarkNext().invokeExact(docHandle, it);
    }
    return List.copyOf(result);
  }

  private static Bookmark toBookmark(MemorySegment docHandle, MemorySegment bm) throws Throwable {
    String title = getBookmarkTitle(bm);
    int pageIndex = (int) ShimBindings.pdfium4jBookmarkPageIndex().invokeExact(docHandle, bm);

    MemorySegment firstChild =
        (MemorySegment) ShimBindings.pdfium4jBookmarkFirstChild().invokeExact(docHandle, bm);
    List<Bookmark> children =
        FfmHelper.isNull(firstChild) ? List.of() : collectBookmarks(docHandle, firstChild);

    return new Bookmark(title, pageIndex, children);
  }

  private static String getBookmarkTitle(MemorySegment bm) throws Throwable {
    try (var _ = ScratchBuffer.acquireScope()) {
      int needed =
          (int) ShimBindings.pdfium4jBookmarkTitle().invokeExact(bm, MemorySegment.NULL, 0);
      if (needed <= 2) return "";

      MemorySegment buf = ScratchBuffer.get(needed);
      int copied = (int) ShimBindings.pdfium4jBookmarkTitle().invokeExact(bm, buf, needed);
      if (copied <= 2) return "";

      return FfmHelper.fromWideString(buf, copied);
    }
  }
}
