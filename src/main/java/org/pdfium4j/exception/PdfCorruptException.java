package org.pdfium4j.exception;

import org.pdfium4j.model.PdfErrorCode;

/**
 * Thrown when a PDF document is malformed or corrupt and cannot be parsed.
 */
public class PdfCorruptException extends PdfiumException {

    public PdfCorruptException(String message) {
        super(message, PdfErrorCode.FORMAT, "open", null);
    }

    public PdfCorruptException(String message, String filePath) {
        super(message, PdfErrorCode.FORMAT, "open", filePath);
    }
}
