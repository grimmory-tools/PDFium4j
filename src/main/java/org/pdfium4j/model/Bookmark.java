package org.pdfium4j.model;

import java.util.List;

/**
 * A bookmark (outline item) in a PDF document.
 *
 * @param title     the bookmark text
 * @param pageIndex the target page (0-based), or -1 if the destination is external/unresolvable
 * @param children  child bookmarks (may be empty, never null)
 */
public record Bookmark(
        String title,
        int pageIndex,
        List<Bookmark> children
) {
    public Bookmark {
        children = children != null ? List.copyOf(children) : List.of();
    }

    /** Returns true if this bookmark navigates to a page in the current document. */
    public boolean isInternal() {
        return pageIndex >= 0;
    }

    /** Returns true if this bookmark has child bookmarks. */
    public boolean hasChildren() {
        return !children.isEmpty();
    }
}
