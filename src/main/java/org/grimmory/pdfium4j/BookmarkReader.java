package org.grimmory.pdfium4j;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.internal.DocBindings;
import org.grimmory.pdfium4j.internal.FfmHelper;
import org.grimmory.pdfium4j.model.Bookmark;

final class BookmarkReader {

  private static final int MAX_BOOKMARK_DEPTH = 100;

  private BookmarkReader() {}

  static List<Bookmark> readBookmarks(MemorySegment docHandle) {
    try (Arena arena = Arena.ofConfined()) {
      return collectBookmarkChildren(arena, docHandle, MemorySegment.NULL, 0);
    }
  }

  private static List<Bookmark> collectBookmarkChildren(
      Arena arena, MemorySegment docHandle, MemorySegment parent, int depth) {
    if (depth > MAX_BOOKMARK_DEPTH) return List.of();

    List<Bookmark> result = new ArrayList<>(16);
    MemorySegment child;
    try {
      child = (MemorySegment) DocBindings.FPDFBookmark_GetFirstChild.invokeExact(docHandle, parent);
    } catch (Throwable t) {
      throw new PdfiumException("FPDFBookmark_GetFirstChild failed", t);
    }

    while (!FfmHelper.isNull(child)) {
      result.add(toBookmark(arena, docHandle, child, depth));
      try {
        child =
            (MemorySegment) DocBindings.FPDFBookmark_GetNextSibling.invokeExact(docHandle, child);
      } catch (Throwable t) {
        throw new PdfiumException("FPDFBookmark_GetNextSibling failed", t);
      }
    }
    return List.copyOf(result);
  }

  private static Bookmark toBookmark(
      Arena arena, MemorySegment docHandle, MemorySegment bm, int depth) {
    String title = getBookmarkTitle(arena, bm);
    int pageIndex = resolveBookmarkPage(docHandle, bm);
    List<Bookmark> children = collectBookmarkChildren(arena, docHandle, bm, depth + 1);
    return new Bookmark(title, pageIndex, children);
  }

  private static String getBookmarkTitle(Arena arena, MemorySegment bm) {
    try {
      long needed =
          (long) DocBindings.FPDFBookmark_GetTitle.invokeExact(bm, MemorySegment.NULL, 0L);
      if (needed <= 2) return "";

      MemorySegment buf = arena.allocate(needed);
      long copied = (long) DocBindings.FPDFBookmark_GetTitle.invokeExact(bm, buf, needed);
      if (copied <= 2) {
        return "";
      }
      return FfmHelper.fromWideString(buf, needed);
    } catch (Throwable e) {
      PdfiumLibrary.ignore(e);
      return "";
    }
  }

  private static int resolveBookmarkPage(MemorySegment docHandle, MemorySegment bm) {
    try {
      MemorySegment action = (MemorySegment) DocBindings.FPDFBookmark_GetAction.invokeExact(bm);
      if (!FfmHelper.isNull(action)) {
        long type = (long) DocBindings.FPDFAction_GetType.invokeExact(action);
        if (type == DocBindings.PDFACTION_GOTO) {
          MemorySegment dest =
              (MemorySegment) DocBindings.FPDFAction_GetDest.invokeExact(docHandle, action);
          if (!FfmHelper.isNull(dest)) {
            return (int) DocBindings.FPDFDest_GetDestPageIndex.invokeExact(docHandle, dest);
          }
        }
      }

      MemorySegment dest =
          (MemorySegment) DocBindings.FPDFBookmark_GetDest.invokeExact(docHandle, bm);
      if (!FfmHelper.isNull(dest)) {
        return (int) DocBindings.FPDFDest_GetDestPageIndex.invokeExact(docHandle, dest);
      }
    } catch (Throwable e) {
      PdfiumLibrary.ignore(e);
    }
    return -1;
  }
}
