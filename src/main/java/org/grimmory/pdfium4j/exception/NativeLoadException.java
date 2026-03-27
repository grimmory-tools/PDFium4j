package org.grimmory.pdfium4j.exception;

import java.io.Serial;

public class NativeLoadException extends PdfiumException {

  @Serial private static final long serialVersionUID = 1L;

  public NativeLoadException(String message) {
    super(message);
  }

  public NativeLoadException(String message, Throwable cause) {
    super(message, cause);
  }
}
