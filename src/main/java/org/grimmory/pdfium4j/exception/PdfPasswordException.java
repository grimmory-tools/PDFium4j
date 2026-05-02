package org.grimmory.pdfium4j.exception;

import java.io.Serial;
import org.grimmory.pdfium4j.model.PdfErrorCode;

/**
 * Thrown when a PDF document requires a password that was not provided or when the provided
 * password is incorrect.
 */
public class PdfPasswordException extends PdfiumException {

  @Serial private static final long serialVersionUID = 1L;

  public PdfPasswordException(String message) {
    super(message, PdfErrorCode.PASSWORD, "open", null);
  }

  public PdfPasswordException(String message, String filePath) {
    super(message, PdfErrorCode.PASSWORD, "open", filePath);
  }

  public PdfPasswordException(
      String message, PdfErrorCode errorCode, String operation, String filePath) {
    super(message, errorCode, operation, filePath);
  }
}
