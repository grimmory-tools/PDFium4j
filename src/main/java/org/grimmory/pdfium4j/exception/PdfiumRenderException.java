package org.grimmory.pdfium4j.exception;

import java.io.Serial;

/**
 * Thrown when a page rendering operation fails, typically due to memory constraints or corrupted
 * page content.
 *
 * <p>This is an <strong>unchecked</strong> exception.
 */
public class PdfiumRenderException extends PdfiumException {
  @Serial private static final long serialVersionUID = 1L;

  public PdfiumRenderException(String message) {
    super(message);
  }

  public PdfiumRenderException(String message, Throwable cause) {
    super(message, cause);
  }
}
