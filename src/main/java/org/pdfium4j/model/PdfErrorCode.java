package org.pdfium4j.model;

/**
 * PDFium error codes returned by FPDF_GetLastError().
 */
public enum PdfErrorCode {
    SUCCESS(0, "No error"),
    UNKNOWN(1, "Unknown error"),
    FILE(2, "File not found or cannot be opened"),
    FORMAT(3, "Invalid or corrupt PDF format"),
    PASSWORD(4, "Password required or incorrect"),
    SECURITY(5, "Unsupported security handler"),
    PAGE(6, "Page not found or invalid");

    private final int code;
    private final String description;

    PdfErrorCode(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int code() { return code; }
    public String description() { return description; }

    public static PdfErrorCode fromCode(int code) {
        for (PdfErrorCode e : values()) {
            if (e.code == code) return e;
        }
        return UNKNOWN;
    }
}
