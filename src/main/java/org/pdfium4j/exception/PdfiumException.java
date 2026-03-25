package org.pdfium4j.exception;

import org.pdfium4j.model.PdfErrorCode;

/**
 * Base exception for all PDFium operations.
 * Carries structured error information including the PDFium error code
 * and the operation that failed.
 */
public class PdfiumException extends RuntimeException {

    private final PdfErrorCode errorCode;
    private final String operation;
    private final String filePath;

    public PdfiumException(String message) {
        this(message, PdfErrorCode.UNKNOWN, null, null);
    }

    public PdfiumException(String message, Throwable cause) {
        this(message, PdfErrorCode.UNKNOWN, null, null, cause);
    }

    public PdfiumException(String message, PdfErrorCode errorCode, String operation, String filePath) {
        super(formatMessage(message, errorCode, operation, filePath));
        this.errorCode = errorCode;
        this.operation = operation;
        this.filePath = filePath;
    }

    public PdfiumException(String message, PdfErrorCode errorCode, String operation, String filePath, Throwable cause) {
        super(formatMessage(message, errorCode, operation, filePath), cause);
        this.errorCode = errorCode;
        this.operation = operation;
        this.filePath = filePath;
    }

    /** The PDFium error code, or UNKNOWN if not available. */
    public PdfErrorCode errorCode() {
        return errorCode;
    }

    /** The operation that failed (e.g., "open", "render", "extractText"), or null. */
    public String operation() {
        return operation;
    }

    /** The file path involved, or null for in-memory operations. */
    public String filePath() {
        return filePath;
    }

    private static String formatMessage(String message, PdfErrorCode errorCode, String operation, String filePath) {
        StringBuilder sb = new StringBuilder();
        if (message != null && !message.isEmpty()) sb.append(message);
        if (errorCode != null && errorCode != PdfErrorCode.UNKNOWN && errorCode != PdfErrorCode.SUCCESS) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append("[").append(errorCode.name()).append(": ").append(errorCode.description()).append("]");
        }
        if (operation != null) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append("(operation: ").append(operation).append(")");
        }
        if (filePath != null) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append("(file: ").append(filePath).append(")");
        }
        return sb.toString();
    }
}
