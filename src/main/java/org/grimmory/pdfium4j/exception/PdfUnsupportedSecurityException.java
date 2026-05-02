package org.grimmory.pdfium4j.exception;

import java.io.Serial;
import org.grimmory.pdfium4j.model.PdfErrorCode;

/** Thrown when a PDF uses a security handler unsupported by the current PDFium build. */
public class PdfUnsupportedSecurityException extends PdfiumException {
  @Serial private static final long serialVersionUID = 1L;

  public PdfUnsupportedSecurityException(String message) {
    super(message, PdfErrorCode.SECURITY, "open", null);
  }

  public PdfUnsupportedSecurityException(String message, String filePath) {
    super(message, PdfErrorCode.SECURITY, "open", filePath);
  }

  public PdfUnsupportedSecurityException(
      String message, PdfErrorCode errorCode, String operation, String filePath) {
    super(message, errorCode, operation, filePath);
  }
}
