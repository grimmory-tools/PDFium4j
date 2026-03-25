package org.pdfium4j.model;

import java.util.List;

/**
 * Result of a PDF health check / diagnostic scan.
 *
 * @param filePath the file that was checked (null for in-memory)
 * @param valid whether the file could be opened and parsed
 * @param pageCount total pages (-1 if undetermined)
 * @param encrypted whether the file is encrypted
 * @param hasText whether any page contains extractable text
 * @param fileVersion PDF version number (e.g. 14 for 1.4, 17 for 1.7)
 * @param warnings list of non-fatal issues found
 */
public record PdfDiagnostic(
        String filePath,
        boolean valid,
        int pageCount,
        boolean encrypted,
        boolean hasText,
        int fileVersion,
        List<String> warnings
) {
    public PdfDiagnostic {
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    /** Whether this appears to be a scanned/image-only PDF (no extractable text). */
    public boolean isImageOnly() {
        return valid && pageCount > 0 && !hasText;
    }

    /** Human-readable PDF version string (e.g. "1.4", "1.7", "2.0"). */
    public String fileVersionString() {
        if (fileVersion <= 0) return "unknown";
        return (fileVersion / 10) + "." + (fileVersion % 10);
    }
}
