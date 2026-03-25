package org.pdfium4j.exception;

import org.pdfium4j.model.PdfErrorCode;

/**
 * Thrown when a PDF document requires a password that was not provided
 * or when the provided password is incorrect.
 */
public class PdfPasswordException extends PdfiumException {

    public PdfPasswordException(String message) {
        super(message, PdfErrorCode.PASSWORD, "open", null);
    }

    public PdfPasswordException(String message, String filePath) {
        super(message, PdfErrorCode.PASSWORD, "open", filePath);
    }
}
