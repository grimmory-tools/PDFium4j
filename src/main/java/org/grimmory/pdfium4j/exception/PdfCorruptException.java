package org.grimmory.pdfium4j.exception;

import java.io.Serial;
import org.grimmory.pdfium4j.model.PdfErrorCode;

/** Thrown when a PDF document is malformed or corrupt and cannot be parsed. */
public class PdfCorruptException extends PdfiumException {

  @Serial private static final long serialVersionUID = 1L;

  public PdfCorruptException(String message) {
    super(message, PdfErrorCode.FORMAT, "open", null);
  }

  public PdfCorruptException(String message, String filePath) {
    super(message, PdfErrorCode.FORMAT, "open", filePath);
  }

  public PdfCorruptException(
      String message, PdfErrorCode errorCode, String operation, String filePath) {
    super(message, errorCode, operation, filePath);
  }

  public PdfCorruptException(
      String message, PdfErrorCode errorCode, String operation, String filePath, Throwable cause) {
    super(message, errorCode, operation, filePath, cause);
  }
}
