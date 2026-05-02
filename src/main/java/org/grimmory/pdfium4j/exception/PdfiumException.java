package org.grimmory.pdfium4j.exception;

import java.io.Serial;
import org.grimmory.pdfium4j.model.PdfErrorCode;

/**
 * Base exception for all PDFium operations. Carries structured error information including the
 * PDFium error code and the operation that failed.
 */
public class PdfiumException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final PdfErrorCode errorCode;
  private final String operation;
  private final String filePath;

  public PdfiumException(String message) {
    this(message, PdfErrorCode.UNKNOWN, null, null);
  }

  public PdfiumException(String message, Throwable cause) {
    this(message, PdfErrorCode.UNKNOWN, null, null, cause);
  }

  public PdfiumException(
      String message, PdfErrorCode errorCode, String operation, String filePath) {
    super(formatMessage(message, errorCode, operation, filePath));
    this.errorCode = errorCode;
    this.operation = operation;
    this.filePath = filePath;
  }

  public PdfiumException(
      String message, PdfErrorCode errorCode, String operation, String filePath, Throwable cause) {
    super(formatMessage(message, errorCode, operation, filePath), cause);
    this.errorCode = errorCode;
    this.operation = operation;
    this.filePath = filePath;
  }

  public PdfErrorCode getErrorCode() {
    return errorCode;
  }

  public String getOperation() {
    return operation;
  }

  public String getFilePath() {
    return filePath;
  }

  private static String formatMessage(
      String message, PdfErrorCode errorCode, String operation, String filePath) {
    int capacity =
        (message != null ? message.length() : 0)
            + (operation != null ? operation.length() + 20 : 0)
            + (filePath != null ? filePath.length() + 20 : 0)
            + 64;
    StringBuilder sb = new StringBuilder(capacity);
    if (message != null && !message.isEmpty()) sb.append(message);
    if (errorCode != null
        && errorCode != PdfErrorCode.UNKNOWN
        && errorCode != PdfErrorCode.SUCCESS) {
      if (!sb.isEmpty()) sb.append(" ");
      sb.append("[")
          .append(errorCode.name())
          .append(": ")
          .append(errorCode.description())
          .append("]");
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
