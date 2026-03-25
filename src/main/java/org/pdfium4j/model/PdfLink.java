package org.pdfium4j.model;

import java.util.Optional;

/**
 * Represents a hyperlink found on a PDF page.
 * Can be either an annotation-based link or a web link detected in text.
 *
 * @param url the URL target (for web links and URI actions)
 * @param rect the bounding rectangle
 * @param targetPage target page index for internal links (-1 if external or unknown)
 */
public record PdfLink(
        Optional<String> url,
        PdfAnnotation.Rect rect,
        int targetPage
) {
    /** Whether this link targets a page within the same document. */
    public boolean isInternal() {
        return targetPage >= 0;
    }

    /** Whether this link opens an external URL. */
    public boolean isExternal() {
        return url.isPresent() && targetPage < 0;
    }
}
