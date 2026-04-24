package org.grimmory.pdfium4j.exception;

/** Thrown when a PDF uses a security handler unsupported by the current PDFium build. */
public class PdfUnsupportedSecurityException extends PdfiumException {
  private static final long serialVersionUID = 1L;

  public PdfUnsupportedSecurityException(String message) {
    super(message);
  }
}
